#include "hex.h"
#include "printf.h"
#include "shell_cmd.h"
#include "wallet.pb.h"
#include "wca.h"

#include <string.h>

#define MAX_COMMAND_LEN  (size_t)(fwpb_wallet_cmd_size + 2)
#define MAX_RESPONSE_LEN (size_t)(fwpb_wallet_rsp_size + 2)
// Note: command data length is limited by SHELL_MAX_LINE_LEN

static struct {
  arg_hex_t* data;
  arg_end_t* end;
} cmd_wca_args;

static uint8_t command_data[MAX_COMMAND_LEN] = {0};
static uint8_t response_data[MAX_RESPONSE_LEN] = {0};

static void cmd_wca_register(void);
static void cmd_wca_run(int argc, char** argv);

static void cmd_wca_register(void) {
  cmd_wca_args.data =
    ARG_HEX_REQ(0, NULL, "binary wca command data", command_data, MAX_COMMAND_LEN);
  cmd_wca_args.end = ARG_END();

  static shell_command_t cmd = {
    .command = "wca",
    .help = "wca interface",
    .handler = cmd_wca_run,
    .argtable = &cmd_wca_args,
  };
  shell_command_register(&cmd);
}
SHELL_CMD_REGISTER("wca", cmd_wca_register);

static void cmd_wca_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&cmd_wca_args);

  if (nerrors) {
    return;
  }

  if (cmd_wca_args.data->header.found) {
    const uint32_t cmd_len = cmd_wca_args.data->len;

    if (cmd_len == 0) {
      printf("err:length\n");
      return;
    }

    // Route proto and print hex response
    uint32_t rsp_len = 0;
    memset(response_data, 0, MAX_RESPONSE_LEN);
    if (wca_proto(command_data, cmd_len, response_data, &rsp_len)) {
      printf("ok:");
      dumphex(response_data, rsp_len);
    } else {
      printf("err:wca_proto\n");
    }
  }
}
