coproc_includes = include_directories('inc')

coproc_lib = library('coproc', [
    'src/coproc.c',
    'src/coproc_power.c'
  ],
  include_directories : coproc_includes,
  dependencies : [assert_dep, helpers_dep, mcu_dep, rtos_dep],
)

coproc_dep = declare_dependency(
  link_with: coproc_lib,
  include_directories : include_directories('inc')
)
