srcs = [
  'proto_helpers.c',
]

deps = [assert_dep, log_dep, ipc_dep, protos_dep]
includes = ['.']

proto_helpers_lib = library('proto-helpers',
  srcs,
  include_directories : includes,
  dependencies : deps,
)
proto_helpers_dep = declare_dependency(
  link_with : proto_helpers_lib,
  include_directories : includes,
  dependencies : deps,
)

proto_helpers_fuzz = executable('proto-helpers-fuzz',
  'proto_helpers_fuzz.cc',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [proto_helpers_dep, fff_dep, fuzzed_data_provider_dep],
)

proto_helpers_test = executable('proto-helpers-test',
  'proto_helpers_test.c',
  include_directories : ['.'],
  dependencies : [proto_helpers_dep, test_deps],
)
test('proto-helpers test', proto_helpers_test)
