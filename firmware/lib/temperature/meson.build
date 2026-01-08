srcs = [
  'src/temperature.c',
]

deps = [
  rtos_dep,
  mcu_deps['efr32mg24'],
  log_dep,
  sysevent_dep,
]

temperature_includes = [include_directories('inc')]

temperature_lib = library('temperature',
  srcs,
  include_directories : temperature_includes,
  dependencies : deps
)

temperature_dep = declare_dependency(
  link_with : temperature_lib,
  include_directories : temperature_includes,
  dependencies : deps
)

temperature_cmd_lib = library('temperature-cmd',
  'src/temperature_cmd.c',
  include_directories : temperature_includes,
  dependencies : [deps, shell_dep, printf_dep],
)

temperature_cmd_dep = declare_dependency(
  link_whole : temperature_cmd_lib,
  dependencies : [deps, shell_dep, printf_dep],
)
