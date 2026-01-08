srcs = [
  'src/captouch_task.c',
  'src/captouch_task_mpu.c',
]

incs = ['inc', mpu_regions_includes]
deps = [
  rtos_dep,
  power_dep,
  led_dep,
  sysevent_dep,
  ipc_dep,
  auth_dep,
  ui_messaging_dep,
]

captouch_task_lib = library(
  'captouch-task',
  srcs,
  include_directories: incs,
  dependencies: deps,
)
captouch_task_dep = declare_dependency(
  link_with: captouch_task_lib,
  include_directories: incs,
  dependencies: deps,
)
