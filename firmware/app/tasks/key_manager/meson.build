srcs = [
  'src/key_manager_task.c',
  'src/crypto_task.c',
]

incs = ['inc']
deps = [
  rtos_dep,
  ipc_dep,
  mempool_dep,
  proto_helpers_dep,
  wallet_dep,
  auth_dep,
  fs_dep,
  sysevent_dep,
  animation_dep,
  secure_channel_dep,
  onboarding_dep,
]

key_manager_task_lib = library('key-manager-task',
  srcs,
  include_directories : incs,
  dependencies : deps,
)
key_manager_task_dep = declare_dependency(
  link_with : key_manager_task_lib,
  include_directories : incs,
  dependencies : deps,
)
