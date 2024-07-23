srcs = [
  'src/sysinfo_task.c'
]

incs = ['inc']
deps = [
  rtos_dep,
  ipc_dep,
  mempool_dep,
  proto_helpers_dep,
  wallet_dep,
  sysinfo_dep,
  metadata_dep,
  power_dep,
  telemetry_storage_dep,
  bitlog_dep,
  feature_flags_dep,
  secure_engine_dep,
  bio_dep,
  sysevent_dep,
  sleep_dep,
  kv_dep,
]

sysinfo_task_lib = library('sysinfo-task',
  srcs,
  include_directories : incs,
  dependencies : deps,
)
sysinfo_task_dep = declare_dependency(
  link_with : sysinfo_task_lib,
  include_directories : incs,
  dependencies : deps,
)
