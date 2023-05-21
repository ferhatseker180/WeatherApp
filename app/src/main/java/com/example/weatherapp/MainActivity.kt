package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.weatherResponse
import com.example.weatherapp.network.weatherService
import com.example.weatherapp.utils.Constants
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var tasarim : ActivityMainBinding

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgressDialog : Dialog? = null
    private lateinit var mSharedPreferences : SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tasarim = ActivityMainBinding.inflate(layoutInflater)
        setContentView(tasarim.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)

        setupUI()

        if (!isLocationEnabled()) {
            Toast.makeText(this@MainActivity, "Your Location Provider Is Turned OFF. Please Turn It", Toast.LENGTH_LONG).
            show()
            val intent = startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else {
            Dexter.withActivity(this@MainActivity).withPermissions(android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {

                        requestLocationData()
                    }

                    if (report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(this@MainActivity,
                            "You Have Denied Location Permission. Please Enable Them As It Mandatory For The App To Work.",
                            Toast.LENGTH_LONG).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialogForPermission()
                }

            }).onSameThread()
                .check()
        }


    }

    private fun isLocationEnabled() : Boolean {

        val locationManager : LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(
            LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,mLocationCallback,
            Looper.myLooper()
        )

    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation : Location? = locationResult.lastLocation
            val latitude = mLastLocation?.latitude
            Log.i("Current Latitude","$latitude")
            val longitude = mLastLocation?.longitude
            Log.i("Current Longitude","$longitude")

            getLocationWeatherDetails(latitude!!,longitude!!)


        }
    }

    private fun getLocationWeatherDetails(latitude : Double, longitude : Double) {
        if (Constants.isNetworkAvailable(this@MainActivity)) {

            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val service: weatherService =
                retrofit.create<weatherService>(weatherService::class.java)

            val listCall: Call<weatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<weatherResponse>{
                override fun onResponse(call: Call<weatherResponse>, response: Response<weatherResponse>) {

                    if (response.isSuccessful) {

                        hideProgressDialog()

                        val weatherList : weatherResponse? = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()

                        setupUI()

                        Log.i("Response Result","$weatherList")
                    }
                    else {
                        val rc = response.code()
                        when(rc) {
                            400 -> {
                                Log.e("Error 400","Bad Connecting")
                            }
                            404 -> {
                                Log.e("Error 404","Not Found")
                            }
                            else -> {
                                Log.e("Error","Generic Error")
                            }
                        }
                    }

                }

                override fun onFailure(call: Call<weatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Error Error",t.message.toString())
                }

            })

        } else {
            Toast.makeText(this@MainActivity,"No Internet Connection Avaliable",Toast.LENGTH_SHORT).show()
        }
    }


    private fun showRationalDialogForPermission() {
        AlertDialog.Builder(this@MainActivity).setMessage("It Looks Like You Have Turned Off Permissions Required For This Feature. It Can Be Enabled Under Application Settings.")
            .setPositiveButton(
                "GO TO SETTINGS"
            ){_,_ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package",packageName,null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e : ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }.setNegativeButton("CANCEL"){dialog,
            _ ->
                dialog.dismiss()
            }.show()
    }

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.custom_progress_dialog)

        mProgressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI() {

        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")

        if (!weatherResponseJsonString.isNullOrEmpty()) {
            val weatherList = Gson().fromJson(weatherResponseJsonString,weatherResponse::class.java)

            for (i in weatherList.weather.indices) {
                Log.i("Weather Name","${weatherList.weather.toString()}")

                tasarim.textViewMain.text = weatherList.weather[i].main

                tasarim.tvMainDescription.text = weatherList.weather[i].description

                tasarim.textViewTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.toString())

                tasarim.tvHumidity.text = weatherList.main.humidity.toString() + " per cent"

                tasarim.textViewMin.text = weatherList.main.temp_min.toString() + getUnit(application.resources.configuration.toString()) +" min"

                tasarim.tvMax.text = weatherList.main.temp_max.toString() +  getUnit(application.resources.configuration.toString()) + " max"

                tasarim.textViewSpeed.text = weatherList.wind.speed.toString()

                tasarim.textViewName.text = weatherList.name

                tasarim.tvCountry.text = weatherList.sys.country

                tasarim.tvSunriseTime.text = unixTime(weatherList.sys.sunrise)

                tasarim.tvSunsetTime.text = unixTime(weatherList.sys.sunset)


                when(weatherList.weather[i].icon) {
                    "01d" -> tasarim.ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> tasarim.ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> tasarim.ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> tasarim.ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> tasarim.ivMain.setImageResource(R.drawable.sunny)
                    "10d" -> tasarim.ivMain.setImageResource(R.drawable.rain)
                    "11d" -> tasarim.ivMain.setImageResource(R.drawable.storm)
                    "13d" -> tasarim.ivMain.setImageResource(R.drawable.snowflake)
                    "01n" -> tasarim.ivMain.setImageResource(R.drawable.sunny)
                    "02n" -> tasarim.ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> tasarim.ivMain.setImageResource(R.drawable.cloud)
                    "10n" -> tasarim.ivMain.setImageResource(R.drawable.cloud)
                    "11n" -> tasarim.ivMain.setImageResource(R.drawable.rain)
                    "13n" -> tasarim.ivMain.setImageResource(R.drawable.snowflake)

                }
            }

        }

    }

    private fun getUnit(value : String) : String? {

        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long) : String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                true
            } else -> super.onOptionsItemSelected(item)
        }

    }



}