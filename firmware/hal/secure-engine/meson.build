srcs = [
  # sl_se_manager.c is copied from Gecko SDK here so that our custom implementation
  # of sli_se_manager_osal_cmsis_rtos2.h is included, since the preprocessor search order
  # for headers looks for files in the same directory first.
  'src/sl_se_manager.c',
  'src/se_aes.c',
  'src/se_hash.c',
  'src/se_ecc.c',
  'src/se_attestation.c',
  'src/se_info.c',
  'src/se_tamper.c',
  se_manager_sources,
]

secure_engine_includes = include_directories('inc')
app_includes = include_directories('port-application')
bl_includes = include_directories('port-bootloader')
features = [
  '-DSL_SE_MANAGER_YIELD_WHILE_WAITING_FOR_COMMAND_COMPLETION',
  '-DSL_CATALOG_FREERTOS_KERNEL_PRESENT',
]

app_deps = [mcu_dep, rtos_dep, secutils_dep]
bl_deps = [mcu_dep, secutils_bl_dep]

secure_engine_lib = library('secure-engine',
  srcs,
  c_args : features,
  include_directories : [secure_engine_includes, app_includes, 'src', se_manager_includes],
  dependencies : app_deps,
)
secure_engine_dep = declare_dependency(
  link_with : secure_engine_lib,
  include_directories : [secure_engine_includes, se_manager_includes],
  dependencies : app_deps,
)

secure_engine_bl_lib = library('secure-engine-bl',
  srcs,
  c_args : features + ['-DSECURE_ENGINE_BOOTLOADER'],
  include_directories : [secure_engine_includes, bl_includes, 'src', se_manager_includes],
  dependencies : bl_deps,
)
secure_engine_bl_dep = declare_dependency(
  link_with : secure_engine_bl_lib,
  include_directories : [secure_engine_includes, se_manager_includes],
  dependencies : bl_deps,
)
