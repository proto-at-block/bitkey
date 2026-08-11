/**
 * stdio-server: WCA stdio transport server for POSIX builds.
 *
 * Wire Protocol:
 *   Request:  [1-byte type][4-byte BE length][payload]
 *   Response: [1-byte type][4-byte BE length][payload]
 *
 * This server implements the WCA APDU protocol for stdio transport.
 * Commands are routed via IPC to real firmware tasks:
 *   - key_manager_task: wallet/crypto operations
 *   - fwup_task: firmware update
 *   - sysinfo_task: device info, wipe_state
 *
 * Debug output goes to stderr; stdout is reserved for the protocol.
 */

#include "auth.h"  // deauthenticate()
#include "auth_sim.h"
#include "confirmation_manager.h"  // Real confirmation manager for W3 flows
#include "device_state.h"          // Device state persistence
#include "display_controller.h"    // Display controller for UI
#include "display_controller_internal.h"
#include "handler_emulator.h"
#include "ipc.h"                // ipc_send_empty()
#include "ipc_ports.h"          // IPC port initialization
#include "posix/stubs.h"        // init_secutils_if_needed
#include "sim_provisioning.h"   // Runtime provisioning for attestation
#include "stdio_defs.h"         // IO functions, LOG macro, proto tags
#include "stdio_tasks.h"        // Real firmware task management
#include "uxc_socket_server.h"  // UXC socket server for ui-simulate
#include "uxc_transport.h"      // UXC message routing for ui-simulate
#include "wallet_emulator.h"
#include "wca.h"  // lib/wca: wca_handle_command

#include <sys/select.h>
#include <sys/types.h>

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

// WCA Version - matches firmware
#define WCA_VERSION 1

// Buffer sizes
#define MAX_APDU_SIZE 4096

// UI port for ui-simulate connection (0 = disabled)
static int g_ui_port = 0;
static bool g_initial_screen_sent = false;
static bool g_stdin_eof = false;

extern display_controller_t controller;

static void parse_args(int argc, char* argv[]) {
  for (int i = 1; i < argc; i++) {
    if (strcmp(argv[i], "--ui-port") == 0 && i + 1 < argc) {
      g_ui_port = atoi(argv[++i]);
    }
  }
}

// Configure fd_set for select(), returns max_fd
static int setup_select_fds(fd_set* read_fds, int server_fd) {
  FD_ZERO(read_fds);
  int max_fd = -1;

  if (!g_stdin_eof) {
    FD_SET(STDIN_FILENO, read_fds);
    max_fd = STDIN_FILENO;
  }

  // Listen for new connections if server is active but no client connected
  if (server_fd >= 0 && !uxc_socket_is_connected()) {
    FD_SET(server_fd, read_fds);
    if (server_fd > max_fd) {
      max_fd = server_fd;
    }
  }

  return max_fd;
}

// Handle incoming ui-simulate connection
static void handle_socket_accept(int server_fd, fd_set* read_fds) {
  if (server_fd < 0 || !FD_ISSET(server_fd, read_fds)) {
    return;
  }

  uxc_socket_accept();
  if (uxc_socket_is_connected() && !g_initial_screen_sent) {
    LOG("ui-simulate connected, sending initial screen");
    display_controller_show_initial_screen();
    g_initial_screen_sent = true;
  }
}

// Set a single-byte error response
static void set_error_response(uint8_t* rsp_buf, uint32_t* rsp_len) {
  rsp_buf[0] = 0;
  *rsp_len = 1;
}

// Dispatch command to appropriate handler based on message type
static void dispatch_command(uint8_t msg_type, uint8_t* cmd_buf, uint32_t cmd_len, uint8_t* rsp_buf,
                             uint32_t* rsp_len) {
  switch (msg_type) {
    case MSG_TYPE_WCA:
      LOG("WCA command received (%u bytes)", cmd_len);
      wca_handle_command(cmd_buf, cmd_len, rsp_buf, rsp_len);
      LOG("WCA response: %u bytes, status byte=0x%02x", *rsp_len, rsp_buf[0]);
      break;

    case MSG_TYPE_UI:
      if (cmd_len < 1) {
        LOG("UI command too short");
        set_error_response(rsp_buf, rsp_len);
      } else {
        stdio_handle_emulator_command(cmd_buf[0], cmd_buf + 1, cmd_len - 1, rsp_buf, rsp_len);
      }
      break;

    default:
      LOG("Unknown message type: 0x%02x", msg_type);
      set_error_response(rsp_buf, rsp_len);
      break;
  }
}

