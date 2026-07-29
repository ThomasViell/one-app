cmake_minimum_required(VERSION 3.22.1)
project("v4l2bridge")

add_library(v4l2bridge SHARED v4l2bridge.c)

# Pixel-Schleifen (RGB↔YUV) IMMER mit -O3 bauen, auch im Debug-APK: der CMake-Debug-Build
# kompiliert sonst mit -O0, und die Konvertierung kostet GEMESSEN 54 ms statt <10 ms pro
# 720p-Frame (Aufnahme fiel dadurch auf ~13–17 fps). Die dokumentierten M3a-Zeiten
# (~3–6 ms) setzen -O3 voraus — siehe RESULT_CAMERA2_UMBAU_2026-07-29.md Abschnitt 10.
target_compile_options(v4l2bridge PRIVATE -O3)

find_library(log-lib log)
# jnigraphics: AndroidBitmap_lockPixels für die native RGB→I420-Konvertierung (M3a).
find_library(jnigraphics-lib jnigraphics)
target_link_libraries(v4l2bridge ${log-lib} ${jnigraphics-lib})
