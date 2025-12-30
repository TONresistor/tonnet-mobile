LOCAL_PATH := $(call my-dir)

# Prebuilt tonutils-proxy library
include $(CLEAR_VARS)
LOCAL_MODULE := tonutils-proxy-prebuilt
LOCAL_SRC_FILES := $(LOCAL_PATH)/../../../libs/jniLibs/$(TARGET_ARCH_ABI)/libtonutils-proxy.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)
include $(PREBUILT_SHARED_LIBRARY)

# JNI bridge
include $(CLEAR_VARS)
LOCAL_MODULE := tonproxy-jni
LOCAL_SRC_FILES := tonproxy_jni.c
LOCAL_SHARED_LIBRARIES := tonutils-proxy-prebuilt
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
