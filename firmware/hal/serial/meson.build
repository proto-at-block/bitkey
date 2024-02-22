srcs = [
  'src/serial.c'
]
serial_includes = include_directories('inc')

deps = [mcu_efr32xg24_dep]

serial_w1a_lib = library('serial-w1a',
  srcs,
  include_directories : serial_includes,
  dependencies : deps,
)
serial_w1a_dep = declare_dependency(
  link_with : serial_w1a_lib,
  include_directories : serial_includes,
  dependencies : deps,
)
