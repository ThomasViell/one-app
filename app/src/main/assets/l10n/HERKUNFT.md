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

## Altbestand (33 Fremdsprachen), Herkunft nicht belegt

Herkunft: nicht belegt — Altbestand vor 2026-09-17, eingefroren
(CEO-Entscheid E-8: Neuuebersetzung per DeepL + Partner). Die Werte stammen aus dem
Mai-2026-Umbau; Verfasser und Quelle sind aus der Git-Historie nicht mehr feststellbar.
Ab 17.09.2026 macht jede Aenderung ohne Nachfuehrung dieser Datei den Waechter-Test rot.

Nachtrag 23.09.2026 (W-33f): Markenzeichen ONE.APP -> DrainQ.ONE in `app_name` und
`dashboard_title` aller 33 Bloecke (CEO-Entscheid „DrainQ ueberall“, siehe
belege/p3_marke.txt). Keine Uebersetzung — nur der Markenname ist getauscht; die
Herkunft der uebrigen Werte bleibt unveraendert (Altbestand). Die 33 Hashes unten
sind nachgefuehrt (Beleg: belege/p3_herkunft.txt).

map:no sha256=fd0ee8ddbc08bb4fa580861ce054f42f51b42579ba34ace8d640f43bd88562b1
map:it sha256=c7561e49a1ecd1b7ee57648b0ce6194b99c82e1c69b861cacccbf5dc90891e86
map:nl sha256=3281e63852e11f984cf53f9519e151fed529361d393111fa531c341e255a6e60
map:fr sha256=7b1bab07dc5deed3d18628486f2fac0fbf71b276ec88f846ba57c6225a3b2a33
map:es sha256=5b18e6bc168a3d3cf508a7482095bc7c2ed446abcb82bc159256f34907a4f046
map:pt sha256=ba8754995adab2702aeac132853d7c55fae3a2ff20ef4c383f1725d411ae4cc4
map:pl sha256=9d25d8ef590b1abd67430c704856658fd1a2fd7f1d8b9d58e658165344e25987
map:cs sha256=8db879a94feb3d384a4623fa877b5b07446dd9070251ac21543123d6799039e6
map:sk sha256=ba682e0556628caa99fc5f3f4ba1d722cd20ae2ce58919f907216e8917c8b833
map:sl sha256=a0fe357f7e436d01c5e723c63a237d0c55e62ef347fdf2f49e69bc768dfd579f
map:hr sha256=27e8b00f0de44cbbc11134638e5eb1d7979a4e26b878e80aa422ffc524bcaa1c
map:hu sha256=70b5dd6571b25dabeb1c5052d7a744b2eabefc330196b1af85202fb580492f2b
map:ro sha256=7122588a99d324da00a5dd41bc25aaece49b3d4c45f7669cbc2e0b09e60ef1d8
map:bg sha256=22674d7707f4fe7a5de92bdcc0ce5beaa542680a27f4f48b57c42f36f555fad0
map:el sha256=f6b247b5804785fda7380e3f58a399326d7726a697964308a6c2e97ffaaf742e
map:da sha256=eb56d22e735677dcab6b88605463b5b72a8f55e412032194545c63978aa3887e
map:sv sha256=5b3fea61cd001eb085a27da861aec3166701fb0923e647a6b37798f755cf7193
map:fi sha256=25697e9d331b63ce3f0ef7a25ac180c622edd8064734be800973a8233bdc1803
map:et sha256=b379f72b865aabf824b103fb5981a647cb277f12a4f06b8862815c7aaeb45b69
map:lv sha256=1059f3961e9f57d1359caeafbdeef95cff3950c3dbdaedb6ba6828b850d55bd5
map:lt sha256=5032be8dea8da6f4151c97f58281fc82eff699d5a97d36a0fc067c17128bedad
map:ga sha256=27ad109d056477706234473d8301e646506ed5b37552c9184eab8f28b5dab568
map:mt sha256=aba61931147d700b7407097a6789e70d808c1845b3ffe4924cea72df2bcb242c
map:ar sha256=6392a25a2b30e26214ee473b324cbe6b8585962e66ca9e3f913b7ff50435358d
map:ru sha256=27f9d06acde60cc14c257aa8b6c134daea8b96a1feaa0f0a1c55ce35d73f7581
map:tr sha256=3f4946dc467a1209b861081fc738d583494689caa1a7c0d593ea1e8fd6c04adf
map:sr sha256=effc5c84123c3398d2846daf3f6852be3c6ef2431b65783c8f163e739f0eb01e
map:sq sha256=7e4ee51705ecf1fd7623e2dffd72b66a281abc52a247c4439cbd0621fe8a0986
map:zh sha256=441fc22ca1c65c8a87b1bf7be3add6f87c48c680f537afba6a0927b7de887b8f
map:ja sha256=5e852e5ab5a52014cb0312ee3a7d6b19ea6fb1bc59deb3bf462673a092bd97cc
map:ko sha256=1a8ed8c226582384e47ecf1a2ad805e38f955e0683501658415f74f314c7c1c8
map:id sha256=f6e71ab5a2daff95272fa31a8a64754429743e6f56e5853cf2d5883d2e6d3017
map:th sha256=522f4b100522d23b9c4383b89fe874c0333c7a003f09d14a14addac567211729
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

## Paket de/en (`app/src/main/assets/l10n/de.json`, `en.json`), Z-2/Z-3

Bezug: welle/l10n-anschluss 09692b1, Portallauf 18.09.2026 (Schritt 3, `tools/l10n/portal-messung.ps1`,
`belege/portalmessung_2026-09-18/`). Eingecheckter Stand der Portalantwort `scope=one,shared`,
unveraendert — kein SHA-Pin (Ausnahme unten), stattdessen Herkunftsvermerk:

- `de.json`: 469 Schluessel, `ETag: W/"d5ddbf53719b4463a9bf92dca84e608b-639195621724148100"`,
  `Last-Modified: Mon, 13 Jul 2026 17:56:12 GMT`, Quelle `GET /api/translations/de.json?scope=one,shared`
- `en.json`: 468 Schluessel, `ETag: W/"5cf31e1939134d3ebe5f0b3c84cda431-639195621724267410"`,
  `Last-Modified: Mon, 13 Jul 2026 17:56:12 GMT`, Quelle `GET /api/translations/en.json?scope=one,shared`

## Ausgenommen

- `de` / `en`: entstehen im Repo (de) bzw. per DeepL/Portal-Abgleich (en, Regel 12) und
  tragen deshalb keinen SHA-Pin — weder als Map-Block noch als Paket-Asset.
