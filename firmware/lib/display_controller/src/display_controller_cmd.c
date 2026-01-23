#ifndef CONFIG_PROD

#include "display_controller.h"
#include "secure_rng.h"
#include "shell_cmd.h"
#include "ui_events.h"
#include "ui_messaging.h"

#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

static struct {
  arg_str_t* flow;
  arg_int_t* addr_len;
  arg_lit_t* list;
  arg_end_t* end;
} display_flow_args;

// Generate a random Bitcoin testnet address of specified length
static void generate_random_address(char* buffer, int length) {
  const char charset[] = "0123456789abcdefghijklmnopqrstuvwxyz";
  const int charset_size = sizeof(charset) - 1;

  // Start with "tb1" prefix (testnet bech32)
  strcpy(buffer, "tb1");

  // Fill the rest with random characters using secure RNG
  uint8_t random_bytes[180];  // Max address length
  if (!crypto_random(random_bytes, length - 3)) {
    // Fallback to pattern if RNG fails
    for (int i = 3; i < length; i++) {
      buffer[i] = 'x';
    }
  } else {
    for (int i = 3; i < length; i++) {
      buffer[i] = charset[random_bytes[i - 3] % charset_size];
    }
  }
  buffer[length] = '\0';
}

static void cmd_display_flow_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&display_flow_args);
  if (nerrors) {
    return;
  }

  // Handle --list
  if (display_flow_args.list->header.found) {
    printf("Available flows:\n");
    printf("  tx-send [-a LEN]      - Send transaction (optional: -a/--addr-len)\n");
    printf("  tx-receive [-a LEN]   - Receive transaction (optional: -a/--addr-len)\n");
    printf("  fwup                  - Firmware update with dummy data\n");
    printf("\nExamples:\n");
    printf("  flow tx-send              - Use default 60-char address (single page)\n");
    printf("  flow tx-send -a 62        - Use 62-char address (2 pages)\n");
    printf("  flow tx-send --addr-len 160 - Use 160-char address (3 pages)\n");
    return;
  }

  // Parse flow name
  if (!display_flow_args.flow->header.found) {
    printf("ERROR: Flow name required. Use --list to see options.\n");
    printf("Usage: flow <flow> [-a addr_len]\n");
    return;
  }

  const char* flow_name = display_flow_args.flow->value;

  // Get address length argument if provided
  int addr_len = 60;  // Default address length (single page)
  if (display_flow_args.addr_len->header.found) {
    addr_len = display_flow_args.addr_len->value;
    if (addr_len < 16 || addr_len > 180) {
      printf("ERROR: Address length must be between 16 and 180\n");
      return;
    }
  }

  // Handle send transaction
  if (strcasecmp(flow_name, "tx-send") == 0 || strcasecmp(flow_name, "send") == 0) {
    send_transaction_data_t send_data = {0};
    generate_random_address(send_data.address, addr_len);
    strncpy(send_data.amount_sats, "0.00123456", sizeof(send_data.amount_sats) - 1);
    strncpy(send_data.fee_sats, "0.00001234", sizeof(send_data.fee_sats) - 1);

    UI_SHOW_EVENT_WITH_DATA(UI_EVENT_START_SEND_TRANSACTION, &send_data, sizeof(send_data));
    printf("Started send transaction flow with %d-char address\n", addr_len);
    return;
  }

  // Handle receive transaction
  if (strcasecmp(flow_name, "tx-receive") == 0 || strcasecmp(flow_name, "receive") == 0) {
    receive_transaction_data_t receive_data = {0};
    generate_random_address(receive_data.address, addr_len);

    UI_SHOW_EVENT_WITH_DATA(UI_EVENT_START_RECEIVE_TRANSACTION, &receive_data,
                            sizeof(receive_data));
    printf("Started receive transaction flow with %d-char address\n", addr_len);
    return;
  }

  // Handle firmware update
  if (strcasecmp(flow_name, "fwup") == 0 || strcasecmp(flow_name, "firmware") == 0) {
    firmware_update_data_t fwup_data = {
      .digest = "abc123def456789012345678901234567890123456789012345678901234567890",
      .version = 12345,
      .size = 524288,
    };

    UI_SHOW_EVENT_WITH_DATA(UI_EVENT_FWUP_START, &fwup_data, sizeof(fwup_data));
    printf("Started firmware update flow with dummy data\n");
    return;
  }

  // Unknown flow
  printf("ERROR: Unknown flow '%s'. Use --list to see available flows.\n", flow_name);
}

