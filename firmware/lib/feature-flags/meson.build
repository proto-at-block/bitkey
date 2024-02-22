srcs = [
  'feature_flags.c',
]

deps = [
  assert_dep,
  protos_dep,
  log_dep,
  fs_dep,
  sysevent_dep,
]

feature_flags_includes = ['.']

feature_flags_lib = library('feature_flags',
  srcs,
  include_directories : [feature_flags_includes],
  dependencies : deps,
)

feature_flags_dep = declare_dependency(
  link_with : feature_flags_lib,
  include_directories : feature_flags_includes,
  dependencies : deps,
)
