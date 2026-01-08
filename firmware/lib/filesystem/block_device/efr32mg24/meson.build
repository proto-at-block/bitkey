srcs = [
  'lfs_bd.c',
]

fs_efr32mg24_lib = library('filesystem-efr32mg24',
  srcs,
  include_directories : ['.', '../', '../..', config_includes],
  dependencies : [helpers_dep, lfs_dep, rtos_dep, log_dep, mcu_deps[system], perf_dep]
)
fs_efr32mg24_dep = declare_dependency(
  link_with : fs_efr32mg24_lib,
  include_directories : ['.'],
)
