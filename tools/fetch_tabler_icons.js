// Lädt Tabler-Outline-SVGs der DrainQ.ONE-Pflicht-Icon-Keys (§4.3) und konvertiert
// sie zu Android-VectorDrawables in app/src/main/res/drawable (Prefix ic_dq_).
// Stroke-basiert (2px, round). Compose DqIcon färbt via tint (SrcIn) — Basisfarbe egal.
const https = require('https');
const fs = require('fs');
const path = require('path');

// DqIcon-Key -> Tabler-Outline-Icon-Name
const MAP = {
  home: 'home',
  inspection: 'video',
  projects: 'folder',
  settings: 'adjustments-horizontal',
  check: 'check',
  chevron_down: 'chevron-down',
  chevron_right: 'chevron-right',
  refresh: 'refresh',
  dot: 'point',
  camera: 'camera',
  photo: 'photo',
  alert: 'alert-triangle',
  probe: 'broadcast',
  light: 'bulb',
  minus: 'minus',
  plus: 'plus',
  meter: 'ruler-2',
  back: 'arrow-left',
  keyboard_hide: 'keyboard-off',
  language: 'world',
  company: 'building',
  weather: 'cloud',
  osd: 'device-tv',
  fullscreen: 'maximize',
  info: 'info-circle',
  map: 'map',
  delete: 'trash',
  edit: 'pencil',
  close: 'x',
  download: 'download',
  moon: 'moon',
  sun: 'sun',
  save: 'device-floppy',
  new_project: 'folder-plus',
  expand_less: 'chevron-up',
};

const OUT = path.join(__dirname, '..', 'app', 'src', 'main', 'res', 'drawable');
const BASE = 'https://raw.githubusercontent.com/tabler/tabler-icons/main/icons/outline/';

function get(url) {
  return new Promise((resolve, reject) => {
    https.get(url, (res) => {
      if (res.statusCode !== 200) { reject(new Error('HTTP ' + res.statusCode + ' ' + url)); return; }
      let data = '';
      res.on('data', (c) => data += c);
      res.on('end', () => resolve(data));
    }).on('error', reject);
  });
}

// SVG-Pfade extrahieren (Bounding-Box-Path mit fill="none"/stroke="none" verwerfen)
function pathsFromSvg(svg) {
  const out = [];
  const re = /<path\b([^>]*)\/?>/g;
  let m;
  while ((m = re.exec(svg)) !== null) {
    const attrs = m[1];
    if (/stroke="none"/.test(attrs) || /fill="none"[^>]*d="M0 0h24v24/.test(attrs)) continue;
    const d = (attrs.match(/\bd="([^"]+)"/) || [])[1];
    if (!d) continue;
    if (d.trim().startsWith('M0 0h24v24H0z')) continue; // bbox
    // gefüllte Icons (Tabler -filled) erkennen: fill gesetzt und kein stroke
    const filled = /fill="(?!none)/.test(attrs) && !/stroke=/.test(attrs);
    out.push({ d, filled });
  }
  return out;
}

function toVector(paths) {
  const body = paths.map((p) => {
    if (p.filled) {
      return `    <path android:fillColor="#FFFFFFFF" android:pathData="${p.d}"/>`;
    }
    return `    <path android:strokeColor="#FFFFFFFF" android:strokeWidth="2" ` +
      `android:strokeLineCap="round" android:strokeLineJoin="round" ` +
      `android:fillColor="#00000000" android:pathData="${p.d}"/>`;
  }).join('\n');
  return `<vector xmlns:android="http://schemas.android.com/apk/res/android"\n` +
    `    android:width="24dp"\n    android:height="24dp"\n` +
    `    android:viewportWidth="24"\n    android:viewportHeight="24"\n` +
    `    android:tint="#FFFFFFFF">\n${body}\n</vector>\n`;
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const failed = [];
  for (const [key, name] of Object.entries(MAP)) {
    try {
      const svg = await get(BASE + name + '.svg');
      const paths = pathsFromSvg(svg);
      if (!paths.length) { failed.push(key + ' (no paths)'); continue; }
      const vec = toVector(paths);
      fs.writeFileSync(path.join(OUT, `ic_dq_${key}.xml`), vec);
      console.log('OK  ic_dq_' + key + '.xml  <- ' + name);
    } catch (e) {
      failed.push(key + ' (' + e.message + ')');
    }
  }
  if (failed.length) { console.log('\nFAILED:\n' + failed.join('\n')); process.exitCode = 1; }
  else console.log('\nAll ' + Object.keys(MAP).length + ' icons generated.');
})();
