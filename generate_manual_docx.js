const {
    Document, Packer, Paragraph, TextRun, HeadingLevel, Table, TableRow, TableCell,
    WidthType, AlignmentType, BorderStyle, ShadingType, PageBreak,
    Header, Footer, PageNumber, NumberFormat, TabStopPosition, TabStopType,
    TableOfContents, StyleLevel, ImageRun, convertInchesToTwip,
    UnderlineType, VerticalAlign, LineRuleType
} = require('docx');
const fs = require('fs');
const path = require('path');

// ─── COLORS ───────────────────────────────────────────────────
const YELLOW = 'FFCE00';
const YELLOW_DARK = 'CB9700';
const DARK_BG = '1A1A2E';
const WHITE = 'FFFFFF';
const LIGHT_GRAY = 'F5F5F5';
const MEDIUM_GRAY = '888888';
const TEXT_GRAY = '555555';
const GREEN = '4CAF50';
const GREEN_DARK = '2E7D32';
const INFO_BG = 'FFF8E1';
const TIP_BG = 'E8F5E9';

// ─── HELPER FUNCTIONS ─────────────────────────────────────────

function heading1(text) {
    return new Paragraph({
        text: text,
        heading: HeadingLevel.HEADING_1,
        spacing: { before: 400, after: 200 },
        border: {
            bottom: { style: BorderStyle.SINGLE, size: 6, color: YELLOW }
        },
        style: 'Heading1'
    });
}

function heading2(text) {
    return new Paragraph({
        text: text,
        heading: HeadingLevel.HEADING_2,
        spacing: { before: 300, after: 150 },
        style: 'Heading2'
    });
}

function heading3(text) {
    return new Paragraph({
        text: text,
        heading: HeadingLevel.HEADING_3,
        spacing: { before: 200, after: 100 },
        style: 'Heading3'
    });
}

function para(text) {
    return new Paragraph({
        children: [new TextRun({ text, font: 'Calibri', size: 22 })],
        spacing: { after: 120, line: 276, lineRule: LineRuleType.AUTO }
    });
}

function paraLight(text) {
    return new Paragraph({
        children: [new TextRun({ text, font: 'Calibri', size: 20, color: TEXT_GRAY, italics: true })],
        spacing: { after: 80 }
    });
}

function bullet(text) {
    return new Paragraph({
        children: [new TextRun({ text, font: 'Calibri', size: 22 })],
        bullet: { level: 0 },
        spacing: { after: 60 }
    });
}

function numberedItem(num, text) {
    return new Paragraph({
        children: [
            new TextRun({ text: `${num}. `, font: 'Calibri', size: 22, bold: true, color: YELLOW_DARK }),
            new TextRun({ text, font: 'Calibri', size: 22 })
        ],
        spacing: { after: 60 },
        indent: { left: 360 }
    });
}

function screenshotPlaceholder(label) {
    return new Paragraph({
        children: [
            new TextRun({ text: `[Screenshot: ${label}]`, font: 'Calibri', size: 22, color: MEDIUM_GRAY, italics: true })
        ],
        spacing: { before: 200, after: 200 },
        alignment: AlignmentType.CENTER,
        border: {
            top: { style: BorderStyle.DASHED, size: 1, color: 'CCCCCC', space: 8 },
            bottom: { style: BorderStyle.DASHED, size: 1, color: 'CCCCCC', space: 8 },
            left: { style: BorderStyle.DASHED, size: 1, color: 'CCCCCC', space: 8 },
            right: { style: BorderStyle.DASHED, size: 1, color: 'CCCCCC', space: 8 }
        },
        shading: { type: ShadingType.CLEAR, fill: LIGHT_GRAY }
    });
}

function infoBox(title, text) {
    return new Table({
        rows: [
            new TableRow({
                children: [
                    new TableCell({
                        width: { size: 100, type: WidthType.AUTO },
                        children: [
                            new Paragraph({
                                children: [
                                    new TextRun({ text: `\u26A0 ${title}: `, font: 'Calibri', size: 21, bold: true, color: YELLOW_DARK }),
                                    new TextRun({ text, font: 'Calibri', size: 21 })
                                ],
                                spacing: { before: 60, after: 60 },
                                indent: { left: 120 }
                            })
                        ],
                        shading: { type: ShadingType.CLEAR, fill: INFO_BG },
                        borders: {
                            left: { style: BorderStyle.SINGLE, size: 12, color: YELLOW },
                            top: { style: BorderStyle.NONE },
                            bottom: { style: BorderStyle.NONE },
                            right: { style: BorderStyle.NONE }
                        }
                    })
                ]
            })
        ],
        width: { size: 100, type: WidthType.PERCENTAGE }
    });
}

function tipBox(text) {
    return new Table({
        rows: [
            new TableRow({
                children: [
                    new TableCell({
                        width: { size: 100, type: WidthType.AUTO },
                        children: [
                            new Paragraph({
                                children: [
                                    new TextRun({ text: 'Tipp: ', font: 'Calibri', size: 21, bold: true, color: GREEN_DARK }),
                                    new TextRun({ text, font: 'Calibri', size: 21, color: GREEN_DARK })
                                ],
                                spacing: { before: 60, after: 60 },
                                indent: { left: 120 }
                            })
                        ],
                        shading: { type: ShadingType.CLEAR, fill: TIP_BG },
                        borders: {
                            left: { style: BorderStyle.SINGLE, size: 12, color: GREEN },
                            top: { style: BorderStyle.NONE },
                            bottom: { style: BorderStyle.NONE },
                            right: { style: BorderStyle.NONE }
                        }
                    })
                ]
            })
        ],
        width: { size: 100, type: WidthType.PERCENTAGE }
    });
}

