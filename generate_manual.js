const PDFDocument = require('pdfkit');
const fs = require('fs');
const path = require('path');

// ─── CONFIG ───────────────────────────────────────────────────
const FONT_DIR = path.join(__dirname, 'app/src/main/res/font');
const OUTPUT = path.join(__dirname, 'ONE_APP_Bedienungsanleitung.pdf');

const YELLOW = '#FFCE00';
const YELLOW_DARK = '#CB9700';
const DARK_BG = '#1A1A2E';
const TEXT_DARK = '#1A1A2E';
const TEXT_GRAY = '#555555';
const TEXT_LIGHT = '#888888';
const WHITE = '#FFFFFF';
const LIGHT_BG = '#F5F5F5';
const BORDER_GRAY = '#DDDDDD';

// Page dimensions
const PAGE_W = 595.28; // A4
const PAGE_H = 841.89;
const MARGIN = 50;
const CONTENT_W = PAGE_W - 2 * MARGIN;

const doc = new PDFDocument({
    size: 'A4',
    margins: { top: 50, bottom: 50, left: 50, right: 50 },
    info: {
        Title: 'ONE.APP Bedienungsanleitung',
        Author: 'UIP / NSP3CT',
        Subject: 'Bedienungsanleitung ONE.APP Slave Monitor',
        Creator: 'ONE.APP Manual Generator'
    }
});

const stream = fs.createWriteStream(OUTPUT);
doc.pipe(stream);

// Register fonts
doc.registerFont('Barlow', path.join(FONT_DIR, 'barlow_regular.ttf'));
doc.registerFont('Barlow-Light', path.join(FONT_DIR, 'barlow_light.ttf'));
doc.registerFont('Barlow-Medium', path.join(FONT_DIR, 'barlow_medium.ttf'));
doc.registerFont('Barlow-SemiBold', path.join(FONT_DIR, 'barlow_semibold.ttf'));
doc.registerFont('Barlow-Bold', path.join(FONT_DIR, 'barlow_bold.ttf'));
doc.registerFont('Barlow-Black', path.join(FONT_DIR, 'barlow_black.ttf'));

let pageNum = 0;

// ─── HELPER FUNCTIONS ─────────────────────────────────────────

function addPageFooter() {
    pageNum++;
    const savedY = doc.y;
    const savedX = doc.x;
    // Footer line
    doc.moveTo(MARGIN, PAGE_H - 35).lineTo(PAGE_W - MARGIN, PAGE_H - 35).strokeColor(BORDER_GRAY).lineWidth(0.5).stroke();
    // Footer text
    doc.font('Barlow-Light').fontSize(8).fillColor(TEXT_LIGHT);
    doc.text('ONE.APP Bedienungsanleitung', MARGIN, PAGE_H - 28, { width: CONTENT_W / 2, align: 'left', lineBreak: false });
    doc.text(`Seite ${pageNum}`, MARGIN + CONTENT_W / 2, PAGE_H - 28, { width: CONTENT_W / 2, align: 'right', lineBreak: false });
    // Restore cursor
    doc.x = savedX;
    doc.y = savedY;
}

function newPage() {
    doc.addPage();
    doc.x = MARGIN;
    doc.y = MARGIN;
    addPageFooter();
}

function checkSpace(needed) {
    if (doc.y + needed > PAGE_H - 70) {
        newPage();
        return true;
    }
    return false;
}

function heading1(text) {
    checkSpace(60);
    doc.moveDown(0.5);
    const yPos = doc.y;
    // Yellow bar
    doc.rect(MARGIN - 5, yPos - 3, 5, 28).fill(YELLOW);
    doc.font('Barlow-Bold').fontSize(22).fillColor(TEXT_DARK);
    doc.text(text, MARGIN + 5, yPos, { width: CONTENT_W - 5 });
    doc.moveDown(0.3);
    // Underline
    const lineY = doc.y;
    doc.moveTo(MARGIN, lineY).lineTo(PAGE_W - MARGIN, lineY).strokeColor(YELLOW).lineWidth(1.5).stroke();
    doc.x = MARGIN;
    doc.moveDown(0.5);
}

function heading2(text) {
    checkSpace(45);
    doc.moveDown(0.4);
    doc.font('Barlow-SemiBold').fontSize(16).fillColor(YELLOW_DARK);
    doc.text(text, MARGIN, doc.y, { width: CONTENT_W });
    doc.x = MARGIN;
    doc.moveDown(0.2);
}

function heading3(text) {
    checkSpace(35);
    doc.moveDown(0.3);
    doc.font('Barlow-Medium').fontSize(13).fillColor(TEXT_DARK);
    doc.text(text, MARGIN, doc.y, { width: CONTENT_W });
    doc.x = MARGIN;
    doc.moveDown(0.15);
}

function para(text) {
    checkSpace(30);
    doc.font('Barlow').fontSize(10.5).fillColor(TEXT_DARK);
    doc.text(text, MARGIN, doc.y, { width: CONTENT_W, lineGap: 3 });
    doc.x = MARGIN;
    doc.moveDown(0.3);
}

function paraLight(text) {
    checkSpace(25);
    doc.font('Barlow-Light').fontSize(10).fillColor(TEXT_GRAY);
    doc.text(text, MARGIN, doc.y, { width: CONTENT_W, lineGap: 2.5 });
    doc.x = MARGIN;
    doc.moveDown(0.2);
}

function bullet(text) {
    checkSpace(25);
    doc.font('Barlow').fontSize(10.5).fillColor(YELLOW_DARK);
    doc.text('  \u2022  ', MARGIN, doc.y, { continued: true, width: CONTENT_W });
    doc.fillColor(TEXT_DARK);
    doc.text(text, { lineGap: 2, width: CONTENT_W - 20 });
    doc.x = MARGIN;
    doc.moveDown(0.1);
}

