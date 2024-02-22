srcs = [
  'perf.c',
]

deps = [
  helpers_dep,
  assert_dep,
  printf_dep,
]

perf_lib = library('perf',
  srcs,
  include_directories : ['.', assert_includes],
  dependencies : [deps, rtos_dep, uthash_dep]
)
perf_dep = declare_dependency(
  link_with : perf_lib,
  include_directories : ['.'],
  dependencies : [deps]
)

# Separate dependency for perf shell commands
perf_cmd_lib = library('perf-cmd',
  'perf_cmd.c',
  include_directories : ['.'],
  dependencies : [deps, shell_dep]
)

# The main application image must depend on this.
perf_cmd_dep = declare_dependency(
  link_whole : perf_cmd_lib,
  link_args : ['-lm'],
  dependencies : [deps]
)

perf_stubs_lib = library('perf-stubs',
  'perf_stubs.c',
  include_directories : ['.'],
  dependencies : [fff_dep]
)
perf_stubs_dep = declare_dependency(
  link_with : perf_stubs_lib,
  include_directories : ['.'],
)