function spacer() {
    return new Paragraph({ text: '', spacing: { after: 120 } });
}

function makeTable(headers, rows) {
    const allRows = [
        new TableRow({
            tableHeader: true,
            children: headers.map(h => new TableCell({
                children: [new Paragraph({
                    children: [new TextRun({ text: h, font: 'Calibri', size: 20, bold: true, color: DARK_BG })],
                    spacing: { before: 40, after: 40 },
                    indent: { left: 80 }
                })],
                shading: { type: ShadingType.CLEAR, fill: YELLOW },
                borders: {
                    top: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDD' },
                    bottom: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDD' },
                    left: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDD' },
                    right: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDD' }
                }
            }))
        }),
        ...rows.map((row, i) => new TableRow({
            children: row.map(cell => new TableCell({
                children: [new Paragraph({
                    children: [new TextRun({ text: cell, font: 'Calibri', size: 20 })],
                    spacing: { before: 30, after: 30 },
                    indent: { left: 80 }
                })],
                shading: { type: ShadingType.CLEAR, fill: i % 2 === 0 ? WHITE : LIGHT_GRAY },
                borders: {
                    top: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDD' },
                    bottom: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDD' },
                    left: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDD' },
                    right: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDD' }
                }
            }))
        }))
    ];

    return new Table({
        rows: allRows,
        width: { size: 100, type: WidthType.PERCENTAGE }
    });
}

// ═══════════════════════════════════════════════════════════════
// BUILD DOCUMENT
// ═══════════════════════════════════════════════════════════════

const children = [];

// ─── COVER PAGE ───────────────────────────────────────────────

children.push(
    new Paragraph({ text: '', spacing: { before: 2000 } }),
    new Paragraph({
        children: [new TextRun({ text: 'ONE.APP', font: 'Calibri', size: 72, bold: true, color: YELLOW_DARK })],
        alignment: AlignmentType.CENTER,
        spacing: { after: 200 }
    }),
    new Paragraph({
        children: [new TextRun({ text: 'Bedienungsanleitung', font: 'Calibri', size: 36, color: DARK_BG })],
        alignment: AlignmentType.CENTER,
        spacing: { after: 100 }
    }),
    new Paragraph({
        children: [new TextRun({ text: 'NSP3CT Slave Monitor', font: 'Calibri', size: 26, color: TEXT_GRAY })],
        alignment: AlignmentType.CENTER,
        spacing: { after: 400 }
    }),
    new Paragraph({
        children: [
            new TextRun({ text: 'Version 1.0  |  Stand: Februar 2026', font: 'Calibri', size: 22, color: MEDIUM_GRAY })
        ],
        alignment: AlignmentType.CENTER,
        spacing: { after: 600 }
    }),
    para('Diese Bedienungsanleitung beschreibt die Funktionen und Bedienung der ONE.APP, der Android-Tablet-Anwendung zum NSP3CT Kanalinspektionssystem.'),
    spacer(),
    new Paragraph({
        children: [new TextRun({ text: 'Hauptfunktionen:', font: 'Calibri', size: 24, bold: true, color: YELLOW_DARK })],
        spacing: { after: 100 }
    }),
    bullet('Live-Videostream der Inspektionskamera (RTSP)'),
    bullet('Schadensdokumentation mit Foto und Annotation'),
    bullet('Sprachnotizen und Textnotizen'),
    bullet('Videoaufnahme mit/ohne Overlay'),
    bullet('PDF- und ZIP-Export der Inspektionsberichte'),
    bullet('Hardware-Steuerung (Licht, Sonde, Meterzähler)'),
    bullet('Mehrsprachige Benutzeroberfläche (35 Sprachen)'),
    bullet('Automatische Wetterabfrage per GPS'),
    spacer(),
    spacer(),
    new Paragraph({
        children: [
            new TextRun({ text: 'UIP Umwelt- und Ingenieurtechnik GmbH', font: 'Calibri', size: 22, bold: true, color: YELLOW_DARK }),
        ],
        alignment: AlignmentType.CENTER,
        spacing: { after: 40 }
    }),
    new Paragraph({
        children: [new TextRun({ text: 'Kanalinspektionssystem ONE', font: 'Calibri', size: 20, color: MEDIUM_GRAY })],
        alignment: AlignmentType.CENTER
    }),
    new Paragraph({ children: [new PageBreak()] })
);

// ─── TABLE OF CONTENTS ───────────────────────────────────────

children.push(
    new Paragraph({
        children: [new TextRun({ text: 'Inhaltsverzeichnis', font: 'Calibri', size: 40, bold: true, color: DARK_BG })],
        spacing: { after: 400 }
    }),
    new TableOfContents("Inhaltsverzeichnis", {
        hyperlink: true,
        headingStyleRange: "1-3"
    }),
    new Paragraph({ children: [new PageBreak()] })
);

// ═══════════════════════════════════════════════════════════════
// CHAPTER 1: EINLEITUNG
// ═══════════════════════════════════════════════════════════════

