package com.example.eldgps.listeners

import android.location.Location

interface LocationUpdateListener {
    fun onLocationUpdate(location: Location)
}