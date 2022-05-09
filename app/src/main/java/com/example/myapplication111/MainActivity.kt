package com.example.myapplication111


import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.annotations.PolygonOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions
import com.mapbox.mapboxsdk.style.expressions.Expression.abs
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.utils.BitmapUtils
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfJoins
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfTransformation
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber
import kotlin.math.min

class MainActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener {
    private var searchfab: FloatingActionButton? = null
    private var permissionsManager: PermissionsManager? = null
    private var mapboxMap: MapboxMap? = null
    private var mapView: MapView? = null
    private var origin: Point? = null
    private var destination: Point? = null
    private var BtnStart: Button? = null
//    private var BntCong: Button? = null
//    private var BntTru: Button? = null
    private var navigationMapRoute: NavigationMapRoute? = null
    private var currentRoute: DirectionsRoute? = null
    private var destinationMarker: Marker? = null
    private var status = 0
    private var polygonOptions: PolygonOptions? = null
    private var latLng: LatLng? = null
//    private val x = 0
//    private var y = 0
    private var longClickRunnable: Runnable? = null
    private var mapLongClickHandler: Handler? = null
    private var startTouchX = 0f
    private var startTouchY = 0f
    private var isTouchInsideCircle = false
    private var isZoomAction = false
    private var startTouchTime = 0
    private var circleCenter: Point? = null
    private var circleRadius = 100
    private val MINIMUM_ZOOM_DETECTED_TIME_IN_MILLIS = 550
    private var minRadius = 360
    private var maxRadius = 500
    private var radius = 0

    private var currentTouchX: Float? = null
    private var currentTouchY: Float? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapView)
        searchfab = findViewById(R.id.fab_location_search)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)
