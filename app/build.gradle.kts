import java.time.Year

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.uip.oneapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.uip.drainq.one"
        minSdk = 26
        targetSdk = 34
        // versionCode-Konvention = Portal-Schema (MAJOR*10000 + MINOR*100 + PATCH),
        // damit der Update-Vergleich gegen license.drainq.com konsistent ist
        // (CEO-Beschluss 2026-06-07: Updates laufen über das DrainQ-Portal).
        versionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 401
        versionName = System.getenv("APP_VERSION_NAME") ?: "0.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        // V4L2-Bridge — native Library für /dev/video0-Zugriff im ONE-Local-Modus
        externalNativeBuild {
            cmake {
                cppFlags += ""
                arguments += "-DANDROID_STL=c++_shared"
            }
        }

        buildConfigField("String", "UPDATE_MODE", "\"proxy\"")
        // Updates kommen vom DrainQ-Portal (Software-Distribution, Produkt "one") —
        // der frühere GitHub-Weg ist abgelöst (CEO-Beschluss 2026-06-07). Das Portal
        // liefert releases.{channel}.json im App-Format (SoftwareDistributionController).
        buildConfigField("String", "UPDATE_PROXY_URL", "\"https://license.drainq.com/api/software/one/\"")
        buildConfigField("String", "UPDATE_CHANNEL", "\"beta\"")
        // Louis 10-07 / B1-Interim: Build-Jahr für die Datums-Plausibilitätsprüfung. Eine Inspektion
        // kann nicht vor dem App-Build liegen — so fängt der Guard die offline auf ~2021 zurückgefallene
        // Geräteuhr (RTC-Reset), ohne echte, jahresnahe Daten fälschlich zu blockieren.
        buildConfigField("int", "BUILD_YEAR", "${Year.now().value}")
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
        // ADR-0005: Plattformschlüssel des Herstellers (bominwellalias). Signiert die App wie
        // System-Firmware, nicht wie eine gewöhnliche Auslieferung — daher eigene Env-Vars statt
        // der oneapp-release.keystore-Variablen oben. Ohne beide Variablen bleibt dieser
        // signingConfig leer und der Debug-Bau signiert wie bisher mit dem Debug-Schlüssel.
        create("platform") {
            val keystorePath = System.getenv("ONE_PLATFORM_KEYSTORE")
            val keystorePass = System.getenv("ONE_PLATFORM_PASS")
            if (keystorePath != null && keystorePass != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePass
                keyAlias = "bominwellalias"
                keyPassword = keystorePass
                // v1/JAR-Signatur zusätzlich zu v2/v3 aktivieren, damit `keytool -printcert
                // -jarfile` (Standard-Werkzeug für den Zertifikatsbeleg) etwas zu prüfen hat.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            if (System.getenv("ONE_PLATFORM_KEYSTORE") != null && System.getenv("ONE_PLATFORM_PASS") != null) {
                signingConfig = signingConfigs.getByName("platform")
            }
        }
    }

    testOptions {
        unitTests { isIncludeAndroidResources = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging {
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/**") }
        jniLibs { pickFirsts += setOf("**/libc++_shared.so") }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

// M4: Room-Schema-Export-Verzeichnis (Voraussetzung für exportSchema=true + Migrationstests).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// W-H4: Leitet Render-Parameter von Gradle-Projekt-Properties an den Test-JVM weiter.
// Aufruf: ./gradlew :app:recordPaparazziDebug -Pscreenshot.lang=en
tasks.withType<Test> {
    (project.findProperty("screenshot.lang") as String?)?.let { systemProperty("screenshot.lang", it) }
    (project.findProperty("screenshot.translationJson") as String?)?.let { systemProperty("screenshot.translationJson", it) }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Video Streaming (RTSP) - ExoPlayer with low-latency optimizations
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // FFmpegKit for video overlay burn-in (ASS subtitles → hardcoded overlay)
    implementation("com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0")

    // MQTT
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")

    // PDF
    implementation("com.itextpdf:itext7-core:7.2.5")

    // (simple-xml entfernt — XML-Export wurde komplett ausgebaut, CEO-Beschluss 2026-06-07)

    // DI
    implementation("io.insert-koin:koin-android:3.5.3")
    implementation("io.insert-koin:koin-androidx-compose:3.5.3")

    // HTTP client for update downloads
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // QR-Kopplung (Dual-Modus W3a): schlanke ZXing-Lib — Encode des WIFI-QR im ONE-Pairing-Screen
    // (core) + Scan/CaptureActivity auf dem Tablet (android-embedded, zieht core transitiv).
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Coroutines & Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    // SVG-Decoder für Vektor-Assets (Splash-Logo). Apache-2.0.
    // androidsvg-aar ausschließen: mapsforge bringt bereits com.caverock:androidsvg:1.4
    // (gleiche Klassen, andere Verpackung) — beide zusammen -> Duplicate-Class-Fehler.
    implementation("io.coil-kt:coil-svg:2.5.0") {
        exclude(group = "com.caverock", module = "androidsvg-aar")
    }

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Auto-Reconnect (W1): Passphrasen bekannter ONEs verschlüsselt at rest —
    // EncryptedSharedPreferences mit Master-Key im Android Keystore (KnownOneStore).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // WorkManager (offline map download in foreground service)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // LiveData → State for WorkInfo observation in Compose
    implementation("androidx.compose.runtime:runtime-livedata")

    // MapsForge — offline OSM vector maps (.map files from download.mapsforge.org)
    implementation("org.mapsforge:mapsforge-map-android:0.21.0")
    implementation("org.mapsforge:mapsforge-themes:0.21.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // W-H4: Synthetische Screenshots (Paparazzi — layoutlib/reines Java, kein native DLL auf Windows)
    testImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.insert-koin:koin-test:3.5.3")
    testImplementation("io.insert-koin:koin-test-junit4:3.5.3")
    testImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
