// Copyright (c) 2013 Kazumasa Kohtaka. All rights reserved.
// This file is available under the MIT license.

#ifndef JNI_UTIL_H_
#define JNI_UTIL_H_

extern "C" {
#include <jni.h>
}

int jniThrowException(
    JNIEnv *env,
    const char *className,
    const char *msg);

JNIEnv *getJNIEnv(void);

int jniRegisterNativeMethods(
    JNIEnv *env,
    const char *className,
    const JNINativeMethod *gMethods,
    int numMethods);

#endif  // JNI_UTIL_H_

