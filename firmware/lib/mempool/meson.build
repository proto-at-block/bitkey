srcs = [
  'src/mempool.c',
]

incs = ['inc']
deps = [helpers_dep, rtos_dep, secutils_dep, wstd_dep]

mempool_lib = library('mempool',
  srcs,
  include_directories : incs,
  dependencies : deps,
)
mempool_dep = declare_dependency(
  link_with : mempool_lib,
  include_directories : incs,
  dependencies : deps,
)

mempool_fuzz = executable('mempool-fuzz',
  'src/mempool_fuzz.cc',
  'src/mempool_impl.h',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [mempool_dep, fff_dep, fuzzed_data_provider_dep],
)


mempool_test = executable('mempool-test',
  'src/mempool_test.c',
  include_directories : [incs, 'src'],
  dependencies : [mempool_dep, test_deps],
)
test('mempool test', mempool_test)
