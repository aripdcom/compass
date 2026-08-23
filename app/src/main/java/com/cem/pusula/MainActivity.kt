package com.cem.pusula

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
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

    private lateinit var root: View
    private lateinit var settingsButton: TextView
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

    /** Şu an hangi ana yönün yakınındayız (0=K, 1=D, 2=G, 3=B); -1 hiçbiri. */
    private var hapticCardinal = -1

    /** Uygulama açılırken ana yöne bakıyorsanız titremesin diye ilk örnek sayılmaz. */
    private var hapticPrimed = false

    /** Son tıkın anı; art arda gelen tetiklemeleri seyreltir. */
    private var lastTickAt = 0L

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

    // Ayarlardan okunan değerler; onResume'da tazelenir.
    private var nightMode = false
    private var unit = Prefs.DEFAULT_UNIT
    private var useTrueNorth = Prefs.DEFAULT_TRUE_NORTH
    private var smoothingAlpha = Prefs.SMOOTHING_ALPHAS[Prefs.DEFAULT_SMOOTHING]
    private var vibrateOnCardinals = Prefs.DEFAULT_VIBRATE
    private var showMagnetic = Prefs.DEFAULT_SHOW_MAGNETIC
    private var showLevel = Prefs.DEFAULT_SHOW_LEVEL
    private var showQibla = Prefs.DEFAULT_SHOW_QIBLA
    private var showSun = Prefs.DEFAULT_SHOW_SUN
    private val palette: Palette get() = Palette.of(nightMode)

    /** Kaydedilen nokta (enlem, boylam); yoksa null. Kadrana uzun basınca konur. */
    private var waypoint: Pair<Double, Double>? = null

    /** Sapma, kıble ve güneş için kullanılan konum; önbellekten de gelebilir. */
    private var coordinates: Pair<Double, Double>? = null
    private var sun: Sun.Position? = null

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Titreşim için `performHapticFeedback` yerine doğrudan `Vibrator`:
     * Galaxy A51 / Android 13'te sabitlerin çoğu (CLOCK_TICK, KEYBOARD_TAP,
     * VIRTUAL_KEY, CONFIRM, CONTEXT_CLICK) `true` dönüyor ama hiç titremiyor;
     * yalnızca LONG_PRESS çalışıyor ve o da 48 ms'lik sert bir vuruş. Ana yön
     * geçişi için kısa bir tık gerektiğinden efekti kendimiz veriyoruz.
     */
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Güneş dakikada 0,25° yol alır; dakikada bir tazelemek fazlasıyla yeter. */
    private val sunTick = object : Runnable {
        override fun run() {
            updateSun()
            handler.postDelayed(this, SUN_UPDATE_MS)
        }
    }

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

        root = findViewById(R.id.root)
        settingsButton = findViewById(R.id.settingsButton)
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

        applySettings()

        // Büyük dereceye dokunmak gece moduna geçirir: en büyük hedef, ayarlara
        // girmeden gece görüşünü kurtarmak için kısayol.
        degreeText.setOnClickListener {
            prefs().edit().putBoolean(Prefs.KEY_NIGHT, !nightMode).apply()
            applySettings()
        }
        targetText.setOnClickListener { askForBearing() }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
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

    /**
     * Ayarları okuyup uygular. Ayarlar ekranından dönüldüğünde de çağrılır, o
     * yüzden ayrı bir "kaydet/uygula" akışı yok.
     */
    private fun applySettings() {
        val stored = prefs()
        nightMode = stored.getBoolean(Prefs.KEY_NIGHT, Prefs.DEFAULT_NIGHT)
        unit = stored.getInt(Prefs.KEY_UNIT, Prefs.DEFAULT_UNIT)
        useTrueNorth = stored.getBoolean(Prefs.KEY_TRUE_NORTH, Prefs.DEFAULT_TRUE_NORTH)
        vibrateOnCardinals = stored.getBoolean(Prefs.KEY_VIBRATE, Prefs.DEFAULT_VIBRATE)
        showMagnetic = stored.getBoolean(Prefs.KEY_SHOW_MAGNETIC, Prefs.DEFAULT_SHOW_MAGNETIC)
        showLevel = stored.getBoolean(Prefs.KEY_SHOW_LEVEL, Prefs.DEFAULT_SHOW_LEVEL)
        showQibla = stored.getBoolean(Prefs.KEY_SHOW_QIBLA, Prefs.DEFAULT_SHOW_QIBLA)
        showSun = stored.getBoolean(Prefs.KEY_SHOW_SUN, Prefs.DEFAULT_SHOW_SUN)
        smoothingAlpha = Prefs.SMOOTHING_ALPHAS[
            stored.getInt(Prefs.KEY_SMOOTHING, Prefs.DEFAULT_SMOOTHING)
                .coerceIn(0, Prefs.SMOOTHING_ALPHAS.lastIndex)
        ]
        if (stored.getBoolean(Prefs.KEY_KEEP_SCREEN, Prefs.DEFAULT_KEEP_SCREEN)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        applyPalette()
        applyMarks()
        lastShownDegree = -1   // yazılar yeni birimle hemen kurulsun
    }

    /** Kadran işaretlerini kuzey çerçevesine ve görünürlük ayarlarına göre kurar. */
    private fun applyMarks() {
        // Manyetik çerçevedeyken "M" kadranın kuzeyiyle çakışır, gösterilmez.
        compassView.setMagneticNorthOffset(if (useTrueNorth && showMagnetic) declination else null)
        compassView.levelVisible = showLevel
        compassView.setQiblaBearing(if (showQibla) qiblaBearing?.let(::toDialFrame) else null)
        compassView.setSun(
            if (showSun) sun?.azimuth?.let(::toDialFrame) else null,
            (sun?.elevation ?: 0f) > 0f
        )
        applyTarget()
        applyWaypoint()
    }

    /**
     * Gerçek kuzeye göre verilmiş bir açıyı kadranın çerçevesine çevirir.
     * Kadran manyetik kuzeye göreyse sapma kadar geri alınır.
     */
    private fun toDialFrame(trueBearing: Float): Float =
        if (useTrueNorth) trueBearing
        else (trueBearing - (declination ?: 0f) + 360f) % 360f

    /** Manyetik açıya eklenince kadran çerçevesini veren düzeltme. */
    private fun frameOffset(): Float = if (useTrueNorth) declination ?: 0f else 0f

    /** Açıyı seçili birimde yazar: derece ya da NATO mili (tam çember 6400). */
    private fun formatBearing(degrees: Float): String {
        val normalized = (degrees % 360f + 360f) % 360f
        return if (unit == Prefs.UNIT_MIL) {
            "%d %s".format(
                (normalized * Prefs.MILS_PER_CIRCLE / 360f).roundToInt() % 6400,
                getString(R.string.mil_suffix)
            )
        } else {
            "%d°".format(normalized.roundToInt() % 360)
        }
    }

    /** Fark açısı: yön değil miktar olduğu için 360'a sarılmaz. */
    private fun formatDelta(degrees: Int): String =
        if (unit == Prefs.UNIT_MIL) {
            "%d %s".format(
                (degrees * Prefs.MILS_PER_CIRCLE / 360f).roundToInt(),
                getString(R.string.mil_suffix)
            )
        } else {
            "%d°".format(degrees)
        }

    /** Renk düzenini bütün görünümlere uygular; yazılar da yeniden kurulur. */
    private fun applyPalette() {
        val colors = palette
        root.setBackgroundColor(colors.background)
        compassView.palette = colors
        degreeText.setTextColor(colors.text)
        directionText.setTextColor(colors.textDim)
        infoText.setTextColor(colors.textDim)
        locationText.setTextColor(colors.textDim)
        statusText.setTextColor(colors.warning)
        settingsButton.setTextColor(colors.textDim)
        // Bu ikisi renkli parça içerdiği için baştan kurulmalı.
        refreshInfoText(lastMagnetic)
        refreshTargetText()
    }

    override fun onResume() {
        super.onResume()
        // Bozulma kararı kesintisiz gözleme dayanıyor; arka planda geçen süre
        // sayılmasın diye ölçüm ve sayaç sıfırdan başlatılır.
        measuredFieldStrength = 0f
        hapticPrimed = false
        disturbedSince = 0L
        disturbed = false
        lastShownFieldStrength = -1
        applySettings()
        registerSensors()
        ensureLocation()
        handler.post(sunTick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(sunTick)
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
        coordinates = latitude to longitude
        declination = field.declination
        expectedFieldStrength = field.fieldStrength / 1000f   // nT -> µT
        qiblaBearing = bearingTo(latitude, longitude, KAABA_LATITUDE, KAABA_LONGITUDE)
        applyMarks()
        updateSun()
        lastShownDegree = -1   // yazıların hemen tazelenmesi için
    }

    /** Güneşin yeri konum ve saatten hesaplanır; ikisi de bilinmeden çizilmez. */
    private fun updateSun() {
        val (latitude, longitude) = coordinates ?: return
        val position = Sun.position(System.currentTimeMillis(), latitude, longitude)
        sun = position
        applyMarks()
        refreshInfoText(lastMagnetic)
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
        if (targetMagnetic != null) {
            clearTarget()
            return
        }
        val magnetic = lastMagnetic ?: return   // henüz sensör okuması yok
        setTargetFromDial((magnetic + frameOffset()) % 360f)
    }

    /**
     * Elle kerteriz girişi. Kadrana dokunmak yalnızca *baktığınız* yönü
     * kilitleyebiliyor; haritadan okunan bir açıyı takip etmek için sayıyla
     * girmek gerekiyor. Girilen değer ekrandaki çerçeve ve birimle aynıdır.
     */
    private fun askForBearing() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = if (unit == Prefs.UNIT_MIL) "0-6400" else "0-360"
            shownTarget()?.let { setText(bearingValue(it).toString()) }
            setSelectAllOnFocus(true)
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val frame = FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.bearing_dialog_title)
            .setView(frame)
            .setPositiveButton(R.string.bearing_dialog_set) { _, _ ->
                val entered = input.text.toString().trim().toFloatOrNull() ?: return@setPositiveButton
                setTargetFromDial(bearingDegrees(entered))
            }
            .setNegativeButton(R.string.bearing_dialog_cancel, null)
        if (targetMagnetic != null) {
            builder.setNeutralButton(R.string.bearing_dialog_clear) { _, _ -> clearTarget() }
        }
        builder.show()
    }

    /** Ekranda gösterilen açının seçili birimdeki sayısal karşılığı. */
    private fun bearingValue(degrees: Float): Int {
        val normalized = (degrees % 360f + 360f) % 360f
        return if (unit == Prefs.UNIT_MIL) {
            (normalized * Prefs.MILS_PER_CIRCLE / 360f).roundToInt() % 6400
        } else {
            normalized.roundToInt() % 360
        }
    }

    /** Kullanıcının girdiği sayıyı dereceye çevirir. */
    private fun bearingDegrees(value: Float): Float =
        if (unit == Prefs.UNIT_MIL) value * 360f / Prefs.MILS_PER_CIRCLE else value

    /** Kadran çerçevesinde verilen açıyı hedef olarak kilitler. */
    private fun setTargetFromDial(dialBearing: Float) {
        val magnetic = (dialBearing - frameOffset() + 360f) % 360f
        targetMagnetic = magnetic
        prefs().edit().putFloat(KEY_TARGET, magnetic).apply()
        applyTarget()
        refreshTargetText()
    }

    private fun clearTarget() {
        targetMagnetic = null
        prefs().edit().remove(KEY_TARGET).apply()
        applyTarget()
        refreshTargetText()
    }

    /** Kilitli hedefin ekranda gösterilen çerçevedeki (gerçek kuzey) karşılığı. */
    private fun shownTarget(): Float? =
        targetMagnetic?.let { (it + frameOffset() + 360f) % 360f }

    private fun applyTarget() = compassView.setTargetBearing(shownTarget())

    /**
     * Hedef ve nokta bilgisini tek satırda toplar. İkisi de renk kodlu, kadrandaki
     * işaretlerle eşleşsin diye; hiçbiri yoksa satır iki hareketi de anlatır.
     */
    private fun refreshTargetText() {
        val parts = SpannableStringBuilder()
        targetSegment()?.let { appendColored(parts, it, palette.target, "   ") }
        waypointSegment()?.let { appendColored(parts, it, palette.waypoint, "   ") }
        if (parts.isEmpty()) {
            targetText.setTextColor(palette.hint)
            targetText.text = getString(R.string.target_hint)
        } else {
            targetText.text = parts
        }
    }

    private fun appendColored(
        builder: SpannableStringBuilder,
        text: String,
        color: Int,
        separator: String = " · "
    ) {
        if (builder.isNotEmpty()) builder.append(separator)
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
        val targetLabel = formatBearing(target)
        val shown = lastShownDegree
        if (shown < 0) return getString(R.string.target_plain, targetLabel)
        // Hedefe kalan açı: pozitifse saat yönünde, yani sağa dönmek gerekir.
        val diff = ((target - shown + 540f) % 360f) - 180f
        val amount = abs(diff).roundToInt()
        return when {
            amount <= ON_TARGET_DEGREES -> getString(R.string.target_reached, targetLabel)
            diff > 0f -> getString(R.string.target_right, targetLabel, formatDelta(amount))
            else -> getString(R.string.target_left, targetLabel, formatDelta(amount))
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
        return getString(
            R.string.waypoint_line,
            formatBearing(toDialFrame(bearing)),
            formatDistance(results[0])
        )
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
            else toDialFrame(bearingTo(here.latitude, here.longitude, wp.first, wp.second))
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
            val alpha = smoothingAlpha
            smoothSin += alpha * (s - smoothSin)
            smoothCos += alpha * (c - smoothCos)
            smoothPitch += alpha * (pitchDegrees - smoothPitch)
            smoothRoll += alpha * (rollDegrees - smoothRoll)
        }

        val magnetic =
            ((Math.toDegrees(atan2(smoothSin, smoothCos).toDouble()) + 360.0) % 360.0).toFloat()
        lastMagnetic = magnetic
        // Gerçek kuzey = manyetik kuzey + sapma (sapma doğuya doğru pozitif);
        // ayarlardan manyetik kuzey seçilmişse düzeltme uygulanmaz.
        val trueFrame = useTrueNorth && declination != null
        val shown = (magnetic + frameOffset() + 360f) % 360f

        compassView.setAzimuth(shown)
        compassView.setTilt(smoothPitch, smoothRoll)
        updateCardinalHaptics(shown)
        updateTiltWarning()

        val rounded = shown.roundToInt() % 360
        if (rounded != lastShownDegree) {
            lastShownDegree = rounded
            degreeText.text = formatBearing(shown)
            directionText.text = "${cardinal(shown)} · " +
                getString(if (trueFrame) R.string.true_north else R.string.magnetic_north)
            refreshInfoText(magnetic)
            refreshTargetText()
        }
    }

    /**
     * Ana yönlerden birine girince kısa bir tık verir: ekrana bakmadan yön
     * tutmayı sağlar. Girme ve çıkma eşikleri farklı, yoksa sınırda titreşim
     * sayısını sayamazdınız. Sistem dokunsal geri bildirimi kapalıysa
     * `performHapticFeedback` sessizce hiçbir şey yapmaz — kullanıcının tercihi.
     */
    private fun updateCardinalHaptics(shown: Float) {
        val nearest = (shown / 90f).roundToInt() % 4
        val delta = abs(((shown - nearest * 90f + 540f) % 360f) - 180f)

        if (!hapticPrimed) {
            hapticCardinal = if (delta <= CARDINAL_ENTER_DEGREES) nearest else -1
            hapticPrimed = true
            return
        }
        if (hapticCardinal == nearest) {
            if (delta > CARDINAL_EXIT_DEGREES) hapticCardinal = -1
            return
        }
        if (delta <= CARDINAL_ENTER_DEGREES) {
            hapticCardinal = nearest
            tickForCardinal()
        }
    }

    /**
     * Kısa tık. Kullanıcı sistemde dokunsal geri bildirimi kapattıysa
     * titreşmez — kendi efektimizi verdiğimiz için bu tercihi elle gözetiyoruz.
     */
    private fun tickForCardinal() {
        val enabled = Settings.System.getInt(
            contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1
        ) != 0
        if (!enabled || !vibrateOnCardinals) return
        // Açılışta yumuşatma otururken açı birkaç bölgeyi hızla kesebiliyor;
        // ölçümde 23 ms içinde üç tık görüldü. Asgari aralık bunu tek tıka indirir.
        val now = SystemClock.elapsedRealtime()
        if (now - lastTickAt < CARDINAL_TICK_MIN_GAP_MS) return
        lastTickAt = now
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            device.vibrate(VibrationEffect.createOneShot(CARDINAL_TICK_MS, CARDINAL_TICK_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            device.vibrate(CARDINAL_TICK_MS)
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
                val magneticPart = magnetic?.let { "Manyetik ${formatBearing(it)} · " } ?: ""
                val yon = if (decl >= 0f) "D" else "B"
                magneticPart + "Sapma %.1f°%s".format(abs(decl), yon)
            }
        }
        // Kıble ve güneş kadrandaki işaretlerle aynı renkte yazılır ki hangisinin
        // hangisi olduğu bakınca anlaşılsın.
        val text = SpannableStringBuilder(base)
        qiblaBearing?.takeIf { showQibla }?.let {
            appendColored(text, getString(R.string.qibla_info, formatBearing(toDialFrame(it))), palette.qibla)
        }
        sun?.takeIf { showSun }?.let {
            val label =
                if (it.elevation > 0f) getString(R.string.sun_info, formatBearing(toDialFrame(it.azimuth)))
                else getString(R.string.sun_info_below, formatBearing(toDialFrame(it.azimuth)))
            appendColored(text, label, palette.sun)
        }
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
        const val SUN_UPDATE_MS = 60_000L

        // Ana yöne bu kadar yaklaşınca tık verilir, bu kadar uzaklaşınca sıfırlanır.
        const val CARDINAL_ENTER_DEGREES = 2f
        const val CARDINAL_EXIT_DEGREES = 5f
        /**
         * 45 ms ve tam genlik. Ölçümden çıktı: Galaxy A51'in titreşim motoru ERM
         * (dönen ağırlık) ve dönmeye başlaması ~30-50 ms alıyor; 20 ms'lik darbe
         * kayda düşüyor ama elde hiç hissedilmiyordu. Cihazın dokunsal şiddeti de
         * LOW olduğu için varsayılan genlik ayrıca kısılıyor, o yüzden genlik açık
         * veriliyor. Aynı sebeple performHapticFeedback'te çalışan tek sabit
         * 48 ms'lik LONG_PRESS'ti.
         */
        const val CARDINAL_TICK_MS = 45L
        const val CARDINAL_TICK_AMPLITUDE = 255
        const val CARDINAL_TICK_MIN_GAP_MS = 700L

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

    }
}
