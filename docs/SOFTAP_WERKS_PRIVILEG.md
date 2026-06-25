# SoftAP — Werks-Image-Privileg für den Tablet-Hotspot (Dual-Modus, Welle 3a)

**Status:** Code fertig (Reflection-Pfad), **am Gerät zu verifizieren** (Welle 5, RK3588).
**Bezug:** `network/AndroidSoftApStarter.kt`, `network/AccessPointController.kt`, `network/SoftAp.kt`.

## Worum es geht

Auf der ONE spannt die App im **DIRECT-Modus** einen WLAN-Hotspot auf, dem ein Tablet ohne
Büro-WLAN beitritt (On-Demand per Pairing-Schalter, Kopplung per WIFI-QR).

Der frühere Pfad nutzte die **öffentliche** API `WifiManager.startLocalOnlyHotspot()`. Die hat
zwei für den Kunden untragbare Eigenschaften:

1. **Standortzwang** — `startLocalOnlyHotspot()` verlangt `ACCESS_FINE_LOCATION` **und** aktivierte
   Standortdienste. Im Werks-/Kiosk-Betrieb ist ein Standort-Prompt ein No-Go.
2. **Keine gebrandete SSID** — die Plattform würfelt SSID + Passwort je Sitzung; ein
   wiedererkennbares `DrainQ-ONE-…` ist über die öffentliche API nicht setzbar.

Lösung: der **privilegierte SoftAP-Pfad**. Die ONE-App ist im Werks-Image privilegiert, daher
darf sie die @SystemApi-Tethering-APIs nutzen — **ohne** Standortberechtigung und **mit** fester,
gebrandeter SSID.

## Was der Code tut (`AndroidSoftApStarter`)

Alle benötigten Typen/Methoden liegen außerhalb des öffentlichen SDK (@SystemApi) und sind daher
**per Reflection** angebunden (gegen `android.jar` nicht kompilierbar). Ablauf:

1. **Zugangsdaten** (`SoftApCredentialStore` / `SoftApSpec`):
   - SSID `DrainQ-ONE-<serial>` aus `Build.getSerial()` (privilegiert) bzw. `ANDROID_ID`-Fallback.
   - WPA2-PSK-Passphrase **einmalig** mit `SecureRandom` erzeugt, in SharedPreferences persistent.
2. **`SoftApConfiguration`** bauen: `SoftApConfiguration.Builder()` →
   `setSsid(...)`, `setPassphrase(pass, SECURITY_TYPE_WPA2_PSK)`, `setAutoShutdownEnabled(false)`.
