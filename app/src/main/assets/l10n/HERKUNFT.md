# Herkunft fremdsprachiger L10n-Werte

Bezug: welle/l10n-anschluss 1a9ad9a, gemessen 17.09.2026 (Schritt 1, Z-7)

Regel: Jeder Sprachblock `<code>Translations()` in `LocalizationManager.kt` (ausser de/en)
und jede Datei `assets/i18n/<code>.json` (ausser de.json/en.json) braucht hier einen Eintrag
mit SHA-256 ueber den normalisierten Text (Zeilenenden auf 
). Aendert sich ein fremdsprachiger
Wert ohne begleitende Aenderung dieser Datei, wird `L10nHerkunftTest` rot
(app/src/test/java/com/uip/oneapp/ui/localization/L10nHerkunftTest.kt).
Seit 19.09.2026 (`A-5`) erhebt der Waechter die Bloecke aus `LocalizationManager.kt` und die
Dateien aus `assets/i18n` selbst, statt eine feste Liste zu pruefen — ein neuer
fremdsprachiger Block ohne Herkunftsvermerk macht den Test dadurch rot.

Format je Zeile: `<schluessel> sha256=<hash>`

Belegpfade `_ketten/...` meinen den Kettenordner `C:\Projekte\_ketten\`, nicht das Repo.

## Altbestand (33 Fremdsprachen), Herkunft nicht belegt

Herkunft: nicht belegt — Altbestand vor 2026-09-17, eingefroren
(CEO-Entscheid E-8: Neuuebersetzung per DeepL + Partner). Die Werte stammen aus dem
Mai-2026-Umbau; Verfasser und Quelle sind aus der Git-Historie nicht mehr feststellbar.
Ab 17.09.2026 macht jede Aenderung ohne Nachfuehrung dieser Datei den Waechter-Test rot.

Nachtrag 23.09.2026 (W-33f): Markenzeichen ONE.APP -> DrainQ.ONE in `app_name` und
`dashboard_title` aller 33 Bloecke (CEO-Entscheid „DrainQ ueberall“, siehe
_ketten/mt-b4/belege/p3_marke.txt). Keine Uebersetzung — nur der Markenname ist getauscht; die
Herkunft der uebrigen Werte bleibt unveraendert (Altbestand). Die 33 Hashes unten
sind nachgefuehrt (Beleg: _ketten/mt-b4/belege/p3_herkunft.txt).

Nachtrag 24.09.2026 (W-33e): `app_name` und `dashboard_title` sind seit W-33e aus allen 35
Bloecken entfernt — beide ohne Verbraucher (CEO R-2). Der Absatz oben ist Stand 23.09.2026
und bleibt als Geschichte stehen; die 33 Hashes sind erneut nachgefuehrt (Beleg:
`_ketten/w33e-neu/belege/10_herkunft_nachgezogen.txt`).

map:no sha256=a1898a81b5065226a6cb092ec5b1623b8e1a272342a777349d4c10c28f3cc5c1
map:it sha256=664e14c98b3d2391652449ffd094a68350a1e8f6b97810d033bfb9e7229a715b
map:nl sha256=428224fc73e9d3a20f74757528682fbaf94b451e5d3022d8146a656137d46122
map:fr sha256=30840b125012fc8b68e89acb2ac570d9fbc6073ef981f98d84703c7e632e38e1
map:es sha256=ddde49a55d6603248cda7d6558d99955d007eb61519949be5464caf4b531a957
map:pt sha256=96a2988c4c02c9b5283c6a303a3f20426684729416ba8903c4f8ea41affe81bc
map:pl sha256=07f4c6562f3980bdd1017d2d5301485eae70ff2208e798e5ea7d94e5a0d1abe5
map:cs sha256=bce47bbb70b43cc9a7ad884ae9f07681adee5f3976f86a9f4a40cfffd4ba8755
map:sk sha256=985d16d3b2355d715878812d0eef9388ba6dd56ce0ae5f7189edef765f9ec900
map:sl sha256=89a38bfe6c83aac08ab6e4b3a5c287b0796fc1004f14b632c29d8fee26dc8d25
map:hr sha256=0bde5f761f7bcf511def146892e7123cc21d70ee7b2039f4700032b8d5a00027
map:hu sha256=98ff133b756c0a9209770db06203532b6d18053893d8430afd51b5577796c046
map:ro sha256=9f521823fb24ee4186b9f720352a80273df373745e6e202080a1d5135647c1fb
map:bg sha256=7c4f2fbd1bbb6a538094611269a523b479fc5f0ebc9c7eda2b4383ca61f33830
map:el sha256=a1fd1c75dc4b054b6ba5c285037ac175590b60aee4971f948442e054d2ee3814
map:da sha256=b40d9c9a1f20ecfd2e8fafeafa7ff2c9aa3073ca5b0480c50dda77f80711b722
map:sv sha256=d4d018a61fe9bb692e7109ed117e1d69fa2721c916fcf866db3426d38d9f7fbb
map:fi sha256=f81f703903d87535289624d98f850bdceddad30191041adaa321ff2beaee42c1
map:et sha256=db431cbb5acb900436512fb255b9e9d30cd24de9a6452b207a339b277d8c7148
map:lv sha256=87871116a1aa3008d676ec098a3f5e6c28083d154dddaeca26e4019f14d30905
map:lt sha256=f41222c291c723724e44450ae274589ebd963935f9531ab8cebb191a89f4ce3f
map:ga sha256=c8a6041ded74c07c1d005642e6b492cde9d507b2b2e715b2002a59b60e2b9434
map:mt sha256=f57579421b5595c8e74b774ba0b766e6a166a0b387d962614a30f9bbfb9069ed
map:ar sha256=dc5816f36681ea2fa2c99091492ffcebbf693e8dd58479b365245e21e0f0de9f
map:ru sha256=0a8f3c907f6ee19342ce1d52f5c0d932e8e2c3d6d0e7a2f1850f61b1e70b210b
map:tr sha256=b9fed6a5dddb0ad801eae3b7a46014821e876c1435fb39069a5adc38c6192242
map:sr sha256=7aa4b887e078886c05348c4d87105b07e2c96e93a111066f193a0d5fae8739da
map:sq sha256=cd6b576a94773756bb8650d98fcfec0fe1d1bcdf49399fec21307c5b8a149a50
map:zh sha256=be944248a41d9392ea70595a5bf10312d83c78dd4d91a91c0a9fe215b27c59e2
map:ja sha256=f922a8ff68f1244e795b682e1ded28f24417b37d4e1d7221a74393ce1643d5f1
map:ko sha256=ff099d1d1f97af9be7e8217ebcd9c65b9c1a0275453154de2d2a72b2311003fb
map:id sha256=031969cee48bd7aaf357116ccca29869dd823bc290a94e1d8966b702e1c0ec5e
map:th sha256=0f8913e8198763d6da58515e3746e1b46ab255eea25caa2a227ee68b1f9ab692
i18n/no.json sha256=fc824072ff4112fc9d294f2d859b5f18f26c5c99d6b9fdd7c5ed8d19163b7e23
i18n/it.json sha256=ddd4cd57574ce34f0fe186556a29a1f6f804b013c738b925fb7555d447837979
i18n/nl.json sha256=837625305cb23406b528c0db3b2480b11aa4a1ecdbfe635fb7ead62f8151f424
i18n/fr.json sha256=9b78b8596e0912c903ec212fb8708cc742f52a30afc1ed9a426b3cd9fc33aa30
i18n/es.json sha256=459104d3d40e9d0d0af815c27beb156f54458c91f6443f85c6dc0360fe61a0e8
i18n/pt.json sha256=b10313d1e8889a153fd66b7462b7474d6d177e13a84a104f0014de867a2de096
i18n/pl.json sha256=c63c1cc1798ae22efa10c5140b934331c54145f1478e29e5a0a4ffcbfd1debf1
i18n/cs.json sha256=aba246aa18da338f94ff732e4098b0b6bdd1f78a879b22c07d5447790c700f78
i18n/sk.json sha256=13bd6047ac37c4afa93b57fa4679bdee3f269461633e068370f3678a6e8be8e9
i18n/sl.json sha256=117a76db91c0e877f9757b8d794db9f2b1f37da5669884f00e6f46c331eb0ecf
i18n/hr.json sha256=c1288ce29fedf8ea30141c58197c4dd3148a8dcc9cb256a3233cf8efcf9371b4
i18n/hu.json sha256=91806c9f46fd2d5753ccfaef1827de6d499007584aaae63bf2c83591acfb9242
i18n/ro.json sha256=293f9d65e5c7032330b16e3e88786ff2ebdd63eba088c1afaad37fb5c41b8539
i18n/bg.json sha256=d8734416e4275766fc1468a1b0f37734fd07ef98a3665e81b8dc4aeb70eb0646
i18n/el.json sha256=c8d45baefdce59c2cce30a4b4a30605f58651c0d9a5b61465f457dc33d22b701
i18n/da.json sha256=06dc59f251cc2afb658a86a92e663d9d0cfbfbfcde825113c3b2271c477ba705
i18n/sv.json sha256=d28d4388be18380cf7fb02a9f474bde9d7bfd2dd139484b6309a5beff0b5acdf
i18n/fi.json sha256=286f3cdc3e7d979cadf49be70f87317757dfb948520ac51c78972e7b6e44ae38
i18n/et.json sha256=a22aa6ee6baafbe8435c27dd4de8428728269c84eaf7126ed9fd4b5a478c9a21
i18n/lv.json sha256=e3d3ee3f5e55ef22ad8b429c2badd8cd0764e4745999318f0cdd9f8adddd3740
i18n/lt.json sha256=c4607378799c4793598ed7035178b38618940decee9e3544089d0a2dc0986a2d
i18n/ga.json sha256=6cbaa8ca7acb9103d717f3ddcab57c52e6054dda507cd6de18c7f8e05252ccf9
i18n/mt.json sha256=6b74f1a028a70e3ae1a6be324086bda4b8f8e4cfb045f3bb2cfa6d6cb34684c6
i18n/ar.json sha256=624bda1381407390c0035484bfbd5778df383a8df8ad823f50aefacd7b949a84
i18n/ru.json sha256=4d8f38e345a2f55a149b8d09f3291fad124ea3d54428fba2625b9a34114b02f5
i18n/tr.json sha256=a31aea4def887682291bffc6ff753ce9b2d5ca4fa03cc32619771fffc376425e
i18n/sr.json sha256=70063c26007a4971cb6c7e065e98dcbec279ff4d95457d10c299c6e5de8b415f
i18n/sq.json sha256=c1c5b46db58051858e3c5a588412e6c6ec1c3d89022cb0868bcebd28369ed972
i18n/zh.json sha256=07a8a9572b16a11ba2c65870697158c3f9d7fa9db8465fd778abf9ec1b7c2c92
i18n/ja.json sha256=0866a17fc39d2f8f6888e2258f2304db91384ac11f0155bce884e637982d6a5c
i18n/ko.json sha256=fb3214b03c9facf3f8b45c31be146c95844d4166b635a37ee95dae563b507c68
i18n/id.json sha256=e37a3a6b65c79dd01dc6ed2ff244bfbc64a6dde04664801570b122f7e76ddf91
i18n/th.json sha256=523339abeac88c0e37fdc685c01343cb2cb3912aca254ea8b01be54a77bc6a2a

**Eigener Fehler des Waechters, gefunden und behoben (Schritt 3, 18.09.2026):** `L10nHerkunftTest.mapBlockText` bestimmte die Blockgrenze des LETZTEN Fremdsprachblocks (`th`)
ueber `indexOf("private fun ", ...)` bis Dateiende, statt bis zum naechsten Klassenmitglied.
Jeder danach eingefuegte Code (hier: Z-2/Z-4/Z-6-Erweiterungen von `LocalizationManager.kt`)
wurde dadurch stillschweigend in den `th`-Hash eingerechnet, obwohl sich am thailaendischen
Wert selbst nichts geaendert hat. Root-Cause-Fix in `L10nHerkunftTest.kt` (Grenze auf das
naechste `private `/`fun `/`@`-Klassenmitglied statt nur `private fun`), **nur** `map:th`
neu berechnet (alle 32 uebrigen Bloecke unveraendert, Gegenprobe: `scratchpad`-Skript).

## Nachtrag 2026-09-24 (W-33e, tote Schluessel)

Welle W-33e (Zweig welle/w33e) hat 83 Schluessel ohne Verbraucher aus allen 35 Bloecken
entfernt (Liste mit Block und Beleg je Schluessel: `_ketten/w33e-neu/TOTE_SCHLUESSEL.md`;
Pfade `_ketten/...` = Kettenordner, Konvention oben). Kein Wert eines verbleibenden
Schluessels wurde geaendert: 71 Zeilen in `no` und je 57 in den 32 uebrigen Fremdbloecken
sind verschwunden, 0 hinzugekommen. Die 33 Summen oben sind neu gerechnet (Beleg:
`_ketten/w33e-neu/belege/10_herkunft_nachgezogen.txt`); die 33 `i18n/`-Zeilen sind
unberuehrt — die Assets sind nicht Gegenstand der Welle.


## Paket de/en (`app/src/main/assets/l10n/de.json`, `en.json`), Z-2/Z-3

Bezug: welle/l10n-anschluss 09692b1, Portallauf 18.09.2026 (Schritt 3, `tools/l10n/portal-messung.ps1`,
`_ketten/l10n-anschluss/belege/portalmessung_2026-09-18/`). Eingecheckter Stand der Portalantwort `scope=one,shared`,
unveraendert — kein SHA-Pin (Ausnahme unten), stattdessen Herkunftsvermerk:

- `de.json`: 469 Schluessel, `ETag: W/"d5ddbf53719b4463a9bf92dca84e608b-639195621724148100"`,
  `Last-Modified: Mon, 13 Jul 2026 17:56:12 GMT`, Quelle `GET /api/translations/de.json?scope=one,shared`
- `en.json`: 468 Schluessel, `ETag: W/"5cf31e1939134d3ebe5f0b3c84cda431-639195621724267410"`,
  `Last-Modified: Mon, 13 Jul 2026 17:56:12 GMT`, Quelle `GET /api/translations/en.json?scope=one,shared`

## Ausgenommen

- `de` / `en`: entstehen im Repo (de) bzw. per DeepL/Portal-Abgleich (en, Regel 12) und
  tragen deshalb keinen SHA-Pin — weder als Map-Block noch als Paket-Asset.