children.push(
    heading1('1  Einleitung'),
    para('Die ONE.APP ist eine Android-Tablet-Anwendung, die als Slave-Monitor für das NSP3CT Kanalinspektionssystem dient. Sie zeigt den Live-Videostream der Inspektionskamera an und ermöglicht die vollständige Schadensdokumentation direkt auf dem Tablet.'),
    para('Die App ist speziell für den Einsatz im Feld optimiert: große Schaltflächen, Dark-Theme für gute Lesbarkeit bei wechselnden Lichtverhältnissen und Landscape-Modus für optimale Videoanzeige auf 10"-Tablets.'),

    heading2('1.1  Systemvoraussetzungen'),
    bullet('Samsung Galaxy Tab S9 FE+ (oder vergleichbar)'),
    bullet('Android 8.0 (API 26) oder höher'),
    bullet('WLAN-Verbindung zum ONE-Controller (SSID: ONE_01)'),
    bullet('Mindestens 500 MB freier Speicherplatz'),

    heading2('1.2  App-Installation'),
    para('Die App wird als APK-Datei bereitgestellt und kann per USB oder Dateimanager auf dem Tablet installiert werden.'),
    numberedItem(1, 'APK-Datei auf das Tablet übertragen (USB, E-Mail oder Cloud)'),
    numberedItem(2, '"Installation aus unbekannten Quellen" in den Android-Einstellungen aktivieren'),
    numberedItem(3, 'APK-Datei öffnen und Installation bestätigen'),
    numberedItem(4, 'App starten und Einrichtung vornehmen'),
    spacer(),
    infoBox('Hinweis', 'Bei der ersten Installation werden Berechtigungen für Kamera, Mikrofon, Speicher und Standort abgefragt. Diese sind für die volle Funktionalität erforderlich.'),
);

// ═══════════════════════════════════════════════════════════════
// CHAPTER 2: ERSTE SCHRITTE
// ═══════════════════════════════════════════════════════════════

children.push(
    heading1('2  Erste Schritte'),

    heading2('2.1  App starten'),
    para('Nach dem Start der App erscheint zunächst ein kurzer Splash-Screen mit dem ONE.APP-Logo und einem Beta-Hinweis. Nach Bestätigung wird die Startseite (Dashboard) angezeigt.'),
    screenshotPlaceholder('Splash-Screen der ONE.APP'),

    heading2('2.2  Navigation'),
    para('Die ONE.APP verwendet eine untere Navigationsleiste (Bottom Navigation Bar) mit vier Hauptbereichen:'),
    spacer(),
    makeTable(
        ['Symbol', 'Bereich', 'Beschreibung'],
        [
            ['🏠', 'Startseite', 'Dashboard mit Übersicht und Schnellzugriff'],
            ['🎥', 'Inspektion', 'Live-Video und Schadenserfassung'],
            ['📁', 'Projekte', 'Projektliste und -verwaltung'],
            ['⚙️', 'Einstellungen', 'App- und Verbindungseinstellungen'],
        ]
    ),
    spacer(),
    para('Zusätzlich sind über die Navigation folgende Bereiche erreichbar:'),
    bullet('Verbindung (Connection) – über Einstellungen oder direkte Navigation'),
    bullet('Berichte – über das Dashboard'),
    bullet('Projektdetails – durch Tippen auf ein Projekt'),
    bullet('Projektformular – über "Neues Projekt" oder den Bearbeitungs-Button'),
    screenshotPlaceholder('Untere Navigationsleiste'),
    tipBox('Die aktive Navigationsseite wird in der Leiste farblich hervorgehoben.'),
);

// ═══════════════════════════════════════════════════════════════
// CHAPTER 3: STARTSEITE
// ═══════════════════════════════════════════════════════════════

children.push(
    heading1('3  Startseite (Dashboard)'),
    para('Die Startseite bietet einen schnellen Überblick über den aktuellen Status und Zugang zu den wichtigsten Funktionen.'),
    screenshotPlaceholder('Dashboard / Startseite'),

    heading2('3.1  Verbindungsstatus'),
    para('Im oberen Bereich der Startseite wird der aktuelle Verbindungsstatus zum ONE-Controller angezeigt:'),
    bullet('Grüner Punkt: Verbindung hergestellt – zeigt die IP-Adresse des Controllers'),
    bullet('Roter Punkt: Keine Verbindung'),
    bullet('Bei aktiver Verbindung wird zusätzlich der Batteriestand angezeigt'),

    heading2('3.2  Schnellzugriff'),
    para('Drei Schnellzugriff-Karten bieten direkten Zugang zu den wichtigsten Funktionen:'),
    numberedItem(1, 'Neues Projekt – Öffnet das Projektformular zum Erstellen eines neuen Inspektionsprojekts'),
    numberedItem(2, 'Inspektion – Wechselt direkt zum Inspektions-Bildschirm mit Live-Video'),
    numberedItem(3, 'Berichte – Öffnet die Berichts-Übersicht'),

    heading2('3.3  Aktuelle Projekte'),
    para('Unterhalb der Schnellzugriff-Karten werden die letzten 5 Projekte als Liste angezeigt. Jede Projektkarte enthält:'),
    bullet('Projektnummer (in der Akzentfarbe)'),
    bullet('Auftraggeber'),
    bullet('Inspektionsdatum'),
    bullet('Durchmesser (DN)'),
    para('Durch Tippen auf ein Projekt wird die Projektdetail-Ansicht geöffnet. Über den Button "Alle anzeigen" gelangt man zur vollständigen Projektliste.'),
    infoBox('Leere Projektliste', 'Wenn noch keine Projekte angelegt sind, wird ein Hinweis mit Button zum Erstellen des ersten Projekts angezeigt.'),
);

// ═══════════════════════════════════════════════════════════════
// CHAPTER 4: VERBINDUNG
// ═══════════════════════════════════════════════════════════════

