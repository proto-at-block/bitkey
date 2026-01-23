deps = [
  helpers_dep,
  log_dep,
  rtos_dep,
  ipc_dep,
  crypto_dep,
  secutils_dep,
  sleep_dep,
  auth_dep,
  wallet_dep,
  wstd_dep,
]

storage_deps = [
  rtos_dep,
  fs_dep,
  crypto_dep,
  log_dep,
  sysevent_dep,
  indexfs_dep,
  fwup_dep,
  mcu_dep,
  wallet_dep,
]

unlock_fake_storage_lib = library('unlock-fake-storage',
  'src/unlock_storage_fake.c',
  include_directories : ['inc', 'src'],
  dependencies : [helpers_dep, rtos_dep, crypto_dep],
)

unlock_fake_storage_dep = declare_dependency(
  link_with : unlock_fake_storage_lib,
  include_directories : ['inc'],
  dependencies : [helpers_dep, rtos_dep, crypto_dep],
)

unlock_storage_lib = library('unlock-storage',
  'src/unlock_storage.c',
  include_directories : ['inc', 'src'],
  dependencies : storage_deps,
)

unlock_storage_dep = declare_dependency(
  link_with : unlock_storage_lib,
  include_directories : ['inc'],
  dependencies : storage_deps,
)

if embedded_build
  deps += [unlock_storage_dep]
else
  deps += [unlock_fake_storage_dep]
endif

unlock_lib = library('unlock',
  'src/unlock.c',
  include_directories : ['inc', 'src'],
  dependencies : deps,
)

unlock_dep = declare_dependency(
  link_with : unlock_lib,
  include_directories : ['inc'],
  dependencies : deps,
)

unlock_test = executable('unlock-test',
  'unlock_test.c',
  include_directories : ['inc', 'src'],
  dependencies : [unlock_dep, test_deps],
)
test('unlock test', unlock_test)

unlock_cmd_lib = library('unlock-cmd',
  'unlock_cmd.c',
  include_directories : ['inc', 'src'],
  dependencies : [unlock_dep, shell_dep],
)

unlock_cmd_dep = declare_dependency(
  link_whole : unlock_cmd_lib,
  dependencies : [unlock_dep, shell_dep],
)
