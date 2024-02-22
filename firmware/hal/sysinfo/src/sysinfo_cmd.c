#include "assert.h"
#include "board_id.h"
#include "hex.h"
#include "log.h"
#include "mcu_devinfo.h"
#include "mcu_gpio.h"
#include "printf.h"
#include "rtos.h"
#include "shell_argparse.h"
#include "shell_cmd.h"
#include "sleep.h"
#include "sysinfo.h"

#include <stdlib.h>
#include <string.h>

static struct {
  arg_str_t* gpio;
  arg_bool_t* state;
  arg_end_t* end;
} gpio_cmd_args;

static void cmd_gpio_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&gpio_cmd_args);

  if (nerrors) {
    return;
  }

  char* gpio = gpio_cmd_args.gpio->value;
  printf("%s, %c\n", gpio, gpio[1]);

  if (strlen(gpio) != 3) {
    goto invalid;
  }
  if (gpio[1] != '.') {
    goto invalid;
  }

  const uint32_t port = (uint32_t)atoi(gpio);
  const uint32_t pin = (uint32_t)atoi(gpio + 2);
  mcu_gpio_output_set(&(const mcu_gpio_config_t){.port = port, .pin = pin},
                      gpio_cmd_args.state->value);

  printf("gpio %u.%u = %u\n", port, pin, gpio_cmd_args.state->value);
  return;

invalid:
  printf("Invalid GPIO '%s'\n", gpio);
  return;
}

static void cmd_gpio_register(void) {
  gpio_cmd_args.gpio = ARG_STR_REQ(0, NULL, "port.pin (eg. 0.1)");
  gpio_cmd_args.state = ARG_BOOL_REQ(0, NULL, "state {0,1}");
  gpio_cmd_args.end = ARG_END();

  static shell_command_t gpio_cmd = {
    .command = "gpio",
    .help = "sets gpio states",
    .handler = cmd_gpio_run,
    .argtable = &gpio_cmd_args,
  };
  shell_command_register(&gpio_cmd);
}
SHELL_CMD_REGISTER("gpio", cmd_gpio_register);

static struct {
  arg_str_t* mlb_serial;
  arg_str_t* assy_serial;
  arg_end_t* end;
} device_id_cmd_args;

static void cmd_device_id_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&device_id_cmd_args);

  if (nerrors) {
    return;
  }

  if (device_id_cmd_args.mlb_serial->header.found) {
    char* mlb_serial = device_id_cmd_args.mlb_serial->value;
    if (strlen(mlb_serial) != SYSINFO_SERIAL_NUMBER_LENGTH) {
      LOGE("Invalid serial '%s'\n", mlb_serial);
      return;
    }
    sysinfo_mlb_serial_write(mlb_serial, SYSINFO_SERIAL_NUMBER_LENGTH);
  }

  if (device_id_cmd_args.assy_serial->header.found) {
    char* assy_serial = device_id_cmd_args.assy_serial->value;
    if (strlen(assy_serial) != SYSINFO_SERIAL_NUMBER_LENGTH) {
      LOGE("Invalid serial '%s'\n", assy_serial);
      return;
    }
    sysinfo_assy_serial_write(assy_serial, SYSINFO_SERIAL_NUMBER_LENGTH);
  }

  char stored_serial[SYSINFO_SERIAL_NUMBER_LENGTH] = {0};
  uint32_t stored_serial_length = sizeof(stored_serial);
  bool mlb_serial_present = sysinfo_mlb_serial_read(&stored_serial[0], &stored_serial_length);

  printf("mlb serial: ");
  if (mlb_serial_present) {
    for (int i = 0; i < 16; i++) printf("%c", stored_serial[i]);
  }
  printf("\n");

  bool assy_serial_present = sysinfo_assy_serial_read(&stored_serial[0], &stored_serial_length);

  printf("assy serial: ");
  if (assy_serial_present) {
    for (int i = 0; i < 16; i++) printf("%c", stored_serial[i]);
  }
  printf("\n");

  uint8_t board_id = 0;
  board_id_read(&board_id);
  printf("board id: %02x\n", board_id);

  uint8_t chipid[8] = {0};
  uint32_t chipid_length = 0;
  sysinfo_chip_id_read(&chipid[0], &chipid_length);
  printf("chip id: ");
  for (uint32_t i = 0; i < sizeof(chipid); i++) printf("%02x", chipid[i]);
  printf("\n");

  printf("device info: ");
  mcu_devinfo_t devinfo = {0};
  mcu_devinfo_read(&devinfo);
  for (uint32_t i = 0; i < sizeof(devinfo); i++) {
    printf("%02x", ((uint8_t*)&devinfo)[i]);
    // TODO: Bad hack to compensate for USART dropping output. Remove this!
    rtos_thread_sleep(1);
  }
  printf("\n");

  return;
}

static void cmd_device_id_register(void) {
  device_id_cmd_args.mlb_serial = ARG_STR_OPT('m', "--mlb-serial", "write mlb serial number");
  device_id_cmd_args.assy_serial = ARG_STR_OPT('a', "--assy-serial", "write assy serial number");
  device_id_cmd_args.end = ARG_END();

  static shell_command_t device_id_cmd = {
    .command = "identifiers",
    .help = "get device identifiers",
    .handler = cmd_device_id_run,
    .argtable = &device_id_cmd_args,
  };
  shell_command_register(&device_id_cmd);
}
SHELL_CMD_REGISTER("identifiers", cmd_device_id_register);

static struct {
  arg_int_t* timeout;
  arg_lit_t* forever;
  arg_end_t* end;
} power_cmd_args;

static void cmd_power_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&power_cmd_args);

  if (nerrors) {
    return;
  }

  if (power_cmd_args.timeout->header.found)
    sleep_set_power_timeout(power_cmd_args.timeout->value);
  else if (power_cmd_args.forever->header.found)
    sleep_set_power_timeout(UINT32_MAX);
}

static void cmd_power_register(void) {
  power_cmd_args.timeout = ARG_INT_OPT('t', "timeout", "timeout in ms");
  power_cmd_args.forever = ARG_LIT_OPT('f', "forever", "leave power on forever");
  power_cmd_args.end = ARG_END();

  static shell_command_t cmd = {
    .command = "power",
    .help = "power control",
    .handler = cmd_power_run,
    .argtable = &power_cmd_args,
  };
  shell_command_register(&cmd);
}
SHELL_CMD_REGISTER("power", cmd_power_register);
