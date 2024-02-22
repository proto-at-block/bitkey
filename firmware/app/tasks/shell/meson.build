srcs = [
  'src/shell_task.c'
]

deps = [helpers_dep, mcu_efr32xg24_dep, rtos_dep, shell_dep]

shell_task_lib = library('shell-task-w1a',
  srcs,
  include_directories : ['inc'],
  dependencies : deps,
)
shell_task_dep = declare_dependency(
  link_with : shell_task_lib,
  include_directories : ['inc'],
  dependencies : deps,
)
