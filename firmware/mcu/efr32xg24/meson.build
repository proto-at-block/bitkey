private_includes = [
  'inc',
  'inc_priv',
  'src',
  'gecko-cfg',
  gecko_sdk_includes,
  rtos_includes,
  config_includes,
  freertos_includes,
  printf_includes,
  perf_includes,
  helpers_includes,
  memfault_sdk_includes,
]

mcu_srcs = [
  'src/mcu_init.c',
  'src/mcu_gpio.c',
  'src/mcu_usart.c',
  'src/mcu_usart_tx.c',
  'src/mcu_usart_rx.c',
  'src/mcu_startup.c',
  'src/mcu_system.c',
  'src/mcu_i2c.c',
  'src/mcu_i2c_opt.c',
  'src/mcu_i2c_helpers.c',
  'src/mcu_spi.c',
  'src/mcu_clock.c',
  'src/mcu_dma.c',
  'src/mcu_nvic.c',
  'src/mcu_systick.c',
  'src/mcu_flash.c',
  'src/mcu_debug.c',
  'src/mcu_devinfo.c',
  'src/mcu_pwm.c',
  'src/mcu_reset.c',
  'src/mcu_smu.c',
  'src/mcu_wdog.c',
]

# only add FreeRTOS hooks for embedded app
app_srcs = ['port-application/mcu_reset_with_reason.c']
bl_srcs = ['port-bootloader/mcu_reset_with_reason.c']

# Can't build gecko-sdk on it's own without device-specific configuration placed in
# gecko-cfg, so we reach into gecko-sdk's sources instead of building it as a standalone dep.
base = '../..'
gecko_sdk = base/'third-party/gecko-sdk'
gecko_platform_srcs = [
  gecko_sdk/'platform/service/device_init/src/sl_device_init_nvic.c',
  gecko_sdk/'platform/service/device_init/src/sl_device_init_dcdc_s2.c',
  gecko_sdk/'platform/service/device_init/src/sl_device_init_emu_s2.c',
]

gecko_cfg_srcs = [
  'gecko-cfg/sl_device_init_clocks.c',
]

gecko_emlib_srcs = [
  gecko_sdk/'platform/emlib/src/em_core.c',
  gecko_sdk/'platform/emlib/src/em_assert.c',
  gecko_sdk/'platform/emlib/src/em_cmu.c',
  gecko_sdk/'platform/emlib/src/em_emu.c',
  gecko_sdk/'platform/emlib/src/em_se.c',
  gecko_sdk/'platform/emlib/src/em_timer.c',
  gecko_sdk/'platform/emlib/src/em_wdog.c',
]

gecko_sdk_srcs = [
  gecko_platform_srcs,
  gecko_cfg_srcs,
  gecko_emlib_srcs,
]

deps = [assert_dep, freertos_dep]
mcu_efr32xg24_includes = include_directories('inc')

mcu_efr32xg24_lib = library('mcu-efr32xg24',
  mcu_srcs,
  gecko_sdk_srcs,
  app_srcs,
  c_args : ['-DIMAGE_TYPE_APPLICATION=1'],
  include_directories : private_includes,
  dependencies : deps,
)

mcu_efr32xg24_dep = declare_dependency(
  link_with : mcu_efr32xg24_lib,
  include_directories : private_includes,  # TODO: Change this to the below line.
  dependencies : deps,
  # include_directories : ['inc']
)


mcu_efr32xg24_bl_lib = library('mcu-efr32xg24-bl',
  mcu_srcs,
  gecko_sdk_srcs,
  bl_srcs,
  c_args : ['-DIMAGE_TYPE_BOOTLOADER=1'],
  include_directories : private_includes,
  dependencies : deps,
)

mcu_efr32xg24_bl_dep = declare_dependency(
  link_with : mcu_efr32xg24_bl_lib,
  include_directories : private_includes,  # TODO: Change this to the below line.
  dependencies : deps,
  # include_directories : ['inc']
)
