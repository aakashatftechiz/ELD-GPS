package com.example.eldgps.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.location.LocationProvider
import android.location.provider.ProviderProperties
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.eldgps.services.R
import com.example.eldgps.Latlng
import com.example.eldgps.helper.APPLICATION_ID
import com.example.eldgps.helper.CRASH_LOG_KEY
import com.example.eldgps.helper.DEVICE_ID
import com.example.eldgps.helper.SYNC_KEY
import com.example.eldgps.helper.SharedPreferenceHelper
import com.example.eldgps.sync.database.getDatabase
import com.example.eldgps.sync.domain.TripPoint
import com.example.eldgps.sync.entity.STATUS
import com.example.eldgps.sync.network.dto.AddressUpdateRequest
import com.example.eldgps.sync.repository.SyncRepository
import com.example.eldgps.sync.viewmodels.DevByteViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.lang.Thread.UncaughtExceptionHandler
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


class MockGpsService : LifecycleService() {
    private var latLng: Latlng? = null
    private val TAG = "MockGpsService"
    private lateinit var locationManager: LocationManager

    /**
     * Builder of the current notification
     */
    private lateinit var notificationBuilder: Notification.Builder

    private var viewModel: DevByteViewModel? = null

    // Custom CoroutineScope tied to the service's lifecycle
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var destroyService = false

    //    private var emulatorDetails: EmulatorDetails? = null
    var tripPoints: List<TripPoint>? = null

    /**
     * Event triggered for network error. This is private to avoid exposing a
     * way to set this value to observers.
     */
    private var _eventNetworkError = MutableLiveData(false)

    /**
     * Event triggered for network error. Views should use this to get access
     * to the data.
     */
    val eventNetworkError: LiveData<Boolean>
        get() = _eventNetworkError

    /**
     * Flag to display the error message. This is private to avoid exposing a
     * way to set this value to observers.
     */
    private var _isNetworkErrorShown = MutableLiveData(false)

    /**
     * Flag to display the error message. Views should use this to get access
     * to the data.
     */
    val isNetworkErrorShown: LiveData<Boolean>
        get() = _isNetworkErrorShown

    //经纬度字符串
    private var latLngInfo: String? = null
    private var isCustomLocation: Boolean = false
    private var isNewTrip: Boolean = false

    companion object {
        lateinit var notificationManager: NotificationManager

        //        lateinit var emulatorRepository: EmulatorRepository
//        lateinit var tripPointsRepository: TripPointsRepository
//        lateinit var stopPointsRepository: StopPointsRepository
        lateinit var syncRepository: SyncRepository
        lateinit var sharedPreferences: SharedPreferences

        const val NOTIFICATION_ID: Int = 1
    }

