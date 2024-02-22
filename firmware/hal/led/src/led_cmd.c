#include "led.h"
#include "printf.h"
#include "shell_cmd.h"

#include <string.h>

static struct {
  arg_str_t* color;
  arg_int_t* duty_cycle;
  arg_bool_t* state;
  arg_end_t* end;
} led_cmd_args;

static void led_cmd_handler(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&led_cmd_args);

  if (nerrors) {
    return;
  }

  const char* color = led_cmd_args.color->value;
  const int duty_cycle = led_cmd_args.duty_cycle->value;
  const bool state = led_cmd_args.state->value;

  if (strlen(color) != 1) {
    printf("Invalid LED color %s\n", color);
    return;
  }

  switch ((char)color[0]) {
    case 'r':
      if (state)
        led_on(LED_R, duty_cycle);
      else
        led_off(LED_R);
      break;
    case 'g':
      if (state)
        led_on(LED_G, duty_cycle);
      else
        led_off(LED_G);
      break;
    case 'b':
      if (state)
        led_on(LED_B, duty_cycle);
      else
        led_off(LED_B);
      break;
  }
}

static void led_cmd_register(void) {
  led_cmd_args.color = ARG_STR_REQ('c', "color", "r/g/b");
  led_cmd_args.duty_cycle = ARG_INT_REQ('d', "duty-cycle", "0-100");
  led_cmd_args.state = ARG_BOOL_REQ('s', "state", "on/off");
  led_cmd_args.end = ARG_END();

  static shell_command_t led_cmd = {
    .command = "led",
    .help = "led driver functions",
    .handler = led_cmd_handler,
    .argtable = &led_cmd_args,
  };

  shell_command_register(&led_cmd);
}
SHELL_CMD_REGISTER("led", led_cmd_register);
