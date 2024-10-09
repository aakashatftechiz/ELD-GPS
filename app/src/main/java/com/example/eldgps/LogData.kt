package com.example.eldgps

data class LogData(val time: String, val message: String)

data class Logs(val data: ArrayList<LogData>)
