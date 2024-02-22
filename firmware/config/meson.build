platform_includes = include_directories('.')

subdir('keys')
subdir('w1a-dev-board-rev-0')
subdir('w1a-dev-board-rev-1')
subdir('w1a-proto-0')
subdir('w1a-evt')
subdir('w1a-dvt')

hardware_configs = {
  'w1a-dev-board-rev-0' : [cfg_w1a_dev_board_rev0_dep, cfg_w1a_dev_board_rev0_args],
  'w1a-dev-board-rev-1' : [cfg_w1a_dev_board_rev1_dep, cfg_w1a_dev_board_rev1_args],
  'w1a-proto-0'         : [cfg_w1a_proto0_dep, cfg_w1a_proto0_args],
  'w1a-proto'           : [cfg_w1a_proto0_dep, cfg_w1a_proto0_args],
  'w1a-evt'             : [cfg_w1a_evt_dep, cfg_w1a_evt_args],
  'w1a-dvt'             : [cfg_w1a_dvt_dep, cfg_w1a_dvt_args],
}

cfg_w1_mfgtest_args = ['-DMFGTEST=1']
cfg_w1_app_args = []

build_env_configs = {
  'mfgtest-dev' : [cfg_w1_dev_security_config_dep, cfg_w1_mfgtest_args, 'dev'],
  'dev'  : [cfg_w1_dev_security_config_dep, ['-DCONFIG_DEV'], 'dev'],

  # Prod gets signed with a dev key as part of the build system. The unsigned prod
  # images are fed to the signing tool, which signs them with the prod key.
  # This allows us to test prod images on dev-fused units.
  'prod' : [cfg_w1_prod_security_config_dep, ['-DCONFIG_PROD'], 'dev'],
}

slot_configs = {
  'a': [
    [] # Extra slot args
  ],
  'b': [
    [] # Extra slot args
  ],
}
