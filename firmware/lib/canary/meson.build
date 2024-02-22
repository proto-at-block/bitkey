srcs = [
  'canary.c',
]

deps = [mcu_efr32xg24_dep, secure_engine_dep]

canary_lib = library('canary',
  srcs,
  include_directories : ['.'],
  dependencies : deps
)
canary_dep = declare_dependency(
  link_with : canary_lib,
  include_directories : ['.'],
  dependencies : deps
)

canary_cmd_lib = library('canary-cmd',
  'canary_cmd.c',
  include_directories : ['.'],
  dependencies : [deps, shell_dep],
)
canary_cmd_dep = declare_dependency(
  link_whole : canary_cmd_lib,
  dependencies : [deps, shell_dep],
)