children.push(
    heading1('4  Verbindung (Connection)'),
    para('Der Verbindungs-Bildschirm dient zur Einrichtung und Überwachung der Verbindung zum ONE-Inspektionssystem. Das Layout ist in zwei Spalten aufgeteilt: Links die Steuerung, rechts die Video-Vorschau.'),
    screenshotPlaceholder('Verbindungs-Bildschirm (2-Spalten-Layout)'),

    heading2('4.1  WiFi-Status'),
    para('Zeigt den aktuellen WLAN-Verbindungsstatus:'),
    bullet('SSID des verbundenen Netzwerks'),
    bullet('Lokale IP-Adresse'),
    bullet('Gateway-Adresse'),
    bullet('Erkennung des ONE-Netzwerks (SSID enthält "ONE")'),
    infoBox('ONE-Netzwerk', 'Verbinden Sie das Tablet mit dem WLAN "ONE_01". Das Subnet ist 192.168.82.x. Die App erkennt das ONE-Netzwerk automatisch und zeigt einen grünen Hinweis.'),

    heading2('4.2  Netzwerk-Scan'),
    para('Über den "Scannen"-Button wird ein automatischer Netzwerk-Scan im aktuellen Subnet durchgeführt:'),
    numberedItem(1, '"Scannen" antippen – der Fortschrittsbalken zeigt den Scan-Fortschritt'),
    numberedItem(2, 'Gefundene Geräte werden mit IP-Adresse, Hostname und offenen Ports aufgelistet'),
    numberedItem(3, 'Bei jedem gefundenen Gerät kann über "RTSP" ein RTSP-Stream-Test gestartet werden'),

    heading2('4.3  RTSP-Stream verbinden'),
    heading3('Automatisch (über Netzwerk-Scan)'),
    para('Nach einem erfolgreichen RTSP-Test erscheint das Ergebnis mit "Stream"-Button. Durch Antippen wird der Videostream gestartet und rechts in der Vorschau angezeigt.'),
    heading3('Manuell'),
    para('Im Abschnitt "Manuelle RTSP-URL" kann eine URL direkt eingegeben werden (z.B. rtsp://192.168.82.100:8554/1234). Über "Test" wird die Erreichbarkeit geprüft, über "Stream starten" wird die Verbindung hergestellt.'),
    tipBox('Die Standard-RTSP-URL des ONE-Systems lautet: rtsp://<IP>:8554/1234'),

    heading2('4.4  Hardware-Status'),
    para('Zeigt den aktuellen Status der ONE-Hardware an und ermöglicht die Steuerung:'),
    spacer(),
    makeTable(
        ['Komponente', 'Anzeige', 'Steuerung'],
        [
            ['Licht', 'AN/AUS mit Leistung (%)', 'Lichtstufe umschalten (0/30/60/90%)'],
            ['Sonde', 'AN/AUS mit Frequenz', 'Frequenz umschalten (0-3)'],
            ['Meter (Absolut)', 'Aktuelle Position in m', 'Auf 0 zurücksetzen'],
            ['Meter (Strecke)', 'Relative Distanz in m', 'Auf 0 zurücksetzen'],
            ['Batterie', 'Ladestand in %', '(nur Anzeige)'],
        ]
    ),
    spacer(),
    para('Am unteren Ende der linken Spalte wird ein Protokoll (Log) der letzten Verbindungsaktivitäten angezeigt.'),
);

// ═══════════════════════════════════════════════════════════════
// CHAPTER 5: PROJEKTE
// ═══════════════════════════════════════════════════════════════

