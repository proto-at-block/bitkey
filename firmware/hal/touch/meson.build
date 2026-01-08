touch_includes = include_directories('inc')

touch_lib = library('touch', [
    'src/touch.c',
  ],
  include_directories : ['inc', 'inc-private'],
  dependencies : [exti_dep, helpers_dep, mcu_dep, rtos_dep],
)

touch_dep = declare_dependency(
  link_with : touch_lib,
  include_directories : ['inc'],
  dependencies : [mcu_dep],
)
