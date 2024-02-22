srcs = [
  'bitlog.c',
]

deps = [
  log_dep,
  helpers_dep,
  assert_dep,
  ringbuf_dep,
]

if embedded_build
  deps += [perf_dep]
else
  deps += [perf_stubs_dep]
endif

bitlog_includes = ['inc']

bitlog_lib = library('bitlog',
  srcs,
  include_directories : [
    bitlog_includes,
    '.',
    memfault_sdk_includes,
    memfault_event_defs
  ],
  dependencies : deps,
)
bitlog_dep = declare_dependency(
  link_with : bitlog_lib,
  include_directories : [
    bitlog_includes,
    memfault_event_defs,
  ],
  dependencies : deps,
)

bitlog_cmd_lib = library('bitlog-cmd',
  'bitlog_cmd.c',
  include_directories : bitlog_includes,
  dependencies : [bitlog_dep, shell_dep],
)
bitlog_cmd_dep = declare_dependency(
  link_whole : bitlog_cmd_lib,
  dependencies : [bitlog_dep, shell_dep],
)

bitlog_test = executable('bitlog-test',
  'bitlog_test.c',
  include_directories : [gecko_sdk_bootloader_includes, '.'],
  dependencies : [bitlog_dep, test_deps],
  c_args : test_defines,
)
test('bitlog test', bitlog_test)