children.push(
    heading1('5  Projekte verwalten'),

    heading2('5.1  Projektliste'),
    para('Die Projektliste zeigt alle angelegten Inspektionsprojekte. Jede Projektkarte enthält die Projektnummer, den Auftraggeber, das Inspektionsdatum sowie technische Daten (DN, Material, Inspektionslänge).'),
    screenshotPlaceholder('Projektliste mit FAB-Button'),
    para('Über den runden Plus-Button (FAB) unten rechts wird ein neues Projekt angelegt. Durch Tippen auf eine Projektkarte öffnet sich die Projektdetail-Ansicht.'),

    heading2('5.2  Neues Projekt anlegen'),
    para('Das Projektformular ist in vier Abschnitte gegliedert:'),

    heading3('Abschnitt 1: Allgemeine Angaben'),
    bullet('Auftraggeber – Name des Auftraggebers'),
    bullet('Standort/Adresse – Ort der Inspektion'),
    bullet('Inspektionsdatum – manuelle Eingabe oder Kalender-Auswahl'),
    bullet('Inspektor – Name des durchführenden Inspektors'),
    bullet('Wetter – Auswahl aus Vorlagen oder Freitext, mit GPS-Wetterabfrage'),
    screenshotPlaceholder('Projektformular – Allgemeine Angaben'),

    heading3('Abschnitt 2: Leitungsdaten'),
    bullet('Leitungstyp – Dropdown (Mischwasser, Schmutzwasser, Regenwasser, Sonstige)'),
    bullet('Material – Dropdown (PVC, Beton, Steinzeug, Gusseisen, Unbekannt)'),
    bullet('Durchmesser (DN) – Nennweite in mm'),
    bullet('Inspektionslänge – Gesamtlänge in Metern'),
    bullet('Startpunkt / Endpunkt – Bezeichnung der Endpunkte'),

    heading3('Abschnitt 3: Inspektionsmethode'),
    bullet('Inspektionssystem – Automatisch "NSP3CT ONE" (nicht editierbar)'),
    bullet('Kameratyp – Auswahl C10 oder C13'),
    bullet('Inspektionsform – Checkboxen: Visuell, Videoaufnahme, Fotoaufnahme'),

    heading3('Abschnitt 4: Video-Einstellungen'),
    bullet('Video-Qualität – SD oder HD (nur bei Neuanlage wählbar)'),
    bullet('Video-Overlay – Projektdaten als Overlay im Video einblenden (nur bei Neuanlage wählbar)'),
    spacer(),
    infoBox('Gesperrte Einstellungen', 'Video-Qualität und -Overlay können nach dem Anlegen eines Projekts nicht mehr geändert werden, um die Konsistenz der Aufnahmen sicherzustellen.'),
    tipBox('Über das GPS-Symbol neben dem Wetter-Feld kann das aktuelle Wetter automatisch per Standort abgerufen werden.'),

    heading2('5.3  Projektdetails'),
    para('Die Projektdetail-Ansicht zeigt alle Informationen und Medien eines Projekts. Die obere Leiste enthält folgende Aktionen:'),
    bullet('Zurück-Pfeil – Zurück zur Projektliste'),
    bullet('Bearbeiten (Stift) – Projektformular zum Bearbeiten öffnen'),
    bullet('Inspektion (Kamera, grün) – Direkt zur Inspektion mit diesem Projekt wechseln'),
    bullet('PDF-Export – Inspektionsbericht als PDF generieren'),
    bullet('ZIP-Export – Alle Projektdaten als ZIP-Archiv exportieren'),
    para('Unterhalb der Kopfzeile wird eine Zusammenfassung des Projekts mit Info-Chips angezeigt (Datum, Inspektor, Material, DN, Länge, Adresse, Videoqualität, OSD-Status).'),

    heading3('Tab-Bereiche'),
    para('Die Projektdaten sind in vier Tabs organisiert:'),
    numberedItem(1, 'Fotos – Galerie-Ansicht aller aufgenommenen Fotos als Rasterlayout. Tippen öffnet die Vollbildansicht. Zeigt Original und annotierte Bilder nebeneinander.'),
    numberedItem(2, 'Schäden – Chronologische Liste aller erfassten Schäden mit Thumbnail, Schadenstyp, Meterposition, Beschreibung und Zeitstempel.'),
    numberedItem(3, 'Videos – Liste der aufgenommenen Videos mit Dateiname, Größe und Datum. Tippen startet die Wiedergabe.'),
    numberedItem(4, 'Notizen – Liste aller Text- und Sprachnotizen mit Position, Text und Audio-Wiedergabe.'),
    screenshotPlaceholder('Projektdetails mit Tab-Ansicht'),
    para('Jeder Tab zeigt die Anzahl der enthaltenen Elemente als Badge neben dem Tab-Titel an.'),
);

// ═══════════════════════════════════════════════════════════════
// CHAPTER 6: INSPEKTION
// ═══════════════════════════════════════════════════════════════

