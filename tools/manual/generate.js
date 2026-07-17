/**
 * DrainQ ONE — Bedienungsanleitung Generator (W-H2 / Phase 6)
 * Liest help_<lang>.json + i18n/<lang>.json + Screenshots → HTML → PDF
 *
 * Aufruf:
 *   node generate.js              # DE + EN
 *   node generate.js --lang de    # nur DE
 *   node generate.js --lang en    # nur EN
 *
 * Ausgabe: docs/manual/DrainQ-ONE_Bedienungsanleitung_<lang>_<version>.pdf
 */

'use strict';

const fs   = require('fs');
const path = require('path');
const puppeteer = require('puppeteer-core');

// ── Pfade ────────────────────────────────────────────────────────────────────
const ROOT        = path.resolve(__dirname, '..', '..');
const HELP_DIR    = path.join(ROOT, 'app', 'src', 'main', 'assets', 'help');
const I18N_DIR    = path.join(ROOT, 'app', 'src', 'main', 'assets', 'i18n');
const FONT_DIR    = path.join(ROOT, 'app', 'src', 'main', 'res', 'font');
const SCREENSHOT_DIR = path.join(ROOT, 'docs', 'manual', 'screenshots');
const OUT_DIR     = path.join(ROOT, 'docs', 'manual');

// Kapitelreihenfolge (Plan §3.4 — Bedienlogik, nicht SCR-Nummern)
const CHAPTER_ORDER = [
  { de: 'Erste Schritte',          en: 'Getting Started',            ids: ['scr02_home', 'scr03_connection'] },
  { de: 'Projekte anlegen',        en: 'Creating Projects',          ids: ['scr05_project_form_new', 'scr05b_project_form_edit', 'dlg_map_picker'] },
  { de: 'Projektliste',            en: 'Project List',               ids: ['scr04_projects'] },
  { de: 'Inspektion',              en: 'Inspection',                 ids: ['scr07_inspection_live', 'scr07b_inspection_recording', 'dlg_damage_dialog', 'dlg_note_dialog', 'scr06_project_detail'] },
  { de: 'Berichte & Export',       en: 'Reports & Export',           ids: ['scr08_reports', 'dlg_pdf_preview', 'dlg_usb_export'] },
  { de: 'Einstellungen',           en: 'Settings',                   ids: ['scr09_settings', 'scr10_network', 'scr12_offline_maps', 'scr11_cloud_login'] },
  { de: 'Kopplung & Dual-Modus',   en: 'Pairing & Dual Mode',        ids: ['scr13_pairing'] },
  { de: 'Anhang',                  en: 'Appendix',                   ids: ['scr02_home_storage_usb'] },
];

// Chrome-Pfad (Windows — wird ggf. angepasst)
const CHROME_PATH =
  process.env.CHROME_PATH ||
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

// CLI-Argument --lang
const langArg = (() => {
  const i = process.argv.indexOf('--lang');
  return i !== -1 ? [process.argv[i + 1]] : ['de', 'en'];
})();

// ── Hilfsfunktionen ──────────────────────────────────────────────────────────

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function fileToBase64(filePath) {
  const ext = path.extname(filePath).toLowerCase().replace('.', '');
  const mime = ext === 'png' ? 'image/png' : `font/${ext === 'ttf' ? 'truetype' : ext}`;
  const data = fs.readFileSync(filePath).toString('base64');
  return { mime, data, dataUrl: `data:${mime};base64,${data}` };
}

function resolveKey(key, l10n) {
  return l10n[key] || key;
}

function screenToHtml(screen, l10n, lang, version) {
  const title   = resolveKey(screen.title, l10n);
  const intro   = resolveKey(screen.intro, l10n);
  const elements = (screen.elements || []).map(el => ({
    label: resolveKey(el.label, l10n),
    text:  resolveKey(el.text, l10n),
  }));

  // Screenshot als Base64 einbetten
  const screenshotPath = path.join(SCREENSHOT_DIR, lang, `${screen.screenshot}.png`);
  const imgTag = fs.existsSync(screenshotPath)
    ? `<img class="screenshot" src="${fileToBase64(screenshotPath).dataUrl}" alt="${title}">`
    : `<div class="screenshot-missing">Screenshot: ${screen.screenshot}.png</div>`;

  const elementsHtml = elements.length === 0 ? '' : `
    <table class="elements-table">
      <thead><tr><th>Element</th><th>Beschreibung</th></tr></thead>
      <tbody>
        ${elements.map(el => `
          <tr>
            <td class="el-label">${escHtml(el.label)}</td>
            <td class="el-text">${escHtml(el.text)}</td>
          </tr>`).join('')}
      </tbody>
    </table>`;

  return `
    <div class="screen-page">
      <h2 class="screen-title">${escHtml(title)}</h2>
      <p class="screen-intro">${escHtml(intro)}</p>
      <div class="screenshot-wrap">${imgTag}</div>
      ${elementsHtml}
    </div>`;
}

