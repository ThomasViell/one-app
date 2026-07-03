cmake_minimum_required(VERSION 3.22.1)
project("v4l2bridge")

add_library(v4l2bridge SHARED v4l2bridge.c)

find_library(log-lib log)
# jnigraphics: AndroidBitmap_lockPixels für die native RGB→I420-Konvertierung (M3a).
find_library(jnigraphics-lib jnigraphics)
target_link_libraries(v4l2bridge ${log-lib} ${jnigraphics-lib})
