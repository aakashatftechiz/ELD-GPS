package com.example.eldgps.activity

import android.Manifest.permission
import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.eldgps.services.databinding.ActivityRegisterBinding
import com.example.eldgps.MainActivity
import com.example.eldgps.helper.*
import com.example.eldgps.helper.HelperUtils.BASE_URL
import com.example.eldgps.helper.HelperUtils.CONTENT_TYPE
import com.example.eldgps.helper.HelperUtils.READ_PHONE_REQUEST_CODE
import com.example.eldgps.helper.HelperUtils.hasPermission
import com.example.eldgps.retrofit.EmulatorData
import com.example.eldgps.retrofit.RetrofitBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

private const val REQUEST_CODE_PERMISSIONS = 101

class RegisterActivity : AppCompatActivity() {

    private var isGPSOpen: Boolean = false
    var realNumber: String? = null
    private val binding: ActivityRegisterBinding by lazy {
        ActivityRegisterBinding.inflate(layoutInflater)
    }

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        // requestPermissions()
        Thread {
            while (!HelperUtils.isGpsOpened(this)) {
                try {
                    Thread.sleep(2000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            isGPSOpen = true
        }.start()

        binding.deviceIdViewHint.setOnClickListener {
            Snackbar.make(
                binding.deviceIdViewHint,
                "This is the unique application Id (check on webapp), which your application will use to sync with logbook",
                Snackbar.LENGTH_LONG
            ).show()
        }
        proceedWithPhoneNumberLogic()
    }



    private fun checkMockEnabled() {
        if (!HelperUtils.isAllowMockLocation(this)) {
            setDialog()
        } else {
            if (SharedPreferenceHelper.getBooleanSharedPreference(this, DATA_POSTED, false)) {
                initEmulatorSsid()
                initFcmId()
            }
            if (!SharedPreferenceHelper.getSharedPreference(this, DEVICE_ID).isNullOrBlank()) {
                initUuid()
            }
        }
    }

    private fun setDialog() {
        AlertDialog.Builder(this).setTitle("Enable location mocking")
            .setMessage("Please set it in \"Developer Options → Select mock location information application\"") //这里是中间显示的具体信息
            .setPositiveButton(
                "Set up"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    startActivity(intent)
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            } //setPositiveButton
            .setNegativeButton(
                "Cancel"
            ) { _, _ -> } //setNegativeButton
            .show()
    }

    private fun initEmulatorSsid() {
        val emulatorName =
            SharedPreferenceHelper.getSharedPreference(this@RegisterActivity, DEVICE_NAME)
        if (!emulatorName.isNullOrBlank()) {
            binding.deviceNameView.text = "$emulatorName"
        }
    }

    private fun initFcmId() {
        val fcmToken = SharedPreferenceHelper.getSharedPreference(this@RegisterActivity, FCM_TOKEN)
        if (!fcmToken.isNullOrBlank()) {
            binding.fcmTokenView.text = "Your Device is registered with token :\n $fcmToken"
            binding.deviceIdViewHint.setOnClickListener {
                Snackbar.make(
                    binding.deviceIdViewHint,
                    "This is the application Id (check on webapp), which your application will use to sync with logbook",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun initUuid() {
        val uuid = SharedPreferenceHelper.getSharedPreference(this@RegisterActivity, DEVICE_ID)
        val isLoggedIn = SharedPreferenceHelper.getBooleanSharedPreference(
            this@RegisterActivity, IS_LOGGED_IN, false
        )
        if (!uuid.isNullOrBlank()) {
            binding.deviceIdView.setText(uuid.toString())
            if (isLoggedIn) {
                startActivity(Intent(this, MainActivity::class.java))
            }
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SDK_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkMockEnabled()
            } else {
                Toast.makeText(this, "Allow permission", Toast.LENGTH_SHORT).show()
            }
        }
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            Log.e("permission_check",requestCode.toString())
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                proceedWithPhoneNumberLogic()
                Log.e("permission_check",grantResults[0].toString())
                binding.enterButton.setOnClickListener {
                    if (binding.deviceIdView.text.isNullOrEmpty()) {
                        binding.deviceIdView.error = "Required to sync with logbook service."
                        binding.deviceIdView.requestFocus()
                        return@setOnClickListener
                    }
                    registerFcm(binding.deviceIdView.text.toString())
                    /*Toast.makeText(applicationContext,"Call...",Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, CallForwardActivity::class.java))*/
                }            } else {
                Toast.makeText(this, "Allow permission", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayListOf<String>()
        if (!hasPermission(ACCESS_FINE_LOCATION, this)) {
            permissions.add(ACCESS_FINE_LOCATION)
        }
        if (!hasPermission(POST_NOTIFICATIONS, this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(POST_NOTIFICATIONS)
            }
        }
        if (permissions.isEmpty()) {
            checkMockEnabled()
            return
        }
        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                this, ACCESS_FINE_LOCATION
            ) && !ActivityCompat.shouldShowRequestPermissionRationale(
                this, POST_NOTIFICATIONS
            )
        ) {
            ActivityCompat.requestPermissions(
                this, permissions.toTypedArray(), Companion.SDK_PERMISSION_REQUEST
            )
        } else {
            allowPermission()
        }
    }

    private fun allowPermission() {
        Toast.makeText(this, "Please Allow all required permissions", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", applicationContext.packageName, null)
        intent.data = uri
        startActivityForResult(intent, READ_PHONE_REQUEST_CODE)
    }

    // Proceed with logic that requires reading phone number
    private fun proceedWithPhoneNumberLogic() {
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
                permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(permission.SEND_SMS)
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

        if (!hasPermission(POST_NOTIFICATIONS, this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(POST_NOTIFICATIONS)
            }
        }

        // Request all permissions in one go
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_CODE_PERMISSIONS
            )
        } else {
            // Permissions are granted, now access the phone number
            val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            realNumber = telephonyManager.line1Number  // This line is now safe to use
            Log.d("realNumber", "Phone Number: $realNumber")
            checkMockEnabled()

        }
    }



    @SuppressLint("HardwareIds")
    fun populateAndPostData(token: String, appId: String) {
        val savedEmulatorData = SharedPreferenceHelper.getEmulatorSharedPreference(this)
        if (SharedPreferenceHelper.getBooleanSharedPreference(this, DATA_POSTED, false)) {
            if (savedEmulatorData.fcmToken.isNullOrBlank() || savedEmulatorData.telephone.isNullOrBlank() || savedEmulatorData.emulatorName.isNullOrBlank() || savedEmulatorData.emulatorSsid.isNullOrBlank()) {
                Toast.makeText(this, "Something Went Wrong during login!!", Toast.LENGTH_SHORT)
                    .show()
            }
        }
        val deviceName = Settings.Secure.getString(
            applicationContext.contentResolver, Settings.Secure.ANDROID_ID
        )
        val phoneNumber = "+11234567890"
        SharedPreferenceHelper.setSharedPreference(this, DEVICE_NAME, deviceName)
        SharedPreferenceHelper.setSharedPreference(this, DEVICE_ID, appId)
        SharedPreferenceHelper.setSharedPreference(this, REAL_NUMBER, realNumber)
        SharedPreferenceHelper.setSharedPreference(this, FCM_TOKEN, token)
        SharedPreferenceHelper.setSharedPreference(this, TELEPHONE, phoneNumber)

        val emulatorData = EmulatorData(
            emulatorSsid = appId,
            realTelephone = realNumber,
            emulatorName = deviceName,
            fcmToken = token,
        )
        postData(emulatorData)
    }

    private fun postData(emulatorData: EmulatorData) {
        val apiClient = RetrofitBuilder.getApi(BASE_URL)
        Log.e("TAG", "postData: $emulatorData")
        apiClient.postData(CONTENT_TYPE, emulatorData)?.enqueue(object : Callback<EmulatorData?> {
            override fun onResponse(
                call: Call<EmulatorData?>, response: Response<EmulatorData?>
            ) {
                val emulatorDataResponse = response.body()
                Log.e("LoginResponseBODY", response.body()?.telephone.toString())

                // Saving a value to SharedPreferences
                val sharedPreferences: SharedPreferences =
                    this@RegisterActivity.getSharedPreferences("MyPreferences", MODE_PRIVATE)
                val editor = sharedPreferences.edit()

// Save data with key-value pairs
                Log.e("Response_Telephone",response.body()?.telephone.toString())
                editor.putString("telephone", response.body()?.telephone.toString())
                editor.putString("realNumber", realNumber)
                Toast.makeText(
                    this@RegisterActivity,
                    "Retrieved Number : $realNumber",
                    Toast.LENGTH_SHORT
                ).show()


// Apply the changes asynchronously
                editor.apply() // or use commit() if you want to save immediately (blocking)

                if (response.code() == 200 && emulatorDataResponse != null && !emulatorDataResponse.emulatorSsid.isNullOrBlank()) {
                    SharedPreferenceHelper.setBooleanSharedPreference(
                        this@RegisterActivity, DATA_POSTED, true
                    )
                    SharedPreferenceHelper.setBooleanSharedPreference(
                        this@RegisterActivity, IS_LOGGED_IN, true
                    )
                    SharedPreferenceHelper.setSharedPreference(
                        this@RegisterActivity, DEVICE_ID, emulatorDataResponse.emulatorSsid
                    )
                    SharedPreferenceHelper.setSharedPreference(
                        this@RegisterActivity, DEVICE_NAME, emulatorDataResponse.emulatorName
                    )
                    SharedPreferenceHelper.setSharedPreference(
                        this@RegisterActivity, TELEPHONE, emulatorDataResponse.telephone
                    )
                    Toast.makeText(
                        this@RegisterActivity, "Logged In Successfully!", Toast.LENGTH_SHORT
                    ).show()
                    val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                    startActivity(intent)
                } else {
                    showErrorDialog(response.errorBody().toString())
                }
            }

            override fun onFailure(call: Call<EmulatorData?>, t: Throwable) {
                Log.d("@response", "onFailure: $t")
                showErrorDialog(error = t.message)
            }
        })
    }

    private fun showErrorDialog(error: String?) {
        val message = error ?: "Do you want to try again?"
        AlertDialog.Builder(this@RegisterActivity).setTitle("Login failed").setMessage(message)
            .setPositiveButton(
                "Okay"
            ) { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun registerFcm(ssid: String) {
        CoroutineScope(Dispatchers.IO).launch {
            var token = ""
            val firebaseMessaging = FirebaseMessaging.getInstance()
            firebaseMessaging.token.addOnCompleteListener { result ->
                try {
                    firebaseMessaging.subscribeToTopic("all_users")
                    token = result.result
                    Log.e("TAG", "registerFcm: $token")
                    populateAndPostData(token, ssid)
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        private const val SDK_PERMISSION_REQUEST = 127
    }
}