//        BntCong = findViewById<Button>(R.id.btnCong)
//        BntTru = findViewById<Button>(R.id.btnTru)
        BtnStart = findViewById(R.id.btnStart)
        searchfab?.setOnClickListener(View.OnClickListener {
            val intent = PlaceAutocomplete.IntentBuilder()
                .accessToken(
                    (if (Mapbox.getAccessToken() != null) Mapbox.getAccessToken() else getString(
                        R.string.mapbox_access_token
                    ))!!
                )
                .placeOptions(
                    PlaceOptions.builder()
                        .backgroundColor(Color.parseColor("#EEEEEE"))
                        .limit(10)
                        .build(PlaceOptions.MODE_CARDS)
                )
                .build(this@MainActivity)
            startActivityForResult(intent, REQUEST_CODE_AUTOCOMPLETE)
        })
        BtnStart?.setOnClickListener(View.OnClickListener {
            if (status != 1) {
                val options = NavigationLauncherOptions.builder()
                    .directionsRoute(currentRoute)
                    .shouldSimulateRoute(true)
                    .build()
                NavigationLauncher.startNavigation(this@MainActivity, options)
            } else if (status == 1) {
                status = 0
                getNavigation(origin, destination)
            }
        })

    }

    private val mapViewTouchListener = object : View.OnTouchListener {

        override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    startTouchTime = System.currentTimeMillis().toInt()
                    mapLongClickHandler?.postDelayed(
                        longClickRunnable!!,
                        ViewConfiguration.getLongPressTimeout().toLong()
                    )
                    startTouchX = motionEvent.x
                    startTouchY = motionEvent.y
                    val currentTouchLocation =
                        mapboxMap!!.projection.fromScreenLocation(PointF(startTouchX, startTouchY))
                    val currentTouchPoint = Point.fromLngLat(
                        currentTouchLocation.longitude,
                        currentTouchLocation.latitude
                    )
                    circleCenter = origin

                    val polygon = circleCenter?.let {

                        TurfTransformation.circle(
                            it,
                            circleRadius.toDouble(),
                            TurfConstants.UNIT_METERS
                        )
                    }
                    isTouchInsideCircle = TurfJoins.inside(currentTouchPoint, polygon)
//                    Log.d("sang", "onTouch: 1111")

                }
                MotionEvent.ACTION_MOVE -> {
                    if (currentTouchX == null) {
                        currentTouchX = motionEvent.x
                        currentTouchY = motionEvent.y
                    }
                    else {
                        if (currentTouchY!! > motionEvent.y) {
                            circleRadius += 10
                        }
                        else {
                            circleRadius -= 10
                        }
                        currentTouchX = motionEvent.x
                        currentTouchY = motionEvent.y
                    }
                    mapboxMap?.removePolygon(polygonOptions!!.polygon)
                    var radius = min(circleRadius, 10)
                    mapboxMap?.addPolygon(drawCircle(circleCenter!!, circleRadius,64))

                    val scaleDistance =
                        Math.abs(startTouchX + startTouchY - (currentTouchX!! + currentTouchY!!))
                    if (isZoomAction) {
                        mapLongClickHandler?.removeCallbacks(longClickRunnable!!)
                    }
                    if (scaleDistance < MINIMUM_SCALE_DISTANCE_ON_MAP) {
                        mapLongClickHandler?.removeCallbacks(longClickRunnable!!)
                        val isZoomDetectedDone =
                            System.currentTimeMillis() - startTouchTime > MINIMUM_ZOOM_DETECTED_TIME_IN_MILLIS

                        if ( !isZoomAction && isZoomDetectedDone) {
                            val currentTouchLocation = mapboxMap!!.projection.fromScreenLocation(
                                PointF(
                                    currentTouchX!!,
                                    currentTouchY!!
                                )
                            )
                            val currentTouchPoint = Point.fromLngLat(
                                currentTouchLocation.longitude,
                                currentTouchLocation.latitude
                            )

                            var currentRadius = TurfMeasurement.distance(
                                circleCenter!!,
                                currentTouchPoint,
                                TurfConstants.UNIT_METERS
                            ).toInt()
                            currentRadius = minRadius.coerceAtLeast(currentRadius)
                            currentRadius = maxRadius.coerceAtMost(currentRadius)
                            circleCenter = currentTouchPoint
                            circleRadius = currentRadius

//                            mapboxMap?.addPolygon(drawCircle(circleCenter!!, circleRadius,64))
//                            Log.d("sang", "onTouch: 11")
//                            viewBinding.createGeofenceRadiusEditText.setText(circleRadius.toString())
                        }
                    }

                }
                MotionEvent.ACTION_UP -> {
                    isTouchInsideCircle = false
                    isZoomAction = false
                    mapLongClickHandler?.removeCallbacks(longClickRunnable!!)
                    mapView?.performClick()

                    startTouchX = 0f
                    startTouchY = 0f

//                    if (!viewBinding.createGeofenceRadiusEditText.isEditTextEnabled()) {
//                        viewBinding.createGeofenceRadiusEditText.setEditTextEnabled(true)
//                    }
//                    viewBinding.createGeofenceRadiusEditText.setText(circleRadius.toString())
                }
                MotionEvent.ACTION_POINTER_UP,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_POINTER_2_DOWN,
                MotionEvent.ACTION_POINTER_2_UP -> {
                    isZoomAction = motionEvent.pointerCount == 2
                }
            }

            // Return "true": touch event will not be passed to the next View (MapView gestures do not work)
            // Return "false": touch event will be passed to the next View (MapView gestures will work)
            if (motionEvent.action == MotionEvent.ACTION_MOVE) {
                return isTouchInsideCircle && !isZoomAction
            }
            return true
        }
    }
        private fun drawCircle(
            circleCenter: Point,
            circleRadius: Int,
            numberOfSides: Int
        ): PolygonOptions {
            this.polygonOptions = generatePerimeter(
                latLng!!,
                circleRadius.toDouble(),
                64
            )
            return this.polygonOptions!!
        }

        private val mapMoveListener = object : MapboxMap.OnMoveListener {
            //last point coordinates
            var lastX = 0F
            var lastY = 0F
            override fun onMoveBegin(detector: MoveGestureDetector) {
                //remember start coordinates of user's finger
                lastX = detector.focalPoint.x
                lastY = detector.focalPoint.y
            }

            override fun onMoveEnd(detector: MoveGestureDetector) {
                val deltaX = detector.focalPoint.x - lastX
                val deltaY = detector.focalPoint.y - lastY
                val MIN_MOVE_DELTA = null
                if (abs(deltaX) != MIN_MOVE_DELTA || abs(deltaY) != MIN_MOVE_DELTA) {
                    this@MainActivity.mapboxMap?.addOnCameraIdleListener(object :
                        MapboxMap.OnCameraIdleListener {
                        override fun onCameraIdle() {
                            getNearbyEvents()
                            this@MainActivity.mapboxMap?.removeOnCameraIdleListener(this)
                        }

                        private fun getNearbyEvents() {
                            TODO("Not yet implemented")
                        }
                    })
                }
            }

            override fun onMove(detector: MoveGestureDetector) {
            }
        }

        private fun generatePerimeter(
            centerCoordinates: LatLng,
            radiusInKilometers: Double,
            numberOfSides: Int
        ): PolygonOptions {
            val positions: MutableList<LatLng> = ArrayList()
            val distanceX =
                radiusInKilometers / (111.319 * Math.cos(centerCoordinates.latitude * Math.PI / 180))
            val distanceY = radiusInKilometers / 110.574
            val slice = 2 * Math.PI / numberOfSides
            var theta: Double
            var x: Double
            var y: Double
            var position: LatLng
            for (i in 0 until numberOfSides) {
                theta = i * slice
                x = distanceX * Math.cos(theta)
                y = distanceY * Math.sin(theta)
                position = LatLng(
                    centerCoordinates.latitude + y,
                    centerCoordinates.longitude + x
                )
                positions.add(position)
            }
            return PolygonOptions()
                .addAll(positions)
                .fillColor(Color.BLUE)
                .alpha(0.4f)
        }


    private fun initLayers(loadedMapStyle: Style) {
        val drawable = ResourcesCompat.getDrawable(resources, R.drawable.map_location, null)
        loadedMapStyle.addImage(DESTINATION_ICON_ID, BitmapUtils.getBitmapFromDrawable(drawable)!!)
        val geoJsonSource = GeoJsonSource(DESTINATION_SOURCE_ID)
        loadedMapStyle.addSource(geoJsonSource)
        val destinationSymbolLayer = SymbolLayer(DESTINATION_SYMBOL_LAYER_ID, DESTINATION_SOURCE_ID)
        destinationSymbolLayer.withProperties(
            PropertyFactory.iconImage(DESTINATION_ICON_ID),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
        loadedMapStyle.addLayer(destinationSymbolLayer)
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(loadedMapStyle: Style) {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            val locationComponent = mapboxMap!!.locationComponent
            locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, loadedMapStyle).build()
            )
            locationComponent?.isLocationComponentEnabled = true
            locationComponent.cameraMode = CameraMode.TRACKING
            locationComponent.renderMode = RenderMode.COMPASS
            origin = Point.fromLngLat(
                locationComponent.lastKnownLocation!!.longitude,
                locationComponent.lastKnownLocation!!.latitude
            )
        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager!!.requestLocationPermissions(this)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager!!.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onExplanationNeeded(permissionsToExplain: List<String>) {
        Toast.makeText(this, "Grant Location Permission", Toast.LENGTH_LONG).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            mapboxMap!!.getStyle { style -> enableLocationComponent(style) }
        } else {
            Toast.makeText(this, "Permission not granted", Toast.LENGTH_LONG).show()
            finish()
        }
    }


    override fun onMapReady(mapboxMap: MapboxMap) {
        mapView?.setOnTouchListener(mapViewTouchListener)

        this.mapboxMap = mapboxMap
        latLng = LatLng(16.061445, 108.236780)
        this.radius = 10
        val cameraPosition = CameraPosition.Builder()
        cameraPosition.target(latLng)
        cameraPosition.zoom(4.0)
        this.mapboxMap!!.cameraPosition = cameraPosition.build()

        polygonOptions = generatePerimeter(
            latLng!!,
            this.radius.toDouble(),
            64
        )

//        mapboxMap.addPolygon(polygonOptions!!)
//        mapView?.setOnTouchListener(mapViewTouchListener)

        mapboxMap.setStyle(
            Style.Builder().fromUri(Style.MAPBOX_STREETS)
        ) { style ->
            val symbolLayerIconFeatureList: List<Feature> =
                java.util.ArrayList()
            enableLocationComponent(style)
            initLayers(style)
            mapboxMap.addOnMapClickListener { point ->
                if (destinationMarker != null) mapboxMap.removeMarker(destinationMarker!!)
                destinationMarker = mapboxMap.addMarker(MarkerOptions().position(point))
                destination = Point.fromLngLat(point.longitude, point.latitude)
                origin =
                    Point.fromLngLat(origin!!.longitude(), origin!!.latitude())
                BtnStart!!.isEnabled = true
                BtnStart!!.setBackgroundResource(com.mapbox.mapboxsdk.R.color.mapbox_blue)
                getRoute(origin, destination)
                latLng = point
//                if (polygonOptions != null) mapboxMap.removePolygon(polygonOptions!!.polygon)
//                polygonOptions = generatePerimeter(
//                    LatLng(point.latitude, point.longitude), 10.0,
//                    64
//
//                )
//                mapboxMap.addPolygon(polygonOptions!!)
                true
            }

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_AUTOCOMPLETE) {
            val selectedCarmenFeature = PlaceAutocomplete.getPlace(data)
            BtnStart!!.isEnabled = true
            BtnStart!!.setBackgroundResource(com.mapbox.mapboxsdk.R.color.mapbox_blue)
            destination = Point.fromLngLat(
                (selectedCarmenFeature.geometry() as Point?)!!.longitude(),
                (selectedCarmenFeature.geometry() as Point?)!!.latitude()
            )
            getRoute(origin, destination)
            if (mapboxMap != null) {
                val style = mapboxMap!!.style
                if (style != null) {
                    val source = style.getSourceAs<GeoJsonSource>("geojsonSourceLayerId")
                    source?.setGeoJson(
                        FeatureCollection.fromFeatures(
                            arrayOf(
                                Feature.fromJson(
                                    selectedCarmenFeature.toJson()
                                )
                            )
                        )
                    )
                    mapboxMap?.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(
                                    LatLng(
                                        (selectedCarmenFeature.geometry() as Point?)!!.latitude(),
                                        (selectedCarmenFeature.geometry() as Point?)!!.longitude()
                                    )
                                )
                                .zoom(14.0)
                                .build()
                        ), 4000
                    )

                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView!!.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView!!.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView!!.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView!!.onStop()
    }

    @SuppressLint("MissingSuperCall")
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView!!.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView!!.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView!!.onLowMemory()
    }

