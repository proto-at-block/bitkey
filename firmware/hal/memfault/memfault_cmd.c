#include "filesystem.h"
#include "log.h"
#include "memfault/components.h"
#include "rtos.h"
#include "shell_cmd.h"
#include "telemetry_storage.h"

static struct {
  arg_lit_t* logging;
  arg_lit_t* coredump;
  arg_lit_t* heartbeat;
  arg_lit_t* trace;
  arg_lit_t* reboot;
  arg_lit_t* crash;
  arg_lit_t* dump;
  arg_lit_t* telemetry_logs;
  arg_end_t* end;
} memfault_cmd_args;

static void memfault_cmd_register(void);
static void memfault_cmd_handler(int argc, char** argv);

static void memfault_cmd_register(void) {
  memfault_cmd_args.logging = ARG_LIT_OPT('l', "logging", "logging tests");
  memfault_cmd_args.coredump = ARG_LIT_OPT('c', "coredump", "coredump tests");
  memfault_cmd_args.heartbeat = ARG_LIT_OPT('b', "heartbeat", "trigger heartbeat");
  memfault_cmd_args.trace = ARG_LIT_OPT('t', "trace", "trace tests");
  memfault_cmd_args.reboot = ARG_LIT_OPT('r', "reboot", "reboot tests");
  memfault_cmd_args.crash = ARG_LIT_OPT('s', "crash", "crash tests");
  memfault_cmd_args.dump = ARG_LIT_OPT('d', "dump", "dump memfault data");
  memfault_cmd_args.telemetry_logs = ARG_LIT_OPT('y', "telemetry-logs", "save logs to flash");
  memfault_cmd_args.end = ARG_END();

  static shell_command_t memfault_cmd = {
    .command = "memfault",
    .help = "memfault commands",
    .handler = memfault_cmd_handler,
    .argtable = &memfault_cmd_args,
  };

  shell_command_register(&memfault_cmd);
}
SHELL_CMD_REGISTER("memfault", memfault_cmd_register);

static void memfault_cmd_handler(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&memfault_cmd_args);

  if (nerrors) {
    return;
  }

  if (memfault_cmd_args.logging->header.found) {
    MEMFAULT_LOG_DEBUG("Debug log!");
    MEMFAULT_LOG_INFO("Info log!");
    MEMFAULT_LOG_WARN("Warning log!");
    MEMFAULT_LOG_ERROR("Error log!");

    LOGD("Debug log!");
    LOGI("Info log!");
    LOGW("Warning log!");
    LOGE("Error log!");
  } else if (memfault_cmd_args.coredump->header.found) {
    rtos_thread_enter_critical();
    memfault_coredump_storage_debug_test_begin();
    rtos_thread_exit_critical();
    memfault_coredump_storage_debug_test_finish();
  } else if (memfault_cmd_args.heartbeat->header.found) {
    memfault_metrics_heartbeat_debug_trigger();
    memfault_metrics_heartbeat_debug_print();
  } else if (memfault_cmd_args.trace->header.found) {
    MEMFAULT_TRACE_EVENT(critical_error);
#if 0
// Don't actually include this in the build, but change the #if above for dev.
    MEMFAULT_TRACE_EVENT_WITH_STATUS(critical_error, 1234);
    MEMFAULT_TRACE_EVENT_WITH_LOG(critical_error, ".");
    MEMFAULT_TRACE_EVENT_WITH_LOG(critical_error, "A test error trace!");
    MEMFAULT_TRACE_EVENT_WITH_LOG(
      critical_error,
      "A really really really really really really really really really really long string");
    MEMFAULT_TRACE_EVENT_WITH_LOG(critical_error, "A format string %d %d %d", 1, 2, 3);
#endif
  } else if (memfault_cmd_args.reboot->header.found) {
    memfault_reboot_tracking_mark_reset_imminent(kMfltRebootReason_UserReset, NULL);
    memfault_platform_reboot();
  } else if (memfault_cmd_args.crash->header.found) {
    LOGE("1 This is a log.");
    LOGE("2 This is a log.");
    LOGE("3 This is a log.");
    LOGE("4 This is a log.");
    LOGE("5 This is a log.");
    LOGE("6 This is a log.");
    LOGE("7 This is a log.");
    LOGE("8 This is a log.");
    LOGE("9 This is a log.");
    LOGE("10 This is a log.");
    LOGE("11 This is a log.");
    LOGE("12 This is a log.");
    LOGE("13 This is a log.");
    LOGE("15 This is a log.");
    LOGE("16 This is a log.");
    LOGE("17 This is a log.");
    LOGE("18 This is a log.");
    LOGE("19 This is a log.");
    LOGE("20 This is a log.");
    LOGE("21 This is a log.");
    ASSERT(false);
  } else if (memfault_cmd_args.dump->header.found) {
    memfault_data_export_dump_chunks();
  }
}
