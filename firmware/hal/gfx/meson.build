srcs = [
  'gfx.c',
]

gfx_includes = include_directories('.')

deps = [
  mcu_dep,
  rtos_dep,
  exti_dep,
  perf_dep,
]

gfx_lib = library('gfx',
  srcs,
  include_directories : gfx_includes,
  dependencies : deps,
)
gfx_dep = declare_dependency(
  link_with : gfx_lib,
  dependencies : deps,
  include_directories : gfx_includes
)
