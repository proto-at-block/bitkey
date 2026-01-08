sysinfo_platform_configs = {
  'w1': {
    'srcs' : [
      'src/core/sysinfo_task.c',
      'src/core/sysinfo_task_mpu.c',
      'src/w1/sysinfo_task_port.c',
    ],
    'deps' : [
      bio_dep,
      bitlog_dep,
      feature_flags_dep,
      ipc_dep,
      kv_dep,
      mempool_dep,
      metadata_dep,
      power_dep,
      proto_helpers_dep,
      rtos_dep,
      secure_engine_dep,
      sleep_dep,
      sysevent_dep,
      sysinfo_dep,
      telemetry_storage_dep,
      ui_messaging_dep,
      wallet_dep,
    ],
  },
  'w3-core': {
    'srcs' : [
      'src/core/sysinfo_task.c',
      'src/core/sysinfo_task_mpu.c',
      'src/w3-core/sysinfo_task_port.c',
    ],
    'deps' : [
      bio_dep,
      bitlog_dep,
      feature_flags_dep,
      ipc_dep,
      kv_dep,
      mempool_dep,
      metadata_dep,
      power_dep,
      proto_helpers_dep,
      rtos_dep,
      secure_engine_dep,
      sleep_dep,
      sysevent_dep,
      sysinfo_dep,
      telemetry_storage_dep,
      uc_dep,
      ui_messaging_dep,
      wallet_dep,
    ],
  },
  'w3-uxc': {
    'srcs': [
      'src/uxc/sysinfo_task.c',
      'src/uxc/sysinfo_task_mpu.c',
    ],
    'deps' : [assert_dep, bitlog_dep, helpers_dep, mcu_dep, metadata_dep, rtos_dep, telemetry_storage_dep, uc_dep],
  },
}

# Header only dependency.
sysinfo_task_dep = declare_dependency(
  include_directories : include_directories('inc'),
)

sysinfo_task_libs = {}
sysinfo_task_deps = {}

foreach platform : sysinfo_platform_configs.keys()
  sysinfo_config = sysinfo_platform_configs[platform]

  sysinfo_task_libs += {
    platform : library('sysinfo-task-' + platform,
      sysinfo_config['srcs'],
      include_directories : ['inc', 'inc-private', config_includes, mpu_regions_includes],
      dependencies : sysinfo_config['deps'],
    )
  }

  sysinfo_task_deps += {
    platform: declare_dependency(
      link_with : sysinfo_task_libs[platform],
      include_directories : include_directories('inc'),
    )
  }
endforeach
