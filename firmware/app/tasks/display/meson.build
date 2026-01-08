display_task_srcs = [
  'src/display_task.c',
  'src/display_task_mpu.c',
]

display_task_lib = library('display-task',
  display_task_srcs,
  include_directories : ['inc', mpu_regions_includes],
  dependencies : [bitlog_dep, display_driver_dep, rtos_dep, assert_dep, log_dep, sysevent_dep, uc_dep, display_ui_dep, protos_dep],
)

display_task_dep = declare_dependency(
  link_with : display_task_lib,
  include_directories : include_directories('inc')
)

display_task_mfgtest_lib = library('display-task-mfgtest',
  display_task_srcs,
  include_directories : ['inc', mpu_regions_includes],
  dependencies : [bitlog_dep, display_driver_mfgtest_dep, rtos_dep, assert_dep, log_dep, sysevent_dep, uc_dep, display_ui_mfgtest_dep, protos_dep],
)

display_task_mfgtest_dep = declare_dependency(
  link_with : display_task_mfgtest_lib,
  include_directories : include_directories('inc')
)
