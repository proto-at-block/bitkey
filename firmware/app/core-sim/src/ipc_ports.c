/**
 * @file ipc_ports.c
 * @brief POSIX IPC port implementation for core-sim
 *
 * Provides:
 * - Port registration for each of the 6 firmware IPC ports
 * - Proto response callback infrastructure
 * - Response synchronization mechanism
 *
 * NOTE: The ports[] array, num_ipc_ports, and ipc_proto_route() are provided
 * by lib/ipc/generated/ipc_internal.c. This file only handles queue creation,
 * port registration, and the proto response callback infrastructure.
 *
 * This enables routing proto commands through the real firmware IPC
 * infrastructure to real task code (key_manager, fwup, sysinfo).
 */

#include "ipc_ports.h"

#include "attributes.h"  // For SHARED_TASK_BSS/DATA macros used by mempool
#include "ipc.h"
#include "ipc_impl.h"
#include "mempool.h"
#include "proto_helpers.h"
#include "rtos.h"
#include "stdio_defs.h"
#include "wallet.pb.h"  // For fwpb_wallet_cmd_*_tag definitions

#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

// Port indices (match lib/ipc/generated/ipc_internal.c)
extern const uint32_t auth_port;
extern const uint32_t fwup_port;
extern const uint32_t key_manager_port;
extern const uint32_t mfgtest_port;
extern const uint32_t sysinfo_port;
extern const uint32_t ui_port;

static rtos_queue_t auth_queue;
static rtos_queue_t fwup_queue;
static rtos_queue_t key_manager_queue;
static rtos_queue_t mfgtest_queue;
static rtos_queue_t sysinfo_queue;
static rtos_queue_t ui_queue;

// Queue item buffers (ipc_ref_t items, 4 deep)
#define QUEUE_DEPTH 4
static ipc_ref_t auth_queue_buf[QUEUE_DEPTH];
static ipc_ref_t fwup_queue_buf[QUEUE_DEPTH];
static ipc_ref_t key_manager_queue_buf[QUEUE_DEPTH];
static ipc_ref_t mfgtest_queue_buf[QUEUE_DEPTH];
static ipc_ref_t sysinfo_queue_buf[QUEUE_DEPTH];
static ipc_ref_t ui_queue_buf[QUEUE_DEPTH];

// Static queue storage (for rtos_queue_create_static)
static StaticQueue_t auth_queue_static;
static StaticQueue_t fwup_queue_static;
static StaticQueue_t key_manager_queue_static;
static StaticQueue_t mfgtest_queue_static;
static StaticQueue_t sysinfo_queue_static;
static StaticQueue_t ui_queue_static;

// Response buffer for proto responses
#define PROTO_RESPONSE_BUF_SIZE 4096
static uint8_t proto_response_buffer[PROTO_RESPONSE_BUF_SIZE];

// Semaphore for synchronizing proto request/response
static rtos_semaphore_t proto_response_sem;

// Local buffer to copy response data (the callback's data pointer may be freed)
static uint8_t g_response_copy[PROTO_RESPONSE_BUF_SIZE];
static uint32_t g_response_size = 0;
static uint32_t g_response_seq = 0;
static uint32_t g_next_cmd_seq = 1;
static pthread_mutex_t g_response_mutex = PTHREAD_MUTEX_INITIALIZER;

// Mempool for IPC proto allocations
static mempool_t* ipc_mempool = NULL;

static uint32_t next_cmd_seq(void) {
  pthread_mutex_lock(&g_response_mutex);
  uint32_t seq = g_next_cmd_seq++;
  if (g_next_cmd_seq == 0) {
    g_next_cmd_seq = 1;
  }
  pthread_mutex_unlock(&g_response_mutex);
  return seq;
}

/**
 * Callback invoked when a proto response is ready.
 * This is called by firmware tasks via ipc_proto_send_response_buffer().
 *
 * IMPORTANT: We must COPY the data here, not just store the pointer, because
 * the data buffer may be freed/reused after this callback returns.
 */
static void proto_response_callback(uint8_t* data, uint32_t size) {
  pthread_mutex_lock(&g_response_mutex);

  g_response_seq = proto_get_last_rsp_seq();
  if (data && size > 0) {
    uint32_t copy_size = size;
    if (copy_size > sizeof(g_response_copy)) {
      copy_size = sizeof(g_response_copy);
      LOG("WARNING: Response truncated from %u to %zu bytes", size, sizeof(g_response_copy));
    }
    memcpy(g_response_copy, data, copy_size);
    g_response_size = copy_size;
  } else {
    g_response_size = 0;
  }
  pthread_mutex_unlock(&g_response_mutex);

  rtos_semaphore_give(&proto_response_sem);
}

/**
 * Initialize IPC ports and proto response infrastructure.
 * Must be called before any IPC operations.
 */