// Process a command from stdin, returns false if main loop should exit
static bool handle_stdin_command(fd_set* read_fds, uint8_t* cmd_buf, size_t cmd_buf_size,
                                 uint8_t* rsp_buf, size_t rsp_buf_size) {
  if (g_stdin_eof || !FD_ISSET(STDIN_FILENO, read_fds)) {
    return true;
  }

  uint8_t msg_type;
  ssize_t cmd_len = read_typed_message(&msg_type, cmd_buf, cmd_buf_size);
  if (cmd_len < 0) {
    LOG("EOF on stdin");
    if (g_ui_port <= 0) {
      LOG("Exiting due to read error or EOF");
      return false;
    }
    g_stdin_eof = true;
    return true;
  }

  uint32_t rsp_len = (uint32_t)rsp_buf_size;
  dispatch_command(msg_type, cmd_buf, (uint32_t)cmd_len, rsp_buf, &rsp_len);

  if (!write_typed_message(msg_type, rsp_buf, rsp_len)) {
    LOG("Failed to write response");
    return false;
  }

  return true;
}

// Handler for display_action messages from ui-simulate
static void handle_display_action(const fwpb_uxc_msg_device* msg, void* context) {
  (void)context;
  const fwpb_display_action_display_action_type action = msg->msg.display_action.action;

  switch (action) {
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE:
      display_controller_handle_action_approve();
      confirmation_manager_approve();
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL:
      display_controller_handle_action_cancel();
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK:
      display_controller_handle_action_back();
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_EXIT:
      display_controller_handle_action_exit();
      break;
    case fwpb_display_action_display_action_type_DISPLAY_ACTION_START_ENROLLMENT:
      display_controller_handle_action_start_enrollment();
      core_sim_start_fingerprint_enrollment(controller.nav.fingerprint.slot_index, "Fingerprint");
      break;
    default:
      LOG("Unhandled display action: %d", action);
      break;
  }
}

int main(int argc, char* argv[]) {
  parse_args(argc, argv);

  LOG("Starting core-sim (WCA version %d)", WCA_VERSION);

  // Initialize device state persistence (loads saved state if available)
  emu_state_init();

  // Initialize secutils (required for secure random in WKEK generation)
  init_secutils_if_needed();

  // Initialize provisioning if CORE_SIM_PROVISION=1 env var is set
  // This generates a device identity (P-256 keypair + certificate) at startup
  sim_provision_init();

  // Initialize bitlog (required for FWUP operations that call BITLOG_EVENT)
  init_bitlog_if_needed();

  // Initialize the wallet emulator (LittleFS + WKEK + seed storage)
  emu_wallet_result_t wallet_result = fwup_emu_wallet_init(NULL);
  if (wallet_result != EMU_WALLET_OK) {
    LOG("WARNING: Wallet emulator init failed: %d", wallet_result);
  }

  // Initialize lib/wca first - wca_init() registers its own ipc_proto callback
  posix_wca_init();

  // Initialize IPC ports AFTER wca - this registers our proto_response_callback,
  // overwriting wca's callback. This is intentional: we route commands via IPC
  // to real firmware tasks and need our callback to receive the responses.
  ipc_ports_init();

  // Initialize confirmation manager (requires mutex from rtos)
  confirmation_manager_init();

  // Start real firmware tasks (they receive IPC messages and process them)
  stdio_tasks_start();

  // Give tasks time to initialize and reach their main loops
  usleep(500000);  // 500ms

  // Initialize display controller (must be before stdio_emulator_init which accesses controller)
  display_controller_init();

  // Initialize emulator handler (for test introspection)
  stdio_emulator_init();

  // Initialize UXC socket server for ui-simulate connection
  if (g_ui_port > 0) {
    if (uxc_socket_init(g_ui_port)) {
      LOG("UXC socket server listening on port %d", g_ui_port);
      // Initialize transport layer and register handlers
      uxc_transport_init();
      uxc_transport_register_handler(fwpb_uxc_msg_device_display_action_tag, handle_display_action,
                                     NULL);
    } else {
      LOG("WARNING: Failed to start UXC socket server on port %d", g_ui_port);
    }
  }

  uint8_t cmd_buf[MAX_APDU_SIZE];
  uint8_t rsp_buf[MAX_APDU_SIZE];

  while (true) {
    int server_fd = (g_ui_port > 0) ? uxc_socket_get_server_fd() : -1;

    fd_set read_fds;
    int max_fd = setup_select_fds(&read_fds, server_fd);

    struct timeval timeout = {.tv_sec = 0, .tv_usec = 20000};  // 20ms to match firmware tick rate
    int ready = select(max_fd + 1, &read_fds, NULL, NULL, &timeout);
    if (ready < 0) {
      LOG("select() error");
      break;
    }

    handle_socket_accept(server_fd, &read_fds);

    if (g_ui_port > 0) {
      uxc_transport_poll();
      // Periodic tick for display controller (enables timer-based transitions)
      display_controller_tick();
    }

    if (!handle_stdin_command(&read_fds, cmd_buf, sizeof(cmd_buf), rsp_buf, sizeof(rsp_buf))) {
      break;
    }
  }

  stdio_tasks_stop();

  if (g_ui_port > 0) {
    uxc_socket_cleanup();
  }

  return 0;
}
