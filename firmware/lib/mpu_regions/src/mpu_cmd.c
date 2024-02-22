#include "mpu_cmd.h"

#include "mcu.h"
#include "printf.h"
#include "proto_helpers.h"
#include "shell_cmd.h"

#include <stddef.h>
#include <stdio.h>
#include <string.h>

int code_as_data = 0x4770bf00;  // nop; bx lr

#define X(arg1, arg2) const uint32_t arg1 = (uint32_t)&arg2;
MPU_HELPER
#undef X

static void _printf_formatted_mpu(int region, char* name, uint32_t start, uint32_t end,
                                  uint32_t size) {
  printf("%-10d%-16s%#-16lx%#-16lx%#-16lx\n", region, name, start, end, size);
}

void mpu_print_map(void) {
#define X(arg1, arg2) const uint32_t arg1 = (uint32_t)&arg2;
  MPU_HELPER
#undef X

  printf("%-10s%-16s%-16s%-16s%-16s\n", "Region", "Name", "Start", "End", "Size");
  _printf_formatted_mpu(0, "ram start", ram_r1_start, ram_r1_end, ram_r1_end - ram_r1_start);
  _printf_formatted_mpu(1, "ramfuncs", ramfunc_start, ramfunc_end, ramfunc_end - ramfunc_start);
  _printf_formatted_mpu(2, "ram end", ram_r2_start, ram_r1_start + ram_total_size, ram_total_size);
  _printf_formatted_mpu(3, "bootloader", bl_prog_start, bl_prog_start + bl_total_size,
                        bl_total_size);
  _printf_formatted_mpu(4, "filesystem", fs_start, fs_start + fs_size, fs_size);
  _printf_formatted_mpu(5, "(A) metadata", app_a_meta_start, app_a_meta_start + app_a_meta_size,
                        app_a_meta_size);
  _printf_formatted_mpu(6, "(A) properties", app_a_prop_start, app_a_prop_start + app_a_prop_size,
                        app_a_prop_size);
  _printf_formatted_mpu(7, "(A) boot", app_a_boot_start, app_a_boot_start + app_a_boot_size,
                        app_a_boot_size);
  _printf_formatted_mpu(8, "(A) signature", app_a_sig_start, app_a_sig_start + app_a_sig_size,
                        app_b_sig_size);
  _printf_formatted_mpu(9, "(B) metadata", app_b_meta_start, app_b_meta_start + app_b_meta_size,
                        app_b_meta_size);
  _printf_formatted_mpu(10, "(B) properties", app_b_prop_start, app_b_prop_start + app_b_prop_size,
                        app_b_prop_size);
  _printf_formatted_mpu(11, "(B) boot", app_b_boot_start, app_b_boot_start + app_b_boot_size,
                        app_b_boot_size);
  _printf_formatted_mpu(12, "(B) signature", app_b_sig_start, app_b_sig_start + app_b_sig_size,
                        app_b_sig_size);
}

/* Testing the eXecute permissions within data section
https://interrupt.memfault.com/blog/fix-bugs-and-secure-firmware-with-the-mpu
*/

NO_OPTIMIZE
void _mpu_test_x(void) {
  uint32_t func_addr = (uint32_t)&code_as_data;
  func_addr |= 1;  // set thumb bit

  // execute data
  void (*execute_data)(void) = (void*)func_addr;
  execute_data();
}

/*
read and write to various regions
writes to flash are superficial since flash can only be written via msc
(https://community.silabs.com/s/article/writing-to-internal-flash?language=en_US)

TODO: this can take size as an arg to change which address is tested
*/

static void _mpu_test_rw(uint32_t addr) {
  uint8_t data[4];
  volatile uint32_t* ptr = (uint32_t*)addr;
  *data = *ptr;  // R
  *ptr = *data;  // W
}

