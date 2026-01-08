fw_root = '../../..'

srcs = [
  'main.c',
  fw_root/'config/secure_boot_config_stm32u5.c',
]

includes = [
  config_includes,
  fw_root/'lib/bootloader/inc',
  fw_root/'third-party/picocert',
  fw_root/'mcu/stm32u5xx/src',
]

partitions_config = meson.project_source_root()/'config/partitions/w3a-uxc/partitions.yml'

base_deps = [
  # MPU
  w3_uxc_mpu_dep,

  # HAL
  clock_dep,
  exti_deps['stm32u5xx'],
  memfault_deps['stm32u5xx'],
  serial_dep,
  sysevent_dep,
  touch_dep,

  # Libs
  bitlog_dep,
  canary_dep,
  crypto_deps['stm32u5xx'],
  fs_dep,
  fs_stm32u585_dep,
  fwup_deps['stm32'],
  gfx_dep,
  langpack_deps['en'],
  metadata_dep,
  mpu_regions_dep,
  protos_dep,
  ringbuf_dep,
  secure_channel_dep,
  telemetry_storage_dep,
  uc_device_dep,

  # Tasks
  fwup_task_deps['uxc'],
  key_manager_task_deps['w3-uxc'],
  mfgtest_task_deps['uxc'],
  sysinfo_task_deps['w3-uxc'],
  usart_task_dep,

  # OS
  freertos_deps['w3-uxc'],
  rtos_deps['stm32u5xx'],
]

display_deps = [display_driver_dep, display_task_dep]
display_mfgtest_deps = [display_driver_mfgtest_dep, display_task_mfgtest_dep]

touch_deps = [touch_task_dep]
touch_mfgtest_deps = [touch_task_mfgtest_dep]

dev_tools_deps = [
  shell_deps['w3-uxc'],
  shell_task_dep,
  log_dep,

  # Commands
  canary_cmd_dep,
  crypto_cmd_deps['stm32u5xx'],
]

dev_variant_deps = base_deps + display_deps + touch_deps + dev_tools_deps
mfgtest_variant_deps = base_deps + display_mfgtest_deps + touch_mfgtest_deps + dev_tools_deps
prod_variant_deps = base_deps + display_deps + touch_deps + [
  log_noprintf_dep
]

foreach hardware : w3_uxc_hardware_configs.keys()
  foreach build_env : w3_build_env_configs.keys()
    foreach slot : slot_configs.keys()
      prog = f'@hardware@-app-@slot@-@build_env@'
      ldscript_name = f'@prog@.ld'
      elf_name = f'@prog@.elf'
      bin_name = f'@prog@.bin'
      meta_name = f'@prog@.metadata.bin'
      nometa_elf_name = f'@prog@.nometa.elf'
      signed_elf_name = f'@prog@.signed.elf'

      hardware_deps = w3_uxc_hardware_configs[hardware][0]
      hardware_args = w3_uxc_hardware_configs[hardware][1]
      slot_args = slot_configs[slot][0]
      build_env_deps = w3_build_env_configs[build_env][0]
      build_env_args = w3_build_env_configs[build_env][1]
      key_type = w3_build_env_configs[build_env][2]

      if build_env == 'prod'
        deps = prod_variant_deps
      elif build_env == 'mfgtest-dev'
        deps = mfgtest_variant_deps
      else
        deps = dev_variant_deps
      endif

      ldscript = custom_target(ldscript_name,
        depend_files : [
          files(
            meson.project_source_root()/'tasks/__init__.py',
            meson.project_source_root()/'config/partitions/__init__.py',
            meson.project_source_root()/'config/partitions/w3a-uxc/memory.jinja.ld',
            meson.project_source_root()/'config/partitions/w3a-uxc/stm32u5xx.jinja.ld',
            meson.project_source_root()/'config/partitions/w3a-uxc/partitions.yml'
          )
        ],
        output : ldscript_name,
        command : [invoke, 'gen.linker', '--platform', 'w3-uxc', '--target', prog, '--output', '@OUTPUT@'],
        build_by_default: true
      )

      nometa_elf = executable(nometa_elf_name,
        srcs,
        ldscript,
        link_args : [
          f'-T' + ldscript.full_path(),
          f'-Wl,-Map=@prog@.map,--cref',
        ],
        c_args : [
          '-DSTM32U585xx',
          f'-DSIGNATURE_SECTION=".app_@slot@_codesigning_signature_section"',
          f'-DPROPERTIES_SECTION=".app_@slot@_properties_section"',
          f'-DMETADATA_SECTION=".app_@slot@_metadata_section"',
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

      # Sign the ELF
      combined_elf_input = '@OUTDIR@'/elf_name
      signed_elf = custom_target(signed_elf_name,
        input : combined_elf,
        output : signed_elf_name,
        command : [signer, 'sign',
                  '--elf', combined_elf_input,
                  '--product', 'w3a-uxc',
                  '--key-type', key_type,
                  '--image-type', 'app',
                  '--slot', slot,
                  '--app-version', fw_version,
                  '--partitions-config', partitions_config,
                  '--keys-dir', meson.current_source_dir() + '/' + fw_root + '/config/keys'],
        build_by_default : true,
        depends : [combined_elf]
      )
    endforeach
  endforeach
endforeach
