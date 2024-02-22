srcs = [
  'src/mpu_regions.c',
]

deps = [mcu_efr32xg24_dep, assert_dep, proto_helpers_dep, log_dep, fwup_dep]

mpu_regions_lib = library('mpu_regions',
  srcs,
  include_directories : mpu_regions_includes,
  dependencies : deps
)
mpu_regions_dep = declare_dependency(
  link_with : mpu_regions_lib,
  include_directories : mpu_regions_includes,
  dependencies : deps
)

mpu_cmd_lib = library('mpu-cmd',
  'src/mpu_cmd.c',
  include_directories : mpu_regions_includes,
  dependencies : [deps, printf_dep, shell_dep],
)
mpu_cmd_dep = declare_dependency(
  link_whole : mpu_cmd_lib,
  dependencies : [deps, printf_dep, shell_dep],
)