children.push(
    heading1('6  Inspektion (Hauptarbeitsbereich)'),
    para('Der Inspektions-Bildschirm ist der zentrale Arbeitsbereich der App. Er ist im Landscape-Modus für optimale Nutzung auf dem Tablet ausgelegt und besteht aus drei Bereichen:'),
    bullet('Links (70%): Live-Video mit Overlay'),
    bullet('Rechts (30%): Hardware-Status und Schäden-/Notizliste'),
    bullet('Unten: Aktionsleiste mit Schnellzugriff-Buttons'),
    screenshotPlaceholder('Inspektions-Bildschirm (Landscape)'),

    heading2('6.1  Video-Ansicht'),
    para('Der Videobereich zeigt den Live-RTSP-Stream der Inspektionskamera. Am unteren Rand wird ein Overlay mit folgenden Informationen eingeblendet:'),
    bullet('Aktuelle Meterposition (z.B. "12.45m")'),
    bullet('Sonde-Frequenz (wenn aktiv)'),
    bullet('Aktuelle Uhrzeit'),
    bullet('Aufnahmestatus und -dauer (wenn Aufnahme läuft)'),

    heading3('Gesten im Videobereich'),
    bullet('Doppeltipp auf Video: Vollbildmodus ein/aus'),
    bullet('Pinch-to-Zoom: Video vergrößern (bis 3x)'),
    bullet('Ziehen: Verschieben des gezoomten Videos'),
    tipBox('Im Vollbildmodus wird die rechte Statusleiste und die untere Aktionsleiste ausgeblendet. Erneutes Doppeltippen kehrt zum normalen Modus zurück.'),

    heading2('6.2  Hardware-Statusleiste'),
    para('Die rechte Spalte zeigt den aktuellen Hardware-Status des ONE-Systems:'),
    bullet('Licht – Status und Steuerung der Lichtstufe'),
    bullet('Sonde – Status und Frequenz-Steuerung'),
    bullet('Meter (Absolut) – Kabellänge mit Reset-Button'),
    bullet('Meter (Strecke) – Relative Distanz mit Reset-Button'),
    bullet('Batterie – Akkustand des Controllers'),
    para('Darunter werden die letzten 5 erfassten Schäden und die letzten 3 Notizen angezeigt. Am unteren Rand erscheint die Projektkarte mit den wichtigsten Projektdaten.'),
    infoBox('Schäden bearbeiten', 'Durch Doppeltipp auf einen Schaden in der Liste öffnet sich der Schadens-Dialog zum Bearbeiten. Gleiches gilt für Notizen.'),

    heading2('6.3  Aktionsleiste'),
    para('Die Aktionsleiste am unteren Rand des Inspektions-Bildschirms enthält vier Schnellzugriff-Buttons:'),
    spacer(),
    makeTable(
        ['Button', 'Funktion', 'Beschreibung'],
        [
            ['📷 Foto', 'Schnellfoto', 'Erstellt sofort ein Foto vom aktuellen Videobild und speichert es als Schadensdokumentation'],
            ['⚠️ Schaden', 'Schaden erfassen', 'Öffnet den Schadens-Dialog mit automatischem Screenshot'],
            ['📝 Notiz', 'Notiz erstellen', 'Öffnet den Notiz-Dialog für Text- und Sprachnotizen'],
            ['🔴 Aufnahme', 'Video aufnehmen', 'Startet/Stoppt die Videoaufnahme des RTSP-Streams'],
        ]
    ),
    spacer(),
    infoBox('Kein Projekt gewählt', 'Die Aktionsbuttons sind nur aktiv, wenn ein Projekt zugewiesen ist. Ohne Projekt wird beim Doppeltipp auf "Kein Projekt" zur Projektliste navigiert.'),

    heading2('6.4  Schaden erfassen'),
    para('Der Schadens-Dialog öffnet sich über den "Schaden"-Button. Er enthält:'),
    screenshotPlaceholder('Schadens-Dialog'),
    numberedItem(1, 'Foto-Vorschau – Zeigt den automatisch aufgenommenen Screenshot. Durch Doppeltipp öffnet sich der Annotations-Editor zum Einzeichnen von Markierungen.'),
    numberedItem(2, 'Position (m) – Aktuelle Meterposition, editierbar'),
    numberedItem(3, 'Schadenstyp – Dropdown mit konfigurierbaren Vorlagen (z.B. "Riss längs", "Wurzeleinwuchs")'),
    numberedItem(4, 'Beschreibung – Optionales Freitextfeld für zusätzliche Informationen'),
    para('Nach dem Speichern erscheint der Schaden sofort in der Schäden-Liste auf dem Inspektions-Bildschirm.'),

    heading3('Bild-Annotation'),
    para('Durch Doppeltipp auf das Foto im Schadens-Dialog öffnet sich der Annotations-Editor. Hier können mit dem Finger Markierungen auf dem Bild eingezeichnet werden. Es stehen verschiedene Farben (Rot, Gelb, Grün, Blau, Weiß) und Strichstärken zur Verfügung. Das annotierte Bild wird separat gespeichert – das Original bleibt erhalten.'),

    heading2('6.5  Notiz erstellen'),
    para('Der Notiz-Dialog ermöglicht die Erstellung von Text- und Sprachnotizen:'),
    screenshotPlaceholder('Notiz-Dialog mit Sprachaufnahme'),
    bullet('Position (m) – Aktuelle Meterposition'),
    bullet('Textfeld – Freitext für schriftliche Notizen'),
    bullet('Sprachaufnahme – Aufnahme-Button für Audio-Notizen (M4A-Format, 44.1kHz, 128kbps)'),
    bullet('Wiedergabe – Bereits aufgenommene Sprachnotizen können direkt abgespielt werden'),
    infoBox('Mikrofon-Berechtigung', 'Für Sprachnotizen wird beim ersten Mal die Mikrofon-Berechtigung angefragt. Diese muss gewährt werden, um Audio aufnehmen zu können.'),

    heading2('6.6  Videoaufnahme'),
    para('Über den Aufnahme-Button in der Aktionsleiste kann der RTSP-Videostream direkt aufgezeichnet werden. Beim Start erscheint ein Dialog mit zwei Optionen:'),
    numberedItem(1, 'Mit Overlay – Projektinformationen werden in das Video eingeblendet'),
    numberedItem(2, 'Ohne Overlay – Reines Videobild ohne Einblendungen'),
    para('Während der Aufnahme wird in der Aktionsleiste die Aufnahmedauer angezeigt und der Button wechselt zu "Stopp". Das Overlay im Videobereich zeigt zusätzlich "REC" und die Dauer an.'),
    para('Die Aufnahmen werden im TS-Format (Transport Stream) im projektbezogenen Verzeichnis gespeichert.'),
    tipBox('Während einer laufenden Aufnahme und geöffnetem Schadens-Dialog wird die Aufnahme automatisch pausiert und beim Schließen wieder fortgesetzt.'),
);

// ═══════════════════════════════════════════════════════════════
// CHAPTER 7: EXPORT & BERICHTE
// ═══════════════════════════════════════════════════════════════

children.push(
    heading1('7  Export & Berichte'),

    heading2('7.1  PDF-Export'),
    para('Der PDF-Export erstellt einen professionellen Inspektionsbericht. Er wird über das PDF-Symbol in der Projektdetail-Ansicht gestartet. Ein Fortschrittsbalken zeigt den Export-Status.'),
    para('Der erzeugte PDF-Bericht enthält:'),
    bullet('Deckblatt mit Projektdaten, Firmendaten und Logo'),
    bullet('Leitungsdaten und Inspektionsmethode'),
    bullet('Alle Schäden mit Fotos, Annotationen und Beschreibungen'),
    bullet('Notizen'),
    para('Nach Abschluss des Exports erscheint ein Dialog mit zwei Optionen:'),
    numberedItem(1, 'Teilen – Öffnet den Android-Teilen-Dialog (E-Mail, Cloud, Bluetooth, etc.)'),
    numberedItem(2, 'Speichern unter – Öffnet den Datei-Explorer zum Speichern auf USB, SD-Karte, etc.'),

    heading2('7.2  ZIP-Export'),
    para('Der ZIP-Export bündelt alle Projektdaten in ein komprimiertes Archiv:'),
    bullet('PDF-Bericht'),
    bullet('Alle Originalfotos und annotierte Bilder'),
    bullet('Alle Videoaufnahmen'),
    bullet('Alle Sprachnotizen'),
    para('Der ZIP-Export wird über das Archiv-Symbol in der Projektdetail-Ansicht gestartet und bietet ebenfalls die Optionen "Teilen" und "Speichern unter".'),
);

