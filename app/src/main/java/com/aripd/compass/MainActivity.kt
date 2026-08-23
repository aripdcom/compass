package com.aripd.compass

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
import android.view.WindowInsets
import android.view.WindowInsetsController
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
import kotlin.math.exp
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
    private lateinit var marksText: TextView
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

    /** Sabit noktaların gerçek kuzeye göre yönleri; konum bilinmeden hesaplanamaz. */
    private var placeBearings: Map<String, Float> = emptyMap()

    /**
     * Kilitlenen yön **manyetik** çerçevede saklanır: sapma sonradan öğrenilse
     * bile (izin gecikmeli verildiğinde) hedef kaymasın diye.
     */
    private var targetMagnetic: Float? = null
    private var lastMagnetic: Float? = null

    private var needsCalibration = false
    private var tilted = false

    /** En son hangi ana yöne yakındık (0=K, 1=D, 2=G, 3=B); -1 henüz bilinmiyor. */
    private var hapticCardinal = -1

    /** Ana yöne göre işaretli fark; işaret değişimi geçiş demektir. */
    private var lastCardinalOffset = 0f

    /** Tık verildikten sonra yeterince uzaklaşılana kadar yeniden tetiklenmez. */
    private var hapticArmed = false

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
    private var fullscreen = Prefs.DEFAULT_FULLSCREEN
    private var unit = Prefs.DEFAULT_UNIT
    private var useTrueNorth = Prefs.DEFAULT_TRUE_NORTH
    private var smoothingTimeConstant = Prefs.SMOOTHING_TIME_CONSTANTS[Prefs.DEFAULT_SMOOTHING]

    /** Bir önceki sensör olayının anı (ns); örnek aralığını ölçmek için. */
    private var lastSensorTimestamp = 0L

    /** Son çizimin anı; kadran sensörden bağımsız bir hızda tazelenir. */
    private var lastRenderAt = 0L
    private var vibrateOnCardinals = Prefs.DEFAULT_VIBRATE

    /** Sistemin dokunsal geri bildirim tercihi; `onResume`'da tazelenir. */
    private var systemHapticsEnabled = true
    private var showMagnetic = Prefs.DEFAULT_SHOW_MAGNETIC
    private var showLevel = Prefs.DEFAULT_SHOW_LEVEL
    private var visiblePlaces: Set<String> = emptySet()
    private var showSun = Prefs.DEFAULT_SHOW_SUN
    private var showSunArc = Prefs.DEFAULT_SHOW_SUN_ARC
    private var showMoon = Prefs.DEFAULT_SHOW_MOON
    private val palette: Palette get() = Palette.of(nightMode)

    /** Kaydedilen noktalar. Kadrana uzun basmak yenisini ekler. */
    private var waypoints: List<Waypoint> = emptyList()

    /** Sapma, kıble ve güneş için kullanılan konum; önbellekten de gelebilir. */
    private var coordinates: Pair<Double, Double>? = null

    /** Ayarlara en son yazılan konum; gereksiz disk yazımını elemek için. */
    private var cachedCoordinates: Pair<Double, Double>? = null
    private var sun: Sun.Position? = null
    private var sunArc: Sun.RiseSet? = null
    private var moon: Moon.Position? = null

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
        marksText = findViewById(R.id.marksText)
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
        if (!lat.isNaN() && !lon.isNaN()) {
            cachedCoordinates = lat.toDouble() to lon.toDouble()
            applyCoordinates(lat.toDouble(), lon.toDouble(), 0.0)
        }

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
            showLocationActions()
            true
        }
        compassView.contentDescription = getString(R.string.a11y_dial)
        compassView.setOnClickListener { toggleTarget() }
        compassView.setOnLongClickListener {
            addWaypoint()
            true
        }
        targetText.setOnLongClickListener {
            showWaypointList()
            true
        }

        loadWaypoints()
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
        visiblePlaces = Places.ALL
            .filter { stored.getBoolean(it.prefKey, it.defaultVisible) }
            .map { it.prefKey }
            .toSet()
        showSun = stored.getBoolean(Prefs.KEY_SHOW_SUN, Prefs.DEFAULT_SHOW_SUN)
        showSunArc = stored.getBoolean(Prefs.KEY_SHOW_SUN_ARC, Prefs.DEFAULT_SHOW_SUN_ARC)
        showMoon = stored.getBoolean(Prefs.KEY_SHOW_MOON, Prefs.DEFAULT_SHOW_MOON)
        smoothingTimeConstant = Prefs.SMOOTHING_TIME_CONSTANTS[
            stored.getInt(Prefs.KEY_SMOOTHING, Prefs.DEFAULT_SMOOTHING)
                .coerceIn(0, Prefs.SMOOTHING_TIME_CONSTANTS.lastIndex)
        ]
        fullscreen = stored.getBoolean(Prefs.KEY_FULLSCREEN, Prefs.DEFAULT_FULLSCREEN)
        applyFullscreen()
        if (stored.getBoolean(Prefs.KEY_KEEP_SCREEN, Prefs.DEFAULT_KEEP_SCREEN)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        applyPalette()
        applyMarks()
        lastShownDegree = -1   // yazılar yeni birimle hemen kurulsun
    }

    /**
     * Tam ekran: durum ve gezinme çubukları gizlenir, kenardan kaydırınca geçici
     * olarak geri gelir. Kadran ekranın tamamını kullandığı için kazanılan yer
     * doğrudan kadranın çapına gider.
     */
    private fun applyFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(!fullscreen)
            val controller = window.insetsController
            if (fullscreen) {
                controller?.hide(WindowInsets.Type.systemBars())
                controller?.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller?.show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (fullscreen) {
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else {
                View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    /**
     * Çubuklar kaydırmayla geçici olarak göründükten sonra kendiliğinden
     * gizlenmiyor; odak geri geldiğinde yeniden uygulanır.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreen()
    }

    /** Kadran işaretlerini kuzey çerçevesine ve görünürlük ayarlarına göre kurar. */
    private fun applyMarks() {
        // Manyetik çerçevedeyken "M" kadranın kuzeyiyle çakışır, gösterilmez.
        compassView.setMagneticNorthOffset(if (useTrueNorth && showMagnetic) declination else null)
        compassView.levelVisible = showLevel
        compassView.setPlaceMarks(mergeNearbyMarks(
            Places.ALL.filter { it.prefKey in visiblePlaces }.mapNotNull { place ->
                placeBearings[place.prefKey]?.let {
                    PlaceMark(getString(place.labelRes), toDialFrame(it))
                }
            }
        ))
        compassView.setSun(
            if (showSun) sun?.azimuth?.let(::toDialFrame) else null,
            (sun?.elevation ?: 0f) > 0f
        )
        compassView.setMoon(
            moon?.takeIf { showMoon }?.let {
                MoonMark(toDialFrame(it.azimuth), it.elevation > 0f, it.illumination, it.waxing)
            }
        )
        compassView.setSunArc(
            if (showSunArc) sunArc?.let { toDialFrame(it.rise) to toDialFrame(it.set) } else null
        )
        applyTarget()
        applyWaypoint()
    }

    /**
     * Birbirine çok yakın işaretleri tek etikette toplar. Türkiye'den bakınca
     * kıble ile Mescid-i Aksa arasında ~2° var; kadranda iki ayrı etiket
     * göstermek hem okunmaz oluyor hem de bir bilgi katmıyor, çünkü o
     * çözünürlükte ikisi zaten aynı yön. Kesin dereceler alt satırda yazıyor.
     */
    private fun mergeNearbyMarks(marks: List<PlaceMark>): List<PlaceMark> {
        // Sıra listedeki sırayla korunur (Kâbe, Aksa, Vatikan); birleşen etiket
        // "Kıble·Aksa" diye okunsun diye, yönlerine göre değil.
        val remaining = marks.toMutableList()
        val merged = ArrayList<PlaceMark>()
        while (remaining.isNotEmpty()) {
            val first = remaining.removeAt(0)
            val group = arrayListOf(first)
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (Geo.separation(candidate.bearing, first.bearing) < MERGE_DEGREES) {
                    group.add(candidate)
                    iterator.remove()
                }
            }
            merged.add(
                if (group.size == 1) first
                else PlaceMark(group.joinToString("·") { it.label }, Geo.meanBearing(group.map { it.bearing }))
            )
        }
        return merged
    }

    /** Gerçek kuzeye göre verilen açıyı kadranın çerçevesine çevirir. */
    private fun toDialFrame(trueBearing: Float): Float =
        Geo.toDialFrame(trueBearing, declination, useTrueNorth)

    /** Manyetik açıya eklenince kadran çerçevesini veren düzeltme. */
    private fun frameOffset(): Float = if (useTrueNorth) declination ?: 0f else 0f

    /** Açıyı seçili birimde yazar: derece ya da NATO mili (tam çember 6400). */
    private fun formatBearing(degrees: Float): String {
        return if (unit == Prefs.UNIT_MIL) {
            "%d %s".format(Geo.degreesToMils(degrees), getString(R.string.mil_suffix))
        } else {
            "%d°".format(Geo.normalize(degrees).roundToInt() % 360)
        }
    }

    /** Fark açısı: yön değil miktar olduğu için 360'a sarılmaz. */
    private fun formatDelta(degrees: Int): String =
        if (unit == Prefs.UNIT_MIL) {
            "%d %s".format((degrees * Geo.MILS_PER_CIRCLE / 360f).roundToInt(), getString(R.string.mil_suffix))
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
        marksText.setTextColor(colors.textDim)
        locationText.setTextColor(colors.textDim)
        statusText.setTextColor(colors.warning)
        settingsButton.setTextColor(colors.textDim)
        // Bunlar renkli parça içerdiği için baştan kurulmalı. Nokta satırı
        // önbellekte durduğundan renk ya da birim değişince o da yenilenmeli.
        refreshInfoText(lastMagnetic)
        refreshWaypointSpan()
        refreshTargetText()
    }

    override fun onResume() {
        super.onResume()
        // Bozulma kararı kesintisiz gözleme dayanıyor; arka planda geçen süre
        // sayılmasın diye ölçüm ve sayaç sıfırdan başlatılır.
        measuredFieldStrength = 0f
        hapticPrimed = false
        lastSensorTimestamp = 0L
        lastRenderAt = 0L
        disturbedSince = 0L
        disturbed = false
        lastShownFieldStrength = -1
        systemHapticsEnabled = Settings.System.getInt(
            contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1
        ) != 0
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
            sensorManager.registerListener(this, rv, SensorManager.SENSOR_DELAY_UI)
            // Rotation vector yönü verir ama alanın büyüklüğünü vermez; anomali
            // ancak ham manyetometreden görülür, o yüzden onu da dinliyoruz.
            if (mag != null) sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
            return
        }
        val acc = accelerometer
        if (acc != null && mag != null) {
            sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
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

            // İki sağlayıcı da dinlenir ama farklı sıklıkta. Sapma, kıble, güneş
            // ve ay kilometreler mertebesinde değişir; onlar için ağ konumu bol
            // bol yeter ve neredeyse bedavadır. GPS yalnızca koordinat paneli ve
            // nokta mesafesi için gerekli, o da saniyede bir tazelenmek zorunda
            // değil — aralıklar buna göre seyreltildi.
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, NETWORK_INTERVAL_MS, NETWORK_DISTANCE_M, locationListener
                )
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, GPS_INTERVAL_MS, GPS_DISTANCE_M, locationListener
                )
            }
        } catch (_: SecurityException) {
        }
        if (declination == null) refreshInfoText(null)
    }

    private fun applyLocation(location: Location) {
        if (!isBetterFix(location, lastLocation)) return
        lastLocation = location
        refreshLocationText()
        refreshWaypointFixes()
        applyWaypoint()
        refreshTargetText()
        saveCoordinateCache(location)
        applyCoordinates(location.latitude, location.longitude, location.altitude)
    }

    /**
     * Önbelleğe alınan konum yalnızca bir sonraki açılışta sapmayı ve kıbleyi
     * beklemeden gösterebilmek için var; metre mertebesinde güncel olması
     * gerekmiyor. Her fix'te yazmak GPS açıkken dakikada dört disk işlemi
     * demekti, o yüzden ancak kayda değer bir yol alınınca yazılıyor.
     */
    private fun saveCoordinateCache(location: Location) {
        cachedCoordinates?.let { (lat, lon) ->
            val results = FloatArray(1)
            Location.distanceBetween(lat, lon, location.latitude, location.longitude, results)
            if (results[0] < COORDINATE_CACHE_DISTANCE_M) return
        }
        cachedCoordinates = location.latitude to location.longitude
        prefs().edit()
            .putFloat(KEY_LATITUDE, location.latitude.toFloat())
            .putFloat(KEY_LONGITUDE, location.longitude.toFloat())
            .apply()
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
            parts.append(dms(location.latitude, R.string.hemisphere_north, R.string.hemisphere_south))
            parts.append("  ")
            parts.append(dms(location.longitude, R.string.hemisphere_east, R.string.hemisphere_west))
        } else {
            parts.append(decimalCoordinate(location.latitude, R.string.hemisphere_north, R.string.hemisphere_south))
            parts.append("  ")
            parts.append(decimalCoordinate(location.longitude, R.string.hemisphere_east, R.string.hemisphere_west))
        }
        if (location.hasAltitude()) parts.append(" · %d m".format(location.altitude.roundToInt()))
        if (location.hasAccuracy()) parts.append(" · ±%d m".format(location.accuracy.roundToInt()))
        if (!hasPreciseLocation()) parts.append(" · ").append(getString(R.string.approximate_location))
        locationText.text = parts.toString()
    }

    /** Ondalık dereceyi derece-dakika-saniyeye çevirir. */
    private fun dms(value: Double, positiveRes: Int, negativeRes: Int): String {
        val magnitude = abs(value)
        val degrees = floor(magnitude).toInt()
        val minutesFull = (magnitude - degrees) * 60.0
        val minutes = floor(minutesFull).toInt()
        val seconds = (minutesFull - minutes) * 60.0
        return getString(
            R.string.coordinate_dms,
            degrees,
            "%02d".format(minutes),
            "%04.1f".format(seconds),
            getString(if (value >= 0) positiveRes else negativeRes)
        )
    }

    private fun decimalCoordinate(value: Double, positiveRes: Int, negativeRes: Int): String =
        getString(
            R.string.coordinate_decimal,
            "%.5f".format(abs(value)),
            getString(if (value >= 0) positiveRes else negativeRes)
        )

    /** Konum satırına uzun basınca: kopyala ya da paylaş. */
    private fun showLocationActions() {
        val location = lastLocation ?: return
        val actions = arrayOf(
            getString(R.string.location_action_copy),
            getString(R.string.location_action_share)
        )
        AlertDialog.Builder(this)
            .setItems(actions) { _, which ->
                if (which == 0) copyLocation()
                else shareLocation(null, location.latitude, location.longitude)
            }
            .setNegativeButton(R.string.bearing_dialog_cancel, null)
            .show()
    }

    /**
     * Konumu metin olarak paylaşır. Yanına harita bağlantısı da konur: alıcı
     * ondalık dereceyi bir uygulamaya yapıştırmak zorunda kalmasın. Bağlantı
     * hesap istemeyen ve tarayıcıda da açılan OpenStreetMap'e verilir.
     */
    private fun shareLocation(name: String?, latitude: Double, longitude: Double) {
        val locale = java.util.Locale.US
        val coordinates = "%.6f, %.6f".format(locale, latitude, longitude)
        val link = "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=17/%.6f/%.6f"
            .format(locale, latitude, longitude, latitude, longitude)
        val text = if (name.isNullOrBlank()) "$coordinates\n$link" else "$name\n$coordinates\n$link"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser)))
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

    /**
     * Konumdan türeyen her şey: manyetik sapma ve kıble yönü.
     *
     * Sapma yüzlerce kilometrede bir derece, kıble binlerce kilometrede kayda
     * değer biçimde oynar. GPS ise on beş saniyede bir fix gönderiyor; her
     * fix'te WMM'nin küresel harmonik modelini yeniden çözüp bütün yer
     * yönlerini, güneşi ve ayı baştan hesaplamanın karşılığı yok. Kayda değer
     * bir yol alınmadıysa eldeki değerler aynen geçerli.
     */
    private fun applyCoordinates(latitude: Double, longitude: Double, altitude: Double) {
        coordinates?.let { (lat, lon) ->
            val results = FloatArray(1)
            Location.distanceBetween(lat, lon, latitude, longitude, results)
            if (results[0] < COORDINATE_REFRESH_DISTANCE_M) return
        }
        val field = GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            altitude.toFloat(),
            System.currentTimeMillis()
        )
        coordinates = latitude to longitude
        declination = field.declination
        expectedFieldStrength = field.fieldStrength / 1000f   // nT -> µT
        placeBearings = Places.ALL.associate { place ->
            place.prefKey to Geo.bearing(latitude, longitude, place.latitude, place.longitude)
        }
        applyMarks()
        updateSun()
        lastShownDegree = -1   // yazıların hemen tazelenmesi için
    }

    /** Güneşin yeri konum ve saatten hesaplanır; ikisi de bilinmeden çizilmez. */
    private fun updateSun() {
        val (latitude, longitude) = coordinates ?: return
        val now = System.currentTimeMillis()
        val position = Sun.position(now, latitude, longitude)
        sun = position
        sunArc = Sun.riseSet(now, latitude, longitude)
        moon = Moon.position(now, latitude, longitude)
        applyMarks()
        refreshInfoText(lastMagnetic)
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
        return if (unit == Prefs.UNIT_MIL) Geo.degreesToMils(degrees)
        else Geo.normalize(degrees).roundToInt() % 360
    }

    /** Kullanıcının girdiği sayıyı dereceye çevirir. */
    private fun bearingDegrees(value: Float): Float =
        if (unit == Prefs.UNIT_MIL) Geo.milsToDegrees(value) else value

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
        // Nokta satırı hazır geliyor: yalnızca hedefe kalan açı her derecede
        // değişiyor, noktalar yer değiştirmedikçe aynı kalıyor.
        if (waypointSpan.isNotEmpty()) {
            if (parts.isNotEmpty()) parts.append("   ")
            parts.append(waypointSpan)
        }
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
        val diff = Geo.difference(shown.toFloat(), target)
        val amount = abs(diff).roundToInt()
        return when {
            amount <= ON_TARGET_DEGREES -> getString(R.string.target_reached, targetLabel)
            diff > 0f -> getString(R.string.target_right, targetLabel, formatDelta(amount))
            else -> getString(R.string.target_left, targetLabel, formatDelta(amount))
        }
    }

    /**
     * Bir noktanın bulunduğumuz yere göre çözümü. Konum değişmedikçe sabittir,
     * o yüzden fix başına bir kez hesaplanıp saklanır.
     *
     * `arrived`: noktanın üstünde sayılıp sayılmadığımız. Mesafe konum hatasının
     * altına inince yön anlamını yitirir — hata çemberinin içinde hangi yöne
     * bakılacağını söylemek uydurma olur. Hem yazı hem kadran işareti buna bakar,
     * yoksa yazı "buradasınız" derken kadranda rastgele bir yöne etiket çıkıyordu.
     */
    private class WaypointFix(
        val point: Waypoint,
        val bearing: Float,
        val distance: Float,
        val arrived: Boolean
    )

    /**
     * Nokta çözümleri, konum ya da nokta listesi değiştikçe tazelenir.
     *
     * Önceden yön ve mesafe her derece değişiminde, her nokta için yeniden
     * hesaplanıyordu: sekiz noktayla saniyede ~500 `distanceBetween` çağrısı
     * ediyordu ve bunların yarısı zaten aynı hesabın tekrarıydı (`isArrived` ile
     * `waypointSegment` mesafeyi ayrı ayrı ölçüyordu). Oysa açı ve mesafe
     * yalnızca yer değiştirince değişir.
     */
    private var waypointFixes: List<WaypointFix> = emptyList()

    /** Nokta yazılarının hazır hâli; ancak çözümler ya da biçim değişince kurulur. */
    private var waypointSpan: CharSequence = ""

    private fun refreshWaypointFixes() {
        val here = lastLocation
        waypointFixes = if (here == null) emptyList() else {
            val arrivedWithin = (if (here.hasAccuracy()) here.accuracy else ARRIVED_MIN_METERS)
                .coerceIn(ARRIVED_MIN_METERS, ARRIVED_MAX_METERS)
            val results = FloatArray(1)
            waypoints.map { point ->
                Location.distanceBetween(
                    here.latitude, here.longitude, point.latitude, point.longitude, results
                )
                WaypointFix(
                    point,
                    Geo.bearing(here.latitude, here.longitude, point.latitude, point.longitude),
                    results[0],
                    results[0] <= arrivedWithin
                )
            }
        }
        refreshWaypointSpan()
    }

    /**
     * Çözümlerden nokta satırını kurar; renk ve birim buraya girer.
     *
     * Yalnızca en yakın birkaç nokta yazılır. Sekiz nokta kayıtlıyken satır
     * beş-altı satıra taşıyor ve ağırlığı 1 olan kadranın yerini yiyordu —
     * kadran uygulamanın asıl işi. Gerisi sayıyla anılıyor; hepsi kadranda
     * duruyor ve satıra uzun basınca açılan listede yazıyor.
     */
    private fun refreshWaypointSpan() {
        val builder = SpannableStringBuilder()
        val nearest = waypointFixes.sortedBy { it.distance }
        nearest.take(WAYPOINT_LINE_LIMIT).forEach { fix ->
            appendColored(builder, waypointSegment(fix), palette.waypoint, "   ")
        }
        val hidden = nearest.size - WAYPOINT_LINE_LIMIT
        if (hidden > 0) {
            appendColored(builder, getString(R.string.waypoint_more, hidden), palette.waypoint, "   ")
        }
        waypointSpan = builder
    }

    private fun waypointSegment(fix: WaypointFix): String =
        if (fix.arrived) getString(R.string.waypoint_here, fix.point.name)
        else getString(
            R.string.waypoint_line,
            fix.point.name,
            formatBearing(toDialFrame(fix.bearing)),
            formatDistance(fix.distance)
        )

    /** Yakında metre, uzakta kilometre; ondalık ayraç cihazın diline uyar. */
    /**
     * Saati cihazın biçimiyle yazar: 12/24 saat tercihi ve dil sistemden gelir,
     * uygulamanın kendi biçimi yoktur.
     */
    private fun formatTime(timeMillis: Long): String =
        android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(timeMillis))

    /** Yakında metre, uzakta kilometre; ondalık ayraç cihazın diline uyar. */
    private fun formatDistance(meters: Float): String =
        if (meters < 1000f) getString(R.string.distance_meters, meters.roundToInt())
        else getString(R.string.distance_kilometers, "%.1f".format(meters / 1000f))

    /**
     * Noktaları okur. Eski sürümlerde tek nokta iki ayrı anahtarda tutuluyordu;
     * varsa listeye taşınır ve eski anahtarlar silinir, böylece kimse kaydını
     * kaybetmez.
     */
    private fun loadWaypoints() {
        val stored = prefs()
        waypoints = Waypoints.decode(stored.getString(KEY_WAYPOINTS, null))
        val legacyLat = stored.getFloat(KEY_WAYPOINT_LATITUDE, Float.NaN)
        val legacyLon = stored.getFloat(KEY_WAYPOINT_LONGITUDE, Float.NaN)
        if (!legacyLat.isNaN() && !legacyLon.isNaN()) {
            waypoints = waypoints + Waypoint(
                Waypoints.nextName(waypoints) { getString(R.string.waypoint_default_name, it) },
                legacyLat.toDouble(),
                legacyLon.toDouble()
            )
            stored.edit()
                .remove(KEY_WAYPOINT_LATITUDE)
                .remove(KEY_WAYPOINT_LONGITUDE)
                .putString(KEY_WAYPOINTS, Waypoints.encode(waypoints))
                .apply()
        }
    }

    private fun saveWaypoints(list: List<Waypoint>) {
        waypoints = list
        prefs().edit().putString(KEY_WAYPOINTS, Waypoints.encode(list)).apply()
        refreshWaypointFixes()
        applyMarks()
        refreshTargetText()
    }

    /** Kadrana uzun basmak bulunduğun yeri yeni bir nokta olarak ekler. */
    private fun addWaypoint() {
        val here = lastLocation
        if (here == null) {
            Toast.makeText(this, getString(R.string.waypoint_needs_location), Toast.LENGTH_SHORT).show()
            return
        }
        if (waypoints.size >= Waypoints.LIMIT) {
            Toast.makeText(
                this,
                getString(R.string.waypoint_limit, Waypoints.LIMIT),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val name = Waypoints.nextName(waypoints) { getString(R.string.waypoint_default_name, it) }
        saveWaypoints(waypoints + Waypoint(name, here.latitude, here.longitude))
        Toast.makeText(this, getString(R.string.waypoint_saved, name), Toast.LENGTH_SHORT).show()
    }

    /** Kayıtlı noktalar: yön ve mesafeleriyle listelenir, seçilince yönetilir. */
    private fun showWaypointList() {
        if (waypoints.isEmpty()) {
            Toast.makeText(this, getString(R.string.waypoints_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val labels = waypoints.map { point ->
            waypointFixes.firstOrNull { it.point === point }?.let(::waypointSegment) ?: point.name
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.waypoints_title)
            .setItems(labels) { _, which -> showWaypointActions(waypoints[which]) }
            .setNegativeButton(R.string.bearing_dialog_cancel, null)
            .show()
    }

    private fun showWaypointActions(point: Waypoint) {
        val actions = arrayOf(
            getString(R.string.waypoint_rename),
            getString(R.string.location_action_share),
            getString(R.string.waypoint_delete)
        )
        AlertDialog.Builder(this)
            .setTitle(point.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showWaypointRename(point)
                    1 -> shareLocation(point.name, point.latitude, point.longitude)
                    else -> deleteWaypoint(point)
                }
            }
            .setNegativeButton(R.string.bearing_dialog_cancel, null)
            .show()
    }

    private fun showWaypointRename(point: Waypoint) {
        val input = EditText(this).apply {
            setText(point.name)
            hint = getString(R.string.waypoint_name_hint)
            setSelectAllOnFocus(true)
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val frame = FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.waypoint_rename)
            .setView(frame)
            .setPositiveButton(R.string.bearing_dialog_set) { _, _ ->
                val name = Waypoints.sanitize(input.text.toString())
                if (name.isEmpty()) return@setPositiveButton
                saveWaypoints(waypoints.map { if (it === point) it.copy(name = name) else it })
            }
            .setNegativeButton(R.string.bearing_dialog_cancel, null)
            .show()
    }

    private fun deleteWaypoint(point: Waypoint) {
        saveWaypoints(waypoints.filterNot { it === point })
        Toast.makeText(this, getString(R.string.waypoint_cleared, point.name), Toast.LENGTH_SHORT).show()
    }

    /** Noktaların kadrandaki yönleri; gerçek kuzeye göre, kadranla aynı çerçevede. */
    private fun applyWaypoint() {
        compassView.setWaypointMarks(
            mergeNearbyMarks(
                waypointFixes.filterNot { it.arrived }
                    .map { PlaceMark(it.point.name, toDialFrame(it.bearing)) }
            )
        )
    }

    // --------------------------------------------------------------- sensör

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                updateFromRotationMatrix(event.timestamp)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
                if (hasGeomagnetic &&
                    SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
                ) {
                    updateFromRotationMatrix(event.timestamp)
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                hasGeomagnetic = true
                updateFieldStrength()
                if (hasGravity &&
                    SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
                ) {
                    updateFromRotationMatrix(event.timestamp)
                }
            }
        }
    }

    private fun updateFromRotationMatrix(timestamp: Long) {
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
            // Katsayı örnek aralığından türetilir: hız değişse de yumuşatmanın
            // süresi sabit kalır. Aralık sıçramalarına karşı sınırlandırılıyor.
            val interval = ((timestamp - lastSensorTimestamp) / 1_000_000_000.0)
                .coerceIn(0.002, 0.25)
            val alpha = (1.0 - exp(-interval / smoothingTimeConstant)).toFloat()
            smoothSin += alpha * (s - smoothSin)
            smoothCos += alpha * (c - smoothCos)
            smoothPitch += alpha * (pitchDegrees - smoothPitch)
            smoothRoll += alpha * (rollDegrees - smoothRoll)
        }
        lastSensorTimestamp = timestamp

        val magnetic =
            ((Math.toDegrees(atan2(smoothSin, smoothCos).toDouble()) + 360.0) % 360.0).toFloat()
        lastMagnetic = magnetic
        // Gerçek kuzey = manyetik kuzey + sapma (sapma doğuya doğru pozitif);
        // ayarlardan manyetik kuzey seçilmişse düzeltme uygulanmaz.
        val trueFrame = useTrueNorth && declination != null
        val shown = (magnetic + frameOffset() + 360f) % 360f

        // Titreşim ve eğim uyarısı her örnekte değerlendirilir: ikisi de ucuz ve
        // hızlı çevirmede örnek atlamak geçişi kaçırmak demektir.
        updateCardinalHaptics(shown)
        updateTiltWarning()

        // Çizim ise seyreltilir. Ölçüm şunu gösterdi: uygulamanın SENSOR_DELAY_UI
        // istemesi bu cihazda işe yaramıyor, çünkü Android bağlantı başına
        // seyreltme yapmıyor — sensörü 50 Hz'de sürdüren başka bir abone varsa
        // olaylar bize de 50 Hz geliyor. O yüzden hızı burada sınırlıyoruz.
        val now = SystemClock.elapsedRealtime()
        if (now - lastRenderAt < RENDER_MIN_INTERVAL_MS) return
        lastRenderAt = now

        compassView.setAzimuth(shown)
        compassView.setTilt(smoothPitch, smoothRoll)

        val rounded = shown.roundToInt() % 360
        if (rounded != lastShownDegree) {
            lastShownDegree = rounded
            degreeText.text = formatBearing(shown)
            val frameName = getString(if (trueFrame) R.string.true_north else R.string.magnetic_north)
            directionText.text = "${cardinal(shown)} · $frameName"
            // Ekran okuyucu "284°" ve "BKB" yerine açık ifadeyi okusun: kısaltma
            // harf harf okunuyor, derece işareti de her okuyucuda tutmuyor.
            degreeText.contentDescription =
                getString(R.string.a11y_heading, spokenBearing(shown), cardinalName(shown), frameName)
            refreshInfoText(magnetic)
            refreshTargetText()
        }
    }

    /**
     * Ana yön geçişinde kısa tık. Bölge değil **geçiş** algılanır: ana yöne göre
     * işaretli fark iki örnek arasında işaret değiştirdiyse üzerinden geçilmiştir.
     *
     * Bunun sebebi örnekleme hızı: "2° yaklaşınca tık" kuralı 50 Hz'de çalışıyordu
     * ama 16 Hz'de hızlı çevirmede örnekler 5-6° atlıyor ve 4°'lik pencere tümüyle
     * ıskalanabiliyor. Geçiş algılama hızdan bağımsızdır.
     *
     * Tam ana yönde durulduğunda gürültü işareti sürekli değiştirebileceği için
     * bir kez tıkladıktan sonra en az `CARDINAL_ARM_DEGREES` uzaklaşılmadan
     * yeniden tıklanmaz.
     */
    private fun updateCardinalHaptics(shown: Float) {
        val nearest = (shown / 90f).roundToInt() % 4
        val offset = Geo.difference(nearest * 90f, shown)

        if (!hapticPrimed || nearest != hapticCardinal) {
            hapticPrimed = true
            hapticCardinal = nearest
            hapticArmed = abs(offset) > CARDINAL_ARM_DEGREES
            lastCardinalOffset = offset
            return
        }
        if (!hapticArmed) {
            if (abs(offset) > CARDINAL_ARM_DEGREES) hapticArmed = true
        } else if ((offset > 0f) != (lastCardinalOffset > 0f)) {
            hapticArmed = false
            tickForCardinal()
        }
        lastCardinalOffset = offset
    }

    /**
     * Kısa tık. Kullanıcı sistemde dokunsal geri bildirimi kapattıysa
     * titreşmez — kendi efektimizi verdiğimiz için bu tercihi elle gözetiyoruz.
     * Tercih `onResume`'da bir kez okunur: her tıkta sormak bir ContentResolver
     * sorgusu demekti ve ayar uygulama önplandayken değişmiyor.
     */
    private fun tickForCardinal() {
        if (!systemHapticsEnabled || !vibrateOnCardinals) return
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
                val magneticPart =
                    magnetic?.let { getString(R.string.magnetic_reading, formatBearing(it)) + " · " } ?: ""
                val hemisphere = getString(
                    if (decl >= 0f) R.string.hemisphere_east else R.string.hemisphere_west
                )
                magneticPart + getString(R.string.declination_reading, "%.1f".format(abs(decl)), hemisphere)
            }
        }
        infoText.text = base
        infoText.contentDescription = decl?.let { d ->
            getString(
                R.string.a11y_declination,
                magnetic?.let { spokenBearing(it) } ?: "",
                "%.1f".format(abs(d)),
                getString(if (d >= 0f) R.string.a11y_declination_east else R.string.a11y_declination_west)
            )
        } ?: base

        // İşaret yönleri ayrı satırda: pusulanın kendi durumu (manyetik açı ve
        // sapma) ile "neyin nerede olduğu" farklı sorular, hepsi tek satıra
        // dizilince üç sıraya taşıp okunmaz oluyordu. Renkler kadrandaki
        // işaretlerle eşleşir.
        val marks = SpannableStringBuilder()
        Places.ALL.filter { it.prefKey in visiblePlaces }.forEach { place ->
            placeBearings[place.prefKey]?.let {
                appendColored(
                    marks,
                    getString(R.string.place_info, getString(place.labelRes), formatBearing(toDialFrame(it))),
                    palette.qibla
                )
            }
        }
        sun?.takeIf { showSun }?.let {
            val label =
                if (it.elevation > 0f) getString(R.string.sun_info, formatBearing(toDialFrame(it.azimuth)))
                else getString(R.string.sun_info_below, formatBearing(toDialFrame(it.azimuth)))
            appendColored(marks, label, palette.sun)
        }
        sunArc?.takeIf { showSunArc }?.let {
            appendColored(
                marks,
                getString(
                    R.string.sun_rise_set,
                    formatTime(it.riseAt),
                    formatBearing(toDialFrame(it.rise)),
                    formatTime(it.setAt),
                    formatBearing(toDialFrame(it.set))
                ),
                palette.sun
            )
        }
        moon?.takeIf { showMoon }?.let {
            val percent = (it.illumination * 100).roundToInt()
            appendColored(
                marks,
                if (it.elevation > 0f) getString(R.string.moon_info, formatBearing(toDialFrame(it.azimuth)), percent)
                else getString(R.string.moon_info_below, formatBearing(toDialFrame(it.azimuth)), percent),
                palette.moon
            )
        }
        marksText.text = marks
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

    /** Açının ekran okuyucuya söylenecek hâli: "284 derece" ya da "5049 mil". */
    private fun spokenBearing(degrees: Float): String {
        return if (unit == Prefs.UNIT_MIL) {
            getString(R.string.a11y_mils, Geo.degreesToMils(degrees))
        } else {
            getString(R.string.a11y_degrees, Geo.normalize(degrees).roundToInt() % 360)
        }
    }

    /**
     * Yön adları saniyede yirmi kez okunabiliyordu; `getStringArray` her seferinde
     * kaynak tablosuna gidip yeni bir dizi üretiyor. Dil değişince etkinlik zaten
     * yeniden kurulduğu için bir kez okumak yeter.
     */
    private val cardinalNames: Array<String> by lazy { resources.getStringArray(R.array.cardinal_names) }
    private val cardinalAbbreviations: Array<String> by lazy {
        resources.getStringArray(R.array.cardinal_abbreviations)
    }

    /** Yön adının açık hâli: "BKB" değil "batı kuzeybatı". */
    private fun cardinalName(degrees: Float): String = cardinalNames[cardinalIndex(degrees)]

    private fun cardinal(degrees: Float): String = cardinalAbbreviations[cardinalIndex(degrees)]

    /** On altı yönlü gülde açının düştüğü dilim. */
    private fun cardinalIndex(degrees: Float): Int = ((degrees / 22.5f) + 0.5f).toInt() % 16

    private fun prefs() = getSharedPreferences("compass", Context.MODE_PRIVATE)

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

        /** İki çizim arasındaki asgari süre; 50 ms yaklaşık 20 kare/saniye eder. */
        const val RENDER_MIN_INTERVAL_MS = 50L

        // Konum güncelleme sıklıkları. Mesafe süzgeci bilerek sıfır: Android
        // güncellemeyi ancak hem süre dolduğunda hem de o kadar yol alındığında
        // gönderiyor, dolayısıyla süzgeç konulunca sabit duran telefona GPS hiç
        // fix göndermiyor ve panel ağ konumunun ±100 m'sine düşüyordu. Sıklığı
        // yalnızca süre belirliyor.
        const val NETWORK_INTERVAL_MS = 60_000L
        const val NETWORK_DISTANCE_M = 0f
        const val GPS_INTERVAL_MS = 15_000L
        const val GPS_DISTANCE_M = 0f

        /** Bu açıdan yakın kadran işaretleri tek etikette birleşir. */
        const val MERGE_DEGREES = 6f

        /** Tıktan sonra yeniden tetiklenmek için gereken uzaklaşma (derece). */
        const val CARDINAL_ARM_DEGREES = 3f
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

        /**
         * Konum önbelleğini yenilemek ve sapma/kıble/güneşi baştan hesaplamak
         * için gereken yer değiştirme. Sapma bu ölçekte binde bir derece oynar.
         */
        const val COORDINATE_CACHE_DISTANCE_M = 250f
        const val COORDINATE_REFRESH_DISTANCE_M = 1_000f

        const val ARRIVED_MIN_METERS = 10f
        const val ARRIVED_MAX_METERS = 25f

        /** Hedef satırında adıyla yazılan nokta sayısı; gerisi "+n" olur. */
        const val WAYPOINT_LINE_LIMIT = 2

        const val KEY_WAYPOINTS = "waypoints"

        // Eski sürümlerin tek noktası; okunup listeye taşındıktan sonra silinir.
        const val KEY_WAYPOINT_LATITUDE = "waypointLatitude"
        const val KEY_WAYPOINT_LONGITUDE = "waypointLongitude"

        /**
         * Bu yaştan büyük fark varsa yeni fix koşulsuz kazanır. Ağ konumu GPS'ten
         * seyrek geldiği için kısa tutulursa kaba fix hassas olanı devirir.
         */
        const val FIX_STALE_MS = 120_000L

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
