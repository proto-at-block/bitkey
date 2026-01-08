srcs = [
  'button.c',
]
button_includes = include_directories('.')

deps = [
  mcu_efr32xg24_dep,
  ringbuf_dep,
  rtos_dep,
  exti_dep,
]

includes = ['.']

button_lib = library('button',
  srcs,
  include_directories : button_includes,
  dependencies : deps,
)
button_dep = declare_dependency(
  link_with : button_lib,
  include_directories : button_includes,
  dependencies : deps,
)
