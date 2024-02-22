srcs = [
  'src/telemetry_storage.c',
]

deps = [
  fs_dep,
  protos_dep,
  log_dep,
  assert_dep,
  crypto_dep,
]

telemetry_storage_includes = ['inc']

telemetry_storage_lib = library('telemetry-storage',
  srcs,
  include_directories : [telemetry_storage_includes],
  dependencies : deps,
)
telemetry_storage_dep = declare_dependency(
  link_with : telemetry_storage_lib,
  include_directories : telemetry_storage_includes,
  dependencies : deps,
)
