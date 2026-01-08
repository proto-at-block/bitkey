private_includes = [
  'src',
  'inc',
  'inc-private',
  mcu_common_includes,
  rtos_includes,
  config_includes,
  printf_includes,
  perf_includes,
  helpers_includes,
  memfault_sdk_includes,
]

mcu_srcs = [
  'src/mcu_aes.c',
  'src/mcu_clock.c',
  'src/mcu_debug.c',
  'src/mcu_devinfo.c',
  'src/mcu_dma.c',
  'src/mcu_flash.c',
  'src/mcu_gpio.c',
  'src/mcu_hash.c',
  'src/mcu_i2c.c',
  'src/mcu_init.c',
  'src/mcu_nvic.c',
  'src/mcu_pka.c',
  'src/mcu_qspi.c',
  'src/mcu_reset.c',
  'src/mcu_rng.c',
  'src/mcu_startup.c',
  'src/mcu_systick.c',
  'src/mcu_tamper.c',
  'src/mcu_usart.c',
  'src/mcu_usart_rx.c',
  'src/mcu_usart_tx.c',
  'src/mcu_wdog.c',
]

deps = [assert_dep, freertos_dep, stm32u5xx_hal_dep, ringbuf_dep]
mcu_stm32u5xx_includes = [
  include_directories('inc'),
  mcu_common_includes,
  stm32u5xx_hal_includes,
  perf_includes,
]

mcu_stm32u5xx_lib = library('mcu-stm32u5xx',
  mcu_srcs,
  c_args : ['-DIMAGE_TYPE_APPLICATION=1'],
  include_directories : private_includes,
  dependencies : deps,
)

mcu_stm32u5xx_dep = declare_dependency(
  link_with : mcu_stm32u5xx_lib,
  include_directories : private_includes,
  dependencies : deps,
)

mcu_stm32u5xx_bl_lib = library('mcu-stm32u5xx-bl',
  mcu_srcs,
  c_args : ['-DIMAGE_TYPE_BOOTLOADER=1'],
  include_directories : private_includes,
  dependencies : deps,
)

mcu_stm32u5xx_bl_dep = declare_dependency(
  link_with : mcu_stm32u5xx_bl_lib,
  include_directories : private_includes,
  dependencies : deps,
)
