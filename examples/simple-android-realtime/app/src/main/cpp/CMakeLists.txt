cmake_minimum_required(VERSION 3.10)

project(whisper_android C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fPIC -O3")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=hidden -fPIC -O3")

# Paths
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper)
set(GGML_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ggml)

# Include directories
include_directories(
    ${WHISPER_DIR}/include
    ${GGML_DIR}/include
    ${GGML_DIR}/src
    ${GGML_DIR}/src/ggml-cpu
)

# GGML source files
set(GGML_SOURCES
    ${GGML_DIR}/src/ggml.c
    ${GGML_DIR}/src/ggml-alloc.c
    ${GGML_DIR}/src/ggml-backend.cpp
    ${GGML_DIR}/src/ggml-backend-reg.cpp
    ${GGML_DIR}/src/ggml-quants.c
    ${GGML_DIR}/src/ggml-threading.cpp
    ${GGML_DIR}/src/gguf.cpp
)

# GGML CPU implementation
file(GLOB GGML_CPU_SOURCES ${GGML_DIR}/src/ggml-cpu/*.c ${GGML_DIR}/src/ggml-cpu/*.cpp)
list(APPEND GGML_SOURCES ${GGML_CPU_SOURCES})

# Whisper source files
set(WHISPER_SOURCES
    ${WHISPER_DIR}/src/whisper.cpp
)

# JNI wrapper
set(JNI_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper_jni.c
)

# Build shared library
add_library(whisper SHARED
    ${GGML_SOURCES}
    ${WHISPER_SOURCES}
    ${JNI_SOURCES}
)

# Compiler definitions
target_compile_definitions(whisper PRIVATE
    GGML_USE_CPU
    GGML_VERSION="1.0.0"
    GGML_COMMIT="standalone"
    WHISPER_VERSION="1.0.0"
    WHISPER_COMMIT="standalone"
)

# Enable ARM NEON on ARM platforms
if(CMAKE_SYSTEM_PROCESSOR MATCHES "^(arm|aarch64)")
    target_compile_definitions(whisper PRIVATE
        GGML_USE_CPU_AARCH64
    )
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "^aarch64")
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8-a")
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a")
    endif()
endif()

# Link libraries
target_link_libraries(whisper
    android
    log
    m
)

# Strip symbols in release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_custom_command(TARGET whisper POST_BUILD
        COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:whisper>)
endif()
