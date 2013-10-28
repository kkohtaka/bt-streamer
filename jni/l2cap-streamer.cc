// Copyright (c) 2013 Kazumasa Kohtaka. All rights reserved.
// This file is available under the MIT license.

#include <android/log.h>
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "BT-STREAMER", __VA_ARGS__))

#include <hardware/hardware.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_sock.h>

#include <cstdlib>
#include <pthread.h>
#include <signal.h>
#include <fcntl.h>

const char * const UUID_STR = "D6D95607-4B61-4280-9043-672953501AAF";

void adapter_state_changed_cb(bt_state_t state) {
  LOGI(
      "adapter_state_changed_cb: %s",
      (state == BT_STATE_ON ? "BT_STATE_ON" : "BT_STATE_OFF"));
}

void adapter_properties_cb(
    bt_status_t status,
    int num_properties,
    bt_property_t *properties) {
  LOGI("adapter_properties_cb: %d %d", status, num_properties);
}

void remote_device_properties_cb(
    bt_status_t status,
    bt_bdaddr_t *bd_addr,
    int num_properties,
    bt_property_t *properties) {
  LOGI("remote_device_properties_cb: %d %d", status, num_properties);
}

void device_found_cb(int num_properties, bt_property_t *properties) {
  LOGI("device_found_cb: %d", num_properties);
}

void discovery_state_changed_cb(bt_discovery_state_t state) {
  LOGI(
      "discovery_state_changed_cb: %s",
      (state == BT_DISCOVERY_STOPPED ? "BT_DISCOVERY_STOPPED" : "BT_DISCOVERY_STARTED"));
}

void pin_request_cb(
    bt_bdaddr_t *remote_bd_addr,
    bt_bdname_t *bd_name,
    uint32_t cod) {
  LOGI("pin_request_cb: %s", bd_name->name);
}

void ssp_request_cb(
    bt_bdaddr_t *remote_bd_addr,
    bt_bdname_t *bd_name,
    uint32_t cod,
    bt_ssp_variant_t pairing_variant,
    uint32_t pass_key) {
  LOGI("ssp_request_cb: %s", bd_name->name);
}

void bond_state_changed_cb(
    bt_status_t status,
    bt_bdaddr_t *remote_bd_addr,
    bt_bond_state_t state) {
  LOGI("bond_state_changed_cb: %d", status);
}

void acl_state_changed_cb(
    bt_status_t status,
    bt_bdaddr_t *remote_bd_addr,
    bt_acl_state_t state) {
  LOGI("acl_state_changed_cb: %d", status);
}

static bool running = false;

void thread_event_cb(bt_cb_thread_evt evt) {
  LOGI("thread_event_cb");
  if (evt  == ASSOCIATE_JVM) {
    LOGI("ASSOCIATE_JVM");
  } else if (evt == DISASSOCIATE_JVM) {
    LOGI("DISASSOCIATE_JVM");
    running = false;
  }
}

void dut_mode_recv_cb(
    uint16_t opcode,
    uint8_t *buf,
    uint8_t len) {
  LOGI("dut_mode_recv_cb: %d %d", opcode, len);
}

void le_test_mode_cb(
    bt_status_t status,
    uint16_t num_packets) {
  LOGI("le_test_mode_cb: %d %d", status, num_packets);
}

static bt_callbacks_t callbacks = {
  sizeof(callbacks),
  adapter_state_changed_cb,
  adapter_properties_cb,
  remote_device_properties_cb,
  device_found_cb,
  discovery_state_changed_cb,
  pin_request_cb,
  ssp_request_cb,
  bond_state_changed_cb,
  acl_state_changed_cb,
  thread_event_cb,
  dut_mode_recv_cb,
  le_test_mode_cb
};

static const bt_interface_t *interface = NULL;
static const btsock_interface_t *socket_interface = NULL;

static pthread_t thread;

void *event_tread(void *_) {
  LOGI("event_tread starting");
  while (running) {
    sleep(2);
  }
  LOGI("event_tread stopping");
}

bool start_event_thread(void) {
  running = true;
  int ret = pthread_create(
      &thread,
      reinterpret_cast<const pthread_attr_t*>(NULL),
      event_tread,
      NULL);
  return ret == 0;
}

void signal_handler(int _) {
  running = false;
}

void connect_to_l2cap_server(void) {
  running = true;
  if (start_event_thread() == false) {
    running = false;
    return;
  }

  hw_module_t *module;
  int error = hw_get_module(
      BT_STACK_MODULE_ID,
      const_cast<const hw_module_t **>(&module));
  LOGI("hw_get_module: %d", error);
  LOGI("module: %p", module);

  hw_device_t *device;
  error = module->methods->open(
      module,
      BT_STACK_MODULE_ID,
      &device);
  LOGI("hw_module_methods_t#open: %d", error);
  LOGI("device: %p", device);

  bluetooth_module_t *stack = reinterpret_cast<bluetooth_module_t *>(device);

  interface = stack->get_bluetooth_interface();
  error = interface->init(&callbacks);
  LOGI("bt_interface_t#init: %d", error);

  socket_interface = reinterpret_cast<const btsock_interface_t *>(
      interface->get_profile_interface(BT_PROFILE_SOCKETS_ID));
  LOGI("bt_interface_t#get_profile_interface: %p", socket_interface);

  signal(SIGINT, signal_handler);
  signal(SIGKILL, signal_handler);

  error = interface->enable();
  LOGI("bt_interface_t#enable: %d", error);

  error = interface->start_discovery();
  LOGI("bt_interface_t#start_discovery: %d", error);

  pthread_join(thread, NULL);

  sleep(120);

  error = interface->disable();
  LOGI("bt_interface_t#disable: %d", error);
}

