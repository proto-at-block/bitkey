srcs = [
  'sysevent.c'
]
includes = ['.']

deps = [
  rtos_dep,
  helpers_dep,
  log_dep,
]

sysevent_lib = library('sysevent',
  srcs,
  include_directories : includes,
  dependencies : [deps]
)
sysevent_dep = declare_dependency(
  link_with : sysevent_lib,
  include_directories : includes,
)