function escHtml(str) {
  return String(str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

async function generatePdf(lang, version) {
  console.log(`\n[${lang.toUpperCase()}] Generiere Handbuch…`);

  const helpStruct = readJson(path.join(HELP_DIR, `help_${lang}.json`));
  const l10n       = readJson(path.join(I18N_DIR, `${lang}.json`));

  // Screen-Map: id → screen-Objekt
  const screenMap = {};
  for (const s of helpStruct.screens) screenMap[s.id] = s;

  // Fonts als Base64 einbetten
  const fontRegular  = fileToBase64(path.join(FONT_DIR, 'inter_regular.ttf')).dataUrl;
  const fontMedium   = fileToBase64(path.join(FONT_DIR, 'inter_medium.ttf')).dataUrl;
  const fontSemibold = fileToBase64(path.join(FONT_DIR, 'inter_semibold.ttf')).dataUrl;

  // ── HTML aufbauen ─────────────────────────────────────────────────────────
  const chapterSections = CHAPTER_ORDER.map(ch => {
    const screens = ch.ids
      .filter(id => screenMap[id])
      .map(id => screenToHtml(screenMap[id], l10n, lang, version));
    if (screens.length === 0) return '';
    const chapterTitle = ch[lang] || ch.de;
    return `
      <div class="chapter">
        <h1 class="chapter-title">${escHtml(chapterTitle)}</h1>
        ${screens.join('')}
      </div>`;
  }).join('');

  const langLabel  = lang === 'de' ? 'DE' : 'EN';
  const titleText  = lang === 'de' ? 'Bedienungsanleitung' : 'User Manual';
  const productText = 'DrainQ ONE — Sewer Inspection Software';

  const html = `<!DOCTYPE html>
<html lang="${lang}">
<head>
<meta charset="UTF-8">
<style>
@font-face { font-family: Inter; src: url('${fontRegular}') format('truetype'); font-weight: 400; }
@font-face { font-family: Inter; src: url('${fontMedium}') format('truetype'); font-weight: 500; }
@font-face { font-family: Inter; src: url('${fontSemibold}') format('truetype'); font-weight: 600; }

* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: Inter, sans-serif; font-size: 10pt; color: #1a1a2e; background: #fff; }

/* Deckblatt */
.cover {
  page-break-after: always;
  min-height: 100vh;
  display: flex; flex-direction: column; justify-content: center; align-items: center;
  background: #0A0A0F; color: #fff; padding: 60px 40px; text-align: center;
}
.cover-product { font-size: 28pt; font-weight: 600; color: #14BDAC; margin-bottom: 8px; }
.cover-subtitle { font-size: 13pt; color: #aaa; margin-bottom: 40px; }
.cover-title   { font-size: 22pt; font-weight: 500; color: #fff; margin-bottom: 16px; }
.cover-version { font-size: 10pt; color: #666; }
.cover-lang    { display: inline-block; background: #0D7377; color: #fff; padding: 4px 12px;
                  border-radius: 4px; font-size: 9pt; margin-top: 20px; }

/* Inhaltsverzeichnis (TOC) — nur per CSS, wird nicht generiert */

/* Kapitel */
.chapter { page-break-before: always; }
.chapter-title {
  font-size: 18pt; font-weight: 600; color: #0D7377;
  border-bottom: 2px solid #14BDAC; padding-bottom: 8px;
  margin-bottom: 20px; margin-top: 20px;
}

/* Screen-Seite */
.screen-page { margin-bottom: 32px; }
.screen-title { font-size: 14pt; font-weight: 600; color: #0F3460; margin-bottom: 8px; }
.screen-intro { font-size: 10pt; color: #333; margin-bottom: 16px; line-height: 1.5; }

/* Screenshot */
.screenshot-wrap { text-align: center; margin: 16px 0; }
.screenshot { max-width: 100%; max-height: 320px; border: 1px solid #ddd; border-radius: 6px; }
.screenshot-missing { background: #f5f5f5; color: #999; padding: 40px; text-align: center;
                       border: 1px dashed #ccc; border-radius: 6px; }

/* Elemente-Tabelle */
.elements-table {
  width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 9.5pt;
  page-break-inside: avoid;
}
.elements-table thead { background: #0D7377; color: #fff; }
.elements-table thead th { padding: 8px 10px; text-align: left; font-weight: 500; }
.elements-table tbody tr:nth-child(even) { background: #f0f8f8; }
.elements-table tbody td { padding: 6px 10px; border-bottom: 1px solid #e8e8e8;
                            vertical-align: top; line-height: 1.4; }
.el-label { font-weight: 500; color: #0F3460; width: 30%; white-space: nowrap; }
.el-text  { color: #333; width: 70%; }

/* Seitenzahlen */
@page { size: A4; margin: 20mm 18mm 18mm 18mm; }
@page:first { margin: 0; }

/* Fußzeile via margin */
body::after { content: ""; }

/* Hinweis-Box (FAIL-Seiten) */
.note-box {
  background: #fff8e1; border-left: 4px solid #ffc107; padding: 10px 14px;
  border-radius: 4px; font-size: 9pt; color: #555; margin: 10px 0;
}
</style>
</head>
<body>

<!-- DECKBLATT -->
<div class="cover">
  <div class="cover-product">DrainQ ONE</div>
  <div class="cover-subtitle">${escHtml(productText)}</div>
  <div class="cover-title">${escHtml(titleText)}</div>
  <div class="cover-version">Version ${escHtml(version)}</div>
  <div class="cover-lang">${langLabel}</div>
</div>

<!-- KAPITEL -->
${chapterSections}

</body>
</html>`;

  // ── HTML-Datei schreiben (Zwischenformat) ─────────────────────────────────
  const htmlFile = path.join(OUT_DIR, `DrainQ-ONE_Bedienungsanleitung_${lang}_${version}.html`);
  fs.writeFileSync(htmlFile, html, 'utf8');
  console.log(`  HTML: ${path.basename(htmlFile)}`);

  // ── PDF via headless Chrome ────────────────────────────────────────────────
  const pdfFile = path.join(OUT_DIR, `DrainQ-ONE_Bedienungsanleitung_${lang}_${version}.pdf`);

  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  });

  try {
    const page = await browser.newPage();
    await page.setContent(html, { waitUntil: 'networkidle0' });
    await page.pdf({
      path: pdfFile,
      format: 'A4',
      printBackground: true,
      displayHeaderFooter: true,
      headerTemplate: '<span></span>',
      footerTemplate: `
        <div style="font-family:sans-serif;font-size:8pt;color:#999;
                    width:100%;text-align:center;padding:0 18mm;">
          DrainQ ONE ${escHtml(titleText)} ${escHtml(version)} — ${langLabel} —
          <span class="pageNumber"></span> / <span class="totalPages"></span>
        </div>`,
      margin: { top: '20mm', bottom: '18mm', left: '18mm', right: '18mm' },
    });
    console.log(`  PDF:  ${path.basename(pdfFile)}`);
  } finally {
    await browser.close();
  }

  return { htmlFile, pdfFile };
}

