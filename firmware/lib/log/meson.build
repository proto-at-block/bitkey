srcs = [
  'src/log.c',
]

deps = [printf_dep, assert_dep, memfault_log_dep]
includes = ['inc', shell_includes]

log_lib = library('log',
  srcs,
  include_directories : includes,
  dependencies : deps,
)
log_dep = declare_dependency(
  link_with : log_lib,
  include_directories : includes,
  dependencies : deps,
)

log_noprintf_lib = library('log-noprintf',
  srcs,
  c_args : ['-DDISABLE_PRINTF=1'],
  include_directories : includes,
  dependencies : deps,
)
log_noprintf_dep = declare_dependency(
  link_with : log_noprintf_lib,
  include_directories : includes,
  dependencies : deps,
)
