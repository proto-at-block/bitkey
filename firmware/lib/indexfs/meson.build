srcs = [
  'src/indexfs.c',
  'src/indexfs_monotonic.c',
]

includes = [
  'inc',
  'inc_priv',
]

deps = [
  helpers_dep,
  assert_dep,
  log_dep,
  mcu_dep,
  secutils_dep,
]

indexfs_lib = library('indexfs',
  srcs,
  include_directories : includes + [assert_includes],
  dependencies : [deps, fwup_dep]
)
indexfs_dep = declare_dependency(
  link_with : indexfs_lib,
  include_directories : includes,
  dependencies : [deps]
)

indexfs_test = executable('indexfs-test',
  ['indexfs_test.c', 'src/indexfs.c'],
  include_directories : includes,
  c_args : ['-DFLASH_PAGE_SIZE=0x00002000UL'],
  dependencies : [deps, fwup_dep, test_deps],
)
test('indexfs test', indexfs_test)

# BCW-29: addr_in_range() boundary arithmetic property fuzzer.
# Property-tests the unsigned-arithmetic range check with all uint32_t boundary
# combinations (range_size == 0, addr == UINT32_MAX, underflow, overflow).
# src/indexfs.c is compiled directly so the real addr_in_range() is exercised;
# hardware dependencies are stubbed inline in the fuzz harness.
# Invariant violations raise SIGILL via fuzz_assert.h.
indexfs_addr_fuzz = executable('indexfs-addr-fuzz',
  ['indexfs_addr_fuzz.cc', 'src/indexfs.c'],
  include_directories : includes + [assert_includes],
  c_args   : fuzz_args + ['-DFLASH_PAGE_SIZE=0x00002000UL'],
  cpp_args : fuzz_args + ['-DFLASH_PAGE_SIZE=0x00002000UL'],
  link_args : fuzz_args,
  dependencies : [deps, fwup_dep, fuzzed_data_provider_dep],
)
