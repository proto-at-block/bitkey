core_common_deps = [
  rtos_dep,
  memfault_dep,
  ipc_dep,
  mempool_dep,
  proto_helpers_dep,
  wallet_dep,
  psbt_dep,
  auth_dep,
  fs_dep,
  sysevent_dep,
  ui_messaging_dep,
  ui_messaging_lib_dep,
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
      'src/core/psbt_signing.c',
    ],
    'deps' : core_common_deps + [secure_channel_deps['w1']],
  },
  'w3-core' : {
    'srcs' : [
      'src/core/key_manager_task.c',
      'src/w3-core/key_manager_task_port.c',
      'src/w3-core/full_account_cloud_backup_restoration.c',
      'src/w3-core/eek_restoration.c',
      'src/w3-core/keyset_repair.c',
      'src/w3-core/lost_app_recovery.c',
      'src/w3-core/rotate_app_auth_keys.c',
      'src/w3-core/upgrade_rotate_app_auth_keys.c',
      'src/w3-core/recovery_composites.c',
      'src/w3-core/sealed_data_utils.c',
      'src/core/key_manager_mpu.c',
      'src/core/crypto_task.c',
      'src/core/psbt_signing.c',
    ],
    'deps' : core_common_deps + [secure_channel_deps['w3-core'], uc_dep, libew_dep, confirmation_dep, psbt_dep, sap_dep, langpack_dep],
  },
  'w3-uxc' : {
    'srcs' : [
      'src/w3-uxc/key_manager_task.c',
      'src/w3-uxc/key_manager_task_mpu.c',
    ],
    'deps' : [
      rtos_dep,
      memfault_dep,
      uc_dep,
      secure_channel_deps['w3-uxc'],
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

lost_app_recovery_test = executable('lost-app-recovery-test',
  'src/w3-core/lost_app_recovery_test.c',
  dependencies : [sap_core_dep, wallet_dep, test_deps],
)
test('lost_app_recovery test', lost_app_recovery_test)

rotate_app_auth_keys_test = executable('rotate-app-auth-keys-test',
  'src/w3-core/rotate_app_auth_keys_test.c',
  dependencies : [sap_core_dep, test_deps],
)
test('rotate_app_auth_keys test', rotate_app_auth_keys_test)

recovery_composites_test = executable('recovery-composites-test',
  'src/w3-core/recovery_composites_test.c',
  dependencies : [sap_core_dep, test_deps],
)
test('recovery_composites test', recovery_composites_test)

streaming_signing_test = executable('streaming-signing-test',
  'src/w3-core/streaming_signing_test.c',
  dependencies : [wallet_dep, test_deps],
)
test('streaming_signing test', streaming_signing_test)
