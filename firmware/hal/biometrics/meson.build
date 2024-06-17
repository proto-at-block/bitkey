fpc_features = [
  '-DSENSOR=fpc_bep_sensor_1523s',
  '-DALGORITHM=fpc_bep_algorithm_pfe_1523s',
  '-DFPC_LOG_LEVEL_DEBUG',
]

# Set this to modify normal biometrics library behavior to allow
# for the `fpc` CLI commands to function properly. If set, will break
# `auth_task`. This flag is strictly meant for FPC development purposes.
#
# IMPORTANT: This should never be checked into git as 'true'.
dev_mode = [
  '-DBIO_DEV_MODE=false',
]

# TODO: Don't compile fpc_selftest.c for production firmware. Need to decide
# on our approach to firmware configurability.
srcs = [
  'src/fpc_hal.c',
  'src/fpc_biometrics.c',
  'src/fpc_selftest.c',
  'src/fpc_storage.c',
  'src/fpc_template_update.c',
]
bio_includes = include_directories('inc')
bio_platform_includes = include_directories('bio_platform_config')

bio_deps = [
  mcu_efr32xg24_dep,
  bitlog_dep,
  fpc_bep_dep,
  log_dep,
  crypto_dep,
  exti_dep,
  perf_dep,
  rtos_dep,
  helpers_dep,
  fs_dep,
  secutils_dep,
  animation_dep,
  ipc_dep,
  sysevent_dep,
  auth_dep,
  memfault_dep,
  feature_flags_dep,
]

bio_lib = library('bio',
  srcs,
  c_args : fpc_features + dev_mode,
  include_directories : [bio_includes, bio_platform_includes, security_config_includes],
  dependencies : bio_deps,
)
bio_dep = declare_dependency(
  link_with : bio_lib,
  dependencies : bio_deps,
  include_directories : bio_includes,
)

bio_cmd_lib = library('bio-cmd',
  'src/fpc_cmd.c',
  include_directories : bio_includes,
  dependencies : [bio_dep, shell_dep],
)
bio_cmd_dep = declare_dependency(
  link_whole : bio_cmd_lib,
  dependencies : [bio_dep, shell_dep],
)