// ═══════════════════════════════════════════════════════════════
// CHAPTER 8: EINSTELLUNGEN
// ═══════════════════════════════════════════════════════════════

children.push(
    heading1('8  Einstellungen'),
    para('Die Einstellungen sind über das Zahnrad-Symbol in der unteren Navigation erreichbar. Änderungen werden über das Speichern-Symbol (Diskette) in der oberen Leiste gesichert.'),
    screenshotPlaceholder('Einstellungs-Bildschirm'),

    heading2('8.1  Sprache'),
    para('Die ONE.APP unterstützt 35 Sprachen. Die Sprachauswahl erfolgt über ein Dropdown-Menü mit Länderflaggen. Die gewählte Sprache wird sofort angewendet und dauerhaft gespeichert.'),
    para('Verfügbare Sprachen (Auszug): Deutsch, Englisch, Norwegisch, Italienisch, Niederländisch, Französisch, Spanisch, Portugiesisch, Polnisch, Tschechisch, und viele mehr.'),
    tipBox('Die Standardsprache ist Deutsch. Fehlende Übersetzungen werden automatisch in Deutsch angezeigt.'),

    heading2('8.2  NSP3CT-Verbindung'),
    para('Konfiguration der NSP3CT-Verbindungsparameter:'),
    bullet('Broker IP – IP-Adresse des MQTT-Brokers'),
    bullet('Broker Port – Port des MQTT-Brokers'),
    bullet('RTSP URL – URL des Video-Streams'),
    para('Über den Button "Verbindung testen" kann die Erreichbarkeit geprüft werden.'),

    heading2('8.3  Firmendaten & Logo'),
    para('Firmendaten, die in den PDF-Berichten verwendet werden:'),
    bullet('Firmenname'),
    bullet('Firmenadresse'),
    bullet('Firmenlogo – Bild aus der Galerie wählen, ändern oder entfernen'),
    screenshotPlaceholder('Firmendaten mit Logo-Upload'),

    heading2('8.4  Wetter-Vorlagen'),
    para('Konfigurierbare Liste von Wetter-Vorlagen, die im Projektformular als Schnellauswahl zur Verfügung stehen. Die Sektion ist einklappbar.'),
    bullet('Vorhandene Vorlagen bearbeiten (Stift-Symbol)'),
    bullet('Vorlagen löschen (Mülleimer-Symbol)'),
    bullet('Neue Vorlagen hinzufügen'),
    bullet('Auf Standard zurücksetzen'),

    heading2('8.5  Schadens-Vorlagen'),
    para('Analog zu den Wetter-Vorlagen können hier die Schadenstypen konfiguriert werden, die im Schadens-Dialog als Dropdown zur Verfügung stehen.'),
    para('Die Standard-Vorlagen orientieren sich an DIN EN 13508-2 und können an die individuellen Anforderungen angepasst werden.'),

    heading3('Weitere Einstellungen'),
    para('Über die Karte "ONE Verbindung" gelangt man direkt zum Verbindungs-Bildschirm (Connection) für die detaillierte Hardware-Einrichtung.'),
    para('Am unteren Ende der Einstellungen wird die App-Information angezeigt:'),
    bullet('App-Name: ONE.APP – NSP3CT Slave Monitor'),
    bullet('Version: 1.0'),
    bullet('Copyright-Information'),
);

// ═══════════════════════════════════════════════════════════════
// CHAPTER 9: GESTEN & SHORTCUTS
// ═══════════════════════════════════════════════════════════════

children.push(
    heading1('9  Gesten & Shortcuts'),
    para('Die ONE.APP nutzt verschiedene Gesten für effiziente Bedienung:'),
    spacer(),
    makeTable(
        ['Geste', 'Bereich', 'Aktion'],
        [
            ['Doppeltipp', 'Video (Inspektion)', 'Vollbildmodus ein/aus'],
            ['Pinch-to-Zoom', 'Video (Inspektion)', 'Video vergrößern (1x–3x)'],
            ['Ziehen', 'Video (gezoomt)', 'Gezoomtes Video verschieben'],
            ['Doppeltipp', 'Schaden in Liste', 'Schaden zum Bearbeiten öffnen'],
            ['Doppeltipp', 'Notiz in Liste', 'Notiz zum Bearbeiten öffnen'],
            ['Doppeltipp', 'Foto im Dialog', 'Annotations-Editor öffnen'],
            ['Doppeltipp', '"Kein Projekt"', 'Zur Projektliste navigieren'],
            ['Tippen', 'Projektkarte (Insp.)', 'Projektformular öffnen'],
        ]
    ),
);

// ═══════════════════════════════════════════════════════════════
// CHAPTER 10: FEHLERBEHEBUNG
// ═══════════════════════════════════════════════════════════════

