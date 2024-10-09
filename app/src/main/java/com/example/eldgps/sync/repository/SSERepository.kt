package com.example.eldgps.sync.repository

import android.content.SharedPreferences
import android.util.Log
import com.example.eldgps.helper.DEVICE_ID
import com.example.eldgps.helper.HelperUtils.SSE_URL
import com.example.eldgps.helper.IS_LOGGED_IN
import com.example.eldgps.sync.entity.SSEEventData
import com.example.eldgps.sync.entity.STATUS
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

class SSERepository(sharedPref: SharedPreferences) {

    private val sseClient =
        OkHttpClient.Builder().connectTimeout(0, TimeUnit.MILLISECONDS).readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS).build()

    private val id = sharedPref.getString(DEVICE_ID, null)

    private val sseRequest =
        Request.Builder().method("GET", null).url(SSE_URL + id).header("Accept", "application/json")
            .addHeader("Accept", "text/event-stream").build()

    private var eventSource: EventSource? = null

    var sseEventsFlow = MutableStateFlow(SSEEventData(STATUS.NONE))
        private set
    var syncEventsFlow = MutableStateFlow(String())
        private set

    private val sseEventSourceListener = object : EventSourceListener() {
        override fun onClosed(eventSource: EventSource) {
            super.onClosed(eventSource)
            Log.d(TAG, "onClosed: $eventSource")
            val event = SSEEventData(STATUS.CLOSED)
            sseEventsFlow.tryEmit(event)
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            super.onEvent(eventSource, id, type, data)
            Log.d(TAG, "onMYEvent: $data")
            if (data.isNotEmpty()) {

                val current = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                val formatted = current.format(formatter)
                syncEventsFlow.tryEmit(formatted)

                val sseEventData = Gson().fromJson(data, SSEEventData::class.java)
                sseEventsFlow.tryEmit(sseEventData)
            }
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            super.onFailure(eventSource, t, response)
            Log.e(TAG, "onFailure: $eventSource \n $t \n $response")
            Log.i(TAG, "onFailure isLoggedIn? : ${sharedPref.getBoolean(IS_LOGGED_IN, true)}")
            if (sharedPref.getBoolean(IS_LOGGED_IN, true)) {
                // wait for 5 seconds and try to reconnect
                CoroutineScope(Dispatchers.Main).launch {
                    delay(5000)
                    initEventSource()
                }
                val event = SSEEventData(STATUS.ERROR)
                sseEventsFlow.tryEmit(event)
                return
            }
            val event = SSEEventData(STATUS.ERROR)
            sseEventsFlow.tryEmit(event)
            return
        }

        override fun onOpen(eventSource: EventSource, response: Response) {
            super.onOpen(eventSource, response)
            Log.d(TAG, "onOpen: $eventSource")
            val event = SSEEventData(STATUS.OPEN)
            sseEventsFlow.tryEmit(event)
        }
    }

    init {
        CoroutineScope(Dispatchers.Main).launch {
            initEventSource()
        }
    }

    fun initEventSource() {
        val event = SSEEventData(STATUS.NONE)
        sseEventsFlow.tryEmit(event)
        try {
            Log.w(TAG, "initEventSource: ${SSE_URL + id}")
            eventSource = EventSources.createFactory(sseClient)
                .newEventSource(request = sseRequest, listener = sseEventSourceListener)
        } catch (e: Exception) {
            Log.e(TAG, "initEventSource ERROR : $e")
        }
    }

    fun stopSSERequest() {
        eventSource?.cancel()
        sseClient.dispatcher.executorService.shutdown()
    }

    companion object {
        private const val TAG = "SSERepository"
    }

}