src_dir = '../../stm32u5xx-hal'

stm32u5xx_hal_includes = include_directories(
  src_dir/'Inc',
  src_dir/'Inc/Legacy',
  # '../../../mcu/stm32u5xx/hal-cfg',  # Not needed - using only LL drivers
  '../../cmsis-device-u5/Include',
  '../../CMSIS_6/CMSIS/Core/Include',
)

# Low-level driver source files
stm32u5xx_ll_srcs = files(
  #src_dir/'Src/stm32u5xx_ll_adc.c',
  #src_dir/'Src/stm32u5xx_ll_comp.c',
  #src_dir/'Src/stm32u5xx_ll_cordic.c',
  #src_dir/'Src/stm32u5xx_ll_crc.c',
  #src_dir/'Src/stm32u5xx_ll_crs.c',
  #src_dir/'Src/stm32u5xx_ll_dac.c',
  # src_dir/'Src/stm32u5xx_ll_dlyb.c',  # Not needed - QSPI uses delay block bypass
  src_dir/'Src/stm32u5xx_ll_dma.c',
  #src_dir/'Src/stm32u5xx_ll_dma2d.c',
  src_dir/'Src/stm32u5xx_ll_exti.c',
  #src_dir/'Src/stm32u5xx_ll_fmac.c',
  #src_dir/'Src/stm32u5xx_ll_fmc.c',
  src_dir/'Src/stm32u5xx_ll_gpio.c',
  #src_dir/'Src/stm32u5xx_ll_i2c.c',
  src_dir/'Src/stm32u5xx_ll_icache.c',
  #src_dir/'Src/stm32u5xx_ll_lpgpio.c',
  #src_dir/'Src/stm32u5xx_ll_lptim.c',
  #src_dir/'Src/stm32u5xx_ll_lpuart.c',
  #src_dir/'Src/stm32u5xx_ll_opamp.c',
  #src_dir/'Src/stm32u5xx_ll_pka.c',
  src_dir/'Src/stm32u5xx_ll_pwr.c',
  src_dir/'Src/stm32u5xx_ll_rcc.c',
  #src_dir/'Src/stm32u5xx_ll_rng.c',
  #src_dir/'Src/stm32u5xx_ll_rtc.c',
  #src_dir/'Src/stm32u5xx_ll_sdmmc.c',
  #src_dir/'Src/stm32u5xx_ll_spi.c',
  #src_dir/'Src/stm32u5xx_ll_tim.c',
  #src_dir/'Src/stm32u5xx_ll_ucpd.c',
  src_dir/'Src/stm32u5xx_ll_usart.c',
  #src_dir/'Src/stm32u5xx_ll_usb.c',
  src_dir/'Src/stm32u5xx_ll_utils.c',
)

# Create library
stm32u5xx_hal_lib = static_library('stm32u5xx-hal',
  stm32u5xx_ll_srcs,
  include_directories: stm32u5xx_hal_includes,
  c_args: ['-DUSE_FULL_LL_DRIVER'],  # Only LL drivers, not HAL
)

# Declare dependency
stm32u5xx_hal_dep = declare_dependency(
  link_with: stm32u5xx_hal_lib,
  include_directories: stm32u5xx_hal_includes,
  compile_args: ['-DUSE_FULL_LL_DRIVER'],  # Only LL drivers, not HAL
)
