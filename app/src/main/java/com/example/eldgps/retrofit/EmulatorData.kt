package com.example.eldgps.retrofit

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class EmulatorData(
    @SerializedName("emulatorName")
    @Expose
    val emulatorName:String? = null,

    @SerializedName("emulatorSsid")
    @Expose
    val emulatorSsid: String? = null,

    @SerializedName("fcmToken")
    @Expose
    val fcmToken:String? = null,

    @SerializedName("telephone")
    @Expose
    val telephone:String? = null,

    @SerializedName("latitude")
    @Expose
    var latitude:String? = null,

    @SerializedName("longitude")
    @Expose
    var longitude:String? = null,

    @SerializedName("realTelephone")
    @Expose
    var realTelephone:String?=null



) {
    override fun toString(): String {
        return "EmulatorData(emulatorName=$emulatorName, emulatorSsid=$emulatorSsid,realTelephone=$realTelephone, fcmToken=$fcmToken, telephone=$telephone, latitude=$latitude, longitude=$longitude)"
    }
}
