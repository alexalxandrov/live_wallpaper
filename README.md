# Radar Screens — Android live wallpaper

A native Android live-wallpaper prototype built with Kotlin and `WallpaperService`.
The wallpaper contains nine horizontally oriented radar-detector display strips stacked vertically. It intentionally does **not** draw detector housings, buttons, outer bezels, or product photos.

## Included in this prototype

- Nine full-width display strips, designed to fill the vertical screen.
- Three boot choreography modes: all at once, sequentially from top to bottom, and randomized delays.
- Per-display startup tests: segment test, X/K/Ka/LASER indicators, self-test progress, calibration messages, and READY state.
- Animated normal operation: changing frequencies, strength bars, direction arrows, scan indicators, and occasional simulated detections.
- A settings activity that launches Android's live-wallpaper preview/set flow.
- Touch an empty area of the wallpaper to cycle the three launch modes.
- Lightweight custom Canvas rendering, capped at 25 frames per second while visible.

All readings and alerts are simulations for the visual effect. They are not real radar measurements. The screen layouts are inspired by common radar-detector display conventions; they are not intended as pixel-perfect firmware replicas.

## Build and install

1. Open the `RadarScreensWallpaper` folder in Android Studio.
2. Allow Gradle sync to complete.
3. Connect the Samsung Galaxy A26 or enable an emulator.
4. Use **Build > Build APK(s)**, then install the generated debug APK.
5. Open **Radar Screens**, choose a boot mode, and tap **Preview / Установить обои**. Confirm the live wallpaper in Android's picker.

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, `compileSdk 35`, and `minSdk 26`.

## Planned next iteration

- Refine each display's layout against selected reference photos/manuals.
- Tune the number and height of strips against screenshots from the actual A26.
- Add a user-selected animation tempo and an optional brightness/dim cycle.
