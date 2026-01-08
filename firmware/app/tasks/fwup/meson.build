fwup_task_libs = {}
fwup_task_deps = {}

fwup_task_libs += {
  'w1-core': library('fwup-task-w1-core', [
      'src/core/fwup_task.c',
      'src/core/fwup_task_mpu.c',
      'src/w1-core/fwup_task_port.c',
    ],
    include_directories: ['inc', 'inc-private', mpu_regions_includes],
    dependencies: [
      auth_dep,
      bio_dep,
      fwup_dep,
      ipc_dep,
      mcu_dep,
      mempool_dep,
      metadata_dep,
      perf_dep,
      proto_helpers_dep,
      rtos_dep,
      sysinfo_dep,
      ui_messaging_dep,
    ],
  )
}
fwup_task_deps += {
  'w1-core': declare_dependency(
    link_with : fwup_task_libs['w1-core'],
    include_directories : include_directories('inc'),
  )
}


fwup_task_libs += {
  'w3-core': library('fwup-task-w3-core', [
      'src/core/fwup_task.c',
      'src/core/fwup_task_mpu.c',
      'src/w3-core/fwup_task_port.c',
    ],
    include_directories: ['inc', 'inc-private', mpu_regions_includes],
    dependencies: [
      auth_dep,
      bio_dep,
      fwup_dep,
      ipc_dep,
      mcu_dep,
      mempool_dep,
      metadata_dep,
      perf_dep,
      proto_helpers_dep,
      rtos_dep,
      sysinfo_dep,
      uc_dep,
      ui_messaging_dep,
    ]
  )
}
fwup_task_deps += {
  'w3-core': declare_dependency(
    link_with : fwup_task_libs['w3-core'],
    include_directories : include_directories('inc'),
  )
}

fwup_task_libs += {
  'uxc': library('uxc-fwup-task', [
      'src/uxc/fwup_task.c',
      'src/uxc/fwup_task_mpu.c'
    ],
    include_directories : ['inc', mpu_regions_includes],
    dependencies : [assert_dep, helpers_dep, fwup_dep, mcu_dep, rtos_dep, uc_dep],
  )
}
fwup_task_deps += {
  'uxc' : declare_dependency(
    link_with : fwup_task_libs['uxc'],
    include_directories : include_directories('inc'),
  )
}
