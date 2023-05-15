package com.example.weatherapp.models

data class Sys(val type : Int,
               val id : Long,
               val country : String,
               val sunrise : Long,
               val sunset : Long) : java.io.Serializable