#include "printf.h"
#include "shell_cmd.h"
#include "temperature.h"

static void temperature_cmd_register(void);
static void temperature_cmd_run(int argc, char** argv);

static void temperature_cmd_register(void) {
  static shell_command_t temp_cmd = {
    .command = "temp",
    .help = "show MCU die temperature",
    .handler = temperature_cmd_run,
  };

  shell_command_register(&temp_cmd);
}

SHELL_CMD_REGISTER("temp", temperature_cmd_register);

static void temperature_cmd_run(int argc, char** argv) {
  (void)argc;
  (void)argv;

  // Get averaged temperature
  float averaged = temperature_get_averaged();

  if (averaged == 0.0f) {
    printf("MCU Temperature: error reading temperature\n");
    return;
  }

  printf("MCU Temperature: %.1f°C\n", averaged);
}
