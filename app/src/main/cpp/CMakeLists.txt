cmake_minimum_required(VERSION 3.22.1)

project(daw_audio_engine VERSION 1.0.0 LANGUAGES CXX)

# Enforce ISO C++20 Standard
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Compiler optimization and warning options
if(CMAKE_CXX_COMPILER_ID MATCHES "Clang" OR CMAKE_CXX_COMPILER_ID MATCHES "GNU")
    add_compile_options(
        -O3
        -Wall
        -Wextra
        -Wpedantic
        -ffast-math
        -fvisibility=hidden
        -fexceptions
        -frtti
    )
endif()

# Source files
set(DAW_SOURCES
    audio/SpscRingBuffer.cpp
    audio/AudioClip.cpp
    audio/AudioTrack.cpp
    audio/AudioEngine.cpp
    audio/WavFile.cpp
    audio/MixdownRenderer.cpp
    dsp/BiquadFilter.cpp
    dsp/DelayEffect.cpp
    dsp/MasterLimiter.cpp
    dsp/Panner.cpp
    dsp/ParametricEq.cpp
    backend/AAudioStreamManager.cpp
    jni/JniBridge.cpp
)

# Header include paths
set(DAW_INCLUDE_DIRS
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/audio
    ${CMAKE_CURRENT_SOURCE_DIR}/dsp
    ${CMAKE_CURRENT_SOURCE_DIR}/backend
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
)

# Target shared library for Android NDK
add_library(daw_audio_engine SHARED ${DAW_SOURCES})

target_include_directories(daw_audio_engine PUBLIC ${DAW_INCLUDE_DIRS})

# Target link libraries
if(ANDROID)
    target_link_libraries(daw_audio_engine PRIVATE
        aaudio
        android
        log
        m
    )
else()
    target_link_libraries(daw_audio_engine PRIVATE
        m
        pthread
    )
endif()

# Define export macro for symbols
target_compile_definitions(daw_audio_engine PRIVATE
    DAW_AUDIO_ENGINE_EXPORTS
)
