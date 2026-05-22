fw_root = '../../..'

srcs = [
  'main.c',
  gecko_sdk_driver_srcs,
  fw_root/'config/secure_boot_config.c',
]

includes = [
  config_includes,
  gecko_sdk_includes,
  # Reach in for a private includes for NFC demo code
  fw_root/'hal/nfc/src/',
  fw_root/'third-party/st-rfal/include',
  fw_root/'third-party/st-rfal/source/st25r3916/',
]

partitions_config = meson.project_source_root()/'config/partitions/w1a/partitions.yml'

common_deps = [
  # HAL
  led_dep,
  serial_dep,
  hal_nfc_deps['embedded'],
  bio_dep,
  secure_engine_dep,
  power_dep,
  memfault_deps['efr32mg24'],
  board_id_dep,
  tamper_dep,
  clock_dep,
  exti_deps['efr32mg24'],

  # Tasks
  nfc_task_w1a_dep,
  auth_task_deps['w1'],
  key_manager_task_deps['w1'],
  mfgtest_task_deps['w1'],
  sysinfo_task_deps['w1'],
  fwup_task_deps['w1-core'],
  captouch_task_dep,
  # MPU
  w1_mpu_dep,

  # Libraries
  animation_dep,
  crypto_deps['efr32mg24'],
  sysinfo_dep,
  protos_dep,
  metadata_dep,
  fs_dep,
  fs_efr32mg24_dep,
  wallet_dep,
  bitlog_dep,
  feature_flags_dep,
  secure_channel_deps['w1'],
  secutils_dep,
  canary_dep,
  mpu_regions_dep,
  grant_dep,

  # OS
  freertos_deps['w1'],
  rtos_deps['efr32mg24'],
]

dev_tools_deps = [
  shell_deps['w1'],
  shell_task_dep,
  log_dep,

  rtos_cmd_deps['efr32mg24'],
  led_cmd_dep,
  perf_cmd_dep,
  bio_cmd_dep,
  metadata_cmd_dep,
  fs_cmd_dep,
  sysinfo_cmd_dep,
  crypto_cmd_deps['efr32mg24'],
  memfault_cmd_deps['efr32mg24'],
  power_cmd_dep,
  wca_cmd_dep,
  bitlog_cmd_dep,
  canary_cmd_dep,
  mpu_cmd_dep,
  ui_cmd_dep,
  unlock_cmd_dep,
]

