mcu_srcs = [
  'src/mcu_init.c',
]

mcu_posix_includes = [
  mcu_common_includes,
]

mcu_posix_lib = library('mcu-posix',
  mcu_srcs,
  c_args : ['-DIMAGE_TYPE_APPLICATION=1'],
  include_directories : mcu_posix_includes,
)

mcu_posix_dep = declare_dependency(
  link_with : mcu_posix_lib,
)

mcu_posix_bl_lib = library('mcu-posix-bl',
  mcu_srcs,
  c_args : ['-DIMAGE_TYPE_BOOTLOADER=1'],
  include_directories : mcu_posix_includes,
)

mcu_posix_bl_dep = declare_dependency(
  link_with : mcu_posix_bl_lib,
)
