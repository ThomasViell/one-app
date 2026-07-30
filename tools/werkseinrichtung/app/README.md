# App-Datei für die Werkseinrichtung

Hier gehört genau eine Datei rein: `DrainQ-ONE_<Version>_<Code>_platform.apk`
(z. B. `DrainQ-ONE_0.6.2_602_platform.apk`).

Sie ist **nicht** im Git-Repo enthalten (`*.apk` ist repo-weit ausgeschlossen). Bauanleitung
und Hintergrund: `docs/WERKSEINRICHTUNG.md`.

`Werkseinrichtung.ps1` prüft beim Start selbst, ob die Datei mit dem Plattformschlüssel
signiert ist, und bricht ohne jeden Geräte-Zugriff ab, falls nicht.
