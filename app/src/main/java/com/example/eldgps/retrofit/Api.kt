package com.example.eldgps.retrofit

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface Api {

    @POST("login")
     fun postData(@Header("Content-Type") contentType: String = "application/json", @Body body: EmulatorData) : Call<EmulatorData?>?

     @POST("saveMessage")
     fun postMessage(@Header("Content-Type") contentType: String = "application/json",@Body body: MessageData) : Call<MessageData>?
}