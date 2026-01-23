srcs = [
  'bip32.c',
]

deps = [
  helpers_dep,
  assert_dep,
  crypto_dep,
  secutils_dep,
  secp256k1_dep,
  wstd_dep,
]

bip32_includes = ['.']

bip32_lib = library('bip32',
  srcs,
  include_directories : [bip32_includes],
  dependencies : deps,
)
bip32_dep = declare_dependency(
  link_with : bip32_lib,
  include_directories : bip32_includes,
  dependencies : deps,
)

bip32_fuzz = executable('bip32-fuzz',
  'bip32_fuzz.cc',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [bip32_dep, fuzzed_data_provider_dep],
)

bip32_test = executable('bip32-test',
  'bip32_test.c',
  include_directories : bip32_includes,
  dependencies : [bip32_dep, crypto_deps['posix'], libbase58_dep, test_deps],
)
test('bip32 test', bip32_test)
