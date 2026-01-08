usart_task_lib = library('usart-task', [
    'src/usart_task.c',
    'src/usart_task_mpu.c',
  ],
  include_directories : ['inc', 'inc-private', mpu_regions_includes],
  dependencies : [assert_dep, helpers_dep, mcu_dep, rtos_dep],
)

usart_task_dep = declare_dependency(
  link_with : usart_task_lib,
  include_directories : include_directories('inc'),
)
