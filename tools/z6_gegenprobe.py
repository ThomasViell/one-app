#!/usr/bin/env python3
"""Gegenprobe fuer die zwei SZENARIEN-Kataloge (N-2, Auflage M-B aus PRUEFBERICHT_A.md).

Prueft AUSSCHLIESSLICH gegen einen benannten git-Bezug (Branch oder Commit) via
`git grep <ref>` bzw. `git show <ref>:<pfad>` -- nie gegen den Arbeitsbaum, nie
implizit gegen HEAD. Zwei Pruefungen:

  1. JUnit-Referenzen: jede Backtick-Zeichenkette mit camelCase-Muster
     (Testnamen-Form dieses Repos) wird als `fun <name>(` in
     app/src/test/java/** am genannten Bezug gesucht.
  2. Zeilenzeiger: zaehlt verbliebene reine `Datei.kt:NNN`-Angaben in den
     Katalogen (N-1 verlangt: nach der Umstellung auf Symbolform steht dort
     keine nackte Zeilennummer mehr).

Jede Ausgabe beginnt mit einer Zeile, die ihren Bezug nennt (Ref-Name +
Kurzhash) -- eine Gegenprobe ohne diese Zeile ist kein Beleg (NACHBESSERUNG,
N-2, Punkt 3).

Aufruf:
    python tools/z6_gegenprobe.py <git-ref> [<datei> ...]

Ohne Dateiangabe werden die zwei Standard-Kataloge geprueft:
    docs/auftraege/SZENARIEN_zeitseite-nachzug.md
    docs/auftraege/SZENARIEN_bedienbefunde-0915.md
"""

import re
import subprocess
import sys

DEFAULT_FILES = [
    "docs/auftraege/SZENARIEN_zeitseite-nachzug.md",
    "docs/auftraege/SZENARIEN_bedienbefunde-0915.md",
]

BACKTICK_RE = re.compile(r"`([^`]+)`")
# Testnamen-Muster dieses Repos: camelCase, mind. ein lower->Upper-Uebergang,
# keine Leerzeichen, kein Dateisuffix, kein reiner Zeilenzeiger.
CAMEL_TESTNAME_RE = re.compile(r"^[a-z][A-Za-z0-9]*_[A-Za-z0-9_]*[a-z][A-Z][A-Za-z0-9_]*$")
LINE_POINTER_RE = re.compile(r"\b[A-Za-z0-9_]+\.kt:\d+")


def run(args):
    return subprocess.run(args, capture_output=True, text=True, cwd=None)


def git_show(ref, path):
    r = run(["git", "show", f"{ref}:{path}"])
    if r.returncode != 0:
        return None
    return r.stdout


def git_grep_count(ref, pattern, pathspec):
    r = run(["git", "grep", "-I", "-c", "-F", "--no-color", pattern, ref, "--", pathspec])
    if r.returncode not in (0, 1):
        raise RuntimeError(r.stderr)
    total = 0
    for line in r.stdout.splitlines():
        # Format: <ref>:<pfad>:<anzahl>
        parts = line.rsplit(":", 1)
        if len(parts) == 2 and parts[1].strip().isdigit():
            total += int(parts[1].strip())
    return total


def resolve_ref(ref):
    short = run(["git", "rev-parse", "--short", ref]).stdout.strip()
    return short


def main():
    if len(sys.argv) < 2:
        print("Aufruf: python tools/z6_gegenprobe.py <git-ref> [<datei> ...]", file=sys.stderr)
        return 2
    ref = sys.argv[1]
    files = sys.argv[2:] if len(sys.argv) > 2 else DEFAULT_FILES

    short = resolve_ref(ref)
    if not short:
        print(f"bezug: {ref} -- NICHT AUFLOESBAR (git rev-parse fehlgeschlagen)")
        return 1
    print(f"bezug: {ref} ({short})")

    testname_total = 0
    testname_found = 0
    testname_missing = []
    pointer_total = 0
    pointer_hits = []

    for path in files:
        content = git_show(ref, path)
        if content is None:
            print(f"  DATEI FEHLT AM BEZUG: {path}")
            continue

        for m in BACKTICK_RE.finditer(content):
            token = m.group(1)
            if not CAMEL_TESTNAME_RE.match(token):
                continue
            testname_total += 1
            hits = git_grep_count(ref, f"fun {token}(", "app/src/test/java")
            if hits > 0:
                testname_found += 1
            else:
                testname_missing.append((path, token))

        for m in LINE_POINTER_RE.finditer(content):
            pointer_total += 1
            pointer_hits.append((path, m.group(0)))

    print(f"referenzen {testname_total} treffer {testname_found} fehlend {testname_total - testname_found}")
    for path, token in testname_missing:
        print(f"  FEHLEND: {path}: `{token}` -> kein `fun {token}(` in app/src/test/java am Bezug {ref}")

    print(f"zeilenzeiger {pointer_total}")
    for path, hit in pointer_hits:
        print(f"  ZEILENZEIGER: {path}: {hit}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
