# ADR-0005 — Plattformsignatur für DrainQ.ONE

**Status:** Angenommen — CEO-Entscheid 11.08.2026. Der Plattformschlüssel signiert seit Version 0.9.0 (29.07.2026) produktiv jede Auslieferung.
**Datum:** 2026-07-29
**Entscheider:** Thomas Viell (CEO)
**Betrifft:** `drainq.one` (Repo `ThomasViell/one-app`), gesamte ausgelieferte ONE-Flotte
**Ersetzt:** die offene Signaturfrage aus `project_one_signing_split` (Debug- gegen Release-Keystore)

---

## 1. Ausgangslage

Seit dem 16.07.2026 ist belegt, dass der Hotspot-Code der ONE fertig, aber tot ist: `startTetheredHotspot()` verlangt die Berechtigung `NETWORK_SETTINGS`, und die ist `signature`-geschützt. Ein Eintrag in der priv-app-Allowlist reicht nachweislich nicht (`docs/SOFTAP_WERKS_PRIVILEG.md`, „Weg A" ist widerlegt). Erwartet wurde ein Plattformschlüssel „Anfang August 2026".

Parallel steht die Flotte seit dem 09.07. in zwei Signaturwelten: 0.4.3-beta und 0.5.0-alpha tragen den Release-Keystore, alles ab 0.5.1 den Debug-Schlüssel aus `%USERPROFILE%\.android\debug.keystore`. Der Konflikt ist real eingetreten — `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, Deinstallation durch die Geräterichtlinie blockiert, Auflösung nur per Werksreset.

## 2. Der Befund vom 29.07.2026

Der vom Hersteller übergebene Keystore `bominwellalias.keystore` (JKS, ein Eintrag, Alias `bominwellalias`, RSA 2048, SHA256withRSA) enthält **den Plattformschlüssel der ONE-Firmware**.

Gemessen, nicht angenommen:

| Quelle | SHA-256 des Zertifikats |
|---|---|
| Keystore `bominwellalias` | `2D:37:0C:21:F5:DF:D5:53:D2:A7:96:31:4B:70:92:5F:B3:8A:DE:EF:90:86:4C:92:0B:BB:BB:12:88:7D:35:22` |
| `/system/framework/framework-res.apk` von der ONE | identisch |

Ermittelt mit `adb pull /system/framework/framework-res.apk` und `keytool -printcert -jarfile`. Zertifikat: `C=US, ST=California, L=Mountain View, O=Android, OU=Android, CN=Android, android@android.com`, Seriennummer `ff0641323cf95512`, gültig 23.12.2014 bis 10.05.2042, `CA:true`, selbstsigniert. Der Keystore-Eintrag wurde am 24.07.2025 angelegt.

**Damit ist der erwartete Schlüssel nicht „unterwegs", sondern seit über einem Jahr vorhanden.**

### 2a. Offene Sicherheitsfrage an den Hersteller
Das Zertifikat trägt die AOSP-Standardkennung und ein Ausstellungsdatum von 2014. Dieses Muster passt zu einem SDK-Standardschlüssel des SoC-Lieferanten, nicht zu einem gerätespezifisch erzeugten Herstellerschlüssel. Trifft das zu, kann jeder mit demselben SDK System-Apps für diese Hardware signieren. Das ändert nichts an unserer Nutzung, aber alles an der Bewertung der Plattformsicherheit und ist CRA-relevant.

**Zu klären mit Bominwell, schriftlich:** Wurde dieser Plattformschlüssel gerätespezifisch für die ONE erzeugt, oder ist es der Standardschlüssel des SoC-SDK? Wer hat ihn außer uns?

## 3. Entscheidung

Ab dem nächsten ausgelieferten Stand wird DrainQ.ONE **ausschließlich mit dem Plattformschlüssel `bominwellalias` signiert**. Der Debug-Schlüssel und der `oneapp-release.keystore` entfallen als Signaturquelle für Auslieferungen.

Damit ist die offene Frage „Debug- oder Release-Schlüssel" gegenstandslos. Es gibt keine zwei Optionen mehr, sondern eine.

**Zeitpunkt: sofort.** Der Signaturwechsel zwingt zu einer einmaligen Deinstallation und Neuinstallation auf jedem bereits ausgelieferten Gerät, mit Verlust der lokalen Projektdaten. Die Flotte besteht heute aus dem Thomas-Gerät, Louis' Gerät und der fabrikneuen Test-ONE. Jeder Monat Verzögerung erhöht die Zahl der betroffenen Kunden — die Umstellung wird nie billiger als jetzt.

## 4. Bevorzugter technischer Weg

Plattformsignierte APK in `/system/priv-app/`, **ohne** `sharedUserId="android.uid.system"`.

Begründung: Die Plattformsignatur allein reicht für `NETWORK_SETTINGS` und alle weiteren `signature`-Berechtigungen. `sharedUserId` lässt die App als System-UID laufen, macht jeden App-Fehler zu einem Systemfehler und ist nachträglich nicht mehr entfernbar, ohne die App-Daten zu verlieren. Der geringere Eingriff gewinnt.

**Ausnahme mit Gate:** Sollte der Test aus Abschnitt 5 zeigen, dass nur die System-UID den Zugriff auf `/dev/video0` löst, wird `sharedUserId` neu bewertet — dann gegen den Nutzen, die Vendor-Abhängigkeit loszuwerden.

## 5. Gate vor der Umsetzung: der Kamera-Test

Vor dem Signaturwechsel ist eine Hypothese zu messen, die den Umfang der Entscheidung verändert:

> Kann eine plattformsignierte DrainQ.ONE auf einer **fabrikneuen** ONE **ohne** die ueventd-Regel aus ADR-0003 das Kamerabild öffnen?

Trifft das zu, entfällt die Forderung an den Hersteller, `/dev/video*   0666   root   root` ins Golden-Image der `vendor`/`super`-Partition einzubacken — und damit eine der beiden externen Abhängigkeiten.

Testaufbau: Gerät `cc1615f07da5e76f` neu flashen oder die overlayfs-Schicht entfernen, plattformsignierten Bau installieren, Inspektionsbildschirm öffnen, `logcat` auf `V4L2Bridge: Opened /dev/video0` prüfen. Ergebnis in beiden Varianten dokumentieren (mit und ohne `sharedUserId`).

## 6. Folgen

**Positiv**
- W3a Hotspot mit eigener SSID zur Laufzeit wird lauffähig; der Code ist fertig, es fehlte nur die Berechtigung.
- Privilegierter WLAN-Pfad und Standort-Automatik ohne Device-Owner-Provisionierung.
- Die Signaturspaltung der Flotte endet dauerhaft.
- Möglicherweise entfällt die ueventd-Abhängigkeit (siehe Abschnitt 5).

**Negativ, bewusst in Kauf genommen**
- Einmalige Deinstallation und Neuinstallation auf jedem ausgelieferten Gerät, mit Verlust lokaler Projektdaten. Vor der Umstellung ist auf jedem Gerät ein USB-Export der vorhandenen Projekte zu ziehen.
- Der Selbst-Update-Weg über das Portal funktioniert über den Signaturwechsel hinweg **nicht** — der erste plattformsignierte Stand muss manuell installiert werden. Erst der darauf folgende läuft wieder über den normalen Update-Kanal.
- Eine Installation nach `/system/priv-app/` ist keine gewöhnliche App-Installation. Sie braucht Schreibzugriff auf die Systempartition, überlebt kein Re-Flash und muss ins Golden-Image. Solange das nicht geklärt ist, ist der plattformsignierte Bau ein Werks- und Servicevorgang, kein Feldupdate.
- Der Beta-Bau ohne Keystore (`assembleDebug`) bleibt für reine Entwicklungsstände zulässig, ist aber ab sofort ausdrücklich **kein Auslieferungsweg** mehr.

## 7. Schlüsselverwahrung

Der Plattformschlüssel signiert nicht eine App, sondern das gesamte System. Sein Schutzbedarf liegt über dem des `oneapp-release.keystore`.

- **Nicht** unverschlüsselt ins Repo. Die Credentials-Policy vom 08.07.2026 deckt Gerätezugänge, nicht Signaturschlüssel dieser Klasse.
- Verwahrung verschlüsselt und außerhalb des Entwicklungsrechners, mit einer zweiten Kopie an getrenntem Ort.
- Passwort getrennt vom Keystore verwahren, nie zusammen übertragen.
- Für Bauläufe über Umgebungsvariablen einspeisen, nie als Pfad in eine committete Gradle-Datei.
- Verlust ist nicht heilbar: ohne diesen Schlüssel ist kein ausgeliefertes Gerät mehr aktualisierbar.

## 8. Umsetzungsschritte in Reihenfolge

1. **Schlüssel sichern** — verschlüsselte Ablage plus Zweitkopie, Passwort getrennt. Vor allem Weiteren.
2. **Rückfrage an Bominwell** zur Exklusivität des Schlüssels (Abschnitt 2a), schriftlich.
3. **Kamera-Test** aus Abschnitt 5, beide Varianten, Ergebnis dokumentiert.
4. **Signaturweg entscheiden** auf Basis von Schritt 3: priv-app allein oder mit `sharedUserId`.
5. **Datensicherung der Flotte** — USB-Export auf allen drei Geräten, bevor irgendetwas neu installiert wird.
6. **Signaturkonfiguration bauen** — `signingConfig` über Umgebungsvariablen, `publish-one-release.ps1` anpassen, Versionsschema fortführen.
7. **Umstellung Gerät für Gerät**, beginnend mit der Test-ONE, dann Thomas, zuletzt Louis nach Absprache.
8. **W3a Hotspot abnehmen** — der eigentliche Zweck der Übung.
9. **ADR-0003 fortschreiben**, falls Schritt 3 die ueventd-Abhängigkeit auflöst.

## 9. Verworfene Alternativen

**Weiter auf einen Schlüssel warten.** Gegenstandslos — er liegt vor.

**priv-app-Allowlist ohne Plattformsignatur.** Am 16.07. per logcat widerlegt: `NETWORK_SETTINGS` ist `signature`-geschützt, keine Allowlist ersetzt das.

**Beim Debug-Schlüssel bleiben und den Hotspot streichen.** Löst die Signaturspaltung nicht, verschenkt ein fertig gebautes Feature und lässt das Verlustrisiko der `debug.keystore` bestehen.

**Umstellung auf später verschieben, bis mehr Geräte im Feld sind.** Der Umstellungsaufwand wächst linear mit der Flotte, der Nutzen nicht. Verschieben verteuert die Entscheidung, ohne sie zu verbessern.

---

## 10. Nachtrag: Zwei Pakete aus einem Bau (CEO 01.10.2026)

### 10.1 Anlass

DrainQ.ONE ist eine App, die auf der ONE-Anlage (Master) und auf handelsüblichen Android-Tablets (Slave) läuft und den Modus selbst erkennt. Seit `android:sharedUserId="android.uid.system"` in jedem Bau steht, lehnt Android die APK auf jedem Gerät ab, dessen Firmware nicht mit dem Plattformschlüssel gebaut ist. Gemessen am 01.10.2026: Ein Test-Tablet mit `0.4.3-lohs-test` findet und lädt das Update `0.9.6`, die Installation scheitert. Die ONE selbst braucht die System-UID weiter (Kameradienst-Start per `SystemProperties ctl.start`, Kamerarecht, Hotspot `DrainQ-ONE-<Serial>`; Belege `docs/archiv/2026-07/RESULT_CAMERA2_UMBAU_2026-07-29.md` Abschnitt 2 und 6, `docs/archiv/2026-07/RESULT_PLATTFORMSIGNATUR_2026-07-29.md`). Eine einzige APK-Datei für beide Gerätearten ist damit nicht möglich.

### 10.2 Entscheidung (CEO 01.10.2026)

1. Eine App, eine Codebasis, **zwei Pakete aus demselben Bau**.
2. Paket **ONE**: wie bisher — `sharedUserId="android.uid.system"`, Plattformschlüssel (`ONE_PLATFORM_*`), Portal-Produkt `one`.
3. Paket **Tablet**: ohne `sharedUserId`, signiert mit einem **eigenen Tablet-Schlüssel** über `ONE_TABLET_KEYSTORE`, `ONE_TABLET_PASS`, `ONE_TABLET_ALIAS`; Portal-Produkt `one-tablet`. Der alte `KEYSTORE_PATH`-Weg wird für Auslieferungen nicht benutzt.
4. Beide Pakete: gleiche `applicationId` `com.uip.drainq.one`, gleicher versionCode/versionName, gleicher Code, gleiche Oberfläche, Erkennung ONE/Tablet unverändert. Übersetzungen weiter aus Produkt `one`.
5. Einmaliger Umstieg ohne Datensicherung: Das einzige Tablet mit Altversion (`0.4.3-lohs-test`, Zertifikat `CN=DrainQ ONE, O=UIP Team GmbH`) wird einmal deinstalliert; der Schlüssel jener Testversion ist nicht mehr zuzuordnen.

### 10.3 Umsetzung

- **Produktvarianten** `one` (Standard) und `tablet` in `app/build.gradle.kts` (Dimension `paket`). `assembleRelease` und `assembleDebug` bauen beide; einzeln `assembleOneRelease` / `assembleTabletRelease`, Tests `testOneDebugUnitTest` / `testTabletDebugUnitTest`.
- **Manifest**: `app/src/main/AndroidManifest.xml` trägt kein `sharedUserId` mehr; das Attribut steht allein in `app/src/one/AndroidManifest.xml` und wird nur in das ONE-Paket gemischt. Abschnitt 4 (Weg ohne `sharedUserId`) gilt damit für das Tablet-Paket; das ONE-Paket bleibt bei der System-UID.
- **Update-Produkt**: `UPDATE_PROXY_URL` je Variante — ONE `https://license.drainq.com/api/software/one/`, Tablet `https://license.drainq.com/api/software/one-tablet/`. Kanal bleibt `beta`.
- **Signatur**: Die bisherige Auswahl (Plattformschlüssel, sonst `KEYSTORE_PATH`, sonst Debug) ist unverändert aus dem Release-Bautyp in die Variante `one` verschoben. Die Variante `tablet` signiert immer mit dem `signingConfig` `tablet`. Fehlt auch nur eine der drei `ONE_TABLET_*`, bricht der Release-Bau des Tablet-Pakets ab (Wächter auf `packageTabletRelease`, dahinter AGP selbst: „SigningConfig "tablet" is missing required property "storeFile"") — kein stiller Rückfall auf `KEYSTORE_PATH` oder den Debug-Schlüssel. Sind die `ONE_TABLET_*` gesetzt, aber `APP_VERSION_CODE`/`APP_VERSION_NAME` nicht, bricht der Bau schon bei der Konfiguration ab (wie beim Plattformschlüssel).
- **Dateinamen**: ONE `DrainQ-ONE_<Version>_<Code>_platform.apk` (unverändert das Muster, das Werkseinrichtung und Partner-ZIP erwarten), Tablet `DrainQ-ONE_<Version>_<Code>_tablet.apk`; Debug-Baue mit `-debug` vor `.apk`. Das Muster der Werkseinrichtung verlangt einen `versionName` nur aus Ziffern und Punkten; der Bau warnt sonst.
- **Veröffentlichung**: `tools/publish-one-release.ps1 -Variante one|tablet` (ohne Parameter `one`), siehe `docs/RELEASE_PUBLISHING.md`.

### 10.4 Tablet-Schlüssel

Der CEO erzeugt den Schlüssel selbst. Anforderungen: **RSA 2048** und v1-Signatur (Gradle setzt v1 und v2), weil `tools/werkseinrichtung/Get-ApkSignatureFingerprint.ps1` nur `META-INF/*.RSA|*.DSA` liest — ein EC-Schlüssel oder eine reine v2-Signatur bricht die Zertifikatsprobe fail-closed ab. `ONE_TABLET_PASS` ist Store- und Schlüsselkennwort zugleich. Beispiel:

```powershell
keytool -genkeypair -keystore <Ablage>\drainq-one-tablet.keystore -alias <Alias> -keyalg RSA -keysize 2048 -validity 10000
```

Verwahrung wie Abschnitt 7: verschlüsselt, außerhalb des Entwicklungsrechners, Zweitkopie an getrenntem Ort, Kennwort getrennt; nie ins Repo, nie als Pfad in eine committete Datei. Verlust heißt: kein Tablet mit dem Tablet-Paket ist mehr aktualisierbar.

### 10.5 Folgen

- Das Tablet-Paket ist auf handelsüblichen Tablets installierbar; System-Aufrufe (Kameradienst-Start, privilegierter Hotspot, `ttyS5`, Systemuhr) laufen dort nicht oder enden geordnet (Erhebung 01.10.2026, alle Stellen mit `try/catch` bzw. Vorprüfung, kein Codeeingriff nötig).
- Das Tablet-Paket gehört nicht in die Werkseinrichtung; das Partner-ZIP enthält weiter nur das ONE-Paket.
- Ein Gerät wechselt nicht zwischen den Paketen per Update: gleiche `applicationId`, aber verschiedene Signaturen — Android lehnt ein Update über die Signaturgrenze ab. Wechsel heißt Deinstallation.
- Das Portal braucht für `one-tablet` keinen Code (Produkt ist freier Text); die Admin-Auswahlliste kennt `one-tablet` nicht, das Anlegen läuft über das Skript.
