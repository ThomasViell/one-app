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

map:no sha256=8a5ffd16e4f8ce43b64602ab764172101bc7e43f898a01fad8efc020da337b9c
map:it sha256=5eb9377c398722cbaa6c265927ab70d79411eff6dc43c7de4e74a7dfb0a7a2e1
map:nl sha256=7abced7e209e756a9aed0e63f812acf0ed95873014b81cc84a9d3c6e1899bec2
map:fr sha256=8d0f3d6ca3250fb3a689a66ff1b4fdcc49a9b8e79b770b6db1a17e9b8f04acab
map:es sha256=5698a03f59ca1f8f70d81511f0b0717fba0fdbc4b7fb983a9a9275d5db4d60c8
map:pt sha256=c118325b79ed21f21f30c320a45c76ed1257153bd7ac7d14b6e536aede3b773e
map:pl sha256=a9529b0606eb945f5a002c398b61b85f239109f0901c02ca95c821385ce60589
map:cs sha256=a05554c2e2fd78dba2c5c9d6ffa5252598a69c65578779b5809c4159a2628001
map:sk sha256=400109521180975bd65bc24cbbd394ea686d9059a6558a0b6334328f2b79861c
map:sl sha256=80adcbf989ca1bf33b06bff250a7633e0d83b1cfeb19ba1bf90fd0858b31342d
map:hr sha256=60e12f3dc52e4167de049dd89d86f78582a335e5d181f451ab0bf1954ad47f49
map:hu sha256=67289894860b47c9f7b592224c694ce735c7e4cc1755ed4a744c61bef504d3ce
map:ro sha256=2b6c211e6a466acedb13729544d83530cdaaa62c3861724f87e6caadb7b4f1d3
map:bg sha256=84756cb46107a10a638fe2e662f9213ad103a69996055ff4bd079c629c20b901
map:el sha256=45f8a0bd81e4ba3984668d017c5bb6a8679e41cd7ee19ffa014ff82d986c95b8
map:da sha256=44f15f86920df9c542ed6d3eba626f691b78d4674d78cb12821c6898ba72c552
map:sv sha256=126b0bcffd8a45abc3bc4704de8c0e67b70ef6c5541c72da391a819af5cb0fcc
map:fi sha256=94059d4be2584166da8a566670b7cbb1f5ecc37030f1119db3b96f1cb8e35e5b
map:et sha256=3a803136616ab3d6fd363157fc8839ae478eed653a94b61b70e4eb7ae20eeecf
map:lv sha256=e10f5c2693b39578912eb24bf41a526a506d617859a2b77712785988890048c7
map:lt sha256=754e0f26ec33ab202db56f6a648132e3c9a3a8ba04e523697d9a98c8c8bc9159
map:ga sha256=3a251d5d7df32a6bfdaaebfd427e8ab87d01cf908e2631a69dcb898d0c9c84d0
map:mt sha256=ef90739eb8cfa8b26795dc61ba262b5521254a9fdc052fc281c0e24c4c5a72e8
map:ar sha256=874ead7f9d422c584cb525f83069d897260d9cd889bc7c4084abfe7f6b8fd7c7
map:ru sha256=0d79de168c738e275c307e26f223aec0d5871aef405585569f9eb2739d844242
map:tr sha256=44a9b47e38df8f83e92c045c745a3a1242f4495cd3000305df9f309af8168801
map:sr sha256=833491e62a18f96786acd2bf2cc88897d2f611e8e84322aad450d75b8351af63
map:sq sha256=4fa99487793a89e8b42bcd328f9d8d6c5b1b4b1fed9e578282e315d0595b0d95
map:zh sha256=ac68799452af5ed5caf641719aa8cd861b348c8bac3aa9d73076cd84f63c5736
map:ja sha256=c596732085bc8f566e48fc0035db8b910a6fe8f14a3479e80dc6ca66bd9b6583
map:ko sha256=3c97fd1de9b43a61d978d74a15ee261b1739dce822e27bc3b0f02fb26db7b292
map:id sha256=11648f9f905e4f2506f0f5d59ca24488cad9ba47c88c227784436c308f73f44d
map:th sha256=d631295c6691f41e49e025b1fcb61b8fa91600ab054edd2d9eb65ce88d09b1a5
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
