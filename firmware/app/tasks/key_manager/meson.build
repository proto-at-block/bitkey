core_common_deps = [
  rtos_dep,
  ipc_dep,
  mempool_dep,
  proto_helpers_dep,
  wallet_dep,
  auth_dep,
  fs_dep,
  sysevent_dep,
  ui_messaging_dep,
  secure_channel_dep,
  onboarding_dep,
  grant_dep,
  policy_dep,
  wstd_dep,
  fwup_dep,
]

key_manager_platform_configs = {
  'w1' : {
    'srcs' : [
      'src/core/key_manager_task.c',
      'src/w1/key_manager_task_port.c',
      'src/core/key_manager_mpu.c',
      'src/core/crypto_task.c',
    ],
    'deps' : core_common_deps,
  },
  'w3-core' : {
    'srcs' : [
      'src/core/key_manager_task.c',
      'src/w3-core/key_manager_task_port.c',
      'src/core/key_manager_mpu.c',
      'src/core/crypto_task.c',
    ],
    'deps' : core_common_deps + [uc_dep],
  },
  'w3-uxc' : {
    'srcs' : [
      'src/w3-uxc/key_manager_task.c',
      'src/w3-uxc/key_manager_task_mpu.c',
    ],
    'deps' : [
      rtos_dep,
      uc_dep,
      secure_channel_dep,
    ],
  },
}

key_manager_task_dep = declare_dependency(
  include_directories : include_directories('inc'),
)

key_manager_task_libs = {}
key_manager_task_deps = {}

foreach platform : key_manager_platform_configs.keys()
  key_manager_config = key_manager_platform_configs[platform]

  key_manager_task_libs += {
    platform : library('key-manager-task-' + platform,
      key_manager_config['srcs'],
      include_directories : ['inc', 'inc-private', mpu_regions_includes],
      dependencies : key_manager_config['deps'],
    )
  }

  key_manager_task_deps += {
    platform: declare_dependency(
      link_with : key_manager_task_libs[platform],
      include_directories : include_directories('inc'),
    )
  }
endforeach
