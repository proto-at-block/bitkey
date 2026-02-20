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

w3a_dev_security_config = library('cfg-w3a-dev-security',
  'w3a-core-dev/security_config.c',
  include_directories : security_config_includes,
  dependencies : deps,
)

# mfgtest prod firmware uses the dev configuration with a prod delta key
w3a_mfgtest_prod_security_config = library('cfg-w3a-mfgtest-prod-security',
  'w3a-core-dev/security_config.c',
  c_args : ['-DMFGTEST_PROD_DELTA_KEY'],
  include_directories : security_config_includes,
  dependencies : deps,
)

w3a_prod_security_config = library('cfg-w3a-prod-security',
  'w3a-core-prod/security_config.c',
  include_directories : security_config_includes,
  dependencies : deps,
)

cfg_w3a_dev_security_config_dep = declare_dependency(
  link_with : w3a_dev_security_config,
)

cfg_w3a_mfgtest_prod_security_config_dep = declare_dependency(
  link_with : w3a_mfgtest_prod_security_config,
)

cfg_w3a_prod_security_config_dep = declare_dependency(
  link_with : w3a_prod_security_config,
)
