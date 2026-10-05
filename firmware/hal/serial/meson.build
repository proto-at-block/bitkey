srcs = [
  'src/serial.c'
]
serial_includes = include_directories('inc')

deps = [mcu_dep, printf_dep, wstd_dep]

serial_lib = library('serial',
  srcs,
  include_directories : serial_includes,
  dependencies : deps,
)
serial_dep = declare_dependency(
  link_with : serial_lib,
  include_directories : serial_includes,
  dependencies : deps,
)

serial_test = executable('serial-test',
  'serial_test.c',
  'src/serial.c',
  include_directories : serial_includes,
  dependencies : [mcu_posix_dep, printf_dep, wstd_dep, test_deps],
)
test('serial test', serial_test)
