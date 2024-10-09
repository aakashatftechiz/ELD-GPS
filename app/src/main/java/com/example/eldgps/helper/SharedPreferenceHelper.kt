package com.example.eldgps.helper

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.example.eldgps.LogData
import com.example.eldgps.Logs
import com.example.eldgps.retrofit.EmulatorData
import com.google.gson.Gson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


const val APPLICATION_ID = "com.videomaster.editor"
const val DATA_POSTED = "data posted"
const val DEVICE_ID = "device id"
const val DEVICE_NAME = "device name"
const val FCM_TOKEN = "fcm token"
const val IS_LOGGED_IN = "is logged in"
const val TELEPHONE = "telephone no"
const val REAL_NUMBER="realTelephone"
const val SYNC_KEY = "sync_key"
const val LOGS_KEY = "log_key"
const val CRASH_LOG_KEY = "crash_log_key"

const val LOG_SIZE = 30

object SharedPreferenceHelper {

    fun saveLogs(ctx: Context, message: String) {
        Log.w("TAG", "saveLogs: saving LOGS!")
        // write the new logs into old logs with a new line
        val gson = Gson()
        var logs = Logs(arrayListOf())
        try {
            val oldLogsJson = getSharedPreference(ctx, LOGS_KEY)
            val oldLogs: Logs? = oldLogsJson?.let { gson.fromJson(it, Logs::class.java) }
            if (oldLogs != null) {
                logs = oldLogs
            }
        } catch (e: Exception) {
            Log.w("TAG", "saveLogs gson Error : ${e.message}")
        }
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = current.format(formatter)
        val logData = LogData(formatted, message)
        logs.data.add(logData)
        while (logs.data.size > LOG_SIZE) {
            logs.data.removeAt(0)
        }
        val jsonObject = gson.toJson(logs)
        setSharedPreference(ctx, LOGS_KEY, jsonObject)
        Log.w("TAG", "saveLogs: saved LOGS!")
    }

    fun setSharedPreference(ctx: Context, Key: String?, Value: String?) {
        val pref = ctx.getSharedPreferences(APPLICATION_ID, Context.MODE_PRIVATE)
        val editor = pref.edit()
        editor.putString(Key, Value)
        editor.apply()
    }

    fun getSharedPreference(ctx: Context): SharedPreferences? {
        return ctx.getSharedPreferences(APPLICATION_ID, Context.MODE_PRIVATE)
    }

    fun getSharedPreference(ctx: Context, Key: String?): String? {
        val pref = ctx.getSharedPreferences(APPLICATION_ID, Context.MODE_PRIVATE)
        return if (pref.contains(Key)) {
            pref.getString(Key, "")
        } else ""
    }

    fun setBooleanSharedPreference(ctx: Context, Key: String?, Value: Boolean) {
        val pref = ctx.getSharedPreferences(APPLICATION_ID, Context.MODE_PRIVATE)
        val editor = pref.edit()
        editor.putBoolean(Key, Value)
        editor.apply()
    }

    fun getBooleanSharedPreference(ctx: Context, Key: String?, defaultValue: Boolean): Boolean {
        val pref = ctx.getSharedPreferences(APPLICATION_ID, Context.MODE_PRIVATE)
        return if (pref.contains(Key)) {
            pref.getBoolean(Key, defaultValue)
        } else defaultValue
    }

    @SuppressLint("HardwareIds")
    fun getEmulatorSharedPreference(ctx: Context): EmulatorData {
        val pref = ctx.getSharedPreferences(APPLICATION_ID, Context.MODE_PRIVATE)
        var deviceId: String? = null
        var deviceName: String? = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ANDROID_ID
        )
        var telephone: String? = "1234567890"
        var fcmToken: String? = "to be added"
        var uuid: String? = ""

        if (pref.contains(DEVICE_NAME)) {
            deviceName = getSharedPreference(ctx, DEVICE_NAME)
        }
        if (pref.contains(DEVICE_ID)) {
            deviceId = getSharedPreference(ctx, DEVICE_ID)
        }
        if (pref.contains(FCM_TOKEN)) {
            fcmToken = getSharedPreference(ctx, FCM_TOKEN)
        }
        if (pref.contains(TELEPHONE)) {
            telephone = getSharedPreference(ctx, TELEPHONE)
        }
        return EmulatorData(
            emulatorName = deviceName,
            emulatorSsid = deviceId,
            fcmToken = fcmToken,
            telephone = telephone,
            latitude = "29",
            longitude = "30"
        )
    }

}
