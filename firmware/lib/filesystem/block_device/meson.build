subdir('efr32mg24')
subdir('stm32u585')

srcs = [
  'bd_cmd.c',
]

fs_bd_cmd_lib = library('fd-bd-cmd',
  srcs,
  include_directories : ['.'],
  dependencies : [helpers_dep, lfs_dep, log_dep, shell_dep]
)

fs_bd_cmd_dep = declare_dependency(
  link_whole : fs_bd_cmd_lib,
  include_directories : ['.'],
  dependencies : [lfs_dep]
)
