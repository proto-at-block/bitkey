srcs = [
  'src/mfgtest_task.c'
]

incs = ['inc']
deps = [
  rtos_dep,
  ipc_dep,
  mempool_dep,
  bio_dep,
  proto_helpers_dep,
  wallet_dep,
  fs_dep,
  sysinfo_dep,
  metadata_dep,
  power_dep,
  led_dep,
]

mfgtest_task_lib = library('mfgtest-task',
  srcs,
  include_directories : incs,
  dependencies : deps,
)
mfgtest_task_dep = declare_dependency(
  link_with : mfgtest_task_lib,
  include_directories : incs,
  dependencies : deps,
)
