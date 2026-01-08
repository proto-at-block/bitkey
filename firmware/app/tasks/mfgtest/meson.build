mfgtest_task_libs = {}
mfgtest_task_deps = {}

mfgtest_task_libs += {
  'w1': library('mfgtest-task-w1', [
      'src/core/mfgtest_task.c',
      'src/core/mfgtest_mpu.c',
      'src/w1/mfgtest_task_port.c',
    ],
    include_directories : ['inc', 'inc-private', mpu_regions_includes],
    dependencies : [
      bio_dep,
      board_id_dep,
      fs_dep,
      hal_nfc_deps['embedded'],
      ipc_dep,
      led_dep,
      mempool_dep,
      metadata_dep,
      power_dep,
      proto_helpers_dep,
      rtos_dep,
      sysinfo_dep,
      ui_messaging_dep,
      wallet_dep,
    ]
  )
}
mfgtest_task_deps += {
  'w1': declare_dependency(
    link_with : mfgtest_task_libs['w1'],
    include_directories : ['inc'],
  )
}

mfgtest_task_libs += {
  'w3-core': library('mfgtest-task-w3-core', [
      'src/core/mfgtest_task.c',
      'src/core/mfgtest_mpu.c',
      'src/w3-core/mfgtest_task_port.c',
    ],
    include_directories : ['inc', 'inc-private', mpu_regions_includes],
    dependencies : [
      bio_dep,
      board_id_dep,
      button_dep,
      display_controller_dep,
      fs_dep,
      hal_nfc_deps['embedded'],
      ipc_dep,
      led_dep,
      mempool_dep,
      metadata_dep,
      power_dep,
      proto_helpers_dep,
      rtos_dep,
      sysinfo_dep,
      uc_dep,
      ui_messaging_dep,
      wallet_dep,
    ]
  )
}
mfgtest_task_deps += {
  'w3-core': declare_dependency(
    link_with : mfgtest_task_libs['w3-core'],
    include_directories : ['inc'],
  )
}

mfgtest_task_libs += {
  'uxc': library('mfgtest-task-uxc', [
      'src/uxc/mfgtest_task.c',
      'src/uxc/mfgtest_task_mpu.c',
    ],
    include_directories : ['inc', mpu_regions_includes],
    dependencies : [
      assert_dep,
      helpers_dep,
      log_dep,
      mcu_dep,
      rtos_dep,
      uc_dep,
      ui_messaging_dep,
    ],
  )
}
mfgtest_task_deps += {
  'uxc': declare_dependency(
    link_with : mfgtest_task_libs['uxc'],
    include_directories : ['inc'],
  )
}