void ipc_ports_init(void) {
  rtos_semaphore_create(&proto_response_sem);

#define REGIONS(X)         \
  X(ipc_pool, r0, 4096, 4) \
  X(ipc_pool, r1, 1024, 8)
  ipc_mempool = mempool_create(ipc_pool);
#undef REGIONS

  ipc_proto_register_api(ipc_mempool, proto_response_buffer, proto_response_callback);

  _rtos_queue_create_static(&auth_queue, sizeof(ipc_ref_t), QUEUE_DEPTH, auth_queue_buf,
                            &auth_queue_static);
  _rtos_queue_create_static(&fwup_queue, sizeof(ipc_ref_t), QUEUE_DEPTH, fwup_queue_buf,
                            &fwup_queue_static);
  _rtos_queue_create_static(&key_manager_queue, sizeof(ipc_ref_t), QUEUE_DEPTH,
                            key_manager_queue_buf, &key_manager_queue_static);
  _rtos_queue_create_static(&mfgtest_queue, sizeof(ipc_ref_t), QUEUE_DEPTH, mfgtest_queue_buf,
                            &mfgtest_queue_static);
  _rtos_queue_create_static(&sysinfo_queue, sizeof(ipc_ref_t), QUEUE_DEPTH, sysinfo_queue_buf,
                            &sysinfo_queue_static);
  _rtos_queue_create_static(&ui_queue, sizeof(ipc_ref_t), QUEUE_DEPTH, ui_queue_buf,
                            &ui_queue_static);

  _ipc_register_port(auth_port, "auth_port", &auth_queue);
  _ipc_register_port(fwup_port, "fwup_port", &fwup_queue);
  _ipc_register_port(key_manager_port, "key_manager_port", &key_manager_queue);
  _ipc_register_port(mfgtest_port, "mfgtest_port", &mfgtest_queue);
  _ipc_register_port(sysinfo_port, "sysinfo_port", &sysinfo_queue);
  _ipc_register_port(ui_port, "ui_port", &ui_queue);
}

/**
 * Route a proto command via IPC and wait for response.
 *
 * This function sends the command to the appropriate task via IPC,
 * waits for the response callback, and copies the response back.
 *
 * @param pb_tag Protobuf message tag (fwpb_wallet_cmd_*_tag)
 * @param cmd Command buffer (encoded protobuf)
 * @param cmd_size Size of command buffer
 * @param rsp Response buffer (to fill with encoded protobuf)
 * @param rsp_size In/out: max size on input, actual size on output
 * @return true if command was handled, false otherwise
 */
bool ipc_ports_route_proto(uint16_t pb_tag, uint8_t* cmd, uint32_t cmd_size, uint8_t* rsp,
                           uint32_t* rsp_size) {
  const uint32_t seq = next_cmd_seq();
  pthread_mutex_lock(&g_response_mutex);
  g_response_size = 0;
  g_response_seq = 0;
  pthread_mutex_unlock(&g_response_mutex);

  proto_set_cmd_seq(seq);
  bool routed = ipc_proto_route(pb_tag, cmd, cmd_size, seq);

  if (!routed) {
    LOG("Proto tag=%u not routed", pb_tag);
    proto_retire_cmd_seq(seq);
    *rsp_size = 0;
    return false;
  }

  bool got_response = false;
  while (rtos_semaphore_take(&proto_response_sem, 30000)) {
    pthread_mutex_lock(&g_response_mutex);
    got_response = (g_response_seq == seq);
    pthread_mutex_unlock(&g_response_mutex);
    if (got_response) {
      break;
    }
    LOG("Dropping stale proto response for tag=%u", pb_tag);
  }

  if (!got_response) {
    LOG("Timeout waiting for proto response (tag=%u)", pb_tag);
    proto_retire_cmd_seq(seq);
    *rsp_size = 0;
    return false;
  }

  pthread_mutex_lock(&g_response_mutex);
  if (g_response_size > 0) {
    uint32_t copy_size = g_response_size;
    if (copy_size > *rsp_size) {
      copy_size = *rsp_size;
    }
    memcpy(rsp, g_response_copy, copy_size);
    *rsp_size = copy_size;
  } else {
    *rsp_size = 0;
  }
  pthread_mutex_unlock(&g_response_mutex);

  return true;
}

/**
 * Get the queue handle for a given port index.
 * Used by task threads that need direct queue access.
 *
 * @param port Port index
 * @return Queue handle, or NULL if invalid port
 */
rtos_queue_t* ipc_ports_get_queue(uint32_t port) {
  if (port == auth_port)
    return &auth_queue;
  if (port == fwup_port)
    return &fwup_queue;
  if (port == key_manager_port)
    return &key_manager_queue;
  if (port == mfgtest_port)
    return &mfgtest_queue;
  if (port == sysinfo_port)
    return &sysinfo_queue;
  if (port == ui_port)
    return &ui_queue;
  return NULL;
}
