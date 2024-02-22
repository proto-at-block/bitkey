srcs = [
  'src/nfc_task.c'
]

deps = [
  hal_nfc_w1a_dep,
  rtos_dep,
  mcu_efr32xg24_dep,
  ipc_dep,
  mempool_dep,
  t4t_dep,
  wca_dep,
  sysevent_dep
]

nfc_task_w1a_lib = library('nfc-task-w1a',
  srcs,
  c_args : [
    features
  ],
  include_directories : ['inc'],
  dependencies : deps,
)
nfc_task_w1a_dep = declare_dependency(
  link_with : nfc_task_w1a_lib,
  include_directories : ['inc'],
  dependencies : deps,
)
