deps = embedded_build ? [printf_dep, helpers_dep] : [helpers_dep]

assert_lib = library('assert',
  'assert.c',
  include_directories : assert_includes,
  dependencies: deps,
)

assert_dep = declare_dependency(
  link_with : assert_lib,
  include_directories : assert_includes,
  dependencies: deps,
)
