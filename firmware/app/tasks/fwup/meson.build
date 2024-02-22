srcs = [
  'fwup_task.c'
]

incs = ['.']
deps = [
  fwup_dep,
  ipc_dep,
  perf_dep,
  rtos_dep,
  bio_dep,
  sysinfo_dep,
  mempool_dep,
  metadata_dep,
  proto_helpers_dep,
  mcu_efr32xg24_dep,
  animation_dep,
  auth_dep,
]

fwup_task_lib = library('fwup-task',
  srcs,
  include_directories : incs,
  dependencies : deps,
)
fwup_task_dep = declare_dependency(
  link_with : fwup_task_lib,
  include_directories : incs,
  dependencies : deps,
)
