LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := main

SDL_PATH := ../SDL

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/$(SDL_PATH)/include \

LOCAL_C_FLAGS += \
	-D__STDC_CONSTANT_MACROS \

# Add your application source files here...
LOCAL_SRC_FILES := \
	$(SDL_PATH)/src/main/android/SDL_android_main.cpp \
	main.c \

LOCAL_SHARED_LIBRARIES := \
	SDL2 \
	libavcodec \
	libavformat \
	libswscale \
	libavutil \
	libavfilter \
	libwsresample \

LOCAL_LDLIBS := \
	-lGLESv1_CM \
	-llog \

include $(BUILD_SHARED_LIBRARY)

$(call import-module,ffmpeg/android/arm)
