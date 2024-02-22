#include "animation.h"
#include "ipc.h"
#include "shell_cmd.h"

#include <stddef.h>
#include <stdio.h>

static struct {
  arg_lit_t* off;
  arg_int_t* pattern;
  arg_lit_t* stop;
  arg_end_t* end;
} led_animation_args;

static void cmd_led_animation_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&led_animation_args);
  if (nerrors) {
    return;
  }

  if (led_animation_args.off->header.found) {
    static led_set_rest_animation_t LED_TASK_DATA rest_msg = {.animation = (uint32_t)ANI_OFF};
    ipc_send(led_port, &rest_msg, sizeof(rest_msg), IPC_LED_SET_REST_ANIMATION);
    ipc_send_empty(led_port, IPC_LED_STOP_ANIMATION);
  } else if (led_animation_args.pattern->header.found) {
    static led_start_animation_t LED_TASK_DATA msg = {.animation = 0, .immediate = true};
    msg.animation = led_animation_args.pattern->value;
    ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
  } else if (led_animation_args.stop->header.found) {
    ipc_send_empty(led_port, IPC_LED_STOP_ANIMATION);
  }
}

static void cmd_led_animation_register(void) {
  led_animation_args.off = ARG_LIT_OPT('o', "off", "all off");
  led_animation_args.pattern = ARG_INT_OPT('p', "pattern", "play a specified pattern");
  led_animation_args.stop = ARG_LIT_OPT('s', "stop", "stop the current animation");
  led_animation_args.end = ARG_END();

  static shell_command_t led_animation_cmd = {
    .command = "led-anim",
    .help = "plays led animations",
    .handler = cmd_led_animation_run,
    .argtable = &led_animation_args,
  };
  shell_command_register(&led_animation_cmd);
}
SHELL_CMD_REGISTER("led-anim", cmd_led_animation_register);