foreach hardware : w1_hardware_configs.keys()
  foreach build_env : w1_build_env_configs.keys()
    foreach slot : slot_configs.keys()
      prog = f'@hardware@-app-@slot@-@build_env@'
      ldscript_name = f'@prog@.ld'
      elf_name = f'@prog@.elf'
      bin_name = f'@prog@.bin'
      meta_name = f'@prog@.metadata.bin'
      nometa_elf_name = f'@prog@.nometa.elf'
      signed_elf_name = f'@prog@.signed.elf'

      hardware_deps = w1_hardware_configs[hardware][0]
      hardware_args = w1_hardware_configs[hardware][1]
      slot_args = slot_configs[slot][0]
      build_env_deps = w1_build_env_configs[build_env][0]
      build_env_args = w1_build_env_configs[build_env][1]
      key_type = w1_build_env_configs[build_env][2]
      fwup_platform_dep = fwup_deps['efr32']

      if build_env == 'mfgtest-dev'
        fwup_platform_dep = fwup_deps['efr32-mfgtest']
      endif

      base_deps = common_deps + [fwup_platform_dep, ui_task_led_dep]
      mfgtest_base_deps = common_deps + [fwup_platform_dep, ui_task_led_mfgtest_dep]
      # log_uart_dep is the tokenized UART transport — only linked into dev.
      # Mfgtest reconfigures the build with -Dlog_tokenized=false (see
      # bitkey/meson.py) so the macro path falls back to legacy printf and
      # nothing in the build references log_uart_emit_*. Prod uses
      # log_noprintf_dep + project-wide DISABLE_PRINTF.
      dev_variant_deps = base_deps + dev_tools_deps + [log_uart_dep]
      mfgtest_variant_deps = mfgtest_base_deps + dev_tools_deps
      prod_variant_deps = base_deps + [log_noprintf_dep]

      if build_env == 'prod'
        deps = prod_variant_deps + [opt_deps['prod']]
      elif build_env == 'mfgtest-dev'
        deps = mfgtest_variant_deps + [opt_deps['dev']]
      else
        deps = dev_variant_deps + [opt_deps['dev']]
      endif

      ldscript = custom_target(ldscript_name,
        depend_files : [
          files(
            meson.project_source_root()/'tasks/__init__.py',
            meson.project_source_root()/'tasks/generate.py',
            meson.project_source_root()/'tasks/lib/config.py',
            meson.project_source_root()/'tasks/lib/paths.py',
            meson.project_source_root()/'tasks/lib/platforms.py',
            meson.project_source_root()/'python/bitkey/meson.py',
            meson.project_source_root()/'config/partitions/__init__.py',
            meson.project_source_root()/'config/partitions/w1a/memory.jinja.ld',
            meson.project_source_root()/'config/partitions/w1a/efr32mg24.jinja.ld',
            meson.project_source_root()/'config/partitions/w1a/partitions.yml'
          )
        ],
        output : ldscript_name,
        command : [invoke, 'gen.linker', '--target', prog, '--output', '@OUTPUT@'],
        build_by_default: true
      )

      nometa_elf = executable(nometa_elf_name,
        srcs,
        ldscript,
        link_args : [
          f'-Wl,-Map=@prog@.map,--cref',
          f'-T' + ldscript.full_path(),
        ],
        # TODO Fix these args. They don't propagate.
        c_args : [
          '-DSENSOR=fpc_bep_sensor_1523s',
          '-DFPC_LOG_LEVEL_DEBUG',
          '-DST25R_SELFTEST',
          f'-DSIGNATURE_SECTION=".app_@slot@_codesigning_signature_section"',
          f'-DPROPERTIES_SECTION=".app_@slot@_properties_section"',
          f'-DSYSINFO_SOFTWARE_TYPE="app-@slot@-@build_env@"',
          f'-DSYSINFO_HARDWARE_REVISION="@hardware@"',
          f'-DSYSINFO_VERSION_STRING="@fw_version@"',
        ] + hardware_args + slot_args + build_env_args,
        include_directories : includes,
        dependencies : [deps, hardware_deps, build_env_deps]
      )

      # Build the bare app binary with an empty metadata section
      bin = custom_target(bin_name,
        input : nometa_elf,
        output : bin_name,
        # --remove-section is needed because, without it, the binary will contain padding which fills up the entire firmware slot
        # until the signature at the end.
        command : [objcopy, '-O', 'binary', '@INPUT@', '@OUTPUT@', '--remove-section', f'.app_@slot@_codesigning_signature_section'],
        build_by_default : true,
      )

      # Build the metadata section
      meta = custom_target(meta_name,
        input : bin,
        output : meta_name,
        command : [invoke, 'gen.meta', '--generate', '-i', '@INPUT@', '-o', '@OUTPUT@', '--hw-rev', hardware, '--build-type', build_env, '--fw-image-type', 'app'],
        build_by_default : true,
      )

      # Add the metadata section into the final elf
      elf_input = '@OUTDIR@'/nometa_elf_name
      combined_elf = custom_target(elf_name,
        input : meta,
        output : elf_name,
        command : [objcopy, '--update-section', f'.app_@slot@_metadata_section=' + '@INPUT@', elf_input, '@OUTPUT@'],
        build_by_default : true,
        depends : [nometa_elf]
      )

      # Sign it
      combined_elf_input = '@OUTDIR@'/elf_name
      signer_cmd = [signer, 'sign',
                  '--elf', combined_elf_input,
                  '--product', 'w1a',
                  '--key-type', key_type,
                  '--image-type', 'app',
                  '--slot', slot,
                  '--app-version', fw_version,
                  '--partitions-config', partitions_config,
                  '--keys-dir', meson.current_source_dir() + '/' + fw_root + '/config/keys']
      signed_elf = custom_target(signed_elf_name,
        input : combined_elf,
        output : signed_elf_name,
        command : signer_cmd,
        build_by_default : true,
        depends : [combined_elf]
      )
    endforeach
  endforeach
endforeach
