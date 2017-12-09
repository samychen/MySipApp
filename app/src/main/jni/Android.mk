LOCAL_PATH := $(call my-dir)
JNI_PATH := $(LOCAL_PATH)

include $(JNI_PATH)/pjsip/android_toolchain/Android.mk
include $(JNI_PATH)/swig-glue/android_toolchain/Android.mk
include $(JNI_PATH)/libyuv/Android.mk


