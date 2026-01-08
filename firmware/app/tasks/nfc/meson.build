srcs = [
  'src/nfc_task.c',
  'src/nfc_task_mpu.c',
]

incs = ['inc', mpu_regions_includes]
deps = [
  hal_nfc_deps['embedded'],
  rtos_dep,
  mcu_dep,
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
  include_directories : incs,
  dependencies : deps,
)
nfc_task_w1a_dep = declare_dependency(
  link_with : nfc_task_w1a_lib,
  include_directories : incs,
  dependencies : deps,
)
