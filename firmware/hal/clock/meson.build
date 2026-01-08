srcs = [
  'src/clock.c'
]
clock_includes = include_directories('inc')

deps = [mcu_dep]

clock_lib = library('clock',
  srcs,
  include_directories : clock_includes,
  dependencies : deps,
)
clock_dep = declare_dependency(
  link_with : clock_lib,
  dependencies : deps,
  include_directories : clock_includes
)
