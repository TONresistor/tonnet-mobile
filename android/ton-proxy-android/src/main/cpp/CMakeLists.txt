cmake_minimum_required(VERSION 3.22.1)
project(tonproxy_jni C)

if(NOT DEFINED TON_PROXY_JNI_LIBS_DIR)
    set(TON_PROXY_JNI_LIBS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/generated/jniLibs")
endif()

set(TON_PROXY_LIBRARY "${TON_PROXY_JNI_LIBS_DIR}/${ANDROID_ABI}/libtonutils-proxy.so")
if(NOT EXISTS "${TON_PROXY_LIBRARY}")
    message(FATAL_ERROR "Pinned TON proxy library is missing: ${TON_PROXY_LIBRARY}")
endif()

add_library(tonutils_proxy SHARED IMPORTED)
set_target_properties(tonutils_proxy PROPERTIES
    IMPORTED_LOCATION "${TON_PROXY_LIBRARY}"
    IMPORTED_NO_SONAME TRUE
)

add_library(tonproxy_jni SHARED tonproxy_jni.c)
target_link_libraries(tonproxy_jni tonutils_proxy log)
target_link_options(tonproxy_jni PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
    "-Wl,-z,relro"
    "-Wl,-z,now"
)
