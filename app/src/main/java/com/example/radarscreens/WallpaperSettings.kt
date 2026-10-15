package com.example.radarscreens

internal object WallpaperSettings {
    const val PREFS_NAME = "radar_screens_wallpaper"
    const val KEY_BOOT_MODE = "boot_mode"

    const val MODE_SYNC = 0
    const val MODE_SEQUENTIAL = 1
    const val MODE_RANDOM = 2

    fun modeName(mode: Int): String = when (mode) {
        MODE_SYNC -> "Все одновременно"
        MODE_SEQUENTIAL -> "По очереди сверху вниз"
        else -> "Случайный запуск"
    }
}
