# Szenarien one-tablet-paket (Zwei Pakete aus einem Bau, CEO 01.10.2026)

Regel 17. Je Szenario: Vorbedingung, Handlung, Messbefehl, Erwartung. Spalte „Stand“: was der
Bauer am 01.10.2026 gemessen hat (Belege im Kettenordner `_ketten/one-tablet-paket/belege/`) und
was offen ist. Offene Proben sind für Prüfer oder CEO; im Bau-Lauf waren `keytool`, `aapt2`,
PowerShell und Gradle mit gesetzten Umgebungsvariablen vom Werkzeug abgelehnt.

Wegwerf-Schlüssel für die Proben (nur unter `%TEMP%`, danach löschen; nie den echten
Tablet- oder Plattformschlüssel für Proben verwenden):

```powershell
$w = "$env:TEMP\otp_probe"; New-Item -ItemType Directory -Force $w | Out-Null
# Alias des Plattform-Wegwerfschlüssels = der in app/build.gradle.kts (signingConfigs "platform", keyAlias) fest eingetragene
keytool -genkeypair -keystore "$w\platform.jks" -alias <keyAlias aus signingConfigs.platform> -keyalg RSA -keysize 2048 -validity 3 -dname "CN=Probe Plattform" -storepass probeP1x -keypass probeP1x
keytool -genkeypair -keystore "$w\tablet.jks"   -alias probetablet    -keyalg RSA -keysize 2048 -validity 3 -dname "CN=Probe Tablet"    -storepass probeT1x -keypass probeT1x
```

| Nr | Vorbedingung | Handlung | Messbefehl | Erwartung | Stand 01.10. |
|---|---|---|---|---|---|
| S-1a | keine Schlüsselvariable gesetzt | beide Debug-Pakete bauen | `.\gradlew.bat :app:assembleDebug`, dann `Get-ChildItem app\build\outputs\apk -Recurse -Filter *.apk` | genau `one\debug\DrainQ-ONE_<V>_<C>_platform-debug.apk` und `tablet\debug\DrainQ-ONE_<V>_<C>_tablet-debug.apk` | gemessen (`11_bau_debug.txt`) |
| S-1b | beide Wegwerf-Schlüssel gesetzt (`ONE_PLATFORM_KEYSTORE/PASS`, `ONE_TABLET_KEYSTORE/PASS/ALIAS`), `APP_VERSION_CODE=1`, `APP_VERSION_NAME=0.0.1` | ein Aufruf baut beide Release-Pakete | `.\gradlew.bat :app:assembleRelease` | `one\release\DrainQ-ONE_0.0.1_1_platform.apk` und `tablet\release\DrainQ-ONE_0.0.1_1_tablet.apk`, Exit 0 | **offen** |
| S-2 | APKs aus S-1a oder S-1b | Manifeste vergleichen | `aapt2 dump xmltree --file AndroidManifest.xml <apk>` je Paket (aapt2 aus `<sdk>\build-tools\<neueste>`), `(line=N)` entfernen, `git diff --no-index` | genau eine Zeile nur im ONE-Paket: `A: android:sharedUserId(…)="android.uid.system"` | Ersatz gemessen am gemischten Manifest (`22_diff.txt`); aapt2 **offen** |
| S-3 | — | BuildConfig je Paket | `.\gradlew.bat :app:testOneDebugUnitTest :app:testTabletDebugUnitTest` | `PaketBuildConfigTest` 5/5 grün je Variante | gemessen (`31_…`, `32_…`), Rot-Beweise X-3a..e (`63_x3.txt`) |
| S-4a | `ONE_TABLET_KEYSTORE`, `ONE_TABLET_PASS` gesetzt, `ONE_TABLET_ALIAS` leer, `APP_VERSION_*` gesetzt | Tablet-Release | `.\gradlew.bat :app:assembleTabletRelease` | Exit ≠ 0, Meldung nennt genau `ONE_TABLET_ALIAS`, kein APK unter `tablet\release` | **offen** |
| S-4b | wie S-4a, aber nur `ONE_TABLET_PASS` leer | dito | dito | Meldung nennt genau `ONE_TABLET_PASS` | **offen** |
| S-4c | wie S-4a, aber nur `ONE_TABLET_KEYSTORE` leer | dito | dito | Meldung nennt genau `ONE_TABLET_KEYSTORE` | **offen** |
| S-4d | alle `ONE_TABLET_*` leer | dito | dito | Meldung nennt alle drei; 0 APK | gemessen (`40_tablet_ohne_schluessel.txt`) |
| S-4e | alle drei `ONE_TABLET_*` gesetzt, `APP_VERSION_*` leer | beliebiger Gradle-Aufruf | `.\gradlew.bat :app:help` | Abbruch bei der Konfiguration: „ONE_TABLET_KEYSTORE/ONE_TABLET_PASS/ONE_TABLET_ALIAS sind gesetzt, aber APP_VERSION_CODE/APP_VERSION_NAME fehlen“ | **offen** |
| S-4f | Wächter ausgeschaltet (Mutation), alle `ONE_TABLET_*` leer | Tablet-Release | dito S-4a | AGP bricht trotzdem ab: „SigningConfig "tablet" is missing required property "storeFile"“ | gemessen (`64_x4.txt`) |
| S-5 | — | alle Unit-Tests | `:app:testOneDebugUnitTest`, `:app:testTabletDebugUnitTest`; Zählung aus `app\build\test-results\<task>\*.xml` | je 652 Tests, 0 failures, 0 errors, 3 skipped (nur `L10nPortalLiveTest`) | gemessen (`50_…`, `51_…`) |
| S-6a | Debug-signierte APK an den Tablet-Release-Pfad kopiert, `ONE_TABLET_*` = Wegwerf | Zertifikatsprobe | `.\tools\publish-one-release.ps1 -Variante tablet -SkipBuild -Trockenlauf -VersionName 0.0.1 -VersionCode 1 -ApiKey x` | Exit ≠ 0 („Keine Signaturdatei“ oder „Zertifikatsprobe FEHLGESCHLAGEN“), kein Portal-Kontakt | **offen** |
| S-6b | Tablet-Release aus S-1b am Pfad | dito | dito | „Zertifikatsprobe OK“, „TROCKENLAUF OK.“, Exit 0 | **offen** |
| S-7a | `ONE_PLATFORM_*` = Wegwerf | Trockenlauf ohne `-Variante` | `.\tools\publish-one-release.ps1 -VersionName 0.0.0 -VersionCode 1 -ApiKey x -Trockenlauf` | Produkt `one`, Task `:app:assembleOneRelease`, Pfad `…\one\release\DrainQ-ONE_0.0.0_1_platform.apk`, Exit 0 | **offen** |
| S-7b | dito | mit `-Variante one` | dito + `-Variante one` | Ausgabe identisch zu S-7a | **offen** |
| S-7c | `ONE_TABLET_*` = Wegwerf | mit `-Variante tablet` | dito + `-Variante tablet` | Produkt `one-tablet`, Task `:app:assembleTabletRelease`, Pfad `…_tablet.apk`, „Auslieferungspaket: keins“ | **offen** |
| S-MS | APKs aus S-1b | Signatur je Paket | `apksigner verify --print-certs <apk>`; `keytool -list -v -keystore <wegwerf>.jks` | ONE-Zertifikat = Wegwerf-Plattform, Tablet = Wegwerf-Tablet | Rückfall ohne Schlüssel gemessen (`82_rueckfall.txt`); Schlüsselfall **offen** |
| S-MD | — | Docs-Gate | `.\tools\manual\verify.ps1` | Exit 0 | **rot**: `scr03_connection` weicht ab (1,335 % de, 1,247 % en), Ursache nicht belegt (`90_verify_docs_gate.txt`); Gegenprobe am Ausgangskopf `d48ea12` offen |
| S-MD2 | — | Record-Weiche | `.\gradlew.bat :app:recordPaparazziDebug --tests=com.uip.oneapp.screenshot.ManualScreenshotTest.scr02_home -Pscreenshot.lang=de` | nur diese Golden neu geschrieben, `git status` leer | gemessen (`92_x8_record.txt`) |

