memfault_includes = [
  memfault_sdk_includes,
  include_directories('inc'),
  memfault_event_defs,
]

if not embedded_build
memfault_dep = declare_dependency(
  include_directories : memfault_includes,
  dependencies : [],
)
memfault_cmd_dep = declare_dependency(
  dependencies : [memfault_dep],
)
else

srcs = files(
  'memfault_platform_port.c',
  'memfault_coredump_storage.c',
)

deps = [
  mcu_efr32xg24_dep,
  printf_dep,
  memfault_log_dep,
  helpers_dep,
  freertos_dep,
  sysinfo_dep,
  log_dep,
  telemetry_storage_dep,
]

srcs += memfault_sdk_srcs

memfault_lib = library('memfault',
  srcs,
  include_directories : memfault_includes,
  dependencies : deps,
)

memfault_dep = declare_dependency(
  link_with : memfault_lib,
  include_directories : memfault_includes,
  dependencies : deps,
)

memfault_cmd_lib = library('memfault-cmd',
  'memfault_cmd.c',
  include_directories : memfault_includes,
  dependencies : [memfault_dep, shell_dep],
)
memfault_cmd_dep = declare_dependency(
  link_whole : memfault_cmd_lib,
  dependencies : [memfault_dep, shell_dep],
)

endif
