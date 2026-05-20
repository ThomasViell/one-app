# RESULT: Settings Cleanup — Migration A

**Datum:** 2026-05-20  
**Branch:** feature/settings-cleanup  
**Ziel:** Obsolete UI-Sektionen nach Migration A (lokal-direkte ONE-Hardware, /dev/ttyS5 + /dev/video0) entfernen

---

## Geänderte Files

| File | Änderung |
|------|----------|
| `app/src/main/java/com/uip/oneapp/ui/screens/settings/SettingsScreen.kt` | 3 Sektionen entfernt, 2 State-Variablen entfernt, 1 Import entfernt |

---

## Entfernte Sektionen

### 1. `Geraetetyp` — DeviceType-Dropdown (komplett gelöscht)

- **Was:** `ExposedDropdownMenuBox` mit `DeviceType.entries` (NSP3CT ONE / NSP3CT TWO)
- **Untertitel:** "Bestimmt die Verbindungs-Konfiguration (Neustart erforderlich)"
- **Dazu entfernt:** AlertDialog für Neustart-Bestätigung nach DeviceType-Wechsel
- **Methode:** Gelöscht (kein `if(false)`)
- **Mitentfernte Variablen:** `deviceTypeDropdownExpanded`, `pendingDeviceType`
- **Mitentfernter Import:** `import com.uip.oneapp.network.DeviceType`

### 2. `NSP3CT Verbindung` — MQTT/RTSP-Konfigurationsblock (komplett gelöscht)

- **Was:** Card mit Feldern `Broker IP-Adresse` (172.169.11.200), `Broker Port` (1883), `RTSP Stream URL`, TWO-spezifische Kamera-Felder (Camera IP/User/Password), Button „Verbindung testen"
- **Methode:** Gelöscht (kein `if(false)`)

### 3. `ONE Verbindung` — Navigation-Item (komplett gelöscht)

- **Was:** Klickbare Card → `navController.navigate("connection")`, Untertitel "WiFi-Scan, RTSP-Erkennung, Controller"
- **Methode:** Gelöscht (kein `if(false)`)

---

## Nicht angefasste Files (bewusst erhalten)

| File | Begründung |
|------|-----------|
| `network/TwoHardwareService.kt` | TWO-Variante könnte reaktiviert werden |
| `network/DeviceType.kt` | Enum mit TWO-Eintrag bleibt (Backend-Logik) |
| `ui/screens/connection/ConnectionScreen.kt` | WLAN-Discovery Screen bleibt navigierbar |
| `ui/screens/settings/SettingsViewModel.kt` | `brokerIp`, `brokerPort`, `rtspUrl`, `updateDeviceType()` etc. bleiben im ViewModel |

---

## Compile-Status

```
BUILD SUCCESSFUL in 1m 35s
:app:compileDebugKotlin — keine Fehler
```

**Warnings (pre-existing, kein neuer Code):**
- `Divider` deprecated → `HorizontalDivider` (5× in SettingsScreen, bereits vor diesem Commit vorhanden)
- `sondeMode` / `lightLevel` unused parameter in `InspectionOsd.kt` (unrelated)

---

## Bekannte Issues

Keine. Der `ConnectionScreen` ist über `navController.navigate("connection")` weiterhin erreichbar, sofern ein anderer Einstiegspunkt ergänzt wird — die Route ist in der NavGraph registriert und bleibt funktionsfähig.