function numberedItem(num, text) {
    checkSpace(25);
    doc.font('Barlow-SemiBold').fontSize(10.5).fillColor(YELLOW_DARK);
    doc.text(`  ${num}.  `, MARGIN, doc.y, { continued: true, width: CONTENT_W });
    doc.font('Barlow').fillColor(TEXT_DARK);
    doc.text(text, { lineGap: 2, width: CONTENT_W - 25 });
    doc.x = MARGIN;
    doc.moveDown(0.1);
}

function screenshotPlaceholder(label, width, height) {
    checkSpace(height + 40);
    const x = MARGIN + (CONTENT_W - width) / 2;
    const y = doc.y + 5;

    // Dashed border
    doc.rect(x, y, width, height).dash(5, { space: 5 }).strokeColor(BORDER_GRAY).lineWidth(1).stroke();
    doc.undash();

    // Background
    doc.rect(x + 1, y + 1, width - 2, height - 2).fill(LIGHT_BG);

    // Camera icon placeholder (simple text)
    doc.font('Barlow-Light').fontSize(12).fillColor(TEXT_LIGHT);
    doc.text('[Screenshot]', x, y + height / 2 - 20, { width: width, align: 'center', lineBreak: false });
    doc.font('Barlow').fontSize(10).fillColor(TEXT_GRAY);
    doc.text(label, x, y + height / 2, { width: width, align: 'center', lineBreak: false });

    doc.x = MARGIN;
    doc.y = y + height + 10;
    doc.moveDown(0.3);
}

function infoBox(title, text) {
    checkSpace(60);
    const boxY = doc.y;
    const boxX = MARGIN;

    doc.roundedRect(boxX, boxY, CONTENT_W, 50, 4).fill('#FFF8E1');
    doc.roundedRect(boxX, boxY, 4, 50, 2).fill(YELLOW);

    doc.font('Barlow-SemiBold').fontSize(10).fillColor(YELLOW_DARK);
    doc.text(title, boxX + 14, boxY + 8, { width: CONTENT_W - 24, lineBreak: false });
    doc.font('Barlow').fontSize(9.5).fillColor(TEXT_DARK);
    doc.text(text, boxX + 14, boxY + 24, { width: CONTENT_W - 24 });

    doc.x = MARGIN;
    doc.y = boxY + 58;
    doc.moveDown(0.3);
}

function tipBox(text) {
    checkSpace(50);
    const boxY = doc.y;

    doc.roundedRect(MARGIN, boxY, CONTENT_W, 40, 4).fill('#E8F5E9');
    doc.roundedRect(MARGIN, boxY, 4, 40, 2).fill('#4CAF50');

    doc.font('Barlow-SemiBold').fontSize(9.5).fillColor('#2E7D32');
    doc.text('Tipp: ', MARGIN + 14, boxY + 12, { continued: true, width: CONTENT_W - 24 });
    doc.font('Barlow').fillColor('#1B5E20');
    doc.text(text, { width: CONTENT_W - 28 });

    doc.x = MARGIN;
    doc.y = boxY + 48;
    doc.moveDown(0.3);
}

let tableRowIndex = 0;
function tableRow(cells, isHeader) {
    const colWidths = cells.map(() => CONTENT_W / cells.length);
    const rowH = 22;

    if (checkSpace(rowH + 5)) {
        tableRowIndex = 0;
    }

    const y = doc.y;

    if (isHeader) {
        doc.rect(MARGIN, y, CONTENT_W, rowH).fill(YELLOW);
        tableRowIndex = 0;
    } else {
        doc.rect(MARGIN, y, CONTENT_W, rowH).fill(tableRowIndex % 2 === 0 ? WHITE : LIGHT_BG);
        tableRowIndex++;
    }
    doc.rect(MARGIN, y, CONTENT_W, rowH).strokeColor(BORDER_GRAY).lineWidth(0.5).stroke();

    let x = MARGIN;
    cells.forEach((cell, i) => {
        doc.font(isHeader ? 'Barlow-SemiBold' : 'Barlow')
           .fontSize(9)
           .fillColor(TEXT_DARK);
        doc.text(cell, x + 6, y + 6, { width: colWidths[i] - 12, align: 'left', lineBreak: false });
        x += colWidths[i];
    });

    doc.x = MARGIN;
    doc.y = y + rowH;
}

// ═══════════════════════════════════════════════════════════════
// COVER PAGE
// ═══════════════════════════════════════════════════════════════

// Full yellow header block
doc.rect(0, 0, PAGE_W, 280).fill(YELLOW);

// Title
doc.font('Barlow-Black').fontSize(42).fillColor(TEXT_DARK);
doc.text('ONE.APP', MARGIN, 80, { width: CONTENT_W, align: 'center', lineBreak: false });

doc.font('Barlow-Light').fontSize(18).fillColor(TEXT_DARK);
doc.text('Bedienungsanleitung', MARGIN, 135, { width: CONTENT_W, align: 'center', lineBreak: false });

doc.font('Barlow').fontSize(12).fillColor(TEXT_DARK);
doc.text('NSP3CT Slave Monitor', MARGIN, 170, { width: CONTENT_W, align: 'center', lineBreak: false });

// Version info
doc.font('Barlow-Medium').fontSize(11).fillColor(TEXT_DARK);
doc.text('Version 1.0', MARGIN, 210, { width: CONTENT_W, align: 'center', lineBreak: false });
doc.font('Barlow-Light').fontSize(10);
doc.text('Stand: Februar 2026', MARGIN, 230, { width: CONTENT_W, align: 'center', lineBreak: false });

// Dark bottom section
doc.rect(0, PAGE_H - 120, PAGE_W, 120).fill(DARK_BG);
doc.font('Barlow-Medium').fontSize(12).fillColor(YELLOW);
doc.text('UIP Umwelt- und Ingenieurtechnik', MARGIN, PAGE_H - 95, { width: CONTENT_W, align: 'center', lineBreak: false });
doc.font('Barlow-Light').fontSize(10).fillColor('#AAAAAA');
doc.text('Kanalinspektionssystem ONE', MARGIN, PAGE_H - 75, { width: CONTENT_W, align: 'center', lineBreak: false });

