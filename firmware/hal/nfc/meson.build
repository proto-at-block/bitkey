rfal = '../../third-party/st-rfal/'

## NFC HAL

if system == 'darwin' or system == 'linux'
  hal_srcs = [
    rfal/'source/rfal_isoDepPATCHED.c',
  ]
  hal_private_includes = ['src/posix', 'inc-private']
  deps = [secutils_dep]
else
  hal_srcs = [
    'src/embedded/analog_config.c',
    'src/embedded/hal_nfc.c',
    'src/embedded/hal_nfc_listener.c',
    'src/embedded/hal_nfc_loopback.c',
    'src/embedded/hal_nfc_reader.c',
    'src/embedded/hal_nfc_stubs.c',
    'src/embedded/hal_nfc_timer.c',
  ]
  hal_private_includes = ['src/embedded', 'inc-private']
  deps = [rtos_dep, log_dep, exti_dep, secutils_dep, wca_dep, t4t_dep]
endif


## ST RFAL
# There's a circular dependency with st-rfal and hal/nfc, so we just pull
# st-rfal sources in here and build them as one library.
features = [
  '-DRFAL_USE_I2C=1',
  '-DST25R3916',
  '-DUSE_HAL_DRIVER'
]

chip_srcs = [
  rfal/'source/st25r3916/st25r3916_aat.c',
  rfal/'source/st25r3916/st25r3916_com.c',
  rfal/'source/st25r3916/st25r3916_irq.c',
  rfal/'source/st25r3916/st25r3916.c',
  rfal/'source/st25r3916/rfal_rfst25r3916.c',
]

srcs = [
  rfal/'source/rfal_analogConfig.c',
  rfal/'source/rfal_crc.c',
  rfal/'source/rfal_cd.c',
  rfal/'source/rfal_dpo.c',
  rfal/'source/rfal_iso15693_2.c',
  rfal/'source/rfal_isoDep.c',
  rfal/'source/rfal_nfc.c',
  rfal/'source/rfal_nfca.c',
  rfal/'source/rfal_nfcb.c',
  rfal/'source/rfal_nfcDep.c',
  rfal/'source/rfal_t4t.c',
  chip_srcs,
  hal_srcs,
]

posix_srcs = [
  hal_srcs,
]

hal_nfc_includes = include_directories('inc')
includes = [
  rfal/'source',
  rfal/'include',
  rfal/'source/st25r3916',
  hal_private_includes,
  rtos_includes,
  config_includes,
] + hal_nfc_includes

hal_nfc_libs = {}
hal_nfc_deps = {}

hal_nfc_libs += {
  'embedded' : library('hal-nfc-embedded',
    srcs,
    c_args : [
      features
    ],
    include_directories : includes,
    dependencies : deps,
  )
}
hal_nfc_deps += {
  'embedded' : declare_dependency(
    link_with : hal_nfc_libs['embedded'],
    dependencies : deps,
    include_directories : ['inc'],
  )
}

hal_nfc_libs += {
  'posix' : library('hal-nfc-posix',
    posix_srcs,
    include_directories : [includes],
    dependencies : deps,
  )
}
hal_nfc_deps += {
  'posix' :  declare_dependency(
    link_with : hal_nfc_libs['posix'],
    dependencies : deps,
    include_directories : ['inc', includes],
  )
}

hal_nfc_cmd_lib = library('hal-nfc-cmd', [
    'src/embedded/hal_nfc_cmd.c',
  ],
  include_directories : [includes],
  dependencies : [hal_nfc_deps['embedded'], shell_dep],
)
hal_nfc_cmd_dep = declare_dependency(
  link_whole : hal_nfc_cmd_lib,
)

nfc_fuzz = executable('nfc-fuzz',
  'src/posix/nfc_fuzz.cc',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [deps, fff_dep, fuzzed_data_provider_dep, hal_nfc_deps['posix']]
)
