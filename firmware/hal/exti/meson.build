srcs = [
  'exti.c'
]
exti_includes = include_directories('.')

deps = [ mcu_efr32xg24_dep, rtos_dep, perf_dep, log_dep ]

exti_lib = library('exti',
  srcs,
  include_directories : exti_includes,
  dependencies : deps,
)
exti_dep = declare_dependency(
  link_with : exti_lib,
  dependencies : deps,
  include_directories : exti_includes
)
