srcs = [
  'lfs_bd.c',
]

fs_stm32u585_lib = library('filesystem-stm32u585',
  srcs,
  include_directories : ['.', '../', '../..', config_includes],
  dependencies : [helpers_dep, lfs_dep, log_dep, mcu_dep, perf_dep],
)
fs_stm32u585_dep = declare_dependency(
  link_with : fs_stm32u585_lib,
  include_directories : ['.'],
)
