deps = [
  bip32_dep,
  wallet_dep,
  bitlog_dep,
  helpers_dep,
  policy_dep,
  log_dep,
  ipc_dep,
]

grant_lib = library('grant',
  'src/grant_protocol.c',
  'src/grant_protocol_storage.c',
  include_directories : ['inc', 'src'],
  dependencies : deps,
)

grant_dep = declare_dependency(
  link_with : grant_lib,
  include_directories : ['inc'],
  dependencies : deps,
)

grant_test = executable('grant-test',
  'grant_protocol_test.c',
  include_directories : ['inc', 'src'],
  dependencies : [grant_dep, test_deps],
)
test('grant test', grant_test)