// Middle content area
doc.font('Barlow').fontSize(11).fillColor(TEXT_DARK);
doc.text('Diese Bedienungsanleitung beschreibt die Funktionen und Bedienung der ONE.APP,', MARGIN, 340, { width: CONTENT_W, align: 'center', lineBreak: false });
doc.text('der Android-Tablet-Anwendung zum NSP3CT Kanalinspektionssystem.', MARGIN, 358, { width: CONTENT_W, align: 'center', lineBreak: false });

// Feature list on cover
doc.font('Barlow-SemiBold').fontSize(12).fillColor(YELLOW_DARK);
doc.text('Hauptfunktionen:', MARGIN + 80, 420, { lineBreak: false });

doc.x = MARGIN + 80;
doc.y = 445;

const features = [
    'Live-Videostream der Inspektionskamera (RTSP)',
    'Schadensdokumentation mit Foto und Annotation',
    'Sprachnotizen und Textnotizen',
    'Videoaufnahme mit/ohne Overlay',
    'PDF- und ZIP-Export der Inspektionsberichte',
    'Hardware-Steuerung (Licht, Sonde, Meterzahler)',
    'Mehrsprachige Benutzeroberflache (35 Sprachen)',
    'Automatische Wetterabfrage per GPS'
];

features.forEach(f => {
    const fy = doc.y;
    doc.font('Barlow').fontSize(10.5).fillColor(YELLOW_DARK);
    doc.text('\u25B8  ', MARGIN + 84, fy, { continued: true, width: CONTENT_W - 84 });
    doc.fillColor(TEXT_DARK);
    doc.text(f);
    doc.x = MARGIN + 80;
});

pageNum = 0;

// ═══════════════════════════════════════════════════════════════
// TABLE OF CONTENTS
// ═══════════════════════════════════════════════════════════════
newPage();

doc.font('Barlow-Bold').fontSize(22).fillColor(TEXT_DARK);
doc.text('Inhaltsverzeichnis', MARGIN, 60, { width: CONTENT_W, lineBreak: false });
doc.x = MARGIN;
doc.y = 95;

const tocEntries = [
    ['1', 'Einleitung', '3'],
    ['1.1', 'Systemvoraussetzungen', '3'],
    ['1.2', 'App-Installation', '3'],
    ['2', 'Erste Schritte', '4'],
    ['2.1', 'App starten', '4'],
    ['2.2', 'Navigation', '4'],
    ['3', 'Startseite (Dashboard)', '5'],
    ['3.1', 'Verbindungsstatus', '5'],
    ['3.2', 'Schnellzugriff', '5'],
    ['3.3', 'Aktuelle Projekte', '5'],
    ['4', 'Verbindung (Connection)', '6'],
    ['4.1', 'WiFi-Status', '6'],
    ['4.2', 'Netzwerk-Scan', '6'],
    ['4.3', 'RTSP-Stream verbinden', '7'],
    ['4.4', 'Hardware-Status', '7'],
    ['5', 'Projekte verwalten', '8'],
    ['5.1', 'Projektliste', '8'],
    ['5.2', 'Neues Projekt anlegen', '8'],
    ['5.3', 'Projektdetails', '9'],
    ['6', 'Inspektion (Hauptarbeitsbereich)', '10'],
    ['6.1', 'Video-Ansicht', '10'],
    ['6.2', 'Hardware-Statusleiste', '11'],
    ['6.3', 'Aktionsleiste', '11'],
    ['6.4', 'Schaden erfassen', '12'],
    ['6.5', 'Notiz erstellen', '12'],
    ['6.6', 'Videoaufnahme', '13'],
    ['7', 'Export & Berichte', '14'],
    ['7.1', 'PDF-Export', '14'],
    ['7.2', 'ZIP-Export', '14'],
    ['8', 'Einstellungen', '15'],
    ['8.1', 'Sprache', '15'],
    ['8.2', 'NSP3CT-Verbindung', '15'],
    ['8.3', 'Firmendaten & Logo', '15'],
    ['8.4', 'Wetter-Vorlagen', '16'],
    ['8.5', 'Schadens-Vorlagen', '16'],
    ['9', 'Gesten & Shortcuts', '17'],
    ['10', 'Fehlerbehebung', '17'],
];

tocEntries.forEach(([num, title, page]) => {
    const isMain = !num.includes('.');
    const indent = isMain ? 0 : 20;
    const y = doc.y;

    doc.font(isMain ? 'Barlow-SemiBold' : 'Barlow').fontSize(isMain ? 11 : 10);
    doc.fillColor(isMain ? TEXT_DARK : TEXT_GRAY);
    doc.text(`${num}`, MARGIN + indent, y, { width: 30, lineBreak: false });
    doc.text(title, MARGIN + indent + 30, y, { width: CONTENT_W - indent - 70, lineBreak: false });
    doc.text(page, MARGIN + CONTENT_W - 30, y, { width: 30, align: 'right', lineBreak: false });

    doc.x = MARGIN;
    doc.y = y + (isMain ? 18 : 15);
});


// ═══════════════════════════════════════════════════════════════
// CHAPTER 1: EINLEITUNG
// ═══════════════════════════════════════════════════════════════
newPage();
heading1('1  Einleitung');

para('Die ONE.APP ist eine Android-Tablet-Anwendung, die als Slave-Monitor fur das NSP3CT Kanalinspektionssystem dient. Sie zeigt den Live-Videostream der Inspektionskamera an und ermoglicht die vollstandige Schadensdokumentation direkt auf dem Tablet.');

para('Die App ist speziell fur den Einsatz im Feld optimiert: grosse Schaltflachen, Dark-Theme fur gute Lesbarkeit bei wechselnden Lichtverhaltnissen und Landscape-Modus fur optimale Videoanzeige auf 10"-Tablets.');

