package com.example.eldgps.retrofit

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class MessageData(
    @SerializedName("realTelephone")
    @Expose
    val realTelephone: String? = null,

    @SerializedName("message")
    @Expose
    val message: String? = null
) {
    override fun toString(): String {
        return "MessageData(realTelephone=$realTelephone, message=$message)"
    }
}