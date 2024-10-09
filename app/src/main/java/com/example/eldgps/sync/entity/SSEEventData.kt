package com.example.eldgps.sync.entity

import com.example.eldgps.Latlng

data class SSEEventData(
    val status: STATUS? = null,
    val latLng: Latlng? = null,
    val time: String? = null,
    val phoneNumber: String? = null,
    val messageContent: String? = null
)

enum class STATUS {
    SUCCESS,
    ERROR,
    NONE,
    CLOSED,
    SYNC,
    OPEN
}