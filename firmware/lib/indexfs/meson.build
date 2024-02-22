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
  mcu_efr32xg24_dep,
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
