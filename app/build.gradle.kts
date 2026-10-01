import java.time.Year

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.paparazzi")
}

// Zwei Pakete aus einem Bau (CEO 01.10.2026, ADR-0005 Nachtrag): Das Tablet-Paket wird mit einem
// eigenen Tablet-Schlüssel signiert, eingespeist nur über diese drei Variablen (analog ONE_PLATFORM_*).
// Werte und Pfade werden nie ausgegeben.
val tabletSigningVars = listOf("ONE_TABLET_KEYSTORE", "ONE_TABLET_PASS", "ONE_TABLET_ALIAS")

android {
    namespace = "com.uip.oneapp"
    compileSdk = 35

    val envVersionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull()
    val envVersionName = System.getenv("APP_VERSION_NAME")
    val platformSigningActive = System.getenv("ONE_PLATFORM_KEYSTORE") != null && System.getenv("ONE_PLATFORM_PASS") != null
    if (platformSigningActive && (envVersionCode == null || envVersionName == null)) {
        // Falle vom 29.07.: stiller Rückfall auf 902/0.9.2 bei einem plattformsignierten Bau
        // hat auf dem Testgerät ein Update als Downgrade blockiert (INSTALL_FAILED_VERSION_DOWNGRADE).
        // Plattformsignatur = Gerätebau, daher hier hart abbrechen statt still zurückzufallen.
        throw GradleException(
            "ONE_PLATFORM_KEYSTORE/ONE_PLATFORM_PASS sind gesetzt, aber APP_VERSION_CODE/APP_VERSION_NAME fehlen. " +
                "Beide Variablen setzen, sonst Rückfall auf 902/0.9.2 und Downgrade-Blocker beim Geräte-Update."
        )
    }
    // Tablet-Paket (CEO 01.10.2026): gleiche Falle wie oben — ein mit dem Tablet-Schlüssel
    // signierter Bau ist ein Auslieferungsbau und darf nicht still auf 902/0.9.2 zurückfallen.
    val tabletSigningActive = tabletSigningVars.all { !System.getenv(it).isNullOrBlank() }
    if (tabletSigningActive && (envVersionCode == null || envVersionName == null)) {
        throw GradleException(
            "ONE_TABLET_KEYSTORE/ONE_TABLET_PASS/ONE_TABLET_ALIAS sind gesetzt, aber APP_VERSION_CODE/APP_VERSION_NAME fehlen. " +
                "Beide Variablen setzen, sonst Rückfall auf 902/0.9.2 und Downgrade-Blocker beim Tablet-Update."
        )
    }
    if (envVersionCode == null || envVersionName == null) {
        logger.warn(
            "WARNUNG: APP_VERSION_CODE/APP_VERSION_NAME nicht gesetzt — Bau fällt auf 902/0.9.2 zurück. " +
                "Nur für reine Kompilierprüfungen geeignet, NICHT für Geräte-Updates."
        )
    }
    // Werkseinrichtung, Update-WerkzeugApp und das Partner-ZIP erkennen die ONE-Datei nur am Muster
    // DrainQ-ONE_<Ziffern.Punkte>_<Code>_platform.apk — ein versionName mit Buchstaben baut, wird
    // dort aber nicht angenommen. Warnen, nicht abbrechen.
    val effectiveVersionName = envVersionName ?: "0.9.2"
    if (!Regex("^[\\d.]+$").matches(effectiveVersionName)) {
        logger.warn(
            "WARNUNG: versionName '$effectiveVersionName' enthält nicht nur Ziffern und Punkte — " +
                "die Werkseinrichtung nimmt diesen Dateinamen nicht an."
        )
    }

    defaultConfig {
        applicationId = "com.uip.drainq.one"
        minSdk = 26
        targetSdk = 34
        // versionCode-Konvention = Portal-Schema (MAJOR*10000 + MINOR*100 + PATCH),
        // damit der Update-Vergleich gegen license.drainq.com konsistent ist
        // (CEO-Beschluss 2026-06-07: Updates laufen über das DrainQ-Portal).
        versionCode = envVersionCode ?: 902
        versionName = envVersionName ?: "0.9.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        // Native Bridge (v4l2bridge) — Farbraum-Konvertierungen (RGB↔YUV, Camera2FrameSource
        // + H264Encoder) und serieller UART-Port. Der frühere direkte V4L2-Zugriff auf
        // /dev/video0 ist mit AP-5 entfernt (RESULT_CAMERA2_UMBAU_2026-07-29.md).
        externalNativeBuild {
            cmake {
                cppFlags += ""
                arguments += "-DANDROID_STL=c++_shared"
            }
        }

        buildConfigField("String", "UPDATE_MODE", "\"proxy\"")
        // Updates kommen vom DrainQ-Portal (Software-Distribution) — der frühere GitHub-Weg ist
        // abgelöst (CEO-Beschluss 2026-06-07). Das Portal liefert releases.{channel}.json im
        // App-Format (SoftwareDistributionController). UPDATE_PROXY_URL steht je Paket in
        // productFlavors (Produkt "one" bzw. "one-tablet", CEO 01.10.2026).
        buildConfigField("String", "L10N_PORTAL_URL", "\"https://license.drainq.com\"")
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
        // Tablet-Schlüssel (CEO 01.10.2026, ADR-0005 Nachtrag): eigener, vom CEO erzeugter
        // Schlüssel für das Tablet-Paket ohne sharedUserId. ONE_TABLET_PASS ist Store- und
        // Schlüsselkennwort zugleich (wie ONE_PLATFORM_PASS). RSA + v1-Signatur, weil
        // Get-ApkSignatureFingerprint.ps1 nur META-INF/*.RSA|*.DSA liest. Ohne alle drei
        // Variablen bleibt dieser signingConfig leer — der Release-Bau des Tablet-Pakets bricht
        // dann ab (Wächter unten), er fällt nie auf KEYSTORE_PATH oder den Debug-Schlüssel zurück.
        create("tablet") {
            if (tabletSigningActive) {
                storeFile = file(System.getenv("ONE_TABLET_KEYSTORE")!!)
                storePassword = System.getenv("ONE_TABLET_PASS")
                keyAlias = System.getenv("ONE_TABLET_ALIAS")
                keyPassword = System.getenv("ONE_TABLET_PASS")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    // Zwei Pakete aus einem Bau (CEO 01.10.2026): gleiche applicationId, gleicher Code, gleiche
    // Version — sie unterscheiden sich nur in sharedUserId (app/src/one/AndroidManifest.xml),
    // Signaturschlüssel und Update-Produkt. assembleRelease/assembleDebug bauen beide.
    flavorDimensions += "paket"
    productFlavors {
        create("one") {
            dimension = "paket"
            isDefault = true
            buildConfigField("String", "UPDATE_PROXY_URL", "\"https://license.drainq.com/api/software/one/\"")
            // Plattformschlüssel hat Vorrang vor oneapp-release.keystore: das ONE-Paket trägt
            // sharedUserId="android.uid.system" (ADR-0005) und ist auf dem Gerät nur
            // plattformsigniert installierbar. Der Portalweg (publish-one-release.ps1)
            // setzt ONE_PLATFORM_* fail-closed voraus. Auswahl unverändert aus buildTypes.release
            // hierher verschoben (CEO 01.10.2026): release setzt keinen signingConfig mehr, AGP
            // nimmt dann den der Produktvariante; debug behält seinen eigenen (Debug-Schlüssel).
            signingConfig = when {
                platformSigningActive -> signingConfigs.getByName("platform")
                System.getenv("KEYSTORE_PATH") != null -> signingConfigs.getByName("release")
                else -> signingConfigs.getByName("debug")
            }
        }
        create("tablet") {
            dimension = "paket"
            buildConfigField("String", "UPDATE_PROXY_URL", "\"https://license.drainq.com/api/software/one-tablet/\"")
            signingConfig = signingConfigs.getByName("tablet")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Kein signingConfig hier: die Auswahl steht je Paket in productFlavors (CEO 01.10.2026).
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

    // Dateinamen (CEO 01.10.2026): Werkseinrichtung und Partner-ZIP erwarten für das ONE-Paket
    // genau DrainQ-ONE_<Version>_<Code>_platform.apk; das Tablet-Paket heißt …_tablet.apk.
    // Debug-Baue tragen "-debug" vor ".apk", damit nie ein Debug-Bau das Werkseinrichtungs-Muster
    // trifft. applicationVariants entfällt in AGP 9 — dann auf androidComponents umstellen.
    applicationVariants.all {
        val variant = this
        val paket = if (variant.flavorName == "one") "platform" else "tablet"
        val debugSuffix = if (variant.buildType.name == "debug") "-debug" else ""
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "DrainQ-ONE_${variant.versionName}_${variant.versionCode}_$paket$debugSuffix.apk"
        }
    }
}

// Fail-closed Tablet-Release (CEO 01.10.2026): fehlt auch nur eine der drei ONE_TABLET_*-Variablen,
// bricht der Bau vor dem Signieren ab — kein stiller Rückfall auf KEYSTORE_PATH oder den
// Debug-Schlüssel. Genannt werden nur Variablennamen, nie Werte oder Pfade.
tasks.configureEach {
    if (name == "packageTabletRelease" || name == "validateSigningTabletRelease") {
        doFirst {
            val missing = tabletSigningVars.filter { System.getenv(it).isNullOrBlank() }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "Release-Bau des Tablet-Pakets abgebrochen: fehlende Umgebungsvariable(n) " +
                        missing.joinToString(", ") + ". Erforderlich sind ONE_TABLET_KEYSTORE, " +
                        "ONE_TABLET_PASS und ONE_TABLET_ALIAS (Tablet-Schlüssel, CEO 01.10.2026). " +
                        "Kein Rückfall auf KEYSTORE_PATH oder den Debug-Schlüssel."
                )
            }
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
    systemProperty("l10n.live", System.getProperty("l10n.live") ?: "")
}

// Zwei Pakete (CEO 01.10.2026): Paparazzi legt je Variante recordPaparazziOneDebug usw. an, aber
// keinen recordPaparazziDebug/verifyPaparazziDebug mehr — tools/manual (verify.ps1, render.ps1)
// ruft genau diese Namen. Ein Golden-Satz für beide Pakete (gleiche Oberfläche), gerendert aus
// der ONE-Variante. PaparazziTask nimmt --tests entgegen und reicht es an alle Test-Tasks weiter
// (@Option "tests" → tasks.withType<Test>().configureEach); Paparazzi erkennt Record/Verify daran,
// dass recordPaparazziOneDebug bzw. verifyPaparazziOneDebug im Task-Graphen steht.
tasks.register("recordPaparazziDebug", app.cash.paparazzi.gradle.PaparazziPlugin.PaparazziTask::class.java) {
    group = "verification"
    description = "Record golden images (Weiche auf recordPaparazziOneDebug, CEO 01.10.2026)"
    dependsOn("recordPaparazziOneDebug")
}
tasks.register("verifyPaparazziDebug", app.cash.paparazzi.gradle.PaparazziPlugin.PaparazziTask::class.java) {
    group = "verification"
    description = "Run screenshot tests (Weiche auf verifyPaparazziOneDebug, CEO 01.10.2026)"
    dependsOn("verifyPaparazziOneDebug")
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    // N-1 (Runde 2, B-1): System-Splash zurueckhalten, bis die gespeicherte Sprache steht.
    implementation("androidx.core:core-splashscreen:1.0.1")
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
