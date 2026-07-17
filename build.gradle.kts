// Top-level build file
plugins {
    id("com.android.application") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    // W-H4: Synthetische Screenshots — Paparazzi nutzt reines Java-layoutlib, kein native DLL nötig
    id("app.cash.paparazzi") version "1.3.4" apply false
}