## CEO-Klickdurchgang (Erfolgsmessung 8)

1. **Zwei signierte Baue** beider Pakete mit Versionscodes N und N+1 (eine App meldet ein Update
   nur bei `latest.versionCode > installierter Code`, `HttpUpdateService.kt:67`):
   Schlüsselvariablen setzen (Passwörter von Hand), `APP_VERSION_CODE=N`,
   `APP_VERSION_NAME=<Ziffern.Punkte>`, `.\gradlew.bat :app:assembleRelease`;
   Dateien sichern; dann dasselbe mit N+1.
2. **Test-Tablet** (`0.4.3-lohs-test`): `adb uninstall com.uip.drainq.one` (einmalig, ohne
   Datensicherung, CEO-Entscheid 5), `adb install …\tablet\release\DrainQ-ONE_<V>_<N>_tablet.apk`.
   App öffnen → Slave-Modus erkannt, Verbindung zur ONE herstellen.
3. **K-1 am Test-Tablet** (System-UID-Pfade enden geordnet):
   ```
   adb shell ls -l /dev/ttyS5                         -> "No such file or directory"
   adb shell am force-stop com.uip.drainq.one         (frischer Prozess, sonst läuft OneApp.onCreate nicht)
   adb logcat -c
   adb shell am start -W -n com.uip.drainq.one/com.uip.oneapp.MainActivity   -> TotalTime notieren
   adb logcat -d -s DeviceFilePermission
   ```
   Soll: genau die Zeile `ONE-Devices not present — skipping chmod (likely TWO-mode or non-ONE-tablet)`
   (`DeviceFilePermissionBootstrap.kt:41`), **keine** Zeile `chmod via su timed out after 3s`
   (Z. 62) — der `su`-Pfad wird nicht betreten. Kein Absturz.
4. **ONE**: ONE-Paket Code N per Werkseinrichtung → GRÜN; Kamera zeigt Bild; Hotspot heißt
   `DrainQ-ONE-<Serial>`.
5. **Portal**: Tablet-Paket Code N+1 mit `.\tools\publish-one-release.ps1 -Variante tablet …`
   nach `one-tablet` / `beta` hochladen; im Portal unter Admin → Releases **Freigeben** und
   danach gesondert **Veröffentlichen** (das Skript tut keins von beiden,
   `RELEASE_PUBLISHING.md` Schritte 4 und 5). Prüfen:
   `curl.exe -si https://license.drainq.com/api/software/one-tablet/releases.beta.json` → 200,
   `latest.versionCode` = N+1. Am Tablet „Nach Updates suchen“ → Update gefunden und installiert.
