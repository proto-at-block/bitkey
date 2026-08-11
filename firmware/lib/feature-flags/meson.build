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

feature_flags_prod_test = executable('feature-flags-prod-test',
  ['feature_flags_test.c', 'feature_flags.c'],
  include_directories : [feature_flags_includes],
  c_args : ['-DCONFIG_PROD=1'],
  dependencies : deps + [test_deps],
)
test('feature flags production policy test', feature_flags_prod_test)
