cmake_minimum_required(VERSION 3.18)
project(mlplayerdsp C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# libsoxr (LGPL-2.1+): ricampionatore di alta qualità, senza SIMD né OpenMP
set(SOXR_DIR ${CMAKE_CURRENT_SOURCE_DIR}/soxr)
add_library(soxr STATIC
    ${SOXR_DIR}/soxr.c ${SOXR_DIR}/data-io.c ${SOXR_DIR}/dbesi0.c ${SOXR_DIR}/filter.c
    ${SOXR_DIR}/fft4g64.c ${SOXR_DIR}/fft4g32.c ${SOXR_DIR}/cr.c ${SOXR_DIR}/cr32.c ${SOXR_DIR}/cr64.c)
target_include_directories(soxr PRIVATE ${SOXR_DIR})
target_compile_definitions(soxr PRIVATE SOXR_LIB NDEBUG)
target_compile_options(soxr PRIVATE -O3 -w -fPIC)

# Oboe (AAudio): uscita esclusiva MMAP sulle uscite del telefono (DirectOutputStream, da LastWave)
find_package(oboe REQUIRED CONFIG)

add_library(mlplayerdsp SHARED mlplayer_dsp_jni.cpp DirectOutputStream.cpp DirectOutputBridge.cpp)
target_include_directories(mlplayerdsp PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(mlplayerdsp soxr oboe::oboe log)
target_compile_options(mlplayerdsp PRIVATE -O3 -fno-math-errno)
# Android 15: pagine da 16 KB
target_link_options(mlplayerdsp PRIVATE -Wl,-z,max-page-size=16384)
