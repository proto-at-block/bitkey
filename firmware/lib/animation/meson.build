srcs = [
  'animation.c',
  'animation_defs.c'
]

deps = [log_dep, helpers_dep, assert_dep]
includes = ['inc']

animation_lib = library('animation',
  srcs,
  include_directories : includes,
  dependencies : deps,
)
animation_dep = declare_dependency(
  link_with : animation_lib,
  include_directories : includes,
  dependencies : deps,
)

animation_cmd_lib = library('animation-cmd',
  'animation_cmd.c',
  include_directories : includes,
  dependencies : [deps, shell_dep, ipc_dep],
)
animation_cmd_dep = declare_dependency(
  link_whole : animation_cmd_lib,
  dependencies : [deps, shell_dep],
)
