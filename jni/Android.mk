# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := bt-streamer
#LOCAL_MODULE := ffmpeg-binder

#LOCAL_C_INCLUDES += \
	jni/include \
	/Volumes/android/aosp/system/core/include \
	/Volumes/android/aosp/hardware/libhardware/include \
	/Volumes/android/aosp/frameworks/native/include \

LOCAL_CFLAGS += \
	-std=c++11 \

#LOCAL_SRC_FILES := \
	bt-streamer.cc \
	l2cap-streamer.cc \
	jni_util.cc \
	org_kohtaka_btstreamer_FFMpegPlayer.cc \
	media_player.cc \
	output.cc \

#LOCAL_PRELINK_MODULE := false

#LOCAL_LDLIBS += \
	-pthread \
	-llog \
	-Llibs -luv \
	-L/Volumes/android/aosp//out/target/product/flo/obj/lib -lhardware \

#LOCAL_REQUIRED_MODULES := bluetooth.default

#LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_SHARED_LIBRARY)

$(call import-module,ffmpeg/android/arm)

