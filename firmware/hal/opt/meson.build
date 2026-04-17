opt_includes = include_directories('inc')

opt_dep = declare_dependency(
  include_directories : opt_includes,
)

opt_prod_lib = library('opt-prod',
  'src/prod/opt.c',
  include_directories : opt_includes,
  dependencies : [mcu_dep, helpers_dep],
)

opt_prod_dep = declare_dependency(
  link_with : opt_prod_lib,
  include_directories : opt_includes,
  dependencies : [mcu_dep, helpers_dep],
)

opt_dev_lib = library('opt-dev',
  'src/dev/opt.c',
  include_directories : opt_includes,
)

opt_dev_dep = declare_dependency(
  link_with : opt_dev_lib,
  include_directories : opt_includes,
)

opt_deps = {
  'prod': opt_prod_dep,
  'dev': opt_dev_dep,
}
