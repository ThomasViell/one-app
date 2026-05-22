#!/system/bin/sh
# DrainQ.ONE Post-Boot-Hook
#
# Wird vom init.drainq.rc als oneshot-Service nach sys.boot_completed=1
# gestartet. Setzt navigation_mode auf 0 (=keine Navi-Bar) und killt
# SystemUI, damit dieser bei seinem Auto-Restart die neuen Settings liest
# und den ScreenDecorOverlayBottom-Balken nicht mehr zeichnet.
#
# Hintergrund: navigation_mode=0 setzen reicht nicht, weil SystemUI
# Settings nur beim eigenen (Re-)Init liest. ROM setzt navigation_mode
# beim Boot wieder auf 2 zurueck -- daher muessen wir hier nach Boot
# beides machen (settings put + killall).

LOG_TAG="drainq-postboot"
log -t "$LOG_TAG" "running post-boot hook"

# 1) navigation_mode auf 0 (ROM resettet es beim Boot auf 2)
settings put secure navigation_mode 0
log -t "$LOG_TAG" "navigation_mode=$(settings get secure navigation_mode)"

# 2) persist.sys.top_app auf DrainQ.ONE setzen (Bominwell-ROM: ScreenDecorOverlayBottom
#    wird nur versteckt wenn dieser Property-Wert mit dem aktuellen Foreground-Package
#    übereinstimmt UND hidebar_enable=true). Muss als Root gesetzt werden -- App-seitig
#    scheitert SystemProperties.set() für persist.* ohne Plattform-Cert.
setprop persist.sys.top_app com.uip.drainq.one

# 3) sys.status.hidebar_enable als Backup nochmal setzen (nach persist.sys.top_app,
#    damit beides korrekt steht wenn SystemUI nach dem killall neu startet)
setprop sys.status.hidebar_enable true

# 3) SystemUI killen -> Auto-Restart liest neue Settings, kein Balken mehr
sleep 1
killall com.android.systemui
log -t "$LOG_TAG" "SystemUI killed for re-init"
