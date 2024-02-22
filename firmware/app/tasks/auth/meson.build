srcs = [
  'src/auth_task.c'
]

deps = [
  rtos_dep,
  log_dep,
  bio_dep,
  ipc_dep,
  fs_dep,
  animation_dep,
  proto_helpers_dep,
  auth_dep,
  wallet_dep,
  secure_channel_dep,
  unlock_dep,
  onboarding_dep,
]

auth_task_lib = library('auth-task',
  srcs,
  include_directories : ['inc'],
  dependencies : deps,
)
auth_task_dep = declare_dependency(
  link_with : auth_task_lib,
  include_directories : ['inc'],
  dependencies : deps,
)
