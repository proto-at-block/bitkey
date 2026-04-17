srcs = [
  'src/auth_task.c',
  'src/auth_task_mpu.c',
]

incs = ['inc', mpu_regions_includes, config_includes]

common_deps = [
  rtos_dep,
  log_dep,
  bio_dep,
  ipc_dep,
  fs_dep,
  fwup_dep,
  ui_messaging_dep,
  proto_helpers_dep,
  auth_dep,
  wallet_dep,
  unlock_dep,
  onboarding_dep,
  grant_dep,
]

auth_task_platform_configs = {
  'w1' : {
    'deps' : common_deps + [secure_channel_deps['w1']],
  },
  'w3-core' : {
    'deps' : common_deps + [secure_channel_deps['w3-core']],
  },
}

auth_task_libs = {}
auth_task_deps = {}

foreach platform : auth_task_platform_configs.keys()
  auth_config = auth_task_platform_configs[platform]

  auth_task_libs += {
    platform : library('auth-task-' + platform,
      srcs,
      include_directories : incs,
      dependencies : auth_config['deps'],
    )
  }

  auth_task_deps += {
    platform: declare_dependency(
      link_with : auth_task_libs[platform],
      include_directories : incs,
    )
  }
endforeach
