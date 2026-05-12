# DrainQ ONE — Hetzner Mirror Deployment

Gilt für den bestehenden Hetzner-Server, der bereits den DrainQ Suite Update-Proxy
(`updates.drainq.de`) betreibt. Diese Anleitung erweitert ihn um den ONE-Subpfad.

**Voraussetzung:** Nginx läuft bereits, `/var/www/drainq-updates/` existiert,
Let's-Encrypt-Cert für `updates.drainq.de` ist aktiv.

---

## 1 — SSH-Verbindung

```bash
ssh root@updates.drainq.de
```

---

## 2 — Webroot anlegen

```bash
mkdir -p /var/www/drainq-updates/one
chown www-data:www-data /var/www/drainq-updates/one
chmod 755 /var/www/drainq-updates/one
```

---

## 3 — Mirror-Skript deployen

```bash
mkdir -p /opt/drainq-mirror-one
cp /tmp/mirror-releases-one.sh /opt/drainq-mirror-one/mirror-releases-one.sh
chmod 755 /opt/drainq-mirror-one/mirror-releases-one.sh
chown root:root /opt/drainq-mirror-one/mirror-releases-one.sh
```

> **Tipp:** Dateien per `scp` oder `rsync` übertragen:
> ```bash
> scp ops/hetzner-update-proxy/mirror-releases-one.sh \
>     root@updates.drainq.de:/tmp/
> ```

---

## 4 — PAT-Datei anlegen

Der Operator legt die PAT-Datei manuell an. Das Token selbst wird **niemals** in
Git, Logs oder Konfigurationsdateien gespeichert.

```bash
# PAT aus Passwort-Manager holen und einfügen:
install -m 600 -o root -g root /dev/null /etc/drainq/github-pat-one
# Öffnet $EDITOR — PAT-Wert einfügen, speichern:
editor /etc/drainq/github-pat-one
```

Alternativ ohne interaktiven Editor:

```bash
# Nur wenn Terminal-Sitzung sicher ist (kein Screen-Recording, kein tmux-Logging):
install -m 600 -o root -g root /dev/null /etc/drainq/github-pat-one
printf '%s\n' 'github_pat_XXXXXX' > /etc/drainq/github-pat-one
```

Datei-Check:

```bash
ls -la /etc/drainq/github-pat-one
# Erwartete Ausgabe: -rw------- 1 root root ... /etc/drainq/github-pat-one
```

> **Hinweis:** `/etc/drainq/` existiert bereits vom Suite-Setup.
> Falls der Suite-PAT denselben `repo`-Scope abdeckt, kann stattdessen
> `/etc/drainq/github-pat` als Symlink verwendet werden — dann Skript-Variable
> `PAT_FILE` in `mirror-releases-one.sh` entsprechend anpassen.

---

## 5 — Systemd-Units installieren

```bash
cp /tmp/drainq-one-mirror.service /etc/systemd/system/
cp /tmp/drainq-one-mirror.timer   /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now drainq-one-mirror.timer
```

Status prüfen:

```bash
systemctl status drainq-one-mirror.timer
systemctl list-timers drainq-one-mirror.timer
```

Erwartete Ausgabe `list-timers`: nächste Aktivierung in ≤ 5 min sichtbar.

---

## 6 — Nginx-Snippet einbinden

Snippet auf Server kopieren:

```bash
cp /tmp/nginx-snippet-one.conf /etc/nginx/snippets/drainq-one.conf
```

Snippet in bestehenden Server-Block einfügen:

```bash
# Öffnet die Nginx-Konfig für updates.drainq.de:
editor /etc/nginx/sites-available/updates.drainq.de
```

Folgende Zeile **innerhalb des `server { ... }` Blocks** ergänzen, z. B.
nach dem existierenden Suite-Location-Block:

```nginx
include /etc/nginx/snippets/drainq-one.conf;
```

Konfig validieren und Nginx neu laden:

```bash
nginx -t
systemctl reload nginx
```

Erwartete Ausgabe von `nginx -t`:
```
nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

---

## 7 — Ersten Mirror-Lauf manuell anstoßen

```bash
systemctl start drainq-one-mirror.service
journalctl -u drainq-one-mirror.service -n 50 --no-pager
```

Erwartete Log-Zeilen (nach erstem Release-Tag im Repo):

```
2026-05-12T10:00:00Z [drainq-one-mirror] Fetching latest release for ThomasViell/one-app
2026-05-12T10:00:01Z [drainq-one-mirror] Latest release tag: v0.4.0
2026-05-12T10:00:01Z [drainq-one-mirror] Downloading releases.stable.json
2026-05-12T10:00:02Z [drainq-one-mirror] Downloading drainq-one-0.4.0.apk
2026-05-12T10:00:30Z [drainq-one-mirror] Downloading drainq-one-0.4.0.apk.sha256
2026-05-12T10:00:30Z [drainq-one-mirror] Done. 3 new file(s) downloaded.
```

Vor dem ersten Release-Tag im Repo endet der Lauf mit:
```
[drainq-one-mirror] No relevant assets found in release ... — nothing to do
```
Das ist korrekt.

---

## 8 — Smoke-Test via curl

```bash
# Manifest erreichbar und kein Cache:
curl -I https://updates.drainq.de/one/releases.stable.json
# Erwartete Header: HTTP/2 200, Cache-Control: no-cache

# APK erreichbar (liefert 200 und Content-Type application/vnd.android.package-archive):
curl -I https://updates.drainq.de/one/drainq-one-0.4.0.apk
# Erwartete Header: HTTP/2 200, Cache-Control: public, max-age=86400, immutable

# Manifest-Inhalt lesbar:
curl -s https://updates.drainq.de/one/releases.stable.json | python3 -m json.tool | head -20

# 404 für nicht-existente Datei (autoindex off):
curl -I https://updates.drainq.de/one/
# Erwartete Antwort: HTTP/2 404 (oder 403 je nach Nginx default)
```

---

## 9 — Laufenden Betrieb prüfen

```bash
# Timer-Status und nächste Ausführung:
systemctl list-timers drainq-one-mirror.timer

# Letzter Lauf (Logs):
journalctl -u drainq-one-mirror.service --since "1 hour ago" --no-pager

# Webroot-Inhalt:
ls -lh /var/www/drainq-updates/one/
```

---

## Rollback / Notfall

Falls der Mirror ein fehlerhaftes Manifest ausgeliefert hat:

```bash
# Altes Manifest wiederherstellen (liegt noch im GitHub Release):
systemctl stop drainq-one-mirror.timer
rm /var/www/drainq-updates/one/releases.stable.json
# Manuell korrektes Manifest einspielen, dann:
systemctl start drainq-one-mirror.timer
```

Clients, die bereits einen Download gestartet haben, brechen bei SHA256-Mismatch
ab — kein inkonsistenter Zustand auf Tablet-Seite möglich.

---

## Datei-Übersicht

| Datei im Repo | Ziel auf Server |
|---|---|
| `ops/hetzner-update-proxy/mirror-releases-one.sh` | `/opt/drainq-mirror-one/mirror-releases-one.sh` |
| `ops/hetzner-update-proxy/drainq-one-mirror.service` | `/etc/systemd/system/drainq-one-mirror.service` |
| `ops/hetzner-update-proxy/drainq-one-mirror.timer` | `/etc/systemd/system/drainq-one-mirror.timer` |
| `ops/hetzner-update-proxy/nginx-snippet-one.conf` | `/etc/nginx/snippets/drainq-one.conf` |
| *(manuell)* PAT vom Operator | `/etc/drainq/github-pat-one` |
