cmake_minimum_required(VERSION 3.18.1)
project("tonproxy-jni")

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

# Get the path to the libs directory (relative to android/app/src/main/cpp)
set(LIBS_DIR ${CMAKE_SOURCE_DIR}/../../../libs)

# Import the prebuilt tonutils-proxy library (standard mode)
add_library(tonutils-proxy SHARED IMPORTED)
set_target_properties(tonutils-proxy PROPERTIES
    IMPORTED_LOCATION ${LIBS_DIR}/jniLibs/${ANDROID_ABI}/libtonutils-proxy.so
    IMPORTED_NO_SONAME TRUE
)

# Build the JNI bridge
add_library(tonproxy-jni SHARED
    tonproxy_jni.c
)

# Treat every bridge warning as a build failure.
target_compile_options(tonproxy-jni PRIVATE
    -Wall
    -Wextra
    -Werror
)

# Android 15+ devices can use a 16 KiB memory page size. Keep every LOAD
# segment compatible instead of relying on the linker's host default.
target_link_options(tonproxy-jni PRIVATE
    "LINKER:-z,max-page-size=16384"
)

# Link against prebuilt library and Android log
target_link_libraries(tonproxy-jni
    tonutils-proxy
    log
)
