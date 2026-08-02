/**
 * @file wca_glue.c
 * @brief POSIX glue layer for lib/wca in core-sim
 *
 * Provides:
 * - Typed message I/O (read_typed_message, write_typed_message)
 * - Proto handler for lib/wca that routes commands to handlers
 * - Initialization for lib/wca with POSIX hooks
 */

#include "attributes.h"  // For SHARED_TASK_BSS/DATA macros used by mempool
#include "device_state.h"
#include "handler_device.h"  // For stdio_query_authentication_handler
#include "handlers.h"
#include "ipc_ports.h"  // For IPC routing
#include "mempool.h"
#include "stdio_defs.h"  // For LOG
#include "wallet.pb.h"   // For fwpb_wallet_cmd_*_tag definitions
#include "wca.h"
#include "wca_sim.h"

#include <arpa/inet.h>

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

// Response buffer size
#define RESPONSE_BUF_SIZE 16384

/**
 * Read exactly `len` bytes from stdin.
 * Returns true on success, false on EOF or error.
 */
static bool read_exact(uint8_t* buf, size_t len) {
  size_t total = 0;
  while (total < len) {
    ssize_t n = read(STDIN_FILENO, buf + total, len - total);
    if (n <= 0) {
      if (n == 0) {
        LOG("EOF on stdin");
      } else {
        perror("read");
      }
      return false;
    }
    total += n;
  }
  return true;
}

/**
 * Write exactly `len` bytes to stdout.
 * Returns true on success, false on error.
 */
static bool write_exact(const uint8_t* buf, size_t len) {
  size_t total = 0;
  while (total < len) {
    ssize_t n = write(STDOUT_FILENO, buf + total, len - total);
    if (n <= 0) {
      perror("write");
      return false;
    }
    total += n;
  }
  return true;
}

/**
 * Read a typed length-prefixed message from stdin.
 * Format: [1-byte type][4-byte BE length][payload]
 */
ssize_t read_typed_message(uint8_t* msg_type, uint8_t* buf, size_t buf_size) {
  if (!read_exact(msg_type, 1)) {
    return -1;
  }

  uint32_t len_be;
  if (!read_exact((uint8_t*)&len_be, sizeof(len_be))) {
    return -1;
  }

  uint32_t len = ntohl(len_be);
  if (len > buf_size) {
    LOG("Message too large: %u > %zu", len, buf_size);
    return -1;
  }

  if (len == 0) {
    return 0;
  }

  if (!read_exact(buf, len)) {
    return -1;
  }

  return (ssize_t)len;
}

/**
 * Write a typed length-prefixed message to stdout.
 * Format: [1-byte type][4-byte BE length][payload]
 */
bool write_typed_message(uint8_t msg_type, const uint8_t* buf, size_t len) {
  if (!write_exact(&msg_type, 1)) {
    return false;
  }
  uint32_t len_be = htonl((uint32_t)len);
  if (!write_exact((uint8_t*)&len_be, sizeof(len_be))) {
    return false;
  }
  if (len > 0 && !write_exact(buf, len)) {
    return false;
  }
  return true;
}

// Static mempool for lib/wca
static mempool_t* wca_mempool = NULL;

// Semaphore callbacks for lib/wca (no-op for synchronous POSIX model)
static bool wca_sem_take(void) {
  return true;
}
static bool wca_sem_give(void) {
  return true;
}

/**
 * Proto handler callback for lib/wca.
 * Routes incoming proto commands via IPC to the appropriate task.
 *
 * This replaces the direct switch-based routing with IPC-based routing
 * that sends commands to real firmware task threads (key_manager, fwup, sysinfo).
 *
 * Some commands (like query_authentication) are handled directly since they
 * require the auth task which isn't fully implemented.
 */
// Command tag name for logging (selected common commands)
static const char* tag_name(uint32_t tag) {
  switch (tag) {
    case fwpb_wallet_cmd_start_fingerprint_enrollment_cmd_tag:
      return "StartFingerprintEnrollment";
    case fwpb_wallet_cmd_get_fingerprint_enrollment_status_cmd_tag:
      return "GetFingerprintEnrollmentStatus";
    case fwpb_wallet_cmd_get_enrolled_fingerprints_cmd_tag:
      return "GetEnrolledFingerprints";
    case fwpb_wallet_cmd_cancel_fingerprint_enrollment_cmd_tag:
      return "CancelFingerprintEnrollment";
    case fwpb_wallet_cmd_query_authentication_cmd_tag:
      return "QueryAuthentication";
    case fwpb_wallet_cmd_device_info_cmd_tag:
      return "DeviceInfo";
    case fwpb_wallet_cmd_seal_csek_cmd_tag:
      return "SealCsek";
    case fwpb_wallet_cmd_unseal_csek_cmd_tag:
      return "UnsealCsek";
    case fwpb_wallet_cmd_wipe_state_cmd_tag:
      return "WipeState";
    default:
      return NULL;
  }
}

static bool wca_proto_handler(uint32_t tag, uint8_t* cmd, uint32_t cmd_size, uint8_t* rsp,
                              uint32_t* rsp_size) {
  const char* name = tag_name(tag);
  if (name) {
    LOG("WCA proto: %s (tag=%u, %u bytes)", name, tag, cmd_size);
  } else {
    LOG("WCA proto: tag=%u (%u bytes)", tag, cmd_size);
  }

  // Check authorization - mirrors real firmware IPC auth check
  if (!device_state_check_command_auth(tag)) {
    LOG("WCA proto: auth check FAILED for tag=%u", tag);
    if (device_state_build_unauth_response(tag, rsp, rsp_size)) {
      return true;
    }
    // Fall through if response build fails
  }

  // Handle commands that aren't routed via IPC (auth task not implemented)
  switch (tag) {
    case fwpb_wallet_cmd_query_authentication_cmd_tag:
      return stdio_query_authentication_handler(rsp, rsp_size);
    default:
      break;
  }

  // Route the command via IPC to the appropriate task
  // The IPC routing uses the same tag-to-port mapping as firmware's ipc_proto_route()
  bool handled = ipc_ports_route_proto((uint16_t)tag, cmd, cmd_size, rsp, rsp_size);

  if (!handled) {
    LOG("Command tag=%u not handled by IPC routing", tag);
    *rsp_size = 0;
  }

  return handled;
}

/**
 * Initialize lib/wca for POSIX core-sim.
 * Sets up the mempool and registers the proto handler.
 */
void posix_wca_init(void) {
#define REGIONS(X) X(wca_pool, r0, 4096, 4)
  wca_mempool = mempool_create(wca_pool);
#undef REGIONS

  wca_api_t api = {
    .mempool = wca_mempool,
    .sem_take = wca_sem_take,
    .sem_give = wca_sem_give,
  };
  wca_init(&api);
  wca_sim_set_proto_handler(wca_proto_handler);

  LOG("lib/wca initialized with POSIX proto handler");
}
