cmake_minimum_required(VERSION 3.10)

project(whisper_android)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fPIC")

# Path to whisper.cpp root
set(WHISPER_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../..)

# Include directories
include_directories(${WHISPER_ROOT}/include)
include_directories(${WHISPER_ROOT}/ggml/include)

# Add whisper.cpp source files
add_library(whisper SHARED
    ${WHISPER_ROOT}/src/whisper.cpp
    ${WHISPER_ROOT}/ggml/src/ggml.c
    ${WHISPER_ROOT}/ggml/src/ggml-alloc.c
    ${WHISPER_ROOT}/ggml/src/ggml-backend.c
    ${WHISPER_ROOT}/ggml/src/ggml-quants.c
    whisper_jni.c
)

# Link libraries
target_link_libraries(whisper
    android
    log
)
