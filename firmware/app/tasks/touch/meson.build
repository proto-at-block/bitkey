touch_task_lib = library('touch-task', [
    'src/touch_task.c',
    'src/touch_task_mpu.c',
  ],
  include_directories : ['inc', mpu_regions_includes],
  dependencies : [assert_dep, helpers_dep, mcu_dep, rtos_dep, touch_dep],
)

touch_task_dep = declare_dependency(
  link_with : touch_task_lib,
  include_directories : include_directories('inc'),
)

# MFGTEST variant
touch_task_mfgtest_lib = library('touch-task-mfgtest', [
    'src/touch_task.c',
    'src/touch_task_mpu.c',
  ],
  include_directories : ['inc', mpu_regions_includes],
  dependencies : [assert_dep, display_ui_mfgtest_dep, helpers_dep, log_dep, mcu_dep, rtos_dep, touch_dep],
  c_args : ['-DMFGTEST=1'],
)

touch_task_mfgtest_dep = declare_dependency(
  link_with : touch_task_mfgtest_lib,
  include_directories : include_directories('inc'),
)