//    override fun onCreateOptionsMenu(menu: Menu): Boolean {
//        val inflater = menuInflaterzo
//        inflater.inflate(R.menu.menu, menu)
//        return true
//    }

    private fun getNavigation(originL: Point?, destination: Point?) {
        NavigationRoute.builder(applicationContext)
            .accessToken(getString(R.string.mapbox_access_token))
            .origin(originL!!)
            .destination(destination!!)
            .build()
            .getRoute(object : Callback<DirectionsResponse?> {
                override fun onResponse(
                    call: Call<DirectionsResponse?>,
                    response: Response<DirectionsResponse?>
                ) {
                    Timber.d("Response code: " + response.code())
                    if (response.body() == null) {
                        Toast.makeText(
                            this@MainActivity,
                            "No routes found, make sure you set the right user and access token.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    } else if (response.body()!!.routes().size < 1) {
                        Toast.makeText(this@MainActivity, "No routes found", Toast.LENGTH_SHORT)
                            .show()
                        return
                    }
                    currentRoute = response.body()!!.routes()[0]
                    val options = NavigationLauncherOptions.builder()
                        .directionsRoute(currentRoute)
                        .shouldSimulateRoute(true)
                        .build()
                    NavigationLauncher.startNavigation(this@MainActivity, options)
                }

                override fun onFailure(call: Call<DirectionsResponse?>, t: Throwable) {
                    Toast.makeText(
                        this@MainActivity, "Error: " + t.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun getRoute(origin: Point?, destination: Point?) {
        NavigationRoute.builder(this)
            .accessToken(Mapbox.getAccessToken()!!)
            .origin(origin!!)
            .destination(destination!!)
            .build()
            .getRoute(object : Callback<DirectionsResponse?> {
                override fun onResponse(
                    call: Call<DirectionsResponse?>,
                    response: Response<DirectionsResponse?>
                ) {
                    // You can get the generic HTTP info about the response
                    Timber.d("Response code: " + response.code())
                    if (response.body() == null) {
                        Timber.e("No routes found, make sure you set the right user and access token.")
                        return
                    } else if (response.body()!!.routes().size < 1) {
                        Timber.e("No routes found")
                        return
                    }

                    // Get the directions route
                    currentRoute = response.body()!!.routes()[0]
                    if (navigationMapRoute != null) {
                        navigationMapRoute!!.removeRoute()
                    } else {
                        navigationMapRoute = NavigationMapRoute(null, mapView!!, mapboxMap!!)
                    }
                    navigationMapRoute!!.addRoute(currentRoute)
                }

                override fun onFailure(call: Call<DirectionsResponse?>, throwable: Throwable) {
                    Timber.e("Error: " + throwable.message)
                    Toast.makeText(
                        this@MainActivity, "Error: " + throwable.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    companion object {
        private const val DESTINATION_SYMBOL_LAYER_ID = "destination-symbol-layer-id"
        private const val DESTINATION_ICON_ID = "destination-icon-id"
        private const val DESTINATION_SOURCE_ID = "destination-source-id"
        private const val REQUEST_CODE_AUTOCOMPLETE = 1
        private const val TAG = "MainActivity"
        private const val MINIMUM_SCALE_DISTANCE_ON_MAP = 64
    }

}