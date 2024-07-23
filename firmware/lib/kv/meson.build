srcs = [
  'kv.c',
]

incs = ['.']
deps = [
  assert_dep,
  fs_dep,
  log_dep,
]

kv_lib = library('kv',
  srcs,
  include_directories : incs,
  dependencies : deps,
)
kv_dep = declare_dependency(
  link_with : kv_lib,
  include_directories : incs,
  dependencies : deps,
)

kv_fuzz = executable('kv-fuzz',
  'kv_fuzz.cc',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [kv_dep, fff_dep, rtos_dep, fuzzed_data_provider_dep],
)

kv_test = executable('kv-test',
  'kv_test.c',
  include_directories : incs,
  dependencies : [kv_dep, test_deps, rtos_dep],
)
test('kv test', kv_test)
