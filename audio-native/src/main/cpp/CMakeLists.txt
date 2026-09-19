cmake_minimum_required(VERSION 3.22.1)
project("lumionative")

add_library(lumionative SHARED
    jni_bridge.cpp
    aaudio_sink.cpp
)

find_library(log-lib log)
find_library(aaudio-lib aaudio)

target_link_libraries(lumionative
    ${log-lib}
    ${aaudio-lib}
)
