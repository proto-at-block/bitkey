if not embedded_build

# IMPORTANT: When changing this file, you may also need to change
# the Makefile in this same directory.

srcs = [
  'teltra.c',
  'teltra_memfault_port.c',
]

deps = [
  helpers_dep,
  assert_dep,
  bitlog_dep,
]

teltra_includes = ['inc']

teltra_lib = library('teltra',
  srcs,
  memfault_serializer_sdk_srcs,
  include_directories : [teltra_includes, memfault_includes],
  dependencies : deps,
)
teltra_dep = declare_dependency(
  link_with : teltra_lib,
  include_directories : teltra_includes,
  dependencies : deps,
)

teltra_test = executable('teltra-test',
  'teltra_test.c',
  include_directories : [gecko_sdk_bootloader_includes, 'inc'],
  dependencies : [teltra_dep, test_deps],
  c_args : [test_defines, '-std=gnu11'],
)
test('teltra test', teltra_test)

endif
