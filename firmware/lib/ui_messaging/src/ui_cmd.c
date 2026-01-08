#include "ipc.h"
#include "shell_cmd.h"
#include "ui_events.h"
#include "ui_messaging.h"

#include <stddef.h>
#include <stdio.h>

static struct {
  arg_int_t* event;
  arg_lit_t* clear;
  arg_end_t* end;
} ui_event_args;

static void cmd_ui_event_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&ui_event_args);
  if (nerrors) {
    return;
  }

  if (ui_event_args.clear->header.found) {
    UI_SHOW_EVENT(UI_EVENT_LED_CLEAR);
    printf("UI cleared\n");
  } else if (ui_event_args.event->header.found) {
    ui_event_type_t event = (ui_event_type_t)ui_event_args.event->value;
    UI_SHOW_EVENT(event);
    printf("Sent UI event %d\n", event);
  } else {
    printf("Use --event <num> to send an event, or --clear to clear UI\n");
  }
}

static void cmd_ui_event_register(void) {
  ui_event_args.event = ARG_INT_OPT('e', "event", "UI event number to send");
  ui_event_args.clear = ARG_LIT_OPT('c', "clear", "Clear UI state");
  ui_event_args.end = ARG_END();

  static shell_command_t ui_event_cmd = {
    .command = "ui-event",
    .help = "Send UI events for testing",
    .handler = cmd_ui_event_run,
    .argtable = &ui_event_args,
  };
  shell_command_register(&ui_event_cmd);
}
SHELL_CMD_REGISTER("ui-event", cmd_ui_event_register);
