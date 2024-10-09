package com.example.eldgps.retrofit


import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object RetrofitBuilder {


    // Nullable Retrofit instance
    private var retrofitInstance: Retrofit? = null


    // HTTP Logging Interceptor to log request and response details
    private val interceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }


    // OkHttpClient instance
    private val client = OkHttpClient.Builder()
        .addInterceptor(interceptor)
        .retryOnConnectionFailure(true)
        .build()


    // Function to create or return the existing Retrofit instance
    private fun getRetrofitInstance(url: String): Retrofit {
        if (retrofitInstance == null) {
            // Create a new Retrofit instance
            retrofitInstance = Retrofit.Builder()
                .baseUrl(url)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofitInstance!!
    }


    // Static-like method to get API service interface
    fun getApi(url: String): Api {
        return getRetrofitInstance(url).create(Api::class.java)
    }
}




