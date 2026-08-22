package com.cem.pusula

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.WindowManager
import android.widget.TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        compassView = findViewById(R.id.compassView)
        degreeText = findViewById(R.id.degreeText)
        directionText = findViewById(R.id.directionText)
        statusText = findViewById(R.id.statusText)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVector == null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
    }

    override fun onResume() {
        super.onResume()
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

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

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

        val degrees = ((Math.toDegrees(atan2(smoothSin, smoothCos).toDouble()) + 360.0) % 360.0).toFloat()
        compassView.setAzimuth(degrees)

        val rounded = degrees.roundToInt() % 360
        if (rounded != lastShownDegree) {
            lastShownDegree = rounded
            degreeText.text = "$rounded°"
            directionText.text = "${cardinal(degrees)} · ${getString(R.string.hint_magnetic)}"
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
            sensor?.type == Sensor.TYPE_ROTATION_VECTOR
        ) {
            statusText.text =
                if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) getString(R.string.calibrate)
                else ""
        }
    }
}
