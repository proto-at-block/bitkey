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

seed_test = executable('seed-test',
  'src/seed_test.c',
  srcs,
  include_directories : [includes, private_includes],
  dependencies : [wallet_dep, crypto_deps['posix'], deps, test_deps],
)
test('seed test', seed_test)

derived_key_cache_test = executable('derived-key-cache-test',
  'src/derived_key_cache_test.c',
  srcs,
  include_directories : [includes, private_includes],
  dependencies : [wallet_dep, crypto_deps['posix'], deps, test_deps],
)
test('derived key cache test', derived_key_cache_test)