static void cmd_display_flow_register(void) {
  display_flow_args.flow = ARG_STR_OPT(0, NULL, "Flow name: tx-send, tx-receive, fwup");
  display_flow_args.addr_len = ARG_INT_OPT('a', "addr-len", "Optional address length (16-180)");
  display_flow_args.list = ARG_LIT_OPT('l', "list", "List available flows");
  display_flow_args.end = ARG_END();

  static shell_command_t display_flow_cmd = {
    .command = "flow",
    .help = "Trigger display flows (use --list for options)",
    .handler = cmd_display_flow_run,
    .argtable = &display_flow_args,
  };
  shell_command_register(&display_flow_cmd);
}
SHELL_CMD_REGISTER("flow", cmd_display_flow_register);

// Privileged action command
static void cmd_pa_run(int argc, char** argv) {
  if (argc < 2) {
    printf("ERROR: Type required\n");
    printf("Usage: pa <type> [args]\n");
    printf("Types:\n");
    printf("  address <address_len>         - Confirm address with generated address\n");
    printf("  string <value>                - Confirm string value\n");
    printf("  fwup <hash> <version>         - Firmware update\n");
    printf("  wipe                          - Wipe device\n");
    return;
  }

  const char* type = argv[1];
  fwpb_display_params_privileged_action params = {0};

  // Check for optional -t flag
  const char* title = NULL;
  int arg_offset = 2;
  if (argc > 2 && strcmp(argv[2], "-t") == 0 && argc > 3) {
    title = argv[3];
    arg_offset = 4;
  }

  if (strcasecmp(type, "address") == 0) {
    if (argc < arg_offset + 1) {
      printf("ERROR: 'address' requires: pa address [-t title] <address_len>\n");
      return;
    }
    // Parse address length
    int addr_len = atoi(argv[arg_offset]);
    if (addr_len < 16 || addr_len > 180) {
      printf("ERROR: Address length must be between 16 and 180\n");
      return;
    }
    strncpy(params.title, title ? title : "CONFIRM ADDRESS", sizeof(params.title) - 1);
    params.which_action = fwpb_display_params_privileged_action_confirm_address_tag;

    // Generate random address
    char generated_address[181];
    generate_random_address(generated_address, addr_len);
    strncpy(params.action.confirm_address.address, generated_address, 127);
  } else if (strcasecmp(type, "string") == 0) {
    if (argc < arg_offset + 1) {
      printf("ERROR: 'string' requires: pa string [-t title] <value>\n");
      return;
    }
    strncpy(params.title, title ? title : "CONFIRM", sizeof(params.title) - 1);
    params.which_action = fwpb_display_params_privileged_action_confirm_string_tag;
    strncpy(params.action.confirm_string.value, argv[arg_offset], 127);
  } else if (strcasecmp(type, "fwup") == 0) {
    if (argc < arg_offset + 2) {
      printf("ERROR: 'fwup' requires: pa fwup [-t title] <hash> <version>\n");
      return;
    }
    strncpy(params.title, title ? title : "FIRMWARE UPDATE", sizeof(params.title) - 1);
    params.which_action = fwpb_display_params_privileged_action_fwup_tag;
    strncpy(params.action.fwup.hash, argv[arg_offset], 64);
    strncpy(params.action.fwup.version, argv[arg_offset + 1], 31);
  } else if (strcasecmp(type, "wipe") == 0) {
    strncpy(params.title, title ? title : "WIPE DEVICE", sizeof(params.title) - 1);
    params.which_action = fwpb_display_params_privileged_action_confirm_action_tag;
    params.action.confirm_action.action_type =
      fwpb_display_privileged_action_type_DISPLAY_PRIVILEGED_ACTION_WIPE_DEVICE;
  } else {
    printf("ERROR: Unknown type '%s'\n", type);
    printf("Valid types: address, string, fwup, wipe\n");
    return;
  }

  printf("Sending privileged action: type=%s, size=%lu\n", type, (unsigned long)sizeof(params));
  UI_SHOW_EVENT_WITH_DATA(UI_EVENT_START_PRIVILEGED_ACTION, &params, sizeof(params));
  printf("Started privileged action: %s\n", type);
}

static void cmd_pa_register(void) {
  static shell_command_t pa_cmd = {
    .command = "pa",
    .help = "Privileged action (e.g. pa wipe, pa value <val>, pa fwup <hash> <ver>)",
    .handler = cmd_pa_run,
  };
  shell_command_register(&pa_cmd);
}
SHELL_CMD_REGISTER("pa", cmd_pa_register);

#endif  // CONFIG_PROD