heading2('1.1  Systemvoraussetzungen');

bullet('Samsung Galaxy Tab S9 FE+ (oder vergleichbar)');
bullet('Android 8.0 (API 26) oder hoher');
bullet('WLAN-Verbindung zum ONE-Controller (SSID: ONE_01)');
bullet('Mindestens 500 MB freier Speicherplatz');

heading2('1.2  App-Installation');

para('Die App wird als APK-Datei bereitgestellt und kann per USB oder Dateimanager auf dem Tablet installiert werden.');

numberedItem(1, 'APK-Datei auf das Tablet ubertragen (USB, E-Mail oder Cloud)');
numberedItem(2, '"Installation aus unbekannten Quellen" in den Android-Einstellungen aktivieren');
numberedItem(3, 'APK-Datei offnen und Installation bestatigen');
numberedItem(4, 'App starten und Einrichtung vornehmen');

infoBox('Hinweis', 'Bei der ersten Installation werden Berechtigungen fur Kamera, Mikrofon, Speicher und Standort abgefragt. Diese sind fur die volle Funktionalitat erforderlich.');

// ═══════════════════════════════════════════════════════════════
// CHAPTER 2: ERSTE SCHRITTE
// ═══════════════════════════════════════════════════════════════
newPage();
heading1('2  Erste Schritte');

heading2('2.1  App starten');

para('Nach dem Start der App erscheint zunachst ein kurzer Splash-Screen mit dem ONE.APP-Logo, anschliessend wird die Startseite (Dashboard) angezeigt.');

screenshotPlaceholder('Splash-Screen der ONE.APP', 300, 120);

heading2('2.2  Navigation');

para('Die ONE.APP verwendet eine untere Navigationsleiste (Bottom Navigation Bar) mit vier Hauptbereichen:');

doc.moveDown(0.3);

// Navigation table
tableRow(['Symbol', 'Bereich', 'Beschreibung'], true);
tableRow(['Startseite', 'Startseite', 'Dashboard mit Ubersicht und Schnellzugriff']);
tableRow(['Inspektion', 'Inspektion', 'Live-Video und Schadenserfassung']);
tableRow(['Projekte', 'Projekte', 'Projektliste und -verwaltung']);
tableRow(['Einstellungen', 'Einstellungen', 'App- und Verbindungseinstellungen']);

doc.moveDown(0.5);

para('Zusatzlich sind uber die Navigation folgende Bereiche erreichbar:');
bullet('Verbindung (Connection) - uber Einstellungen oder direkte Navigation');
bullet('Berichte - uber das Dashboard');
bullet('Projektdetails - durch Tippen auf ein Projekt');
bullet('Projektformular - uber "Neues Projekt" oder Bearbeitungs-Button');

screenshotPlaceholder('Untere Navigationsleiste', 400, 60);

tipBox('Die aktive Navigationsseite wird in der Leiste farblich hervorgehoben.');

// ═══════════════════════════════════════════════════════════════
// CHAPTER 3: STARTSEITE
// ═══════════════════════════════════════════════════════════════
newPage();
heading1('3  Startseite (Dashboard)');

para('Die Startseite bietet einen schnellen Uberblick uber den aktuellen Status und Zugang zu den wichtigsten Funktionen.');

screenshotPlaceholder('Dashboard / Startseite', 400, 200);

heading2('3.1  Verbindungsstatus');

para('Im oberen Bereich der Startseite wird der aktuelle Verbindungsstatus zum ONE-Controller angezeigt:');

bullet('Gruner Punkt: Verbindung hergestellt - zeigt die IP-Adresse des Controllers');
bullet('Roter Punkt: Keine Verbindung');
bullet('Bei aktiver Verbindung wird zusatzlich der Batteriestand angezeigt');

heading2('3.2  Schnellzugriff');

para('Drei Schnellzugriff-Karten bieten direkten Zugang zu den wichtigsten Funktionen:');

numberedItem(1, 'Neues Projekt - Offnet das Projektformular zum Erstellen eines neuen Inspektionsprojekts');
numberedItem(2, 'Inspektion - Wechselt direkt zum Inspektions-Bildschirm mit Live-Video');
numberedItem(3, 'Berichte - Offnet die Berichts-Ubersicht');

heading2('3.3  Aktuelle Projekte');

para('Unterhalb der Schnellzugriff-Karten werden die letzten 5 Projekte als Liste angezeigt. Jede Projektkarte enthalt:');

bullet('Projektnummer (in der Akzentfarbe)');
bullet('Auftraggeber');
bullet('Inspektionsdatum');
bullet('Durchmesser (DN)');

para('Durch Tippen auf ein Projekt wird die Projektdetail-Ansicht geoffnet. Uber den Button "Alle anzeigen" gelangt man zur vollstandigen Projektliste.');

infoBox('Leere Projektliste', 'Wenn noch keine Projekte angelegt sind, wird ein Hinweis mit Button zum Erstellen des ersten Projekts angezeigt.');

// ═══════════════════════════════════════════════════════════════
// CHAPTER 4: VERBINDUNG
// ═══════════════════════════════════════════════════════════════
newPage();
heading1('4  Verbindung (Connection)');

para('Der Verbindungs-Bildschirm dient zur Einrichtung und Uberwachung der Verbindung zum ONE-Inspektionssystem. Das Layout ist in zwei Spalten aufgeteilt: Links die Steuerung, rechts die Video-Vorschau.');

screenshotPlaceholder('Verbindungs-Bildschirm (2-Spalten-Layout)', 450, 220);

heading2('4.1  WiFi-Status');

para('Zeigt den aktuellen WLAN-Verbindungsstatus:');
bullet('SSID des verbundenen Netzwerks');
bullet('Lokale IP-Adresse');
bullet('Gateway-Adresse');
bullet('Erkennung des ONE-Netzwerks (SSID enthalt "ONE")');

