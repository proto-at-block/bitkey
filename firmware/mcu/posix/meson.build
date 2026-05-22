mcu_srcs = [
  'src/mcu_init.c',
  'src/mcu_devinfo.c',
]

mcu_posix_includes = [
  include_directories('inc'),
  mcu_common_includes,
]

mcu_posix_lib = library('mcu-posix',
  mcu_srcs,
  c_args : ['-DIMAGE_TYPE_APPLICATION=1'],
  include_directories : mcu_posix_includes,
)

mcu_posix_dep = declare_dependency(
  link_with : mcu_posix_lib,
  include_directories : mcu_posix_includes,
)

mcu_posix_bl_lib = library('mcu-posix-bl',
  mcu_srcs,
  # LOG_FORCE_RAW: bootloaders don't link log_uart_dep — see lib/log/inc/log.h.
  c_args : ['-DIMAGE_TYPE_BOOTLOADER=1', '-DLOG_FORCE_RAW=1'],
  include_directories : mcu_posix_includes,
)

mcu_posix_bl_dep = declare_dependency(
  link_with : mcu_posix_bl_lib,
  include_directories : mcu_posix_includes,
)
