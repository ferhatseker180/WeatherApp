package com.example.weatherapp.myFuns

import java.text.SimpleDateFormat
import java.util.*

class convertFun {
    fun getUnit(value : String = "°C") : String? {

        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

     fun unixTime(timex: Long) : String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}