package com.example.radarscreens

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var modeStatus: TextView
    private val prefs by lazy {
        getSharedPreferences(WallpaperSettings.PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(26), dp(22), dp(24))
            setBackgroundColor(Color.rgb(4, 7, 12))
        }

        val title = TextView(this).apply {
            text = "RADAR SCREENS"
            textSize = 25f
            setTextColor(Color.rgb(225, 242, 255))
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }
        root.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Только дисплеи радар-детекторов.\nПолноэкранная вертикальная композиция."
            textSize = 14f
            setTextColor(Color.rgb(130, 158, 184))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(24))
        }
        root.addView(subtitle, matchWrap())

        modeStatus = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(99, 235, 255))
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(18))
        }
        root.addView(modeStatus, matchWrap())

        addModeButton(root, "ВСЕ ВКЛЮЧАЮТСЯ ВМЕСТЕ", WallpaperSettings.MODE_SYNC)
        addModeButton(root, "ЗАПУСК ПО ОЧЕРЕДИ", WallpaperSettings.MODE_SEQUENTIAL)
        addModeButton(root, "СЛУЧАЙНЫЙ ЗАПУСК", WallpaperSettings.MODE_RANDOM)

        val apply = Button(this).apply {
            text = "ПРЕДПРОСМОТР / УСТАНОВИТЬ ОБОИ"
            textSize = 14f
            setOnClickListener { openWallpaperPicker() }
        }
        val applyParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
        applyParams.topMargin = dp(20)
        root.addView(apply, applyParams)

        val hint = TextView(this).apply {
            text = "Подсказка: коснись пустого участка обоев, чтобы переключить режим запуска.\n\nСамотесты и показания — визуальная симуляция, не реальные измерения."
            textSize = 12f
            setTextColor(Color.rgb(105, 121, 140))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(22), dp(8), 0)
        }
        root.addView(hint, matchWrap())

        setContentView(root)
        updateModeStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::modeStatus.isInitialized) updateModeStatus()
    }

    private fun addModeButton(root: LinearLayout, label: String, mode: Int) {
        val button = Button(this).apply {
            text = label
            textSize = 13f
            setOnClickListener {
                prefs.edit().putInt(WallpaperSettings.KEY_BOOT_MODE, mode).apply()
                updateModeStatus()
            }
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
        params.bottomMargin = dp(8)
        root.addView(button, params)
    }

    private fun updateModeStatus() {
        val mode = prefs.getInt(WallpaperSettings.KEY_BOOT_MODE, WallpaperSettings.MODE_SYNC)
        modeStatus.text = "ТЕКУЩИЙ РЕЖИМ\n${WallpaperSettings.modeName(mode).uppercase()}"
    }

    private fun openWallpaperPicker() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this@MainActivity, RadarWallpaperService::class.java)
            )
        }
        startActivity(intent)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