infoBox('ONE-Netzwerk', 'Verbinden Sie das Tablet mit dem WLAN "ONE_01". Das Subnet ist 192.168.82.x. Die App erkennt das ONE-Netzwerk automatisch und zeigt einen grunen Hinweis.');

heading2('4.2  Netzwerk-Scan');

para('Uber den "Scannen"-Button wird ein automatischer Netzwerk-Scan im aktuellen Subnet durchgefuhrt:');

numberedItem(1, '"Scannen" antippen - der Fortschrittsbalken zeigt den Scan-Fortschritt');
numberedItem(2, 'Gefundene Gerate werden mit IP-Adresse, Hostname und offenen Ports aufgelistet');
numberedItem(3, 'Bei jedem gefundenen Gerat kann uber "RTSP" ein RTSP-Stream-Test gestartet werden');

heading2('4.3  RTSP-Stream verbinden');

heading3('Automatisch (uber Netzwerk-Scan)');
para('Nach einem erfolgreichen RTSP-Test erscheint das Ergebnis mit "Stream"-Button. Durch Antippen wird der Videostream gestartet und rechts in der Vorschau angezeigt.');

heading3('Manuell');
para('Im Abschnitt "Manuelle RTSP-URL" kann eine URL direkt eingegeben werden (z.B. rtsp://192.168.82.100:8554/1234). Uber "Test" wird die Erreichbarkeit gepruft, uber "Stream starten" wird die Verbindung hergestellt.');

tipBox('Die Standard-RTSP-URL des ONE-Systems lautet: rtsp://<IP>:8554/1234');

heading2('4.4  Hardware-Status');

para('Zeigt den aktuellen Status der ONE-Hardware an und ermoglicht die Steuerung:');

doc.moveDown(0.2);
tableRow(['Komponente', 'Anzeige', 'Steuerung'], true);
tableRow(['Licht', 'AN/AUS mit Leistung (%)', 'Lichtstufe umschalten (0/30/60/90%)']);
tableRow(['Sonde', 'AN/AUS mit Frequenz', 'Frequenz umschalten (0-3)']);
tableRow(['Meter (Absolut)', 'Aktuelle Position in m', 'Auf 0 zurucksetzen']);
tableRow(['Meter (Strecke)', 'Relative Distanz in m', 'Auf 0 zurucksetzen']);
tableRow(['Batterie', 'Ladestand in %', '(nur Anzeige)']);
doc.moveDown(0.5);

para('Am unteren Ende der linken Spalte wird ein Protokoll (Log) der letzten Verbindungsaktivitaten angezeigt.');

// ═══════════════════════════════════════════════════════════════
// CHAPTER 5: PROJEKTE
// ═══════════════════════════════════════════════════════════════
newPage();
heading1('5  Projekte verwalten');

heading2('5.1  Projektliste');

para('Die Projektliste zeigt alle angelegten Inspektionsprojekte. Jede Projektkarte enthalt die Projektnummer, den Auftraggeber, das Inspektionsdatum sowie technische Daten (DN, Material, Inspektionslange).');

screenshotPlaceholder('Projektliste mit FAB-Button', 400, 180);

para('Uber den runden Plus-Button (FAB) unten rechts wird ein neues Projekt angelegt. Durch Tippen auf eine Projektkarte offnet sich die Projektdetail-Ansicht.');

heading2('5.2  Neues Projekt anlegen');

para('Das Projektformular ist in vier Abschnitte gegliedert:');

heading3('Abschnitt 1: Allgemeine Angaben');
bullet('Auftraggeber - Name des Auftraggebers');
bullet('Standort/Adresse - Ort der Inspektion');
bullet('Inspektionsdatum - manuelle Eingabe oder Kalender-Auswahl');
bullet('Inspektor - Name des durchfuhrenden Inspektors');
bullet('Wetter - Auswahl aus Vorlagen oder Freitext, mit GPS-Wetterabfrage');

screenshotPlaceholder('Projektformular - Allgemeine Angaben', 400, 160);

heading3('Abschnitt 2: Leitungsdaten');
bullet('Leitungstyp - Dropdown (Mischwasser, Schmutzwasser, Regenwasser, Sonstige)');
bullet('Material - Dropdown (PVC, Beton, Steinzeug, Gusseisen, Unbekannt)');
bullet('Durchmesser (DN) - Nennweite in mm');
bullet('Inspektionslange - Gesamtlange in Metern');
bullet('Startpunkt / Endpunkt - Bezeichnung der Endpunkte');

heading3('Abschnitt 3: Inspektionsmethode');
bullet('Inspektionssystem - Automatisch "NSP3CT ONE" (nicht editierbar)');
bullet('Kameratyp - Auswahl C10 oder C13');
bullet('Inspektionsform - Checkboxen: Visuell, Videoaufnahme, Fotoaufnahme');

heading3('Abschnitt 4: Video-Einstellungen');
bullet('Video-Qualitat - SD oder HD (nur bei Neuanlage wahlbar)');
bullet('Video-Overlay - Projektdaten als Overlay im Video einblenden (nur bei Neuanlage wahlbar)');

infoBox('Gesperrte Einstellungen', 'Video-Qualitat und -Overlay konnen nach dem Anlegen eines Projekts nicht mehr geandert werden, um die Konsistenz der Aufnahmen sicherzustellen.');

tipBox('Uber das GPS-Symbol neben dem Wetter-Feld kann das aktuelle Wetter automatisch per Standort abgerufen werden.');

// ═══════════════════════════════════════════════════════════════
// CHAPTER 5.3: PROJEKTDETAILS
// ═══════════════════════════════════════════════════════════════
newPage();
heading2('5.3  Projektdetails');

para('Die Projektdetail-Ansicht zeigt alle Informationen und Medien eines Projekts. Die obere Leiste enthalt folgende Aktionen:');

