srcs = [
  'src/sysinfo.c'
]
includes = ['inc']

deps = [
  mcu_efr32xg24_dep,
  helpers_dep,
  fs_dep,
  log_dep,
]

sysinfo_lib = library('sysinfo',
  srcs,
  include_directories : includes,
  dependencies : [deps]
)
sysinfo_dep = declare_dependency(
  link_with : sysinfo_lib,
  include_directories : includes,
)

cmd_deps = [
  sysinfo_dep,
  mcu_efr32xg24_dep,
  printf_dep,
  shell_dep,
  log_dep,
  board_id_dep,
  sleep_dep,
]

sysinfo_cmd_lib = library('sysinfo-cmd',
  'src/sysinfo_cmd.c',
  include_directories : includes,
  dependencies : cmd_deps,
)
sysinfo_cmd_dep = declare_dependency(
  link_whole : sysinfo_cmd_lib,
  dependencies : cmd_deps,
)
