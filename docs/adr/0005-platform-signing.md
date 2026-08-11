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