bullet('Zuruck-Pfeil - Zurück zur Projektliste');
bullet('Bearbeiten (Stift) - Projektformular zum Bearbeiten offnen');
bullet('Inspektion (Kamera, grun) - Direkt zur Inspektion mit diesem Projekt wechseln');
bullet('PDF-Export - Inspektionsbericht als PDF generieren');
bullet('ZIP-Export - Alle Projektdaten als ZIP-Archiv exportieren');

para('Unterhalb der Kopfzeile wird eine Zusammenfassung des Projekts mit Info-Chips angezeigt (Datum, Inspektor, Material, DN, Lange, Adresse, Videoqualitat, OSD-Status).');

heading3('Tab-Bereiche');

para('Die Projektdaten sind in vier Tabs organisiert:');

numberedItem(1, 'Fotos - Galerie-Ansicht aller aufgenommenen Fotos als Rasterlayout. Tippen offnet die Vollbildansicht. Zeigt Original und annotierte Bilder nebeneinander.');
numberedItem(2, 'Schaden - Chronologische Liste aller erfassten Schaden mit Thumbnail, Schadenstyp, Meterposition, Beschreibung und Zeitstempel.');
numberedItem(3, 'Videos - Liste der aufgenommenen Videos mit Dateiname, Grosse und Datum. Tippen startet die Wiedergabe.');
numberedItem(4, 'Notizen - Liste aller Text- und Sprachnotizen mit Position, Text und Audio-Wiedergabe.');

screenshotPlaceholder('Projektdetails mit Tab-Ansicht', 450, 200);

para('Jeder Tab zeigt die Anzahl der enthaltenen Elemente als Badge neben dem Tab-Titel an.');

// ═══════════════════════════════════════════════════════════════
// CHAPTER 6: INSPEKTION
// ═══════════════════════════════════════════════════════════════
newPage();
heading1('6  Inspektion (Hauptarbeitsbereich)');

para('Der Inspektions-Bildschirm ist der zentrale Arbeitsbereich der App. Er ist im Landscape-Modus fur optimale Nutzung auf dem Tablet ausgelegt und besteht aus drei Bereichen:');

bullet('Links (70%): Live-Video mit Overlay');
bullet('Rechts (30%): Hardware-Status und Schadensliste');
bullet('Unten: Aktionsleiste mit Schnellzugriff-Buttons');

screenshotPlaceholder('Inspektions-Bildschirm (Landscape)', 480, 240);

heading2('6.1  Video-Ansicht');

para('Der Videobereich zeigt den Live-RTSP-Stream der Inspektionskamera. Am unteren Rand wird ein Overlay mit folgenden Informationen eingeblendet:');

bullet('Aktuelle Meterposition (z.B. "12.45m")');
bullet('Sonde-Frequenz (wenn aktiv)');
bullet('Aktuelle Uhrzeit');
bullet('Aufnahmestatus und -dauer (wenn Aufnahme lauft)');

heading3('Gesten');
bullet('Doppeltipp auf Video: Vollbildmodus ein/aus');
bullet('Pinch-to-Zoom: Video vergroessern (bis 3x)');
bullet('Ziehen: Verschieben des gezoomten Videos');

tipBox('Im Vollbildmodus wird die rechte Statusleiste und die untere Aktionsleiste ausgeblendet. Erneutes Doppeltippen kehrt zum normalen Modus zuruck.');

heading2('6.2  Hardware-Statusleiste');

para('Die rechte Spalte zeigt den aktuellen Hardware-Status des ONE-Systems:');

bullet('Licht - Status und Steuerung der Lichtstufe');
bullet('Sonde - Status und Frequenz-Steuerung');
bullet('Meter (Absolut) - Kabellange mit Reset-Button');
bullet('Meter (Strecke) - Relative Distanz mit Reset-Button');
bullet('Batterie - Akkustand des Controllers');

para('Darunter werden die letzten 5 erfassten Schaden und die letzten 3 Notizen angezeigt. Am unteren Rand erscheint die Projektkarte mit den wichtigsten Projektdaten.');

infoBox('Schaden bearbeiten', 'Durch Doppeltipp auf einen Schaden in der Liste offnet sich der Schadens-Dialog zum Bearbeiten. Gleiches gilt fur Notizen.');

// ═══════════════════════════════════════════════════════════════
// CHAPTER 6.3-6.6
// ═══════════════════════════════════════════════════════════════
newPage();
heading2('6.3  Aktionsleiste');

para('Die Aktionsleiste am unteren Rand des Inspektions-Bildschirms enthalt vier Schnellzugriff-Buttons:');

doc.moveDown(0.2);
tableRow(['Button', 'Funktion', 'Beschreibung'], true);
tableRow(['Foto', 'Schnellfoto', 'Erstellt sofort ein Foto vom aktuellen Videobild und speichert es als Schadensdokumentation']);
tableRow(['Schaden', 'Schaden erfassen', 'Offnet den Schadens-Dialog mit automatischem Screenshot']);
tableRow(['Notiz', 'Notiz erstellen', 'Offnet den Notiz-Dialog fur Text- und Sprachnotizen']);
tableRow(['Aufnahme', 'Video aufnehmen', 'Startet/Stoppt die Videoaufnahme des RTSP-Streams']);
doc.moveDown(0.5);

infoBox('Kein Projekt gewahlt', 'Die Aktionsbuttons sind nur aktiv, wenn ein Projekt zugewiesen ist. Ohne Projekt wird beim Tippen auf "Kein Projekt" (Doppeltipp) zur Projektliste navigiert.');

heading2('6.4  Schaden erfassen');

para('Der Schadens-Dialog offnet sich uber den "Schaden"-Button. Er enthalt:');

screenshotPlaceholder('Schadens-Dialog', 350, 180);

