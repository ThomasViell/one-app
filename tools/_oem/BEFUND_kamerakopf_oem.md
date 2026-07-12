# Befund: Kamerakopf-Erkennung via OEM-App (2026-06-05)

OEM-App `com.bominwell.minipush` (Branding "NSP3CT PRO", Bominwell-Rebrand), V1.2.9-RC.1,
auf dieser ONE gegengeprueft. Kopf zum Testzeitpunkt: **C10**.

## Ergebnis (kippt bisherige Annahme)
1. **OEM-App zeigt den Kopftyp an** — Chip unten links neben BATTERY = "C10" (Screenshot `oem_shot1.png`).
   → Auto-Erkennung auf dieser Hardware ist grundsaetzlich moeglich.
2. **Gruppen 23 (0x17) UND 24 (0x18) sind auf der Leitung** — entgegen unserer bisherigen
   Annahme ("kein Group 23/24"). Die OEM-App loggt den Roh-Stream
   (`MiniPushControlHelper: initDataParseThread ... hexByte=`); jedes Paket enthaelt ALLE Gruppen
   21/22/23/24 in EINEM Frame.
3. **Schlussfolgerung:** Die Luecke liegt bei UNS — unser `OneFrameCodec`/Parser extrahiert
   23/24 nicht aus dem kombinierten Frame (mis-Segmentierung oder falsches Byte), NICHT am Kopf.

## Frame-Format (dekodiert, Laenge 0x2D=45 passt exakt)
```
FA AF | 00 2D (Gesamtlaenge inkl. FA AF = 45) | 00 04 (Typ)
| 0C 15  00*9 19            Gruppe 21 STATUS (len 0x0C=12: len+grp+10)
| 0C 16  FFFFFFFF 00*5 1A   Gruppe 22 METER  (len 12; FFFFFFFF = Meter uninit)
| 08 17  00 00 02 XX 01 CK  Gruppe 23        (len 8)  XX schwankt ~0x1FE..0x210 -> Analogwert, NICHT der Kopftyp-Literal
| 07 18  00 01 01 00 1F     Gruppe 24        (len 7)  konstant
```
Aufbau je Gruppe: `[len][group][payload...]`, len zaehlt len+group+payload.

## Offen / naechster Schritt zum Replizieren
- Der Byte->Name-Mapping (C10/C18) liegt im OEM-Parser (`DeviceType`/`getCameraID`/`writeCamType`).
  Ohne Decompiler (jadx) nicht 1:1 ablesbar. Zwei Wege:
  (a) jadx auf base.apk -> `MiniPushControlHelper`/`DeviceType`-Parse-Logik lesen (sauber).
  (b) Empirisch: Frame mit C10 (vorhanden) vs. C18 vergleichen -> das stabil unterschiedliche
      Byte = Kopftyp. (Gruppe 0x17-XX scheidet aus, da pro Frame schwankend.)
- Verdacht: Typfeld `00 04` oder das konstante `00 01 01 00` in Gruppe 24 koennte den Typ tragen.
  Erst mit C18-Capture oder jadx bestaetigen.

## Hinweis
- OEM-App ist auf dieser ONE bewusst deaktiviert (enabled=3/disabled-user). Fuer den Test
  temporaer aktiviert, danach wieder deaktiviert. Unsere App haelt /dev/ttyS5 exklusiv —
  fuer den OEM-Test musste sie kurz gestoppt werden.