children.push(
    heading1('10  Fehlerbehebung'),

    heading2('Kein Videostream'),
    bullet('Prüfen Sie die WLAN-Verbindung zum ONE-Netzwerk (ONE_01)'),
    bullet('Stellen Sie sicher, dass der ONE-Controller eingeschaltet ist'),
    bullet('Versuchen Sie einen Netzwerk-Scan auf dem Verbindungs-Bildschirm'),
    bullet('Prüfen Sie die RTSP-URL in den Einstellungen (Standard: rtsp://<IP>:8554/1234)'),

    heading2('Hardware-Status zeigt "Keine Daten"'),
    bullet('Führen Sie eine Hardware-Suche auf dem Verbindungs-Bildschirm durch'),
    bullet('Prüfen Sie, ob das Tablet im gleichen WLAN wie der Controller ist'),
    bullet('Controller neustarten und erneut verbinden'),

    heading2('PDF-Export fehlgeschlagen'),
    bullet('Prüfen Sie den verfügbaren Speicherplatz auf dem Tablet'),
    bullet('Stellen Sie sicher, dass Firmendaten in den Einstellungen hinterlegt sind'),
    bullet('Versuchen Sie den Export erneut'),

    heading2('Sprachnotiz kann nicht aufgenommen werden'),
    bullet('Stellen Sie sicher, dass die Mikrofon-Berechtigung erteilt wurde'),
    bullet('Prüfen Sie in den Android-Einstellungen unter "App-Berechtigungen"'),

    heading2('App reagiert nicht'),
    bullet('App über den Android Task-Manager schließen und neu starten'),
    bullet('Bei anhaltenden Problemen: App-Daten in den Android-Einstellungen löschen (Achtung: Projektdaten gehen verloren!)'),
    spacer(),
    spacer(),

    // Contact box
    new Table({
        rows: [
            new TableRow({
                children: [
                    new TableCell({
                        width: { size: 100, type: WidthType.AUTO },
                        children: [
                            new Paragraph({
                                children: [new TextRun({ text: 'Support & Kontakt', font: 'Calibri', size: 26, bold: true, color: YELLOW })],
                                spacing: { before: 120, after: 60 },
                                indent: { left: 200 }
                            }),
                            new Paragraph({
                                children: [new TextRun({ text: 'UIP Umwelt- und Ingenieurtechnik GmbH', font: 'Calibri', size: 22, color: WHITE })],
                                spacing: { after: 40 },
                                indent: { left: 200 }
                            }),
                            new Paragraph({
                                children: [new TextRun({ text: 'NSP3CT Kanalinspektionssystem – ONE.APP', font: 'Calibri', size: 22, color: WHITE })],
                                spacing: { after: 120 },
                                indent: { left: 200 }
                            }),
                        ],
                        shading: { type: ShadingType.CLEAR, fill: DARK_BG },
                        borders: {
                            top: { style: BorderStyle.NONE },
                            bottom: { style: BorderStyle.NONE },
                            left: { style: BorderStyle.NONE },
                            right: { style: BorderStyle.NONE }
                        }
                    })
                ]
            })
        ],
        width: { size: 100, type: WidthType.PERCENTAGE }
    })
);

// ═══════════════════════════════════════════════════════════════
// CREATE DOCUMENT
// ═══════════════════════════════════════════════════════════════

const doc = new Document({
    title: 'ONE.APP Bedienungsanleitung',
    creator: 'UIP / NSP3CT',
    description: 'Bedienungsanleitung für die ONE.APP - NSP3CT Slave Monitor',
    styles: {
        default: {
            document: {
                run: { font: 'Calibri', size: 22 }
            },
            heading1: {
                run: { font: 'Calibri', size: 44, bold: true, color: DARK_BG }
            },
            heading2: {
                run: { font: 'Calibri', size: 32, bold: true, color: YELLOW_DARK }
            },
            heading3: {
                run: { font: 'Calibri', size: 26, color: DARK_BG }
            },
            listParagraph: {
                run: { font: 'Calibri', size: 22 }
            }
        }
    },
    numbering: {
        config: []
    },
    sections: [{
        properties: {
            page: {
                margin: {
                    top: convertInchesToTwip(0.8),
                    bottom: convertInchesToTwip(0.8),
                    left: convertInchesToTwip(1),
                    right: convertInchesToTwip(1)
                }
            }
        },
        headers: {
            default: new Header({
                children: [
                    new Paragraph({
                        children: [
                            new TextRun({ text: 'ONE.APP Bedienungsanleitung', font: 'Calibri', size: 16, color: MEDIUM_GRAY })
                        ],
                        alignment: AlignmentType.RIGHT,
                        border: {
                            bottom: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDD', space: 4 }
                        }
                    })
                ]
            })
        },
        footers: {
            default: new Footer({
                children: [
                    new Paragraph({
                        children: [
                            new TextRun({ text: 'UIP / NSP3CT  |  ', font: 'Calibri', size: 16, color: MEDIUM_GRAY }),
                            new TextRun({ text: 'Seite ', font: 'Calibri', size: 16, color: MEDIUM_GRAY }),
                            new TextRun({ children: [PageNumber.CURRENT], font: 'Calibri', size: 16, color: MEDIUM_GRAY }),
                            new TextRun({ text: ' von ', font: 'Calibri', size: 16, color: MEDIUM_GRAY }),
                            new TextRun({ children: [PageNumber.TOTAL_PAGES], font: 'Calibri', size: 16, color: MEDIUM_GRAY }),
                        ],
                        alignment: AlignmentType.CENTER,
                        border: {
                            top: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDD', space: 4 }
                        }
                    })
                ]
            })
        },
        children: children
    }]
});

// ═══════════════════════════════════════════════════════════════
// SAVE
// ═══════════════════════════════════════════════════════════════

const OUTPUT = path.join(__dirname, 'ONE_APP_Bedienungsanleitung.docx');

Packer.toBuffer(doc).then(buffer => {
    fs.writeFileSync(OUTPUT, buffer);
    console.log(`Word-Dokument erstellt: ${OUTPUT}`);
    console.log(`Größe: ${(buffer.length / 1024).toFixed(0)} KB`);
}).catch(err => {
    console.error('Fehler:', err);
});
