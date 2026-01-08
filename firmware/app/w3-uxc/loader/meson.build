fw_root = '../../..'

srcs = [
  'main.c',
  'bootload.S',
]

includes = [
  '.',
]

bl_deps = [
  bootloader_bl_deps['picocert'],
  canary_dep,
  clock_dep,
  crypto_bl_deps['stm32u5xx'],
  helpers_dep,
  mcu_bl_deps['stm32u5xx'],
  secutils_bl_dep,
]

dev_bl_deps = bl_deps
prod_bl_deps = bl_deps

partitions_config = meson.project_source_root()/'config/partitions/w3a-uxc/partitions.yml'

foreach hardware : w3_uxc_hardware_configs.keys()
  foreach build_env : w3_build_env_configs.keys()
    prog = f'@hardware@-loader-@build_env@'
    ldscript_name = f'@prog@.ld'
    elf_name = f'@prog@.elf'
    bin_name = f'@prog@.bin'
    meta_name = f'@prog@.metadata.bin'
    nometa_elf_name = f'@prog@.nometa.elf'
    signed_elf_name = f'@prog@.signed.elf'

    hardware_deps = w3_uxc_hardware_configs[hardware][0]
    build_env_deps = w3_build_env_configs[build_env][0]
    key_type = w3_build_env_configs[build_env][2]

    if build_env == 'prod'
      deps = prod_bl_deps
      # For the W3-UXC loader, override to use the production certificate.
      # This ensures the prod loader can verify prod-signed applications.
      key_type = 'prod'
    else
      deps = dev_bl_deps
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
      build_by_default : true
    )

    nometa_elf = executable(nometa_elf_name,
      srcs,
      ldscript,
      link_args : [
        f'-Wl,-Map=@prog@.map,--cref',
        f'-T' + ldscript.full_path(),
      ],
      include_directories : includes,
      dependencies : [deps, hardware_deps, build_env_deps],
    )

    # Build the bare app binary with an empty metadata section.
    bin = custom_target(bin_name,
      input : nometa_elf,
      output : bin_name,
      command : [objcopy, '-O', 'binary', '@INPUT@', '@OUTPUT@'],
      build_by_default : true,
    )

    # Build the metadata section
    meta = custom_target(meta_name,
      input : bin,
      output : meta_name,
      command : [invoke, 'gen.meta', '--generate', '-i', '@INPUT@', '-o', '@OUTPUT@', '--hw-rev', hardware, '--build-type', build_env, '--fw-image-type', 'bl'],
      build_by_default : true,
    )

    # Add the metadta section into the final elf.
    elf_input = '@OUTDIR@'/nometa_elf_name
    combined_elf = custom_target(elf_name,
      input : meta,
      output : elf_name,
      command : [objcopy, '--update-section', '.bl_metadata_section=@INPUT@', elf_input, '@OUTPUT@'],
      build_by_default : true,
      depends : [nometa_elf]
    )

    # Sign the bootloader
    combined_elf_input = '@OUTDIR@'/elf_name
    signed_elf = custom_target(signed_elf_name,
      input : combined_elf,
      output : signed_elf_name,
      command : [signer, 'sign',
                '--elf', combined_elf_input,
                '--product', 'w3a-uxc',
                '--key-type', key_type,
                '--image-type', 'bl',
                '--app-version', bl_version,
                '--partitions-config', partitions_config,
                '--keys-dir', meson.current_source_dir() + '/' + fw_root + '/config/keys'],
      build_by_default : true,
      depends : [combined_elf]
    )
  endforeach
endforeach
