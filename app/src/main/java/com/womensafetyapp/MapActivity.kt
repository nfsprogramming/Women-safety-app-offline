package com.womensafetyapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private lateinit var currentLocation: Location
    private lateinit var btnMyLocation: FloatingActionButton
    
    private val LOCATION_PERMISSION_REQUEST_CODE = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nearby Safe Places"

        // Initialize OSMDroid configuration
        initializeOSMDroid()
        
        initViews()
        setupMap()
        setupLocationUpdates()
    }

    private fun initializeOSMDroid() {
        // OSMDroid configuration for offline/online maps
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = "WomenSafetyApp/1.0"
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        btnMyLocation = findViewById(R.id.btnMyLocation)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        btnMyLocation.setOnClickListener {
            centerMapOnCurrentLocation()
        }
    }

    private fun setupMap() {
        // Set up the map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        // Built-in zoom controls are enabled by default in newer versions
        mapView.setMultiTouchControls(true)
        
        val mapController = mapView.controller
        mapController.setZoom(15.0)
        
        // Start with default location or user's last known location
        val startPoint = GeoPoint(20.0, 77.0) // Default to India
        mapController.setCenter(startPoint)
    }

    private fun setupLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            
            try {
                // Request from both GPS and Network providers
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        5000, 10f, locationListener
                    )
                }
                
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        5000, 10f, locationListener
                    )
                }
                
                // Get initial location from either provider
                val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                
                val initialLocation = if (lastGps != null && lastNetwork != null) {
                    if (lastGps.time > lastNetwork.time) lastGps else lastNetwork
                } else {
                    lastGps ?: lastNetwork
                }
                
                initialLocation?.let {
                    currentLocation = it
                    updateMapLocation(it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Location services unavailable", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Request permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            updateMapLocation(location)
        }
        
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun updateMapLocation(location: Location) {
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        
        // Update map center
        mapView.controller.setCenter(geoPoint)
        
        // Add or update current location marker
        val currentMarker = Marker(mapView)
        currentMarker.position = geoPoint
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        currentMarker.title = "My Location"
        
        // Clear existing markers and add new one
        mapView.overlays.clear()
        mapView.overlays.add(currentMarker)
        
        // Add nearby safe place markers
        addSafePlaceMarkers(geoPoint)
        
        mapView.invalidate() // Refresh map
    }

    private fun addSafePlaceMarkers(currentLocation: GeoPoint) {
        // Add markers for predefined safe places (can be customized)
        val safePlaces = listOf(
            Pair("Police Station", GeoPoint(currentLocation.latitude + 0.01, currentLocation.longitude + 0.01)),
            Pair("Hospital", GeoPoint(currentLocation.latitude - 0.01, currentLocation.longitude + 0.01)),
            Pair("Public Area", GeoPoint(currentLocation.latitude + 0.01, currentLocation.longitude - 0.01))
        )
        
        safePlaces.forEach { (name, location) ->
            val marker = Marker(mapView)
            marker.position = location
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = name
            // Use a star icon for safe places
            marker.icon = ContextCompat.getDrawable(this, android.R.drawable.star_on)
            
            marker.setOnMarkerClickListener { m, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Start Journey to ${m.title}?")
                    .setMessage("Would you like to start tracking your journey to this safe place? Emergency contacts will be notified if you deviate from the route.")
                    .setPositiveButton("Start Tracking") { _, _ ->
                        val tracker = SafeRouteTracker(this)
                        val destination = android.location.Location("").apply {
                            latitude = m.position.latitude
                            longitude = m.position.longitude
                        }
                        tracker.startTracking(destination)
                        Toast.makeText(this, "Journey tracking started to ${m.title}", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            mapView.overlays.add(marker)
        }
    }

    private fun centerMapOnCurrentLocation() {
        if (::currentLocation.isInitialized) {
            updateMapLocation(currentLocation)
        } else {
            Toast.makeText(this, "Location not available yet", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, 
                                           grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupLocationUpdates()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}