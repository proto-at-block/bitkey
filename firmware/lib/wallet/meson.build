srcs = [
  'src/derived_key_cache.c',
  'src/seed.c',
  'src/wallet.c',
  'src/wallet_storage.c',
  'src/wkek.c',
]

deps = [
  helpers_dep,
  bip32_dep,
  mempool_dep,
  crypto_dep,
  fs_dep,
  log_dep,
  protos_dep,
  bitlog_dep,
]
includes = ['inc']
private_includes = ['src']

wallet_lib = library('wallet',
  srcs,
  include_directories : [includes, private_includes],
  dependencies : deps,
)
wallet_dep = declare_dependency(
  link_with : wallet_lib,
  include_directories : includes,
  dependencies : deps,
)

wallet_cmd_lib = library('wallet-cmd',
  'src/wallet_cmd.c',
  include_directories : [includes, private_includes],
  dependencies : [wallet_dep, helpers_dep, log_dep, mempool_dep, shell_dep],
)
wallet_cmd_dep = declare_dependency(
  link_whole : wallet_cmd_lib,
)

wallet_ops_fuzz = executable('wallet-ops-fuzz',
  'src/wallet_ops_fuzz.cc',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [wallet_dep, deps, fff_dep, fuzzed_data_provider_dep],
)

wallet_keys_fuzz = executable('wallet-keys-fuzz',
  'src/wallet_keys_fuzz.cc',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [wallet_dep, deps, fff_dep, fuzzed_data_provider_dep],
)

wallet_test = executable('wallet-test',
  'src/wallet_test.c',
  srcs,
  include_directories : [includes, private_includes],
  dependencies : [wallet_dep, deps, test_deps],
)
test('wallet test', wallet_test)

seed_test = executable('seed-test',
  'src/seed_test.c',
  srcs,
  include_directories : [includes, private_includes],
  dependencies : [wallet_dep, deps, test_deps],
)
test('seed test', seed_test)

derived_key_cache_test = executable('derived-key-cache-test',
  'src/derived_key_cache_test.c',
  srcs,
  include_directories : [includes, private_includes],
  dependencies : [wallet_dep, deps, test_deps],
)
test('derived key cache test', derived_key_cache_test)
