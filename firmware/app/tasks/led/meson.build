srcs = [
  'led_task.c',
  'led_mfgtest.c',
]

incs = ['.']
deps = [
  rtos_dep,
  power_dep,
  animation_dep,
  fs_dep,
  led_dep,
  log_dep,
  sysevent_dep,
  ipc_dep,
]

led_task_lib = library('led-task',
  srcs,
  include_directories : incs,
  dependencies : deps,
)
led_task_dep = declare_dependency(
  link_with : led_task_lib,
  include_directories : incs,
  dependencies : deps,
)
