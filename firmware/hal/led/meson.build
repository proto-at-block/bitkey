srcs = [
  'src/led.c'
]
led_includes = include_directories('inc')

deps = [mcu_efr32xg24_dep]

led_lib = library('led',
  srcs,
  include_directories : led_includes,
  dependencies : deps,
)
led_dep = declare_dependency(
  link_with : led_lib,
  dependencies : deps,
  include_directories : led_includes
)

led_cmd_lib = library('led-cmd',
  'src/led_cmd.c',
  include_directories : led_includes,
  dependencies : [led_dep, printf_dep, deps, shell_dep],
)
led_cmd_dep = declare_dependency(
  link_whole : led_cmd_lib,
  dependencies : [led_dep, printf_dep, deps, shell_dep],
)