numberedItem(1, 'Foto-Vorschau - Zeigt den automatisch aufgenommenen Screenshot. Durch Doppeltipp offnet sich der Annotations-Editor zum Einzeichnen von Markierungen.');
numberedItem(2, 'Position (m) - Aktuelle Meterposition, editierbar');
numberedItem(3, 'Schadenstyp - Dropdown mit konfigurierbaren Vorlagen (z.B. "Riss langs", "Wurzeleinwuchs")');
numberedItem(4, 'Beschreibung - Optionales Freitextfeld fur zusatzliche Informationen');

para('Nach dem Speichern erscheint der Schaden sofort in der Schadensliste auf dem Inspektions-Bildschirm.');

heading3('Bild-Annotation');
para('Durch Doppeltipp auf das Foto im Schadens-Dialog offnet sich der Annotations-Editor. Hier konnen mit dem Finger Markierungen, Pfeile und Texte auf dem Bild eingezeichnet werden. Das annotierte Bild wird separat gespeichert - das Original bleibt erhalten.');

heading2('6.5  Notiz erstellen');

para('Der Notiz-Dialog ermoglicht die Erstellung von Text- und Sprachnotizen:');

screenshotPlaceholder('Notiz-Dialog mit Sprachaufnahme', 350, 160);

bullet('Position (m) - Aktuelle Meterposition');
bullet('Textfeld - Freitext fur schriftliche Notizen');
bullet('Sprachaufnahme - Aufnahme-Button fur Audio-Notizen (M4A-Format)');
bullet('Wiedergabe - Bereits aufgenommene Sprachnotizen konnen direkt abgespielt werden');

infoBox('Mikrofon-Berechtigung', 'Fur Sprachnotizen wird beim ersten Mal die Mikrofon-Berechtigung angefragt. Diese muss gewahrt werden, um Audio aufnehmen zu konnen.');

newPage();
heading2('6.6  Videoaufnahme');

para('Uber den Aufnahme-Button in der Aktionsleiste kann der RTSP-Videostream direkt aufgezeichnet werden. Beim Start erscheint ein Dialog mit zwei Optionen:');

numberedItem(1, 'Mit Overlay - Projektinformationen werden in das Video eingeblendet');
numberedItem(2, 'Ohne Overlay - Reines Videobild ohne Einblendungen');

para('Wahrend der Aufnahme wird in der Aktionsleiste die Aufnahmedauer angezeigt und der Button wechselt zu "Stopp". Das Overlay im Videobereich zeigt zusatzlich "REC" und die Dauer an.');

para('Die Aufnahmen werden im TS-Format (Transport Stream) gespeichert unter:');
paraLight('  /storage/emulated/0/Android/data/com.uip.oneapp/files/recordings/project_<ID>/');

tipBox('Wahrend einer laufenden Aufnahme und geoffnetem Schadens-Dialog wird die Aufnahme automatisch pausiert und beim Schliessen wieder fortgesetzt.');

// ═══════════════════════════════════════════════════════════════
// CHAPTER 7: EXPORT & BERICHTE
// ═══════════════════════════════════════════════════════════════
heading1('7  Export & Berichte');

heading2('7.1  PDF-Export');

para('Der PDF-Export erstellt einen professionellen Inspektionsbericht. Er wird uber das PDF-Symbol in der Projektdetail-Ansicht gestartet. Ein Fortschrittsbalken zeigt den Export-Status.');

para('Der erzeugte PDF-Bericht enthalt:');
bullet('Deckblatt mit Projektdaten, Firmendaten und Logo');
bullet('Leitungsdaten und Inspektionsmethode');
bullet('Alle Schaden mit Fotos, Annotationen und Beschreibungen');
bullet('Notizen');

para('Nach Abschluss des Exports erscheint ein Dialog mit zwei Optionen:');
numberedItem(1, 'Teilen - Offnet den Android-Teilen-Dialog (E-Mail, Cloud, Bluetooth, etc.)');
numberedItem(2, 'Speichern unter - Offnet den Datei-Explorer zum Speichern auf USB, SD-Karte, etc.');

heading2('7.2  ZIP-Export');

para('Der ZIP-Export bundelt alle Projektdaten in ein komprimiertes Archiv:');
bullet('PDF-Bericht');
bullet('Alle Originalfotos und annotierte Bilder');
bullet('Alle Videoaufnahmen');
bullet('Alle Sprachnotizen');

para('Der ZIP-Export wird uber das Archiv-Symbol in der Projektdetail-Ansicht gestartet und bietet ebenfalls die Optionen "Teilen" und "Speichern unter".');

// ═══════════════════════════════════════════════════════════════
// CHAPTER 8: EINSTELLUNGEN
// ═══════════════════════════════════════════════════════════════
newPage();
heading1('8  Einstellungen');

para('Die Einstellungen sind uber das Zahnrad-Symbol in der unteren Navigation erreichbar. Anderungen werden uber das Speichern-Symbol in der oberen Leiste gesichert.');

screenshotPlaceholder('Einstellungs-Bildschirm', 400, 200);

heading2('8.1  Sprache');

para('Die ONE.APP unterstutzt 35 Sprachen. Die Sprachauswahl erfolgt uber ein Dropdown-Menu mit Landesflaggen. Die gewählte Sprache wird sofort angewendet und dauerhaft gespeichert.');

para('Verfugbare Sprachen (Auszug): Deutsch, Englisch, Norwegisch, Italienisch, Niederlandisch, Franzosisch, Spanisch, Portugiesisch, Polnisch, Tschechisch, und viele mehr.');

tipBox('Die Standardsprache ist Deutsch. Fehlende Ubersetzungen werden automatisch in Deutsch angezeigt.');

heading2('8.2  NSP3CT-Verbindung');

para('Konfiguration der NSP3CT-Verbindungsparameter:');
bullet('Broker IP - IP-Adresse des MQTT-Brokers');
bullet('Broker Port - Port des MQTT-Brokers');
bullet('RTSP URL - URL des Video-Streams');

para('Uber den Button "Verbindung testen" kann die Erreichbarkeit gepruft werden.');

heading2('8.3  Firmendaten & Logo');

