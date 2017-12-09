LOCAL_PATH := $(call my-dir)/..
SWIG_GLUE_PATH := $(LOCAL_PATH)
SWIG_GLUE_NATIVE_PATH := $(SWIG_GLUE_PATH)/nativesrc
SWIG_GLUE_NATIVE_FILE := pjsua2_wrap.cpp
JAVA_MODULE := pjsua2
JAVA_PACKAGE := org.pjsip.pjsua2
JAVA_PACKAGE_DIR := $(SWIG_GLUE_PATH)/../../src/$(subst .,/,$(JAVA_PACKAGE))

PJ_ROOT_DIR := $(SWIG_GLUE_PATH)/../pjsip/sources

PJSIPUA2_SRC_DIR := $(PJ_ROOT_DIR)/pjsip/src/pjsua2

#$(SWIG_GLUE_PATH)/.$(JAVA_MODULE).i
CONCAT_PJSUA_FILE := $(PJ_ROOT_DIR)/pjsip-apps/src/swig/pjsua2.i

INTERFACES_FILES := $(CONCAT_PJSUA_FILE)

 #Swig generation target
#$(SWIG_GLUE_NATIVE_PATH)/$(SWIG_GLUE_NATIVE_FILE) :: $(CONCAT_INTERFACE_FILE)
#	@mkdir  $(SWIG_GLUE_NATIVE_PATH)
#	$(call host-mkdir,$(SWIG_GLUE_NATIVE_PATH)) 
#	@rm $(JAVA_PACKAGE_DIR)
#	$(call host-mkdir,$(JAVA_PACKAGE_DIR)) 
#	@mkdir  $(JAVA_PACKAGE_DIR)
#	swig -java -package $(JAVA_PACKAGE) -c++ -I$(PJ_ROOT_DIR)/pjsip/include -o $@ -outdir $(JAVA_PACKAGE_DIR) $(CONCAT_PJSUA_FILE)
#	swig -java -package $(JAVA_PACKAGE) -c++ -I$(PJ_ROOT_DIR)/pjsip/include -o $@ -outdir $(JAVA_PACKAGE_DIR) $(CONCAT_PJSUA_FILE)
	

# The swig-glue module
include $(CLEAR_VARS)
LOCAL_PATH := $(SWIG_GLUE_PATH)
LOCAL_MODULE    := $(JAVA_MODULE)
#LOCAL_LDFLAGS   := $(APP_LDXXFLAGS)
#LOCAL_LDLIBS    := $(APP_LDXXLIBS)

LOCAL_CFLAGS := $(MY_PJSIP_FLAGS) -fno-strict-aliasing

# Include PJ interfaces
LOCAL_C_INCLUDES += $(PJ_ROOT_DIR)/pjsip/include $(PJ_ROOT_DIR)/pjlib-util/include/ \
	$(PJ_ROOT_DIR)/pjlib/include/ $(PJ_ROOT_DIR)/pjmedia/include \
	$(PJ_ROOT_DIR)/pjnath/include $(PJ_ROOT_DIR)/pjlib/include 
# Include PJ_android interfaces
LOCAL_C_INCLUDES += $(PJ_ANDROID_ROOT_DIR)/pjmedia/include/pjmedia-audiodev \
	$(PJ_ANDROID_ROOT_DIR)/pjmedia/include/pjmedia-videodev

# Self interface
#LOCAL_C_INCLUDES += $(LOCAL_PATH)

LOCAL_SRC_FILES := nativesrc/$(SWIG_GLUE_NATIVE_FILE)

LOCAL_STATIC_LIBRARIES += pjsip pjmedia pjnath pjlib-util resample srtp speex pjlib 
# pjlib pjsip pjmedia pjnath pjlib-util resample srtp speex

include $(BUILD_SHARED_LIBRARY)

