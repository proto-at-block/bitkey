#include "bitlog.h"
#include "log.h"
#include "rtos.h"
#include "shell_cmd.h"

static struct {
  arg_int_t* drain;
  arg_int_t* create;
  arg_end_t* end;
} bitlog_cmd_args;

static void bitlog_cmd_register(void);
static void bitlog_cmd_handler(int argc, char** argv);

#define DRAIN_SIZE (10)

static void bitlog_cmd_register(void) {
  bitlog_cmd_args.drain = ARG_INT_OPT('d', "drain", "drain and print up to 10 bitlogs");
  bitlog_cmd_args.create = ARG_INT_OPT('c', "create", "create event(s)");
  bitlog_cmd_args.end = ARG_END();

  static shell_command_t bitlog_cmd = {
    .command = "bitlog",
    .help = "bitlog commands",
    .handler = bitlog_cmd_handler,
    .argtable = &bitlog_cmd_args,
  };

  shell_command_register(&bitlog_cmd);
}
SHELL_CMD_REGISTER("bitlog", bitlog_cmd_register);

static void bitlog_cmd_handler(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&bitlog_cmd_args);

  if (nerrors) {
    return;
  }

  if (bitlog_cmd_args.drain->header.found) {
    uint8_t drain_buf[DRAIN_SIZE * sizeof(bitlog_event_t)] = {0};
    uint32_t len = bitlog_cmd_args.drain->value * sizeof(bitlog_event_t);
    uint32_t written = 0;
    bitlog_drain(drain_buf, len, &written);
    for (uint32_t i = 0; i < len; i += sizeof(bitlog_event_t)) {
      bitlog_print((bitlog_event_t*)&drain_buf[i]);
    }
  } else if (bitlog_cmd_args.create->header.found) {
    static uint8_t status_counter = 0;
    for (int i = 0; i < bitlog_cmd_args.create->value; i++) {
      BITLOG_EVENT(critical_error, status_counter++);
    }
  }
}