para('Firmendaten, die in den PDF-Berichten verwendet werden:');
bullet('Firmenname');
bullet('Firmenadresse');
bullet('Firmenlogo - Bild aus der Galerie wahlen, andern oder entfernen');

screenshotPlaceholder('Firmendaten mit Logo-Upload', 350, 120);

newPage();
heading2('8.4  Wetter-Vorlagen');

para('Konfigurierbare Liste von Wetter-Vorlagen, die im Projektformular als Schnellauswahl zur Verfugung stehen. Die Sektion ist einklappbar.');

bullet('Vorhandene Vorlagen bearbeiten (Stift-Symbol)');
bullet('Vorlagen loschen (Mulleimer-Symbol)');
bullet('Neue Vorlagen hinzufugen');
bullet('Auf Standard zurucksetzen');

heading2('8.5  Schadens-Vorlagen');

para('Analog zu den Wetter-Vorlagen konnen hier die Schadenstypen konfiguriert werden, die im Schadens-Dialog als Dropdown zur Verfugung stehen.');

para('Die Standard-Vorlagen orientieren sich an DIN EN 13508-2 und konnen an die individuellen Anforderungen angepasst werden.');

heading3('Weitere Einstellungen');

para('Uber die Karte "ONE Verbindung" gelangt man direkt zum Verbindungs-Bildschirm (Connection) fur die detaillierte Hardware-Einrichtung.');

para('Am unteren Ende der Einstellungen wird die App-Information angezeigt:');
bullet('App-Name: ONE.APP - NSP3CT Slave Monitor');
bullet('Version: 1.0');
bullet('Copyright-Information');

// ═══════════════════════════════════════════════════════════════
// CHAPTER 9: GESTEN & SHORTCUTS
// ═══════════════════════════════════════════════════════════════
heading1('9  Gesten & Shortcuts');

para('Die ONE.APP nutzt verschiedene Gesten fur effiziente Bedienung:');

doc.moveDown(0.2);
tableRow(['Geste', 'Bereich', 'Aktion'], true);
tableRow(['Doppeltipp', 'Video (Inspektion)', 'Vollbildmodus ein/aus']);
tableRow(['Pinch-to-Zoom', 'Video (Inspektion)', 'Video vergroessern (1x-3x)']);
tableRow(['Ziehen', 'Video (gezoomt)', 'Gezoomtes Video verschieben']);
tableRow(['Doppeltipp', 'Schaden in Liste', 'Schaden zum Bearbeiten offnen']);
tableRow(['Doppeltipp', 'Notiz in Liste', 'Notiz zum Bearbeiten offnen']);
tableRow(['Doppeltipp', 'Foto im Dialog', 'Annotations-Editor offnen']);
tableRow(['Doppeltipp', '"Kein Projekt"', 'Zur Projektliste navigieren']);
tableRow(['Tippen', 'Projektkarte (Insp.)', 'Projektformular offnen']);
doc.moveDown(0.5);

// ═══════════════════════════════════════════════════════════════
// CHAPTER 10: FEHLERBEHEBUNG
// ═══════════════════════════════════════════════════════════════
newPage();
heading1('10  Fehlerbehebung');

heading2('Kein Videostream');
bullet('Prufen Sie die WLAN-Verbindung zum ONE-Netzwerk (ONE_01)');
bullet('Stellen Sie sicher, dass der ONE-Controller eingeschaltet ist');
bullet('Versuchen Sie einen Netzwerk-Scan auf dem Verbindungs-Bildschirm');
bullet('Prufen Sie die RTSP-URL in den Einstellungen (Standard: rtsp://<IP>:8554/1234)');

heading2('Hardware-Status zeigt "Keine Daten"');
bullet('Fuhren Sie eine Hardware-Suche auf dem Verbindungs-Bildschirm durch');
bullet('Prufen Sie, ob das Tablet im gleichen WLAN wie der Controller ist');
bullet('Controller neustarten und erneut verbinden');

heading2('PDF-Export fehlgeschlagen');
bullet('Prufen Sie den verfugbaren Speicherplatz auf dem Tablet');
bullet('Stellen Sie sicher, dass Firmendaten in den Einstellungen hinterlegt sind');
bullet('Versuchen Sie den Export erneut');

heading2('Sprachnotiz kann nicht aufgenommen werden');
bullet('Stellen Sie sicher, dass die Mikrofon-Berechtigung erteilt wurde');
bullet('Prufen Sie in den Android-Einstellungen unter "App-Berechtigungen"');

heading2('App reagiert nicht');
bullet('App uber den Android Task-Manager schliessen und neu starten');
bullet('Bei anhaltenden Problemen: App-Daten in den Android-Einstellungen loschen (Achtung: Projektdaten gehen verloren!)');

doc.moveDown(1);

// Final page with contact
const boxY2 = doc.y;
doc.roundedRect(MARGIN, boxY2, CONTENT_W, 80, 8).fill(DARK_BG);
doc.font('Barlow-SemiBold').fontSize(14).fillColor(YELLOW);
doc.text('Support & Kontakt', MARGIN + 20, boxY2 + 15, { width: CONTENT_W - 40, lineBreak: false });
doc.font('Barlow').fontSize(11).fillColor(WHITE);
doc.text('UIP Umwelt- und Ingenieurtechnik GmbH', MARGIN + 20, boxY2 + 38, { width: CONTENT_W - 40, lineBreak: false });
doc.text('NSP3CT Kanalinspektionssystem - ONE.APP', MARGIN + 20, boxY2 + 55, { width: CONTENT_W - 40, lineBreak: false });

doc.x = MARGIN;
doc.y = boxY2 + 100;

// ═══════════════════════════════════════════════════════════════
// FINISH
// ═══════════════════════════════════════════════════════════════

doc.end();

stream.on('finish', () => {
    console.log(`PDF erfolgreich erstellt: ${OUTPUT}`);
    console.log(`Seitenanzahl: ${pageNum + 1}`);
});
