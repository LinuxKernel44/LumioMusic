cmake_minimum_required(VERSION 3.22.1)
project("lumionative")

add_library(lumionative SHARED
    jni_bridge.cpp
)

find_library(log-lib log)

target_link_libraries(lumionative
    ${log-lib}
)
