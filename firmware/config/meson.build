platform_includes = include_directories('.')

subdir('keys')
subdir('platform')

w1_build_env_configs = {
  'mfgtest-dev' : [cfg_w1_dev_security_config_dep,    ['-DMFGTEST=1'],    'dev'],
  'dev'         : [cfg_w1_dev_security_config_dep,    ['-DCONFIG_DEV'],   'dev'],

  # Prod gets signed with a dev key as part of the build system. The unsigned prod
  # images are fed to the signing tool, which signs them with the prod key.
  # This allows us to test prod images on dev-fused units.
  'prod'        : [cfg_w1_prod_security_config_dep,   ['-DCONFIG_PROD'],  'dev'],
}

w3_build_env_configs = {
  'mfgtest-dev' : [cfg_w3a_core_dev_security_config_dep,    ['-DMFGTEST=1'],    'dev'],
  'dev'         : [cfg_w3a_core_dev_security_config_dep,    ['-DCONFIG_DEV'],   'dev'],

  # Prod gets signed with a dev key as part of the build system. The unsigned prod
  # images are fed to the signing tool, which signs them with the prod key.
  # This allows us to test prod images on dev-fused units.
  'prod'        : [cfg_w3a_core_prod_security_config_dep,   ['-DCONFIG_PROD'],  'dev'],
}

slot_configs = {
  'a': [
    [] # Extra slot args
  ],
  'b': [
    [] # Extra slot args
  ],
}
