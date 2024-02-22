#include "ipc.h"

#include "assert.h"
#include "ipc_impl.h"
#include "rtos.h"

#include <string.h>

// Defined in auto-generated code.
extern ipc_port_obj_t ports[];
extern uint32_t num_ipc_ports;

const ipc_options_t ipc_default_send_opts = {
  .timeout_ms = IPC_TIMEOUT_MAX,
  .take_ownership = false,
};
const ipc_options_t ipc_default_recv_opts = {
  .timeout_ms = IPC_TIMEOUT_MAX,
  .take_ownership = false,
};

static struct {
  mempool_t* mempool;  // Mempool for external protobuf comms
  uint8_t* response_buffer;
  ipc_proto_ready_cb_t ready_callback;
  rtos_mutex_t response_lock;
} ipc_priv SHARED_TASK_DATA = {
  .mempool = NULL,
  .response_buffer = NULL,
  .ready_callback = NULL,
  .response_lock = {0},
};

static inline bool port_obj_is_initialized(ipc_port_obj_t* obj) {
  return obj != NULL && obj->queue != NULL;
}

ipc_port_obj_t* get_port_obj(ipc_port_t port) {
  ASSERT(port < num_ipc_ports);
  return &ports[port];
}

void _ipc_register_port(ipc_port_t port, char* name, rtos_queue_t* queue) {
  ipc_port_obj_t* obj = get_port_obj(port);
  strncpy(obj->name, name, IPC_QUEUE_MAX_NAME_SIZE);
  obj->queue = queue;
}

bool _ipc_send(ipc_port_t port, ipc_ref_t* ref, ipc_options_t options) {
  ASSERT(ref);

  ipc_port_obj_t* obj = get_port_obj(port);
  if (!port_obj_is_initialized(obj)) {
    return false;
  }

  if (options.take_ownership) {
    uint8_t* owned_msg = mempool_alloc(ipc_priv.mempool, ref->length);
    memcpy(owned_msg, ref->object, ref->length);
    ref->object = owned_msg;
  }

  return rtos_queue_send(obj->queue, (void*)ref, options.timeout_ms);
}

bool ipc_recv_opt(ipc_port_t port, ipc_ref_t* ref, ipc_options_t options) {
  ASSERT(ref);

  ipc_port_obj_t* obj = get_port_obj(port);
  if (!port_obj_is_initialized(obj)) {
    return false;
  }

  return rtos_queue_recv(obj->queue, (void*)ref, options.timeout_ms);
}

void ipc_release(ipc_ref_t* ref) {
  mempool_free(ipc_priv.mempool, ref->object);
}

uint8_t* ipc_proto_get_response_buffer(void) {
  rtos_mutex_lock(&ipc_priv.response_lock);
  return ipc_priv.response_buffer;
}

void ipc_proto_send_response_buffer(uint8_t* encoded_proto, uint32_t size) {
  ipc_priv.ready_callback(encoded_proto, size);
  rtos_mutex_unlock(&ipc_priv.response_lock);
}

void ipc_proto_notify_done(void) {
  ipc_priv.ready_callback(NULL, 0);
  rtos_mutex_unlock(&ipc_priv.response_lock);
}

void ipc_proto_register_api(mempool_t* pool, uint8_t* response_buffer,
                            ipc_proto_ready_cb_t callback) {
  ipc_priv.mempool = pool;
  ipc_priv.response_buffer = response_buffer;
  ipc_priv.ready_callback = callback;
  rtos_mutex_create(&ipc_priv.response_lock);
}

uint8_t* ipc_proto_alloc(uint32_t size) {
  return mempool_alloc(ipc_priv.mempool, size);
}

void ipc_proto_free(uint8_t* buffer) {
  mempool_free(ipc_priv.mempool, buffer);
}