// ── Versionsinfo aus Gradle lesen ─────────────────────────────────────────────
function readVersion() {
  // Env-Var hat Vorrang (wie in build.gradle.kts)
  if (process.env.APP_VERSION_NAME) return process.env.APP_VERSION_NAME;
  // Fallback: --version CLI-Argument
  const vArg = process.argv.indexOf('--version');
  if (vArg !== -1 && process.argv[vArg + 1]) return process.argv[vArg + 1];
  // Fallback: aus portal-Manifest abgeleitet
  try {
    const gradle = fs.readFileSync(
      path.join(ROOT, 'app', 'build.gradle.kts'), 'utf8');
    // Versuche hardcodierten Wert im Fallback-Ausdruck
    const m = gradle.match(/\?\s*"([0-9]+\.[0-9]+\.[0-9]+)"/);
    return m ? m[1] : '0.5.17';
  } catch { return '0.5.17'; }
}

// ── Hauptprogramm ─────────────────────────────────────────────────────────────
(async () => {
  const version = readVersion();
  console.log(`DrainQ ONE Manual Generator v${version}`);
  console.log(`Chrome: ${CHROME_PATH}`);
  console.log(`Sprachen: ${langArg.join(', ')}`);

  for (const lang of langArg) {
    await generatePdf(lang, version);
  }

  console.log('\nFertig.');
})().catch(err => {
  console.error('FEHLER:', err.message);
  process.exit(1);
});