3. **Persistieren**: `WifiManager.setSoftApConfiguration(config)` (best-effort).
4. **Starten**: `WifiManager.startTetheredHotspot(config)`.
5. **Zustand beobachten**: `WifiManager.registerSoftApCallback(executor, SoftApCallback)` über einen
   dynamischen `Proxy` (ENABLED→aktiv, FAILED→fehlgeschlagen, DISABLED-nach-Hochlauf→gestoppt).
   Bleibt die Callback auf einem Image stumm, greift ein Fallback-Timer (best-effort „aktiv").
6. **Stoppen**: `WifiManager.stopSoftAp()` + Callback abmelden.

Fehlt das Privileg (z. B. Debug-Build ohne Allowlist), wirft die Plattform `SecurityException`
(bzw. die Hidden-API-Policy `NoSuchMethodException`). Das wird zu **`REASON_PRIVILEGE`** klassifiziert
→ UI-Hinweis „Hotspot benötigt Werks-Image-Privileg" statt Crash.

### Alternative API (dokumentiert, nicht primär genutzt)

Statt `WifiManager.startTetheredHotspot(...)` ginge auch
`TetheringManager.startTethering(TetheringRequest, executor, StartTetheringCallback)` mit einer
`TetheringRequest`, die per `setSoftApConfiguration(...)` die gebrandete Config trägt. Gleicher
Privileg-Bedarf (`TETHER_PRIVILEGED`), mehr Reflection-Oberfläche. Falls `startTetheredHotspot`
auf dem RK3588-Image **nicht** zieht, ist das der Fallback, der am Gerät zu erproben ist.

## Was das ONE-Image braucht

Damit die @SystemApi-Aufrufe zur Laufzeit gelingen, muss **eine** der folgenden Bedingungen
erfüllt sein. **Welche exakt auf dem RK3588-Werks-Image greift, ist am Gerät zu verifizieren —
beide Wege sind hier dokumentiert, nicht geraten.**

### Weg A — privilegierte App + privapp-permissions-Allowlist (bevorzugt)

1. **App als priv-app installieren**: APK liegt unter `/system/priv-app/DrainQONE/` (nicht
   `/system/app/` und nicht `/data/app/`). Nur priv-apps dürfen `privapp-permissions`-Einträge
   bekommen.
2. **Allowlist-XML** unter `/etc/permissions/privapp-permissions-drainq.xml` (oder produktspezifisch),
   die genau die signature|privileged-Permissions freigibt, die der SoftAP-Pfad braucht:

   ```xml
   <permissions>
     <privapp-permissions package="com.uip.drainq.one">
       <!-- mind. eine davon — am Gerät prüfen, welche das Image für startTetheredHotspot/
            setSoftApConfiguration/stopSoftAp/registerSoftApCallback verlangt: -->
       <permission name="android.permission.NETWORK_SETTINGS"/>
       <permission name="android.permission.TETHER_PRIVILEGED"/>
     </privapp-permissions>
   </permissions>
   ```

   - `NETWORK_SETTINGS` deckt `setSoftApConfiguration`/`registerSoftApCallback` ab.
   - `TETHER_PRIVILEGED` deckt den eigentlichen Tethering-Start (`startTetheredHotspot` /
     `TetheringManager.startTethering`) ab.
   - Im Zweifel **beide** eintragen.
3. **Hidden-API-Block**: priv-apps unterliegen der Hidden-API-Policy **nicht** in der gleichen Härte;
   die @SystemApi-Reflection gelingt. (Optional/zur Diagnose, nicht für Produktion:
   `settings put global hidden_api_policy 1`.)

> Diese Permissions sind `protectionLevel="signature|privileged"`. Sie werden **nicht** im
> `AndroidManifest.xml` als `<uses-permission>` deklariert müssen, damit die App ohne Werks-Image
> (Dev-Gerät) weiter installierbar bleibt — der privilegierte Pfad scheitert dort dann sauber mit
> `REASON_PRIVILEGE`. (Falls das Image die Manifest-Deklaration zwingend verlangt, separat ergänzen
> und am Gerät verifizieren.)

### Weg B — Plattform-Signatur

App mit dem **Plattform-Schlüssel** des ONE-Images signieren (`platform.x509.pem` /
`platform.pk8`) und `android:sharedUserId="android.uid.system"` setzen. Dann gelten alle
`signature`-Permissions automatisch, der SoftAP-Pfad ist ohne Allowlist freigeschaltet.
Nachteil: stärkere Kopplung an das Image, eigener Release-Signaturpfad.

## Abnahme am Gerät (Welle 5, TODO(device))

Mit privilegiertem Image auf der ONE (RK3588):

- [ ] Pairing-Schalter AN → Hotspot startet **ohne** Standort-Prompt.
- [ ] SSID ist gebrandet (`DrainQ-ONE-<serial>`), Passwort stabil über Neustarts.
- [ ] Tablet scannt den WIFI-QR → joint dem Hotspot.
- [ ] Video/Telemetrie/Steuerung laufen über den Hotspot wie über Büro-WLAN
      (Discovery findet die ONE im `192.168.43.0/24`-Subnetz, vgl. `OneRemoteServer.localServerIp`).
- [ ] Schalter AUS → `stopSoftAp()` beendet den AP; STA-Verbindung kommt zurück.
- [ ] Verifizieren, welche Permission/Signatur das Image real verlangt (Weg A vs. B) und welcher
      Start-Aufruf zieht (`startTetheredHotspot` vs. `TetheringManager.startTethering`).
- [ ] Prüfen, ob `SoftApCallback` ENABLED liefert (sonst greift nur der Fallback-Timer).
