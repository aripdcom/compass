package com.cem.pusula

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
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
import android.os.SystemClock
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var rotationVector: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private lateinit var compassView: CompassView
    private lateinit var degreeText: TextView
    private lateinit var directionText: TextView
    private lateinit var infoText: TextView
    private lateinit var locationText: TextView
    private lateinit var targetText: TextView
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
    private var smoothPitch = 0f
    private var smoothRoll = 0f
    private var initialized = false
    private var lastShownDegree = -1

    /** Manyetik sapma (doğuya doğru pozitif). null ise gerçek kuzey bilinmiyor. */
    private var declination: Float? = null

    /** Kâbe yönü, gerçek kuzeye göre. Konum bilinmeden hesaplanamaz. */
    private var qiblaBearing: Float? = null

    /**
     * Kilitlenen yön **manyetik** çerçevede saklanır: sapma sonradan öğrenilse
     * bile (izin gecikmeli verildiğinde) hedef kaymasın diye.
     */
    private var targetMagnetic: Float? = null
    private var lastMagnetic: Float? = null

    private var needsCalibration = false
    private var tilted = false

    /** O konumda beklenen toplam alan şiddeti (µT); konum bilinmeden karşılaştırma yapılamaz. */
    private var expectedFieldStrength: Float? = null
    private var measuredFieldStrength = 0f
    private var disturbed = false

    /** Sapmanın eşiği kesintisiz aştığı ilk an; 0 ise şu anda aşmıyor. */
    private var disturbedSince = 0L
    private var lastShownFieldStrength = -1

    private var locationManager: LocationManager? = null

    /** Panelde gösterilen son konum; sağlayıcılar arasından en iyisi seçilir. */
    private var lastLocation: Location? = null

    /** Konum satırı derece-dakika-saniye mi gösteriyor; dokununca değişir. */
    private var showDms = false

    /** Kaydedilen nokta (enlem, boylam); yoksa null. Kadrana uzun basınca konur. */
    private var waypoint: Pair<Double, Double>? = null

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
        locationText = findViewById(R.id.locationText)
        targetText = findViewById(R.id.targetText)
        statusText = findViewById(R.id.statusText)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (rotationVector == null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }
        locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager

        // Bir kez bulunan konum sonraki açılışlarda hemen kullanılsın; GPS'i
        // beklerken de gerçek kuzey ve kıble gösterilebilsin diye önbelleğe alınıyor.
        val lat = prefs().getFloat(KEY_LATITUDE, Float.NaN)
        val lon = prefs().getFloat(KEY_LONGITUDE, Float.NaN)
        if (!lat.isNaN() && !lon.isNaN()) applyCoordinates(lat.toDouble(), lon.toDouble(), 0.0)

        val savedTarget = prefs().getFloat(KEY_TARGET, Float.NaN)
        if (!savedTarget.isNaN()) targetMagnetic = savedTarget
        applyTarget()

        infoText.setOnClickListener { onInfoTapped() }
        locationText.setOnClickListener {
            showDms = !showDms
            refreshLocationText()
        }
        locationText.setOnLongClickListener {
            copyLocation()
            true
        }
        compassView.setOnClickListener { toggleTarget() }
        compassView.setOnLongClickListener {
            toggleWaypoint()
            true
        }

        val wpLat = prefs().getFloat(KEY_WAYPOINT_LATITUDE, Float.NaN)
        val wpLon = prefs().getFloat(KEY_WAYPOINT_LONGITUDE, Float.NaN)
        if (!wpLat.isNaN() && !wpLon.isNaN()) waypoint = wpLat.toDouble() to wpLon.toDouble()
    }

    override fun onResume() {
        super.onResume()
        // Bozulma kararı kesintisiz gözleme dayanıyor; arka planda geçen süre
        // sayılmasın diye ölçüm ve sayaç sıfırdan başlatılır.
        measuredFieldStrength = 0f
        disturbedSince = 0L
        disturbed = false
        lastShownFieldStrength = -1
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
        val mag = magnetometer
        if (rv != null) {
            sensorManager.registerListener(this, rv, SensorManager.SENSOR_DELAY_GAME)
            // Rotation vector yönü verir ama alanın büyüklüğünü vermez; anomali
            // ancak ham manyetometreden görülür, o yüzden onu da dinliyoruz.
            if (mag != null) sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
            return
        }
        val acc = accelerometer
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

    /** Kullanıcı "yaklaşık" konumu seçtiyse koordinatlar kilometrelerce şaşabilir. */
    private fun hasPreciseLocation(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private fun ensureLocation() {
        if (!hasLocationPermission()) {
            requestPermissions(locationPermissions, REQ_LOCATION)
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

            // Sapma için ağ konumu yeterdi; koordinat paneli için GPS'in hassasiyeti
            // de gerekiyor, o yüzden ikisi de dinlenip en iyisi seçiliyor.
            for (provider in arrayOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )) {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, 10_000L, 10f, locationListener)
                }
            }
        } catch (_: SecurityException) {
        }
        if (declination == null) refreshInfoText(null)
    }

    private fun applyLocation(location: Location) {
        if (!isBetterFix(location, lastLocation)) return
        lastLocation = location
        refreshLocationText()
        applyWaypoint()
        refreshTargetText()
        prefs().edit()
            .putFloat(KEY_LATITUDE, location.latitude.toFloat())
            .putFloat(KEY_LONGITUDE, location.longitude.toFloat())
            .apply()
        applyCoordinates(location.latitude, location.longitude, location.altitude)
    }

    /**
     * Bir dakikadan yeni bir fix her zaman kazanır (yer değiştirmiş olabiliriz),
     * eşit yaşta olanlarda daha küçük hata payı olan seçilir.
     */
    private fun isBetterFix(candidate: Location, current: Location?): Boolean {
        if (current == null) return true
        val age = candidate.time - current.time
        if (age > FIX_STALE_MS) return true
        if (age < -FIX_STALE_MS) return false
        return candidate.accuracy <= current.accuracy
    }

    /** Konum satırı: koordinatlar, rakım ve hata payı. Dokunuş biçim değiştirir. */
    private fun refreshLocationText() {
        val location = lastLocation
        if (location == null) {
            locationText.visibility = View.GONE
            return
        }
        locationText.visibility = View.VISIBLE
        val parts = StringBuilder()
        if (showDms) {
            parts.append(dms(location.latitude, "K", "G"))
            parts.append("  ")
            parts.append(dms(location.longitude, "D", "B"))
        } else {
            parts.append("%.5f° %s".format(abs(location.latitude), if (location.latitude >= 0) "K" else "G"))
            parts.append("  ")
            parts.append("%.5f° %s".format(abs(location.longitude), if (location.longitude >= 0) "D" else "B"))
        }
        if (location.hasAltitude()) parts.append(" · %d m".format(location.altitude.roundToInt()))
        if (location.hasAccuracy()) parts.append(" · ±%d m".format(location.accuracy.roundToInt()))
        if (!hasPreciseLocation()) parts.append(" · ").append(getString(R.string.approximate_location))
        locationText.text = parts.toString()
    }

    /** Ondalık dereceyi derece-dakika-saniyeye çevirir. */
    private fun dms(value: Double, positive: String, negative: String): String {
        val magnitude = abs(value)
        val degrees = floor(magnitude).toInt()
        val minutesFull = (magnitude - degrees) * 60.0
        val minutes = floor(minutesFull).toInt()
        val seconds = (minutesFull - minutes) * 60.0
        return "%d°%02d'%04.1f\" %s".format(
            degrees, minutes, seconds, if (value >= 0) positive else negative
        )
    }

    private fun copyLocation() {
        val location = lastLocation ?: return
        // Haritalara yapıştırılabilecek sade biçim; nokta ayraçlı, işaretli.
        val plain = "%.6f, %.6f".format(java.util.Locale.US, location.latitude, location.longitude)
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), plain))
        // Android 13'ten itibaren sistem kendi kopyalama onayını gösteriyor;
        // üstüne bir de kendi bildirimimizi çıkarmak tekrar olurdu.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, getString(R.string.location_copied, plain), Toast.LENGTH_SHORT).show()
        }
    }

    /** Konumdan türeyen her şey: manyetik sapma ve kıble yönü. */
    private fun applyCoordinates(latitude: Double, longitude: Double, altitude: Double) {
        val field = GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            altitude.toFloat(),
            System.currentTimeMillis()
        )
        declination = field.declination
        expectedFieldStrength = field.fieldStrength / 1000f   // nT -> µT
        compassView.setMagneticNorthOffset(field.declination)

        val qibla = bearingTo(latitude, longitude, KAABA_LATITUDE, KAABA_LONGITUDE)
        qiblaBearing = qibla
        compassView.setQiblaBearing(qibla)

        applyTarget()
        lastShownDegree = -1   // yazıların hemen tazelenmesi için
    }

    /**
     * İki nokta arasındaki başlangıç açısı (great-circle), gerçek kuzeye göre.
     * Kıble tanımı da budur: Kâbe'ye giden en kısa yolun çıkış yönü.
     */
    private fun bearingTo(
        latitude: Double,
        longitude: Double,
        destLatitude: Double,
        destLongitude: Double
    ): Float {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(destLatitude)
        val deltaLon = Math.toRadians(destLongitude - longitude)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
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
            requestPermissions(locationPermissions, REQ_LOCATION)
        } else {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }
    }

    // ----------------------------------------------------------- hedef kilidi

    /** Kadrana dokunmak o anki yönü kilitler; kilitliyken dokunmak bırakır. */
    private fun toggleTarget() {
        val editor = prefs().edit()
        if (targetMagnetic != null) {
            targetMagnetic = null
            editor.remove(KEY_TARGET)
        } else {
            val magnetic = lastMagnetic ?: return   // henüz sensör okuması yok
            targetMagnetic = magnetic
            editor.putFloat(KEY_TARGET, magnetic)
        }
        editor.apply()
        applyTarget()
        refreshTargetText()
    }

    /** Kilitli hedefin ekranda gösterilen çerçevedeki (gerçek kuzey) karşılığı. */
    private fun shownTarget(): Float? =
        targetMagnetic?.let { (it + (declination ?: 0f) + 360f) % 360f }

    private fun applyTarget() = compassView.setTargetBearing(shownTarget())

    /**
     * Hedef ve nokta bilgisini tek satırda toplar. İkisi de renk kodlu, kadrandaki
     * işaretlerle eşleşsin diye; hiçbiri yoksa satır iki hareketi de anlatır.
     */
    private fun refreshTargetText() {
        val parts = SpannableStringBuilder()
        targetSegment()?.let { appendColored(parts, it, CompassView.COLOR_TARGET) }
        waypointSegment()?.let { appendColored(parts, it, CompassView.COLOR_WAYPOINT) }
        if (parts.isEmpty()) {
            targetText.setTextColor(COLOR_HINT)
            targetText.text = getString(R.string.target_hint)
        } else {
            targetText.text = parts
        }
    }

    private fun appendColored(builder: SpannableStringBuilder, text: String, color: Int) {
        if (builder.isNotEmpty()) builder.append("   ")
        val start = builder.length
        builder.append(text)
        builder.setSpan(
            ForegroundColorSpan(color),
            start,
            builder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun targetSegment(): String? {
        val target = shownTarget() ?: return null
        val targetDegree = target.roundToInt() % 360
        val shown = lastShownDegree
        if (shown < 0) return getString(R.string.target_plain, targetDegree)
        // Hedefe kalan açı: pozitifse saat yönünde, yani sağa dönmek gerekir.
        val diff = ((target - shown + 540f) % 360f) - 180f
        val amount = abs(diff).roundToInt()
        return when {
            amount <= ON_TARGET_DEGREES -> getString(R.string.target_reached, targetDegree)
            diff > 0f -> getString(R.string.target_right, targetDegree, amount)
            else -> getString(R.string.target_left, targetDegree, amount)
        }
    }

    private fun waypointSegment(): String? {
        val (wpLat, wpLon) = waypoint ?: return null
        val here = lastLocation ?: return null
        val bearing = bearingTo(here.latitude, here.longitude, wpLat, wpLon)
        val results = FloatArray(1)
        Location.distanceBetween(here.latitude, here.longitude, wpLat, wpLon, results)
        // Mesafe konum hatasının altına inince yön anlamını yitirir: hata çemberinin
        // içinde hangi yöne bakacağınızı söylemek uydurma olur. Eşik fix'in kendi
        // hata payı, ama makul bir aralığa sıkıştırılmış — çok iyi bir fix'te bile
        // birkaç metrede yön güvenilmez, çok kötü bir fix'te de yüz metre öteye
        // "buradasınız" demek yanlış olurdu.
        val arrivedWithin = (if (here.hasAccuracy()) here.accuracy else ARRIVED_MIN_METERS)
            .coerceIn(ARRIVED_MIN_METERS, ARRIVED_MAX_METERS)
        if (results[0] <= arrivedWithin) return getString(R.string.waypoint_here)
        return getString(R.string.waypoint_line, bearing.roundToInt() % 360, formatDistance(results[0]))
    }

    /** Yakında metre, uzakta kilometre; ondalık ayraç cihazın diline uyar. */
    private fun formatDistance(meters: Float): String =
        if (meters < 1000f) "%d m".format(meters.roundToInt())
        else "%.1f km".format(meters / 1000f)

    /** Kadrana uzun basmak bulunduğun yeri kaydeder; kayıtlıyken siler. */
    private fun toggleWaypoint() {
        val editor = prefs().edit()
        if (waypoint != null) {
            waypoint = null
            editor.remove(KEY_WAYPOINT_LATITUDE).remove(KEY_WAYPOINT_LONGITUDE)
            Toast.makeText(this, getString(R.string.waypoint_cleared), Toast.LENGTH_SHORT).show()
        } else {
            val here = lastLocation
            if (here == null) {
                Toast.makeText(this, getString(R.string.waypoint_needs_location), Toast.LENGTH_SHORT).show()
                return
            }
            waypoint = here.latitude to here.longitude
            editor.putFloat(KEY_WAYPOINT_LATITUDE, here.latitude.toFloat())
                .putFloat(KEY_WAYPOINT_LONGITUDE, here.longitude.toFloat())
            Toast.makeText(this, getString(R.string.waypoint_saved), Toast.LENGTH_SHORT).show()
        }
        editor.apply()
        applyWaypoint()
        refreshTargetText()
    }

    /** Noktanın kadrandaki yönü; gerçek kuzeye göre, kadranla aynı çerçevede. */
    private fun applyWaypoint() {
        val wp = waypoint
        val here = lastLocation
        compassView.setWaypointBearing(
            if (wp == null || here == null) null
            else bearingTo(here.latitude, here.longitude, wp.first, wp.second)
        )
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
                updateFieldStrength()
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
        val pitchDegrees = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val rollDegrees = Math.toDegrees(orientation[2].toDouble()).toFloat()
        if (!initialized) {
            smoothSin = s
            smoothCos = c
            smoothPitch = pitchDegrees
            smoothRoll = rollDegrees
            initialized = true
        } else {
            val alpha = 0.12f
            smoothSin += alpha * (s - smoothSin)
            smoothCos += alpha * (c - smoothCos)
            smoothPitch += alpha * (pitchDegrees - smoothPitch)
            smoothRoll += alpha * (rollDegrees - smoothRoll)
        }

        val magnetic =
            ((Math.toDegrees(atan2(smoothSin, smoothCos).toDouble()) + 360.0) % 360.0).toFloat()
        lastMagnetic = magnetic
        val decl = declination
        // Gerçek kuzey = manyetik kuzey + sapma (sapma doğuya doğru pozitif)
        val shown = if (decl != null) (magnetic + decl + 360f) % 360f else magnetic

        compassView.setAzimuth(shown)
        compassView.setTilt(smoothPitch, smoothRoll)
        updateTiltWarning()

        val rounded = shown.roundToInt() % 360
        if (rounded != lastShownDegree) {
            lastShownDegree = rounded
            degreeText.text = "$rounded°"
            directionText.text = "${cardinal(shown)} · " +
                getString(if (decl != null) R.string.true_north else R.string.magnetic_north)
            refreshInfoText(magnetic)
            refreshTargetText()
        }
    }

    /**
     * Telefon yatay değilken okuma sapar. Eğim, ekran normalinin düşeyden
     * açısıdır: remap edilmiş matrisin [8] elemanı bu açının kosinüsüdür.
     * Uyarının açılıp kapanma eşikleri farklı, sınırda titremesin diye.
     */
    private fun updateTiltWarning() {
        val tilt = Math.toDegrees(
            acos(remappedMatrix[8].coerceIn(-1f, 1f).toDouble())
        ).toFloat()
        val next = if (tilted) tilt > TILT_CLEAR_DEGREES else tilt > TILT_WARN_DEGREES
        if (next != tilted) {
            tilted = next
            refreshStatusText()
        }
    }

    private fun refreshInfoText(magnetic: Float?) {
        val decl = declination
        val base = when {
            decl == null && !hasLocationPermission() -> getString(R.string.need_location)
            decl == null -> getString(R.string.waiting_location)
            else -> {
                val magneticPart = magnetic?.let { "Manyetik ${it.roundToInt() % 360}° · " } ?: ""
                val yon = if (decl >= 0f) "D" else "B"
                magneticPart + "Sapma %.1f°%s".format(abs(decl), yon)
            }
        }
        val qibla = qiblaBearing
        if (qibla == null) {
            infoText.text = base
            return
        }
        // Kıble kısmı kadrandaki işaretle aynı renkte olsun ki hangisi olduğu belli olsun.
        val text = SpannableStringBuilder(base).append(" · ")
        val start = text.length
        text.append(getString(R.string.qibla_info, qibla.roundToInt() % 360))
        text.setSpan(
            ForegroundColorSpan(CompassView.COLOR_QIBLA),
            start,
            text.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        infoText.text = text
    }

    /**
     * Ölçülen alan, o konumda beklenenden belirgin sapıyorsa yakında mıknatıs ya
     * da mıknatıslanmış metal var demektir: pusula sessizce yanlış yön gösterir.
     * Cihazın kendi hassasiyet bayrağı bunu çoğu zaman fark etmez, çünkü sabit
     * bir bozulma "kararlı" görünür.
     */
    private fun updateFieldStrength() {
        val magnitude = sqrt(
            geomagnetic[0] * geomagnetic[0] +
                geomagnetic[1] * geomagnetic[1] +
                geomagnetic[2] * geomagnetic[2]
        )
        measuredFieldStrength =
            if (measuredFieldStrength == 0f) magnitude
            else measuredFieldStrength + 0.1f * (magnitude - measuredFieldStrength)

        val expected = expectedFieldStrength ?: return
        val deviation = abs(measuredFieldStrength - expected) / expected
        val threshold = if (disturbed) DISTURBED_CLEAR else DISTURBED_WARN
        val now = SystemClock.elapsedRealtime()

        if (deviation <= threshold) {
            disturbedSince = 0L
            if (disturbed) {
                disturbed = false
                refreshStatusText()
            }
            return
        }

        // Eşiği aşmak tek başına yetmiyor: telefonu çevirirken kalibrasyon
        // geçici olarak %20'ye varan sapma üretebiliyor. Uyarı ancak sapma
        // kesintisiz sürerse çıkar, böylece geçici sıçramalar elenir.
        if (disturbedSince == 0L) disturbedSince = now
        if (!disturbed) {
            if (now - disturbedSince < DISTURBED_HOLD_MS) return
            disturbed = true
            refreshStatusText()
            return
        }
        // Uyarı çıktıktan sonra da yazıdaki sayı canlı kalsın, ama her örnekte
        // tazelenmesin: 1 µT'lik oynamalar yazıyı saniyede birkaç kez değiştirip
        // göz yorardı, o yüzden ancak 2 µT'yi aşan bir kayma yazıya yansır.
        if (abs(measuredFieldStrength.roundToInt() - lastShownFieldStrength) >= 2) refreshStatusText()
    }

    private fun refreshStatusText() {
        // Anomali en tehlikelisi: açı yanlış ama ekranda hiçbir şey belli olmuyor.
        statusText.text = when {
            disturbed -> {
                lastShownFieldStrength = measuredFieldStrength.roundToInt()
                getString(
                    R.string.magnetic_disturbance,
                    lastShownFieldStrength,
                    (expectedFieldStrength ?: 0f).roundToInt()
                )
            }
            needsCalibration -> getString(R.string.calibrate)
            tilted -> getString(R.string.hold_flat)
            else -> ""
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
            needsCalibration = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
            refreshStatusText()
        }
    }

    private companion object {
        const val REQ_LOCATION = 1
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_TARGET = "target"
        /** "Buradasınız" eşiğinin alt ve üst sınırı (metre). */
        const val ARRIVED_MIN_METERS = 10f
        const val ARRIVED_MAX_METERS = 25f

        const val KEY_WAYPOINT_LATITUDE = "waypointLatitude"
        const val KEY_WAYPOINT_LONGITUDE = "waypointLongitude"

        /** Bu yaştan büyük fark varsa yeni fix koşulsuz kazanır. */
        const val FIX_STALE_MS = 60_000L

        /** Kâbe'nin koordinatları (Mescid-i Haram, Mekke). */
        const val KAABA_LATITUDE = 21.4224779
        const val KAABA_LONGITUDE = 39.8251832

        /** Bu kadar yaklaşınca "hedeftesiniz" denir. */
        const val ON_TARGET_DEGREES = 2

        // Uyarı bu eşiğin üstünde çıkar, altındakinde kaybolur.
        const val TILT_WARN_DEGREES = 40f
        const val TILT_CLEAR_DEGREES = 30f

        // Beklenen alandan bu oranda sapma anomali sayılır (aç/kapa eşikleri farklı).
        const val DISTURBED_WARN = 0.25f
        const val DISTURBED_CLEAR = 0.15f

        /** Sapmanın uyarı sayılması için kesintisiz sürmesi gereken süre. */
        const val DISTURBED_HOLD_MS = 2_500L

        val COLOR_HINT = android.graphics.Color.parseColor("#FF6C7683")
    }
}