    //log debug
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    @SuppressLint("WrongConstant")
    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).i("onCreate: STARTED!")
        sharedPreferences = getSharedPreferences(APPLICATION_ID, Context.MODE_PRIVATE)
        syncRepository = SyncRepository(getDatabase(application), sharedPreferences)
        locationManager = this.getSystemService(LOCATION_SERVICE) as LocationManager
        viewModel = DevByteViewModel(application, sharedPreferences)
        rmNetworkTestProvider()
        rmGPSTestProvider()
        setNetworkTestProvider()
        setGPSTestProvider()

        eventNetworkError.observe(this) { networkError ->
            if (networkError) {
                updateNotification("Network Connection Not available.")
            }
        }

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (isCustomLocation) {
            setGpsLocation()
            setNetworkLocation()
        }
        serviceScope.launch {
            // Schedule periodic location updates
            while (!destroyService) {
                setMockLocation(LocationManager.NETWORK_PROVIDER)
                setMockLocation(LocationManager.GPS_PROVIDER)
                delay(100)
            }
        }

        viewModel?.syncEvents?.observe(this) {
            SharedPreferenceHelper.setSharedPreference(this, SYNC_KEY, it)
        }

        viewModel?.sseEvents?.observe(this) {
            it?.let { event ->
                when (event.status) {
                    STATUS.SYNC -> {
                        val current = LocalDateTime.now()
                        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                        val formatted = current.format(formatter)
                        SharedPreferenceHelper.setSharedPreference(this, SYNC_KEY, formatted)
                    }

                    STATUS.OPEN -> {
                        Timber.tag(TAG).i("SSE: session opened")
                        updateNotification("Session opened")
                    }

                    STATUS.SUCCESS -> {
                        Timber.tag(TAG).i("SSE: Data received")
                        updateNotification("Data received" + event.latLng.toString())
                        if (event.latLng != null) {
                            try {
                                this.latLng = event.latLng
                                locationManager.setTestProviderLocation(
                                    LocationManager.NETWORK_PROVIDER,
                                    generateLocation(event.latLng, LocationManager.NETWORK_PROVIDER)
                                )

                                locationManager.setTestProviderLocation(
                                    LocationManager.GPS_PROVIDER,
                                    generateLocation(event.latLng, LocationManager.GPS_PROVIDER)
                                )

                                sendNewAddressString(event.latLng)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@MockGpsService, e.message, Toast.LENGTH_SHORT
                                ).show()
                            }

                        } else {
                            updateNotification("No image received")
                        }

                    }

                    STATUS.ERROR -> {
                        Timber.tag(TAG).e("onCreate: Error Connecting to SSE")
                        updateNotification("Session Error, reconnecting...")
                    }

                    STATUS.CLOSED -> {
                        Timber.tag(TAG).e("onCreate: Session None")
                        updateNotification("Session closed")
                    }

                    else -> {
                        // STATUS.NONE
                        Timber.tag(TAG).e("onCreate: Session None")
                        updateNotification("Session None")
                        if (!event.phoneNumber.isNullOrEmpty() && !event.messageContent.isNullOrEmpty()) {
                            //send sms

                            try {
                                val smsManager: SmsManager = SmsManager.getDefault()
                                smsManager.sendTextMessage("+"+event.phoneNumber, null, event.messageContent, null, null)
                                Toast.makeText(applicationContext, "Message Sent", Toast.LENGTH_LONG).show()
                            } catch (e: java.lang.Exception) {
                                Toast.makeText(applicationContext, "Some fields is Empty", Toast.LENGTH_LONG).show()
                            }

                        }else{
                            //Do nothing
                        }

                    }
                }
            }
        }
        viewModel?.getSSEEvents()
        viewModel?.getSyncEvents()
        registerUncaughtExceptionHandler()
    }

    private fun sendNewAddressString(latLng: Latlng) {
        val request = sharedPreferences.getString(DEVICE_ID, null)?.let {
            AddressUpdateRequest(
                emulatorSsid = it, address = getAddressString(
                    latLng.latitude, latLng.longitude
                )
            )
        }
        if (request == null) {
            updateNotification("Unable to update address")
            return
        }
        try {
            updateNotification("Updating emulator Address")
            CoroutineScope(Dispatchers.IO).launch {
                syncRepository.updateAddress(request)
                updateNotification("Updated emulator Address")
            }
        } catch (error: IOException) {
            error.printStackTrace()
            updateNotification("Unable to update address")
        }
    }

    private fun getAddressString(latitude: Double, longitude: Double): String {
        try {
            val geocoder = Geocoder(this) // Replace 'context' with your actual context
            val addressList = geocoder.getFromLocation(latitude, longitude, 1)
            var addressString = ""
            if (!addressList.isNullOrEmpty()) {
                val address = addressList[0]
                // Extract the AddressComponentType for each address component
                for ((index, i) in (0..address.maxAddressLineIndex).withIndex()) {
                    if (index != address.maxAddressLineIndex) {
                        addressString = addressString + (address.getAddressLine(i)) + " ,"
                    } else {
                        addressString += (address.getAddressLine(i))
                    }
                }
            }
            Timber.e("addressString : %s", addressString)
            return addressString
        } catch (e: Exception) {
            return "N/A"
        }
    }

    // Function to set mock location based on the provider (NETWORK_PROVIDER or GPS_PROVIDER)
    private fun setMockLocation(provider: String) {
        if (latLng == null) {
            return
        }
        try {
            locationManager.setTestProviderLocation(
                provider, generateLocation(latLng!!, provider)
            )
        } catch (e: Exception) {
            Timber.e("setMockLocation error: $provider")
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        latLngInfo = intent?.getStringExtra("key")
        isCustomLocation = intent?.getBooleanExtra("isCustomLocation", false) == true
        isNewTrip = intent?.getBooleanExtra("isNewTrip", false) == true
        val channelId = resources.getString(R.string.custom_notification_channel_id)
        val name = resources.getString(R.string.custom_notification_channel_name)
        val title = if (isNewTrip) {
            "Mocking New Trip"
        } else {
            "Mocking Trip "
        }
        val desc = "Location Service Running"
        val mChannel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(mChannel)
        notificationBuilder =
            Notification.Builder(this, channelId).setContentTitle(title).setContentText(desc)
                .setSmallIcon(R.mipmap.ic_launcher)
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
        return START_STICKY
    }

    override fun onDestroy() {
        destroyService = true
        //remove test provider
        rmNetworkTestProvider()
        rmGPSTestProvider()
        viewModel?.stopSSERequest()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    //remove network provider
    private fun rmNetworkTestProvider() { //#1
        try {
            val providerStr = LocationManager.NETWORK_PROVIDER
            if (locationManager.isProviderEnabled(providerStr)) {
                Log.d(TAG, "now remove NetworkProvider")
                locationManager.removeTestProvider(providerStr)
            } else {
                Log.d(TAG, "NetworkProvider is not enabled")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG, "rmNetworkProvider error")
        }
    }

    // for test: set GPS provider
    private fun rmGPSTestProvider() { //#2
        try {
            val providerStr = LocationManager.GPS_PROVIDER
            if (locationManager.isProviderEnabled(providerStr)) {
                Log.d(TAG, "now remove GPSProvider")
                locationManager.removeTestProvider(providerStr)
            } else {
                Log.d(TAG, "GPSProvider is not enabled")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG, "rmGPSProvider error")
        }
    }

    //set new network provider
    private fun setNetworkTestProvider() { //#3
        val providerStr = LocationManager.NETWORK_PROVIDER
        try {
            locationManager.addTestProvider(
                providerStr,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                1,
                ProviderProperties.ACCURACY_FINE
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        if (!locationManager.isProviderEnabled(providerStr)) {
            try {
                locationManager.setTestProviderEnabled(providerStr, true)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d(TAG, "setTestProviderEnabled[NETWORK_PROVIDER] error")
            }
        }
    }

    private fun setGPSTestProvider() {//#4
        try {
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER, false, true, true, false, true, true, true, 1, 1
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            try {
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        locationManager.setTestProviderStatus(
            LocationManager.GPS_PROVIDER,
            LocationProvider.AVAILABLE,
            null,
            System.currentTimeMillis()
        )
    }

    private fun setNetworkLocation() {
        if (latLng == null) {
            return
        }
        val providerStr = LocationManager.NETWORK_PROVIDER
        try {
            locationManager.setTestProviderLocation(
                providerStr, generateLocation(latLng!!, providerStr)
            )
        } catch (e: Exception) {
            Log.d(TAG, "setNetworkLocation error")
            e.printStackTrace()
        }
    }

    private fun setGpsLocation() {
        if (latLng == null) {
            return
        }
        val providerStr = LocationManager.GPS_PROVIDER
        try {
            locationManager.setTestProviderLocation(
                providerStr, generateLocation(latLng!!, providerStr)
            )
        } catch (e: Exception) {
            Log.d(TAG, "setNetworkLocation error")
            e.printStackTrace()
        }
    }

    private fun generateLocation(latLng: Latlng, provider: String): Location { //#6
        val loc = Location(provider)
        loc.accuracy = 2.0f
        loc.altitude = 55.0
        loc.bearing = latLng.bearing
        val bundle = Bundle()
        bundle.putInt("satellites", 7)
        loc.extras = bundle
        loc.latitude = latLng.latitude
        loc.longitude = latLng.longitude
        loc.time = System.currentTimeMillis()
        loc.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        return loc
    }

    private fun updateNotification(str: String?) {
        if (str != null) {
          /*  val notification = notificationBuilder.setContentText(str).build()
            notificationManager.notify(NOTIFICATION_ID, notification)*/
            SharedPreferenceHelper.saveLogs(this, str)
        }
    }

    private lateinit var mDefaultUncaughtExceptionHandler: UncaughtExceptionHandler

    private fun registerUncaughtExceptionHandler() {
        mDefaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex -> // Save Log
            saveLog(ex)
            // Throw system
            mDefaultUncaughtExceptionHandler.uncaughtException(thread, ex)
        }
    }

    private fun saveLog(exception: Throwable) {
        try {
            val stackTrace = Log.getStackTraceString(exception)
            writeCrashLog(stackTrace)
            // Save it to SharedPreferences or DB as you like
        } catch (e: java.lang.Exception) {
        }
    }

    private fun writeCrashLog(message: String) {
        SharedPreferenceHelper.setSharedPreference(this, CRASH_LOG_KEY, message)
    }
}