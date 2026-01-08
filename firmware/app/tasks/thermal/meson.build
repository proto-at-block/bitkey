thermal_task_srcs = [
  'src/thermal_task.c',
  'src/thermal_task_mpu.c',
]

thermal_task_lib = library('thermal-task',
  thermal_task_srcs,
  include_directories : ['inc', mpu_regions_includes],
  dependencies : [
    assert_dep,
    log_dep,
    rtos_dep,
    sysevent_dep,
    temperature_dep,
    ui_messaging_dep,
  ],
)

thermal_task_dep = declare_dependency(
  link_with : thermal_task_lib,
  include_directories : include_directories('inc')
)
