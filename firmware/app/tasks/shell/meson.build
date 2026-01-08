srcs = [
  'src/shell_task.c',
  'src/shell_task_mpu.c',
]

incs = ['inc', mpu_regions_includes]
deps = [helpers_dep, mcu_dep, rtos_dep, serial_dep, shell_dep]

shell_task_lib = library('shell-task',
  srcs,
  include_directories : incs,
  dependencies : deps,
)
shell_task_dep = declare_dependency(
  link_with : shell_task_lib,
  include_directories : incs,
  dependencies : deps,
)
