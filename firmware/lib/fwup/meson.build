fwup_platform_configs = {
  'efr32mg24' : {
    'name' : 'efr32',
    'includes' : [
      gecko_sdk_bootloader_includes,
      security_config_includes,
    ],
    'sources' : [
      'src/efr32/fwup_flash.c',
      'src/efr32/fwup_verify.c',
    ],
    'dependencies' : [
      bootloader_app_deps['app-cert'],
      crypto_dep,
      mcu_dep,
      perf_dep,
      wallet_dep,
    ],
  },
  'stm32u5xx' : {
    'name' : 'stm32',
    'includes' : [
      security_config_includes,
    ],
    'sources' : [
      'src/stm32/fwup_flash.c',
      'src/stm32/fwup_verify.c',
    ],
    'dependencies' : [
      bootloader_app_deps['picocert'],
      crypto_dep,
      mcu_dep,
      perf_dep,
    ],
  },
  'posix' : {
    'name' : 'posix',
    'includes' : [
      security_config_includes,
    ],
    'sources' : [
      'src/efr32/fwup_verify.c',
      'src/posix/fwup_flash.c',
    ],
    'dependencies' : [
      bootloader_app_deps['app-cert'],
      crypto_dep,
      fff_dep,
      perf_stubs_dep,
      wallet_dep,
    ],
  },
}

srcs = [
  'src/fwup.c',
  'src/fwup_delta.c',
  'src/fwup_addr.c',
]

deps = [
  bitlog_dep,
  detools_dep,
  fs_dep,
  log_dep,
  mempool_dep,
  perf_dep,
  protos_dep,
]

fwup_libs = {}
fwup_deps = {}

# Header only dependency.
fwup_dep = declare_dependency(
  include_directories : include_directories('inc'),
  dependencies : [protos_dep],
)

foreach fwup_platform : fwup_platform_configs.keys()
  fwup_config = fwup_platform_configs[fwup_platform]

  fwup_libs += {
    fwup_config['name'] : library('fwup-' + fwup_config['name'],
      srcs + fwup_config['sources'],
      c_args : detools_c_flags,
      include_directories : ['inc', 'inc-private', 'src'] + fwup_config['includes'],
      dependencies : deps + fwup_config['dependencies'],
    )
  }

  fwup_deps += {
    fwup_config['name'] : declare_dependency(
      link_with : fwup_libs[fwup_config['name']],
      include_directories : ['inc'],
    )
  }
endforeach

fwup_fuzz = executable('fwup-fuzz',
  'fwup_fuzz.cc',
  include_directories : ['inc', 'inc-private', 'src', security_config_includes],
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : deps + [fwup_deps['posix'], crypto_deps['posix'], fff_dep, fuzzed_data_provider_dep],
)

fwup_efr32_test = executable('fwup-efr32-test',
  'fwup_test.c',
  include_directories : ['inc', 'inc-private', 'src', gecko_sdk_bootloader_includes, security_config_includes],
  dependencies : deps + [fwup_deps['posix'], crypto_deps['posix'], test_deps],
)
test('fwup-efr32 test', fwup_efr32_test)
