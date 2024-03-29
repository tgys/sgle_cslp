package com.jingyang.songname

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.data.kml.KmlLayer
import java.net.URL
import java.util.ArrayList

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, LocationListener {

    private lateinit var mMap: GoogleMap
    var song = Song()
    var option = "1"
    private var words = ArrayList<String>()
    private val context = this
    private var markerInfo = ArrayList<MarkerInfo>()
    private var newLinelist = ArrayList<String>()
    private var numPoints: Int = 0


    internal val PERMISSION_ALL = 1
    internal val PERMISSIONS = arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    private var locationManager: LocationManager? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        val myActionBar = supportActionBar
        myActionBar!!.title ="SongName"

        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        song.number = intent.getStringExtra("number")
        song.title = intent.getStringExtra("title")
        song.artist = intent.getStringExtra("artist")
        song.link = intent.getStringExtra("link")
        option = intent.getStringExtra("option")

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= 23 && !isPermissionGranted()) {
            requestPermissions(PERMISSIONS, PERMISSION_ALL)
        } else
            requestLocation()
        if (!isLocationEnabled())
            Toast.makeText(this, "You need to enable your location permission !", Toast.LENGTH_SHORT).show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val edinburgh = LatLng(55.94425, -3.188396)
        mMap.moveCamera(CameraUpdateFactory.newLatLng(edinburgh))
        mMap.animateCamera(CameraUpdateFactory.zoomTo(16f))
        mMap.setOnMarkerClickListener(object: GoogleMap.OnMarkerClickListener {
           override fun onMarkerClick(m: Marker): Boolean {

              var locMarker = LatLng(m.position.latitude,m.position.longitude)
              val numWord = words.size
              val loc = locationManager!!.getLastKnownLocation(LocationManager.GPS_PROVIDER)
              if (loc == null) {
                  locationManager!!.getLastKnownLocation(LocationManager.GPS_PROVIDER)
              }
              var result = true
              if(loc != null) {
                  val locCurrent = LatLng(loc.latitude, loc.longitude)
                  val dis = distanceMeters(locMarker, locCurrent)
                  var isExist = false
                  val num = markerInfo.size
                  if(num>0) {
                      for(i in 0..num-1) {
                          if(markerInfo[i].id == m.id) {
                              isExist = true
                              break
                          }
                      }
                  }
                  if (dis < 20 && !isExist) {
                      var markerinfo = MarkerInfo()
                      markerinfo.id = m.id
                      markerinfo.title = m.title
                      markerinfo.snippet = m.snippet
                      markerInfo.add(markerinfo)

                      val icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                      val name = m.title
                      var seq = name.splitToSequence(":")
                      var l = seq.elementAt(0).toInt()
                      var c = seq.elementAt(1).toInt()
                      var line = newLinelist[l - 1]
                      var seq2 = line.splitToSequence(" ", "\t")
                      var word = seq2.elementAt(c)
                      words.add(word)
                      m.setIcon(icon)
                      m.title = word
                      m.snippet = ""
                      Toast.makeText(context, "The new word: $word", Toast.LENGTH_SHORT).show()
                      val toneG = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                      toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 50)
                      result = false
                  } else if (isExist) {
                      result = false
                  } else {
                      result = true
                  }
              }
              return result
           }
        })
        if(isNetworkConnected()) {
            var dt = DataTask()
            dt.execute()
        } else {
            Toast.makeText(this, "The network is not connected !", Toast.LENGTH_SHORT).show()
        }
    }

    fun distanceMeters(p1: LatLng, p2: LatLng): Double {
        val R = 6378.137
        val dLat = p2.latitude * Math.PI / 180 - p1.latitude * Math.PI / 180
        val dLon = p2.longitude * Math.PI / 180 - p1.longitude * Math.PI / 180
        val a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(p1.latitude * Math.PI / 180) * Math.cos(p2.latitude * Math.PI / 180) *
                        Math.sin(dLon/2) * Math.sin(dLon/2)
        var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
        var d = R * c
        return d * 1000
    }

    private inner class DataTask : AsyncTask<String, Void, KmlLayer?>() {

        var networkOK = true
        override fun doInBackground(vararg url: String): KmlLayer? {
   // Parsing the data in non-ui thread
        var layer: KmlLayer? = null
        try {
            var dataSource = DataParser()
            val url = URL("http://www.inf.ed.ac.uk/teaching/courses/cslp/data/songs/" + song.number + "/map" + option + ".kml")
            newLinelist = dataSource.readWordLine(song.number)
            val connection = url.openConnection()
            val kmlInputStream = connection.getInputStream()
            layer = KmlLayer(mMap, kmlInputStream, getApplicationContext()) as KmlLayer
            kmlInputStream.close()
            numPoints = dataSource.getNodeNum(song.number, option)
            val num = numPoints

        } catch (e: Exception) {
            Log.d("DataInput", e.toString())
            e.printStackTrace()
        }
        return layer
    }

        // Executes in UI thread, after the parsing process
    override fun onPostExecute(layer: KmlLayer?) {

            layer?.addLayerToMap()
            val edinburgh = LatLng(55.94425, -3.188396)
            mMap.moveCamera(CameraUpdateFactory.newLatLng(edinburgh))
            mMap.animateCamera(CameraUpdateFactory.zoomTo(16f))
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_action, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        when (item.itemId) {
            R.id.media_route_menu_item -> {
                val intent = Intent(context, ResultActivity::class.java)
                intent.putExtra("number", song.number)
                intent.putExtra("title", song.title)
                intent.putExtra("artist", song.artist)
                intent.putExtra("link", song.link)

                val basicScore = Math.round((1.0-(words.size.toFloat()-1.0)/(numPoints.toFloat()-1.0))*40.0+40.0+(5.0-option.toFloat())*5)
                intent.putExtra("basicScore", basicScore.toString())

                var wordlist ="You have collected these words:\n\n"

                for(i in 0..words.size-1) {
                    if(i<words.size-1) {
                        wordlist = wordlist + words[i] + ", "
                    } else {
                        wordlist = wordlist + words[i] + ". "
                    }
                }
                intent.putExtra("wordlist", wordlist);

                startActivity(intent)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getActiveNetworkInfo() != null;
    }

    private fun requestLocation() {
        val criteria = Criteria()
        criteria.accuracy = Criteria.ACCURACY_FINE
        criteria.powerRequirement = Criteria.POWER_HIGH
        val provider = locationManager!!.getBestProvider(criteria, true)
        locationManager!!.requestLocationUpdates(provider, 10000, 10f, this)
    }

    private fun isLocationEnabled(): Boolean {
        return locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun isPermissionGranted(): Boolean {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true
        } else {
            return false
        }
    }



    override fun onLocationChanged(location: Location) {

    }

    override fun onStatusChanged(s: String, i: Int, b: Bundle) {

    }

    override fun onProviderEnabled(s: String) {

    }

    override fun onProviderDisabled(s: String) {

    }
}
