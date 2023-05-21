package com.example.weatherapp.network

import com.example.weatherapp.models.weatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface weatherService {
    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lon : Double,
        @Query("lon") lat : Double,
        @Query("units") units: String?,
        @Query("appid") appid: String?
    ) : Call<weatherResponse>
}