NO_OPTIMIZE void mpu_test_map(void) {
  extern uint32_t active_slot;

  // tests that should not fault are uncommented

  _mpu_test_rw((uint32_t)&ram_addr);  // r0
  /*
   * r2: testing with +0x100 since r2 will fault in the first ~0x50 bytes due rtos tasks/timers
   * living there
   */
  _mpu_test_rw((uint32_t)&__ramfunc_end__ + 0x100);
  _mpu_test_rw((uint32_t)&flash_filesystem_addr);  // r6

  if ((uint32_t)&active_slot == (uint32_t)fwpb_firmware_slot_SLOT_A) {
    _mpu_test_rw((uint32_t)&app_b_metadata_page);              // r11
    _mpu_test_rw((uint32_t)&__application_b_properties_addr);  // r12
    _mpu_test_rw((uint32_t)&__application_b_boot_addr);        // r13
    _mpu_test_rw((uint32_t)&__application_b_signature_addr);   // r14
  } else if ((uint32_t)&active_slot == (uint32_t)fwpb_firmware_slot_SLOT_B) {
    _mpu_test_rw((uint32_t)&app_a_metadata_page);              // r7
    _mpu_test_rw((uint32_t)&__application_a_properties_addr);  // r8
    _mpu_test_rw((uint32_t)&__application_a_boot_addr);        // r9
    _mpu_test_rw((uint32_t)&__application_a_signature_addr);   // r10
  }

  // tests that should fault are commented out for now

  //_mpu_test_rw((uint32_t)&__ramfunc_start__);  // r1: verified fault
  //_mpu_test_rw((uint32_t)&bl_base_addr);  // r3 : verified fault
  // if ((uint32_t)&active_slot == (uint32_t)fwpb_firmware_slot_SLOT_B) {
  // _mpu_test_rw((uint32_t)&app_b_metadata_page);              // r9: verified fault
  //_mpu_test_rw((uint32_t)&__application_b_properties_addr);  // r10: verified fault
  //_mpu_test_rw((uint32_t)&__application_b_boot_addr);  // r11: verified fault
  //_mpu_test_rw((uint32_t)&__application_b_signature_addr);  // r12: verified fault
  //} else if ((uint32_t)&active_slot == (uint32_t)fwpb_firmware_slot_SLOT_A) {
  //_mpu_test_rw((uint32_t)&app_a_metadata_page);              // r5: verified fault
  //_mpu_test_rw((uint32_t)&__application_a_properties_addr);  // r6 verified fault
  //_mpu_test_rw((uint32_t)&__application_a_boot_addr);        // r7: verified fault
  //_mpu_test_rw((uint32_t)&__application_a_signature_addr);  // r8: verified fault
  //}

  // _mpu_test_x(); // verified fault in ram
}

static struct {
  arg_lit_t* print_map;
  arg_lit_t* test_map;
  arg_end_t* end;
} mpu_args;

static void cmd_mpu_register(void);
static void cmd_mpu_run(int argc, char** argv);

static void cmd_mpu_register(void) {
  mpu_args.print_map =
    ARG_LIT_OPT('p', "print_map", "print the flashmap boundaries from MPUs perspective");
  mpu_args.test_map =
    ARG_LIT_OPT('t', "test_map", "test that the MPU permissions behave as expected");
  mpu_args.end = ARG_END();

  static shell_command_t mpu_cmd = {
    .command = "mpu",
    .help = "mpu testing",
    .handler = cmd_mpu_run,
    .argtable = &mpu_args,
  };
  shell_command_register(&mpu_cmd);
}
SHELL_CMD_REGISTER("mpu", cmd_mpu_register);

static void cmd_mpu_run(int argc, char** argv) {
  const uint32_t nerrors = shell_argparse_parse(argc, argv, (void**)&mpu_args);
  if (nerrors) {
    return;
  }

  if (mpu_args.print_map->header.found) {
    mpu_print_map();
  }
  if (mpu_args.test_map->header.found) {
    mpu_test_map();
  }
}
