#include "attributes.h"
#include "hal_nfc.h"
#include "hal_nfc_impl.h"
#include "hal_nfc_loopback_impl.h"
#include "shell_argparse.h"
#include "shell_cmd.h"

#include <stdint.h>

static struct {
  arg_str_t* loopback;
  arg_int_t* timeout;
  arg_end_t* end;
} hal_nfc_cmd_args;

static void hal_nfc_cmd_handler(int argc, char** argv) {
  const uint32_t res = shell_argparse_parse(argc, argv, (void**)&hal_nfc_cmd_args);
  uint32_t timeout = HAL_NFC_DEFAULT_READER_TIMEOUT_MS;

  if (res) {
    return;
  }

  if (hal_nfc_cmd_args.timeout->header.found) {
    timeout = hal_nfc_cmd_args.timeout->value;
  }

  if (hal_nfc_cmd_args.loopback->header.found) {
    if (hal_nfc_cmd_args.loopback->len != 1) {
      return;
    }

    bool found = false;
    if ((hal_nfc_cmd_args.loopback->value[0] == 'a') ||
        (hal_nfc_cmd_args.loopback->value[0] == 'A')) {
      found = hal_nfc_loopback_test(HAL_NFC_MODE_LOOPBACK_A, timeout);
    } else if ((hal_nfc_cmd_args.loopback->value[0] == 'b') ||
               (hal_nfc_cmd_args.loopback->value[0] == 'B')) {
      found = hal_nfc_loopback_test(HAL_NFC_MODE_LOOPBACK_B, timeout);
    }

    if (found) {
      printf("Card detected");
    } else {
      printf("No card detected");
    }
  }
}

static void hal_nfc_cmd_register(void) {
  hal_nfc_cmd_args.loopback = ARG_STR_OPT('l', "loopback", "perform a loopback test [A]");
  hal_nfc_cmd_args.timeout = ARG_INT_OPT('t', "timeout", "timeout (ms) for operation");
  hal_nfc_cmd_args.end = ARG_END();

  static shell_command_t SHARED_TASK_DATA hal_nfc_cmd = {
    .command = "nfc",
    .help = "NFC test commands",
    .handler = hal_nfc_cmd_handler,
    .argtable = (void*)&hal_nfc_cmd_args,
  };

  shell_command_register(&hal_nfc_cmd);
}

SHELL_CMD_REGISTER("nfc", hal_nfc_cmd_register);
