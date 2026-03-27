cmake_minimum_required(VERSION 3.18.1)
project("tonproxy-jni")

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

# Include the header from jni directory
target_include_directories(tonproxy-jni PRIVATE
    ${CMAKE_SOURCE_DIR}/../jni
)

# Link against prebuilt library and Android log
target_link_libraries(tonproxy-jni
    tonutils-proxy
    log
)
