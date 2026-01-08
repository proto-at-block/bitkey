srcs = [
  'src/auth_task.c',
  'src/auth_task_mpu.c',
]

incs = ['inc', mpu_regions_includes]
deps = [
  rtos_dep,
  log_dep,
  bio_dep,
  ipc_dep,
  fs_dep,
  fwup_dep,
  ui_messaging_dep,
  proto_helpers_dep,
  auth_dep,
  wallet_dep,
  secure_channel_dep,
  unlock_dep,
  onboarding_dep,
  grant_dep,
]

auth_task_lib = library('auth-task',
  srcs,
  include_directories : incs,
  dependencies : deps,
)
auth_task_dep = declare_dependency(
  link_with : auth_task_lib,
  include_directories : incs,
  dependencies : deps,
)
