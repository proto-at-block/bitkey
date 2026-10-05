srcs = [
  'src/telemetry_storage.c',
]

common_deps = [
  protos_dep,
  log_dep,
  assert_dep,
  crypto_dep,
]

telemetry_storage_includes = ['inc']

telemetry_storage_lib = library('telemetry-storage',
  srcs,
  include_directories : [telemetry_storage_includes],
  dependencies : [fs_headers_dep] + common_deps,
)
telemetry_storage_dep = declare_dependency(
  link_with : telemetry_storage_lib,
  include_directories : telemetry_storage_includes,
  dependencies : [fs_dep] + common_deps,
)

telemetry_storage_test = executable('telemetry-storage-test',
  'src/telemetry_storage_test.c',
  link_with : telemetry_storage_lib,
  include_directories : telemetry_storage_includes,
  dependencies : [fs_headers_dep] + common_deps + test_deps,
)
test('telemetry storage test', telemetry_storage_test)
