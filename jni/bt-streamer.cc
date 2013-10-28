// Copyright (c) 2013 Kazumasa Kohtaka. All rights reserved.
// This file is available under the MIT license.

#include <android/log.h>
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "BT-STREAMER", __VA_ARGS__))

#include "include/l2cap-streamer.h"
#include "uv/uv.h"
#include <cstdio>
#include <jni.h>

static ::uv_loop_t *loop;
static ::uv_tcp_t server;

static void on_connect(
    ::uv_stream_t *handle,
    int status) {
  LOGI("on_connect() called with status: %d\n", status);
}

static void init(void) {

  LOGI("initialize BT-STREAMER");
  //connect_to_l2cap_server();
  loop = ::uv_default_loop();
}

static int init_tcp_server(void) {
  return ::uv_tcp_init(loop, &server);
}

static int bind_tcp_server(uint32_t port) {
  struct ::sockaddr_in address;
  ::uv_ip4_addr("0.0.0.0", port, &address);
  return ::uv_tcp_bind(
      &server,
      reinterpret_cast< ::sockaddr *>(&address));
}

static int start_tcp_server(void) {
  ::uv_listen(
      reinterpret_cast< ::uv_stream_t *>(&server),
      128,
      ::on_connect);
  LOGI("listening...\n");
  return ::uv_run(loop, UV_RUN_DEFAULT);
}

extern "C" {
  JNIEXPORT void JNICALL Java_org_kohtaka_btstreamer_BTStreamer_init(
    JNIEnv *env,
    jobject thiz) {

    init();
  }
};

