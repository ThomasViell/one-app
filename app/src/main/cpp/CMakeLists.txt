cmake_minimum_required(VERSION 3.22.1)
project("v4l2bridge")

add_library(v4l2bridge SHARED v4l2bridge.c serialport.c)

find_library(log-lib log)
target_link_libraries(v4l2bridge ${log-lib})
