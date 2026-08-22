package com.cem.pusula

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var rotationVector: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private lateinit var compassView: CompassView
    private lateinit var degreeText: TextView
    private lateinit var directionText: TextView
    private lateinit var infoText: TextView
    private lateinit var statusText: TextView

    // Rotation-vector yoksa kullanılacak yedek yol için ham okumalar
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // Açıyı sin/cos üzerinden yumuşatıyoruz; 359° -> 0° geçişinde sıçrama olmasın diye.
    private var smoothSin = 0f
    private var smoothCos = 1f
    private var initialized = false
    private var lastShownDegree = -1

    /** Manyetik sapma (doğuya doğru pozitif). null ise gerçek kuzey bilinmiyor. */
    private var declination: Float? = null
    private var locationManager: LocationManager? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = applyLocation(location)

        // API 29 öncesinde bu üçü varsayılan gövdeye sahip değil, açıkça override edilmeli.
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        compassView = findViewById(R.id.compassView)
        degreeText = findViewById(R.id.degreeText)
        directionText = findViewById(R.id.directionText)
        infoText = findViewById(R.id.infoText)
        statusText = findViewById(R.id.statusText)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVector == null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
        locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager

        // Bir kez bulunan sapma sonraki açılışlarda hemen kullanılsın; konum
        // beklerken de gerçek kuzey gösterilebilsin diye önbelleğe alınıyor.
        val cached = prefs().getFloat(KEY_DECLINATION, Float.NaN)
        if (!cached.isNaN()) {
            declination = cached
            compassView.setMagneticNorthOffset(cached)
        }

        infoText.setOnClickListener { onInfoTapped() }
    }

    override fun onResume() {
        super.onResume()
        registerSensors()
        ensureLocation()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: SecurityException) {
        }
    }

    private fun registerSensors() {
        val rv = rotationVector
        if (rv != null) {
            sensorManager.registerListener(this, rv, SensorManager.SENSOR_DELAY_GAME)
            return
        }
        val acc = accelerometer
        val mag = magnetometer
        if (acc != null && mag != null) {
            sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME)
        } else {
            statusText.text = getString(R.string.no_sensor)
        }
    }

    // ---------------------------------------------------------------- konum

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureLocation() {
        if (!hasLocationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
            refreshInfoText(null)
            return
        }
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val lm = locationManager ?: return
        try {
            // Sapma yüzlerce kilometrede bir derece değişir; en son bilinen konum yeter.
            var best: Location? = null
            for (provider in lm.getProviders(true)) {
                val candidate = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || candidate.time > best!!.time) best = candidate
            }
            best?.let { applyLocation(it) }

            val provider = when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                else -> null
            }
            if (provider != null) {
                lm.requestLocationUpdates(provider, 60_000L, 1_000f, locationListener)
            }
        } catch (_: SecurityException) {
        }
        if (declination == null) refreshInfoText(null)
    }

    private fun applyLocation(location: Location) {
        val field = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            System.currentTimeMillis()
        )
        declination = field.declination
        prefs().edit().putFloat(KEY_DECLINATION, field.declination).apply()
        compassView.setMagneticNorthOffset(field.declination)
        lastShownDegree = -1   // yazıların hemen tazelenmesi için
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQ_LOCATION) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startLocationUpdates()
            } else {
                refreshInfoText(null)
            }
        }
    }

    /** İzin satırına dokunulunca: izni yeniden iste, kalıcı reddedildiyse ayarları aç. */
    private fun onInfoTapped() {
        if (hasLocationPermission()) return
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        } else {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }
    }

    // --------------------------------------------------------------- sensör

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                updateFromRotationMatrix()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
                if (hasGeomagnetic &&
                    SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
                ) {
                    updateFromRotationMatrix()
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                hasGeomagnetic = true
                if (hasGravity &&
                    SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
                ) {
                    updateFromRotationMatrix()
                }
            }
        }
    }

    private fun updateFromRotationMatrix() {
        // Ekran döndüğünde sensör eksenlerini ekrana göre yeniden eşle.
        val (axisX, axisY) = when (currentDisplayRotation()) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
        SensorManager.getOrientation(remappedMatrix, orientation)

        val azimuthRad = orientation[0]
        val s = sin(azimuthRad)
        val c = cos(azimuthRad)
        if (!initialized) {
            smoothSin = s
            smoothCos = c
            initialized = true
        } else {
            val alpha = 0.12f
            smoothSin += alpha * (s - smoothSin)
            smoothCos += alpha * (c - smoothCos)
        }

        val magnetic =
            ((Math.toDegrees(atan2(smoothSin, smoothCos).toDouble()) + 360.0) % 360.0).toFloat()
        val decl = declination
        // Gerçek kuzey = manyetik kuzey + sapma (sapma doğuya doğru pozitif)
        val shown = if (decl != null) (magnetic + decl + 360f) % 360f else magnetic

        compassView.setAzimuth(shown)

        val rounded = shown.roundToInt() % 360
        if (rounded != lastShownDegree) {
            lastShownDegree = rounded
            degreeText.text = "$rounded°"
            directionText.text = "${cardinal(shown)} · " +
                getString(if (decl != null) R.string.true_north else R.string.magnetic_north)
            refreshInfoText(magnetic)
        }
    }

    private fun refreshInfoText(magnetic: Float?) {
        val decl = declination
        infoText.text = when {
            decl == null && !hasLocationPermission() -> getString(R.string.need_location)
            decl == null -> getString(R.string.waiting_location)
            else -> {
                val magneticPart = magnetic?.let { "Manyetik ${it.roundToInt() % 360}° · " } ?: ""
                val yon = if (decl >= 0f) "D" else "B"
                magneticPart + "Sapma %.1f°%s".format(abs(decl), yon)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }

    private fun cardinal(degrees: Float): String {
        val names = arrayOf("K", "KKD", "KD", "DKD", "D", "DGD", "GD", "GGD",
            "G", "GGB", "GB", "BGB", "B", "BKB", "KB", "KKB")
        val index = ((degrees / 22.5f) + 0.5f).toInt() % 16
        return names[index]
    }

    private fun prefs() = getSharedPreferences("pusula", Context.MODE_PRIVATE)

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
            sensor?.type == Sensor.TYPE_ROTATION_VECTOR
        ) {
            statusText.text =
                if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) getString(R.string.calibrate)
                else ""
        }
    }

    private companion object {
        const val REQ_LOCATION = 1
        const val KEY_DECLINATION = "declination"
    }
}
