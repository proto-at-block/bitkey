srcs = [
  'src/wca.c',
]

deps = [helpers_dep, mempool_dep, ipc_dep, iso7816_dep, log_dep, ui_messaging_dep]
includes = ['inc', shell_includes]

wca_lib = library('wca',
  srcs,
  include_directories : includes,
  dependencies : deps,
)
wca_dep = declare_dependency(
  link_with : wca_lib,
  include_directories : includes,
  dependencies : deps,
)

wca_fuzz = executable('wca-fuzz',
  'src/wca_fuzz.cc',
  'src/wca_impl.h',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [wca_dep, mempool_dep, fff_dep, fuzzed_data_provider_dep],
)

# BCW-01, BCW-04, BCW-06, BCW-09, BCW-10: stateful multi-session WCA fuzzer.
# Uses heap-allocated exact-size buffers (std::vector) so ASAN catches the
# BCW-01 cmd[4] OOB read that the existing wca-fuzz masks with a 250-byte array.
wca_session_fuzz = executable('wca-session-fuzz',
  'src/wca_session_fuzz.cc',
  'src/wca_impl.h',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [wca_dep, mempool_dep, ipc_dep, fff_dep, fuzzed_data_provider_dep],
)

# BCW-07, BCW-19, BCW-31: proto NULL-deref and ASSERT-trigger fuzzer.
# Thin harness — reproduces exact vulnerable patterns without linking auth_task.c.
wca_proto_handlers_fuzz = executable('wca-proto-handlers-fuzz',
  'src/wca_proto_handlers_fuzz.cc',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [proto_helpers_dep, mempool_dep, ipc_dep, fff_dep, fuzzed_data_provider_dep],
)

wca_test = executable('wca-test',
  'src/wca_test.c',
  include_directories : [includes, 'src'],
  dependencies : [wca_dep, test_deps],
)
test('wca test', wca_test)

wca_cmd_lib = library('wca-cmd',
  'src/wca_cmd.c',
  include_directories : includes,
  dependencies : deps,
)
wca_cmd_dep = declare_dependency(
  link_whole : wca_cmd_lib,
  dependencies : deps,
)
