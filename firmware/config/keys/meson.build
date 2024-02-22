deps = [
  secutils_bl_dep
]

w1_dev_security_config = library('cfg-w1-dev-security',
  'w1a-dev/security_config.c',
  include_directories : security_config_includes,
  dependencies : deps,
)

w1_prod_security_config = library('cfg-w1-prod-security',
  'w1a-prod/security_config.c',
  include_directories : security_config_includes,
  dependencies : deps,
)

cfg_w1_dev_security_config_dep = declare_dependency(
  link_with : w1_dev_security_config,
)

cfg_w1_prod_security_config_dep = declare_dependency(
  link_with : w1_prod_security_config,
)
