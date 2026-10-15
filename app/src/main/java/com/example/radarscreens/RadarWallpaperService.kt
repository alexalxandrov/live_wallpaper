package com.example.radarscreens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class RadarWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = RadarEngine()

    private inner class RadarEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val prefs by lazy {
            getSharedPreferences(WallpaperSettings.PREFS_NAME, MODE_PRIVATE)
        }
        private var visible = false
        private var bootMode = WallpaperSettings.MODE_SYNC
        private var sessionStartedAt = SystemClock.elapsedRealtime()
        private var startOffsets = LongArray(SCREEN_COUNT)
        private var canvasWidth = 0
        private var canvasHeight = 0

        private val paint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            typeface = Typeface.create("monospace", Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        private val frameTask = object : Runnable {
            override fun run() {
                if (visible) {
                    drawFrame()
                    handler.postDelayed(this, FRAME_INTERVAL_MS)
                }
            }
        }

        init {
            setTouchEventsEnabled(true)
            bootMode = prefs.getInt(WallpaperSettings.KEY_BOOT_MODE, WallpaperSettings.MODE_SYNC)
            createBootSchedule(sessionStartedAt)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(frameTask)
            if (visible) {
                drawFrame()
                handler.post(frameTask)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            canvasWidth = width
            canvasHeight = height
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frameTask)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frameTask)
            super.onDestroy()
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_UP) {
                val current = prefs.getInt(WallpaperSettings.KEY_BOOT_MODE, bootMode)
                bootMode = (current + 1) % 3
                prefs.edit().putInt(WallpaperSettings.KEY_BOOT_MODE, bootMode).apply()
                sessionStartedAt = SystemClock.elapsedRealtime()
                createBootSchedule(sessionStartedAt)
                drawFrame()
            }
            super.onTouchEvent(event)
        }

        private fun createBootSchedule(now: Long) {
            startOffsets = LongArray(SCREEN_COUNT) { index ->
                when (bootMode) {
                    WallpaperSettings.MODE_SYNC -> 0L
                    WallpaperSettings.MODE_SEQUENTIAL -> index * SEQUENTIAL_DELAY_MS
                    else -> Random.nextLong(0L, RANDOM_DELAY_RANGE_MS)
                }
            }
            sessionStartedAt = now
        }

        private fun drawFrame() {
            val latestMode = prefs.getInt(WallpaperSettings.KEY_BOOT_MODE, bootMode)
            if (latestMode != bootMode) {
                bootMode = latestMode
                createBootSchedule(SystemClock.elapsedRealtime())
            }

            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    if (canvasWidth <= 0) canvasWidth = canvas.width
                    if (canvasHeight <= 0) canvasHeight = canvas.height
                    renderWallpaper(canvas, SystemClock.elapsedRealtime())
                }
            } catch (_: Exception) {
                // Surface may be temporarily unavailable while Android changes wallpapers.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                        // Ignore a surface that was destroyed during drawing.
                    }
                }
            }
        }

        private fun renderWallpaper(canvas: Canvas, now: Long) {
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()
            canvas.drawColor(Color.BLACK)

            val rowHeight = height / SCREEN_COUNT
            val separator = max(2f, height * 0.0015f)

            for (index in 0 until SCREEN_COUNT) {
                val top = index * rowHeight
                val bottom = if (index == SCREEN_COUNT - 1) height else (index + 1) * rowHeight
                val actualTop = top + separator * 0.5f
                val actualBottom = bottom - separator * 0.5f
                val age = now - sessionStartedAt - startOffsets[index]
                drawDisplay(canvas, index, width, actualTop, actualBottom, age, now)

                if (index < SCREEN_COUNT - 1) {
                    paint.color = Color.rgb(8, 15, 22)
                    canvas.drawRect(0f, bottom - separator / 2f, width, bottom + separator / 2f, paint)
                }
            }
        }

        private fun drawDisplay(
            canvas: Canvas,
            index: Int,
            width: Float,
            top: Float,
            bottom: Float,
            age: Long,
            now: Long
        ) {
            val height = bottom - top
            val accent = ACCENTS[index % ACCENTS.size]
            val background = BACKGROUNDS[index % BACKGROUNDS.size]
            paint.color = background
            canvas.drawRect(0f, top, width, bottom, paint)

            // A very soft luminous scanline makes each strip read as an LCD/OLED surface,
            // without drawing a device body, bezel, or outer frame.
            paint.color = withAlpha(accent, 24)
            canvas.drawRect(0f, top + height * 0.06f, width, top + height * 0.075f, paint)

            if (age < 0L) {
                paint.color = withAlpha(accent, 30)
                paint.textSize = height * 0.105f
                drawCentered(canvas, "STANDBY", width / 2f, top + height * 0.57f)
                return
            }

            if (age < BOOT_DURATION_MS) {
                drawBootSequence(canvas, index, width, top, height, age, accent)
            } else {
                val runningAge = age - BOOT_DURATION_MS
                drawOperatingScreen(canvas, index, width, top, height, runningAge, now, accent)
            }
        }

        private fun drawBootSequence(
            canvas: Canvas,
            index: Int,
            width: Float,
            top: Float,
            height: Float,
            age: Long,
            accent: Int
        ) {
            val middle = top + height * 0.56f
            when {
                age < 460L -> {
                    val blink = (age / 90L) % 2L == 0L
                    paint.color = if (blink) accent else Color.WHITE
                    paint.textSize = height * 0.26f
                    drawCentered(canvas, "888.888", width / 2f, middle)
                    drawBars(canvas, width, top, height, SCREEN_BARS, true, accent, true)
                }
                age < 1250L -> {
                    paint.textSize = height * 0.12f
                    paint.color = Color.rgb(215, 230, 241)
                    drawCentered(canvas, "X     K     KA     LASER", width / 2f, top + height * 0.37f)
                    val activeBand = ((age - 460L) / 190L).toInt().coerceIn(0, 3)
                    val labels = arrayOf("X", "K", "KA", "LASER")
                    val totalWidth = width * 0.82f
                    val left = (width - totalWidth) / 2f
                    val slot = totalWidth / 4f
                    labels.forEachIndexed { i, label ->
                        paint.color = if (i == activeBand) accent else withAlpha(Color.WHITE, 80)
                        paint.textSize = height * 0.19f
                        drawCentered(canvas, label, left + slot * (i + 0.5f), top + height * 0.68f)
                    }
                }
                age < 2200L -> {
                    paint.color = accent
                    paint.textSize = height * 0.17f
                    drawCentered(canvas, "SELF TEST", width / 2f, top + height * 0.46f)
                    val progress = ((age - 1250L).toFloat() / 950f).coerceIn(0f, 1f)
                    drawProgress(canvas, width, top, height, progress, accent)
                }
                age < 3000L -> {
                    paint.color = Color.rgb(208, 230, 241)
                    paint.textSize = height * 0.145f
                    val message = when (index % 4) {
                        0 -> "GPS SEARCH"
                        1 -> "PLL CHECK"
                        2 -> "DIRECTION TEST"
                        else -> "SIGNAL CAL"
                    }
                    drawCentered(canvas, message, width / 2f, top + height * 0.51f)
                    drawProgress(canvas, width, top, height, ((age - 2200L) / 800f).coerceIn(0f, 1f), accent)
                }
                else -> {
                    paint.color = Color.WHITE
                    paint.textSize = height * 0.20f
                    drawCentered(canvas, "READY", width / 2f, top + height * 0.53f)
                    paint.color = accent
                    paint.textSize = height * 0.085f
                    drawCentered(canvas, "HIGHWAY  •  SCAN ACTIVE", width / 2f, top + height * 0.76f)
                    drawBars(canvas, width, top, height, SCREEN_BARS, true, accent, true)
                }
            }
        }

        private fun drawOperatingScreen(
            canvas: Canvas,
            index: Int,
            width: Float,
            top: Float,
            height: Float,
            runningAge: Long,
            now: Long,
            accent: Int
        ) {
            val loop = (runningAge + index * 713L) % (OPERATING_CYCLE_MS + index * 330L)
            val alertStart = 4800L + (index % 3) * 230L
            val isAlert = loop in alertStart..(alertStart + 1700L)
            val alertProgress = if (isAlert) ((loop - alertStart) / 1700f).coerceIn(0f, 1f) else 0f
            val blink = (now / 460L) % 2L == 0L
            val headerY = top + height * 0.22f
            val mainY = top + height * 0.58f
            val bottomY = top + height * 0.83f

            // Tiny status glyphs and labels are deliberately kept inside the active display area.
            paint.textSize = height * 0.09f
            paint.color = withAlpha(accent, 220)
            drawText(canvas, when (index % 3) {
                0 -> "GPS   HIGHWAY   AUTO MUTE"
                1 -> "ADVANCED     GPS READY"
                else -> "X   K   KA     SCAN"
            }, width * 0.035f, headerY)
            drawTinyStatus(canvas, width, top, height, index, blink, accent)

            if (isAlert) {
                when (index % 4) {
                    0 -> drawR3Alert(canvas, width, top, height, mainY, bottomY, accent, alertProgress)
                    1 -> drawR4Alert(canvas, width, top, height, mainY, bottomY, accent, alertProgress)
                    2 -> drawDirectionalAlert(canvas, width, top, height, mainY, bottomY, accent, index, alertProgress)
                    else -> drawMultiSignalAlert(canvas, width, top, height, mainY, bottomY, accent, index, alertProgress)
                }
            } else {
                when (index % 5) {
                    0 -> drawR3Scanning(canvas, width, top, height, mainY, bottomY, accent, loop)
                    1 -> drawR4Scanning(canvas, width, top, height, mainY, bottomY, accent, loop)
                    2 -> drawDirectionalScanning(canvas, width, top, height, mainY, bottomY, accent, index, loop)
                    3 -> drawMultiSignalScanning(canvas, width, top, height, mainY, bottomY, accent, index, loop)
                    else -> drawSpectrumScanning(canvas, width, top, height, mainY, bottomY, accent, loop)
                }
            }
        }

        private fun drawR3Scanning(c: Canvas, w: Float, t: Float, h: Float, mainY: Float, bottomY: Float, accent: Int, loop: Long) {
            paint.textSize = h * 0.22f
            paint.color = Color.rgb(242, 242, 242)
            drawText(c, "12.8V", w * 0.035f, mainY)
            paint.color = if (loop % 1800L < 420L) Color.rgb(255, 73, 111) else accent
            drawText(c, "Ka", w * 0.31f, mainY)
            paint.color = Color.WHITE
            paint.textSize = h * 0.205f
            drawText(c, "34.708", w * 0.46f, mainY)
            paint.textSize = h * 0.095f
            drawText(c, "GHz", w * 0.79f, mainY)
            drawBars(c, w, t, h, (loop / 210L % (SCREEN_BARS + 1)).toInt(), true, accent)
            drawText(c, "X   K   KA", w * 0.035f, bottomY)
        }

        private fun drawR4Scanning(c: Canvas, w: Float, t: Float, h: Float, mainY: Float, bottomY: Float, accent: Int, loop: Long) {
            paint.color = Color.rgb(255, 181, 69)
            paint.textSize = h * 0.20f
            drawText(c, "13.5 V", w * 0.035f, mainY)
            paint.textSize = h * 0.235f
            drawText(c, "Ka", w * 0.32f, mainY)
            drawBars(c, w, t, h, (loop / 190L % (SCREEN_BARS + 1)).toInt(), true, accent)
            paint.color = Color.WHITE
            paint.textSize = h * 0.20f
            drawText(c, "34.700", w * 0.60f, mainY)
            paint.textSize = h * 0.082f
            drawText(c, "VOLTAGE   AUTO", w * 0.035f, bottomY)
        }

        private fun drawDirectionalScanning(c: Canvas, w: Float, t: Float, h: Float, mainY: Float, bottomY: Float, accent: Int, index: Int, loop: Long) {
            paint.textSize = h * 0.13f
            paint.color = Color.WHITE
            drawText(c, "42 mph", w * 0.035f, mainY - h * 0.04f)
            drawText(c, "NW", w * 0.07f, bottomY)
            paint.color = Color.rgb(255, 67, 78)
            paint.textSize = h * 0.36f
            drawCentered(c, arrowFor(index, loop), w * 0.43f, mainY + h * 0.05f)
            paint.color = accent
            paint.textSize = h * 0.105f
            drawText(c, if (index % 2 == 0) "Ka" else "K", w * 0.60f, mainY - h * 0.08f)
            paint.color = Color.WHITE
            paint.textSize = h * 0.19f
            drawText(c, if (index % 2 == 0) "34.673" else "24.150", w * 0.67f, mainY + h * 0.12f)
            drawBars(c, w, t, h, (loop / 260L % (SCREEN_BARS + 1)).toInt(), true, accent)
        }

        private fun drawMultiSignalScanning(c: Canvas, w: Float, t: Float, h: Float, mainY: Float, bottomY: Float, accent: Int, index: Int, loop: Long) {
            paint.color = accent
            paint.textSize = h * 0.18f
            drawText(c, "FRONT", w * 0.035f, mainY)
            paint.color = Color.WHITE
            paint.textSize = h * 0.205f
            drawText(c, "KA", w * 0.30f, mainY)
            paint.color = Color.rgb(255, 65, 83)
            paint.textSize = h * 0.29f
            drawCentered(c, arrowFor(index, loop), w * 0.52f, mainY)
            paint.color = Color.WHITE
            paint.textSize = h * 0.17f
            drawText(c, "34.720", w * 0.65f, mainY)
            paint.textSize = h * 0.085f
            drawText(c, "REAR   " + (1 + (loop / 1400L % 3)), w * 0.035f, bottomY)
            drawBars(c, w, t, h, (loop / 150L % (SCREEN_BARS + 1)).toInt(), true, accent)
        }

        private fun drawSpectrumScanning(c: Canvas, w: Float, t: Float, h: Float, mainY: Float, bottomY: Float, accent: Int, loop: Long) {
            paint.color = Color.rgb(116, 239, 151)
            paint.textSize = h * 0.14f
            drawText(c, "SCANNING", w * 0.035f, mainY - h * 0.07f)
            paint.color = Color.WHITE
            paint.textSize = h * 0.18f
            drawText(c, "K  24.158 GHz", w * 0.035f, mainY + h * 0.18f)
            drawSpectrum(c, w, t, h, loop, accent)
            paint.color = accent
            paint.textSize = h * 0.08f
            drawText(c, "SIGNAL ACQUISITION", w * 0.035f, bottomY)
        }

        private fun drawR3Alert(c: Canvas, w: Float, t: Float, h: Float, mainY: Float, bottomY: Float, accent: Int, p: Float) {
            paint.color = if ((p * 8).toInt() % 2 == 0) Color.rgb(255, 46, 76) else Color.rgb(255, 111, 125)
            paint.textSize = h * 0.25f
            drawText(c, "Ka", w * 0.28f, mainY)
            paint.color = Color.WHITE
            paint.textSize = h * 0.22f
            drawText(c, "34.708 GHz", w * 0.46f, mainY)
            drawBars(c, w, t, h, min(SCREEN_BARS, 3 + (p * 5).toInt()), true, Color.rgb(255, 49, 74), true)
            paint.color = Color.rgb(255, 102, 119)
            paint.textSize = h * 0.095f
            drawText(c, "SIGNAL DETECTED", w * 0.035f, bottomY)
        }

        private fun drawR4Alert(c: Canvas, w: Float, t: Float, h: Float, mainY: Float, bottomY: Float, accent: Int, p: Float) {
            paint.color = Color.rgb(255, 177, 54)
            paint.textSize = h * 0.23f
            drawText(c, "Ka", w * 0.30f, mainY)
            paint.color = Color.WHITE
            paint.textSize = h * 0.21f
            drawText(c, "34.700", w * 0.48f, mainY)
            drawBars(c, w, t, h, min(SCREEN_BARS, 2 + (p * 6).toInt()), true, accent, true)
            paint.color = Color.rgb(255, 196, 91)
            paint.textSize = h * 0.085f
            drawText(c, "HIGH PRIORITY   ALERT", w * 0.035f, bottomY)
        }

        private fun drawDirectionalAlert(c: Canvas, w: Float, t: Float, h: Float, mainY: Float, bottomY: Float, accent: Int, index: Int, p: Float) {
            paint.color = Color.rgb(255, 48, 65)
            paint.textSize = h * 0.38f
            drawCentered(c, arrowFor(index, (p * 3000).toLong()), w * 0.42f, mainY + h * 0.04f)
            paint.color = Color.WHITE
            paint.textSize = h * 0.19f
            drawText(c, "K 24.150", w * 0.60f, mainY)
            drawBars(c, w, t, h, min(SCREEN_BARS, 4 + (p * 4).toInt()), true, accent, true)
            paint.color = Color.rgb(255, 92, 104)
            paint.textSize = h * 0.09f
            drawText(c, "FRONT / REAR ALERT", w * 0.035f, bottomY)
        }

        private fun drawMultiSignalAlert(c: Canvas, w: Float, t: Float, h: Float, mainY: Float, bottomY: Float, accent: Int, index: Int, p: Float) {
            paint.color = Color.rgb(255, 57, 76)
            paint.textSize = h * 0.22f
            drawText(c, "KA", w * 0.035f, mainY)
            paint.color = Color.WHITE
            paint.textSize = h * 0.19f
            drawText(c, "34.720", w * 0.22f, mainY)
            paint.color = Color.rgb(255, 65, 81)
            paint.textSize = h * 0.25f
            drawCentered(c, "↑  ↓", w * 0.63f, mainY)
            paint.color = accent
            paint.textSize = h * 0.09f
            drawText(c, "BOGEY COUNT: " + (2 + (p * 3).toInt()), w * 0.035f, bottomY)
            drawBars(c, w, t, h, min(SCREEN_BARS, 5 + (p * 3).toInt()), true, Color.rgb(255, 55, 70), true)
        }

        private fun drawTinyStatus(c: Canvas, w: Float, t: Float, h: Float, index: Int, blink: Boolean, accent: Int) {
            val status = when (index % 4) {
                0 -> "GPS"
                1 -> "MUTE"
                2 -> "ADV"
                else -> "CITY"
            }
            paint.color = if (blink) accent else withAlpha(accent, 120)
            paint.textSize = h * 0.09f
            val statusWidth = paint.measureText(status)
            drawText(c, status, w - statusWidth - w * 0.04f, t + h * 0.22f)
        }

        private fun drawBars(
            c: Canvas,
            width: Float,
            top: Float,
            height: Float,
            litBars: Int,
            useColors: Boolean,
            accent: Int,
            fullTest: Boolean = false
        ) {
            val count = SCREEN_BARS
            val barW = width * 0.044f
            val gap = width * 0.012f
            val total = count * barW + (count - 1) * gap
            val left = width - total - width * 0.035f
            val barBottom = top + height * 0.88f
            val maxBarH = height * 0.12f
            for (i in 0 until count) {
                val bh = maxBarH * (0.35f + i * 0.095f)
                val rect = RectF(left + i * (barW + gap), barBottom - bh, left + i * (barW + gap) + barW, barBottom)
                val lit = fullTest || i < litBars
                paint.color = if (lit) {
                    if (useColors && i >= count - 2) Color.rgb(255, 51, 82) else if (useColors && i >= count - 4) Color.rgb(255, 180, 57) else accent
                } else withAlpha(accent, 45)
                c.drawRect(rect, paint)
            }
        }

        private fun drawProgress(c: Canvas, width: Float, top: Float, height: Float, progress: Float, accent: Int) {
            val left = width * 0.13f
            val right = width * 0.87f
            val y = top + height * 0.78f
            val h = max(3f, height * 0.028f)
            paint.color = withAlpha(accent, 48)
            c.drawRect(left, y, right, y + h, paint)
            paint.color = accent
            c.drawRect(left, y, left + (right - left) * progress, y + h, paint)
        }

        private fun drawSpectrum(c: Canvas, width: Float, top: Float, height: Float, loop: Long, accent: Int) {
            val left = width * 0.40f
            val right = width * 0.96f
            val base = top + height * 0.78f
            val barCount = 28
            val gap = width * 0.004f
            val barW = (right - left - gap * (barCount - 1)) / barCount
            for (i in 0 until barCount) {
                val phase = (loop / 125L + i * 7L) % 19L
                val normalized = (0.18f + (phase / 19f) * 0.82f).coerceIn(0.15f, 1f)
                val bh = height * 0.28f * normalized
                paint.color = when {
                    i > 22 -> Color.rgb(255, 58, 74)
                    i > 15 -> Color.rgb(255, 180, 58)
                    else -> accent
                }
                c.drawRect(left + i * (barW + gap), base - bh, left + i * (barW + gap) + barW, base, paint)
            }
        }

        private fun drawCentered(c: Canvas, text: String, centerX: Float, baseline: Float) {
            val oldAlign = paint.textAlign
            paint.textAlign = Paint.Align.CENTER
            c.drawText(text, centerX, baseline, paint)
            paint.textAlign = oldAlign
        }

        private fun drawText(c: Canvas, text: String, x: Float, baseline: Float) {
            c.drawText(text, x, baseline, paint)
        }

        private fun arrowFor(index: Int, time: Long): String = when ((time / 700L + index) % 4L) {
            0L -> "↑"
            1L -> "→"
            2L -> "↓"
            else -> "←"
        }

        private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    companion object {
        private const val SCREEN_COUNT = 9
        private const val SCREEN_BARS = 8
        private const val FRAME_INTERVAL_MS = 40L // 25 FPS; adequate for small OLED-style UI animations.
        private const val BOOT_DURATION_MS = 3600L
        private const val SEQUENTIAL_DELAY_MS = 620L
        private const val RANDOM_DELAY_RANGE_MS = 5200L
        private const val OPERATING_CYCLE_MS = 9400L

        private val ACCENTS = intArrayOf(
            Color.rgb(76, 220, 255),
            Color.rgb(255, 173, 63),
            Color.rgb(255, 74, 91),
            Color.rgb(73, 227, 235),
            Color.rgb(106, 255, 152),
            Color.rgb(255, 90, 111),
            Color.rgb(83, 168, 255),
            Color.rgb(250, 202, 71),
            Color.rgb(147, 246, 161)
        )

        private val BACKGROUNDS = intArrayOf(
            Color.rgb(1, 5, 10),
            Color.rgb(8, 5, 2),
            Color.rgb(7, 1, 4),
            Color.rgb(0, 6, 8),
            Color.rgb(1, 7, 4),
            Color.rgb(7, 1, 3),
            Color.rgb(1, 3, 8),
            Color.rgb(7, 5, 1),
            Color.rgb(1, 6, 3)
        )
    }
}
