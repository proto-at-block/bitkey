srcs = [
  'lfs_bd.c',
]

fs_efr32mg24_lib = library('filesystem',
  srcs,
  include_directories : ['.', '../'],
  dependencies : [helpers_dep, lfs_dep, rtos_dep, log_dep, mcu_efr32xg24_dep, perf_dep]
)
fs_efr32mg24_dep = declare_dependency(
  link_with : fs_efr32mg24_lib,
  include_directories : ['.'],
)
