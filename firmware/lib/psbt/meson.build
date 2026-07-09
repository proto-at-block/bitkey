psbt_srcs = [
  'src/psbt.c',
]

psbt_includes = [include_directories('inc')]

psbt_deps = [
  libew_dep,
  bip32_dep,
  crypto_deps['efr32mg24'],
  mempool_dep,
  wstd_dep,
]

psbt_lib = library('psbt',
  psbt_srcs,
  include_directories : psbt_includes,
  dependencies : psbt_deps,
)

psbt_dep = declare_dependency(
  link_with : psbt_lib,
  include_directories : psbt_includes,
  dependencies : psbt_deps,
)

psbt_test = executable('psbt-test',
  'src/psbt_test.c',
  'src/psbt.c',
  include_directories : [
    psbt_includes,
    libew_secp256k1_include_dir,
    include_directories('../../third-party/libew'),
    include_directories('../../third-party/libew/third-party/libwally/include')
  ],
  dependencies : [
    bip32_dep,
    crypto_deps['posix'],
    mempool_dep,
    wstd_dep,
    secp256k1_dep,
    test_deps,
  ],
  link_with : [libew_lib],
)
test('psbt test', psbt_test)

psbt_fuzz = executable('psbt-fuzz',
  ['psbt_fuzz.cc', 'src/psbt.c'],
  include_directories : [
    psbt_includes,
    libew_secp256k1_include_dir,
    include_directories('../../third-party/libew'),
    include_directories('../../third-party/libew/third-party/libwally/include'),
  ],
  # Rename allocator calls only in the fuzz-compiled copy of psbt.c. This is
  # portable across macOS ld64 and GNU ld, unlike -Wl,--wrap.
  c_args   : fuzz_args + [
    '-Dmempool_alloc=psbt_fuzz_mempool_alloc',
    '-Dmempool_free=psbt_fuzz_mempool_free',
  ],
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [
    bip32_dep,
    crypto_deps['posix'],
    fff_dep,
    mempool_dep,
    rtos_dep,
    wstd_dep,
    secp256k1_dep,
    fuzzed_data_provider_dep,
  ],
  link_with : [libew_lib],
)
