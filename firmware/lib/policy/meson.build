deps = [
  helpers_dep,
  bip32_dep,
  bitlog_dep,
  log_dep,
]

policy_lib = library('policy',
  'policy.c',
  include_directories : ['.'],
  dependencies : deps,
)

policy_dep = declare_dependency(
  link_with : policy_lib,
  include_directories : ['.'],
  dependencies : deps,
)

policy_test = executable('policy-test',
  'policy_test.c',
  include_directories : ['.'],
  dependencies : [crypto_deps['posix'], policy_dep, test_deps],
)
test('policy test', policy_test)
