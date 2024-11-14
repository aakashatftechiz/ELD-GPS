package com.example.eldgps

import android.Manifest.permission
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ListView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.eldgps.services.R
import com.eldgps.services.databinding.ActivityMainBinding
import com.example.eldgps.activity.RegisterActivity
import com.example.eldgps.callforwardingstatus.CallForwardingReceiver
import com.example.eldgps.callforwardingstatus.sms.ForwardingConfig
import com.example.eldgps.callforwardingstatus.sms.ListAdapter
import com.example.eldgps.callforwardingstatus.sms.SmsReceiverService
import com.example.eldgps.helper.*
import com.example.eldgps.helper.HelperUtils.isServiceRunning
import com.example.eldgps.services.MockGpsService


private const val REQUEST_CODE_ALL_PERMISSIONS = 101
private const val SDK_PERMISSION_REQUEST = 127

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val mainActivityMainBinding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private var isMockLocOpen = false
    private var isGPSOpen = false
    private var gpsPrompted = false
    private var listAdapter: ListAdapter? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mainActivityMainBinding.root)

        // Display device details
        displayDeviceDetails()

        // Check and request all necessary permissions once
        checkAndRequestPermissions()

        // Clipboard logic
        setupClipboardListeners()

        // Observe log changes
        observeLogs()

        // Set up logout logic
        setupLogoutButton()

        // Start the GPS checking thread
        startGpsCheckThread()
    }

    // Display device details in the UI
    private fun displayDeviceDetails() {
        val deviceId = SharedPreferenceHelper.getSharedPreference(this, DEVICE_ID)
        if (!deviceId.isNullOrBlank()) {
            mainActivityMainBinding.deviceIdView.text = "SSID :\n $deviceId"
        }

        val device = """
           FINGERPRINT:${Build.FINGERPRINT}
           MODEL:${Build.MODEL}
           MANUFACTURER:${Build.MANUFACTURER}
           BRAND:${Build.BRAND}
           DEVICE:${Build.DEVICE}
           BOARD:${Build.BOARD}
           HOST:${Build.HOST}
           PRODUCT:${Build.PRODUCT}
           """.trimIndent()

        Log.i("Device Info", "onCreate: $device")
    }

    // Check and request all necessary permissions once
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check each permission and add it to the list if not granted
        if (ActivityCompat.checkSelfPermission(
                this,
                permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(permission.READ_PHONE_STATE)
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(permission.READ_SMS)
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(permission.CALL_PHONE)
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                permission.RECEIVE_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(permission.RECEIVE_SMS)
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(permission.SEND_SMS)
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(permission.ACCESS_FINE_LOCATION)
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(permission.ACCESS_COARSE_LOCATION)
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                permission.READ_PHONE_NUMBERS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(permission.READ_PHONE_NUMBERS)
        }

        // Request all permissions in one go
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_CODE_ALL_PERMISSIONS
            )
        } else {
            // All permissions are already granted
            proceedWithAllPermissionsGranted()
        }
    }

    // Handle the result of the permission request
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_ALL_PERMISSIONS) {
            var allPermissionsGranted = true

            // Check if all permissions were granted
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }

            if (allPermissionsGranted) {
                // Proceed with app logic if all permissions were granted
                proceedWithAllPermissionsGranted()
            } else {
                // If any permission is denied, show a message to the user
                Toast.makeText(
                    this,
                    "All permissions are required to use this app",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // This method will be called once all permissions are granted
    private fun proceedWithAllPermissionsGranted() {



        // Broadcast the phone number
        // Retrieve data from SharedPreferences
        val telephone = SharedPreferenceHelper.getSharedPreference(this, TELEPHONE) ?: ""
        Log.e("call_telephone", telephone)
        Toast.makeText(this,"Virtual Number: $telephone",Toast.LENGTH_SHORT).show()

        val intent = Intent("de.kaiserdragon.callforwardingstatus.TOGGLE_CALL_FORWARDING")
        intent.setClass(applicationContext, CallForwardingReceiver::class.java)
        intent.putExtra("phoneNumber", telephone)//+919760375225 dummy for india
        applicationContext.sendBroadcast(intent)

        // Proceed with SMS-related logic
        proceedWithSmsLogic()

        // Proceed with GPS-related logic
        setupMockGpsService()
    }

    // Proceed with SMS-related logic
    private fun proceedWithSmsLogic() {
        showList()
    }

    // Show the list of forwarding configurations
    private fun showList() {
        val listView = findViewById<ListView>(R.id.listMView)

        val configs: ArrayList<ForwardingConfig> = ArrayList()
        configs.add(ForwardingConfig.getConfig(this))
        listAdapter = ListAdapter(configs, this)
        listView.adapter = listAdapter
        if (!this.isServiceRunning) {
            startService()
        }
    }

    // Setup clipboard copy actions for device ID and logs
    private fun setupClipboardListeners() {
        mainActivityMainBinding.deviceIdViewCpy.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("ssid", mainActivityMainBinding.deviceIdView.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "COPIED!", Toast.LENGTH_SHORT).show()
        }

        mainActivityMainBinding.logViewCpy.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val stringBuilder = StringBuilder()
            val crashLog = SharedPreferenceHelper.getSharedPreference(this, CRASH_LOG_KEY)
            val logs = SharedPreferenceHelper.getSharedPreference(this, LOGS_KEY)
            stringBuilder.append("Crash Logs: ").append(crashLog).append("\nLogs : ").append(logs)
                .append("\n")
            val clip = ClipData.newPlainText("logs", stringBuilder.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "COPIED!", Toast.LENGTH_SHORT).show()
        }
    }

    // Setup logout button
    private fun setupLogoutButton() {
        mainActivityMainBinding.logoutButton.setOnClickListener {
            stopService(Intent(this, MockGpsService::class.java))
            SharedPreferenceHelper.setBooleanSharedPreference(this, IS_LOGGED_IN, false)
            SharedPreferenceHelper.setBooleanSharedPreference(this, DATA_POSTED, false)
            SharedPreferenceHelper.setSharedPreference(this, DEVICE_NAME, "")
            SharedPreferenceHelper.setSharedPreference(this, TELEPHONE, "")
            SharedPreferenceHelper.setSharedPreference(this, REAL_NUMBER, "")
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    // Observe logs in shared preferences
    private fun observeLogs() {
        val pref = SharedPreferenceHelper.getSharedPreference(this)
        pref?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null) return
        when (key) {
            LOGS_KEY -> updateLogs(sharedPreferences.getString(LOGS_KEY, "EMPTY LOGS"))
            CRASH_LOG_KEY -> mainActivityMainBinding.crashLog.text =
                sharedPreferences.getString(CRASH_LOG_KEY, "EMPTY CRASH LOGS")

            SYNC_KEY -> mainActivityMainBinding.lastSyncView.text =
                "LAST SYNC : " + sharedPreferences.getString(SYNC_KEY, "EMPTY SYNC LOG")
        }
    }

    // Update logs in the UI
    private fun updateLogs(logs: String?) {
        mainActivityMainBinding.logs.text = logs ?: "No logs available"
    }

    private fun startService() {
        val appContext = applicationContext
        val receiverIntent = Intent(this, SmsReceiverService::class.java)
        appContext.startForegroundService(receiverIntent)
    }

    private val isServiceRunning: Boolean
        get() {
            val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (SmsReceiverService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }

    // Check permission and start mock GPS service
    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkPermissionAndRunMockGpsService() {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                permission.ACCESS_FINE_LOCATION
            )
        ) {
            val permissions = ArrayList<String>()
            if (checkSelfPermission(permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(permission.ACCESS_FINE_LOCATION)
            }
            if (checkSelfPermission(permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(permission.ACCESS_COARSE_LOCATION)
            }
            if (permissions.isNotEmpty()) {
                requestPermissions(permissions.toTypedArray(), SDK_PERMISSION_REQUEST)
            } else {
                setupMockGpsService()
            }
        } else {
            allowPermission()
        }
    }

    // Setup Mock GPS service
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupMockGpsService() {
        val mockLocServiceIntent = Intent(this, MockGpsService::class.java)
        if (!HelperUtils.isAllowMockLocation(this).also { isMockLocOpen = it }) {
            setDialog()
        } else {
            if (isServiceRunning(this, MockGpsService::class.java)) {
                stopService(mockLocServiceIntent)
            }
            startForegroundService(mockLocServiceIntent)
            Toast.makeText(this@MainActivity, "Location mocking is on", Toast.LENGTH_LONG).show()
        }
    }

    // Show dialog for enabling mock location
    private fun setDialog() {
        AlertDialog.Builder(this).setTitle("Enable location mocking")
            .setMessage("Please set it in \"Developer Options → Select mock location information application\"")
            .setPositiveButton("Set up") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }

    // Start GPS checking thread
    private fun startGpsCheckThread() {
        Thread {
            while (!HelperUtils.isGpsOpened(this)) {
                if (!isGPSOpen && !gpsPrompted) {
                    runOnUiThread {
                        setGpsDialog()
                    }
                }
                try {
                    Thread.sleep(2000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            isGPSOpen = true
        }.start()
    }

    // Show dialog to enable GPS
    private fun setGpsDialog() {
        gpsPrompted = true
        AlertDialog.Builder(this).setTitle("Enable Location")
            .setMessage("Please turn on Locations")
            .setPositiveButton("Set up") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> gpsPrompted = false }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun allowPermission() {
        if (checkSelfPermission(permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivityForResult(intent, 101)
        } else {
            setupMockGpsService()
        }
    }
}


