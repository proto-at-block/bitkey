deps = [helpers_dep, log_dep, rtos_dep, crypto_dep, picocert_dep, secutils_dep, sysevent_dep, wstd_dep, fs_dep, shell_dep, protos_dep]
includes = ['.']

# Common sources used by all platforms
common_srcs = [
  'src/secure_channel_cert_common.c',
  'src/secure_channel_cert_impl.c',
  'src/secure_channel_common.c',
  'src/secure_channel_nfc.c',
  'src/secure_channel_uart.c',
]

# Platform-specific certificate descriptor sources
secure_channel_platform_configs = {
  'w1': {
    'srcs': [
      'src/w1/secure_channel_certs.c',
    ],
  },
  'w3-uxc': {
    'srcs': [
      'src/w3-uxc/secure_channel_certs.c',
      'src/secure_channel_cert_test.c',
      'src/secure_channel_cmd.c',
    ],
  },
  'w3-core': {
    'srcs': [
      'src/w3-core/secure_channel_certs.c',
      'src/secure_channel_cert_test.c',
      'src/secure_channel_cmd.c',
    ],
  },
}

# Build platform-specific libraries
secure_channel_libs = {}
secure_channel_platform_deps = {}
secure_channel_cmd_deps = {}

foreach platform : secure_channel_platform_configs.keys()
  platform_config = secure_channel_platform_configs[platform]

  secure_channel_libs += {
    platform : library('secure-channel-' + platform,
      common_srcs + platform_config['srcs'],
      include_directories : ['inc', 'inc-private', 'src', security_config_includes],
      dependencies : deps,
    )
  }

  secure_channel_platform_deps += {
    platform: declare_dependency(
      link_with : secure_channel_libs[platform],
      include_directories : ['inc'],
      dependencies : deps,
    )
  }

  secure_channel_cmd_deps += {
    platform: declare_dependency(
      link_whole : secure_channel_libs[platform],
      include_directories : ['inc'],
    )
  }
endforeach

# Export as maps so consumers can select the appropriate platform
# Usage: secure_channel_deps['w3-core'], secure_channel_cmd_deps['w3-uxc'], etc.
secure_channel_deps = secure_channel_platform_deps

# Convenience dependencies that auto-select based on current build platform
secure_channel_dep = secure_channel_deps.get(platform)
secure_channel_cmd_dep = secure_channel_cmd_deps.get(platform)

secure_channel_uart_test = executable('secure-channel-uart-test',
  [
    'src/secure_channel_uart_test.c',
    'src/secure_channel_uart.c',
  ],
  include_directories : [
    'inc',
    'inc-private',
    'src',
    helpers_includes,
    assert_includes,
    security_config_includes,
    '../crypto/inc',
    '../log/inc',
    '../shell/inc',
    '../../hal/sysevent',
  ],
  dependencies : [
    rtos_dep,
    secutils_dep,
    wstd_dep,
    picocert_dep,
    fs_dep,
    protos_dep,
    test_deps,
  ],
  c_args : test_defines,
)
test('secure-channel uart test', secure_channel_uart_test)

# BCW-08, BCW-15, BCW-16: NFC secure channel input-size validation fuzzer.
# Built on posix only using just common_srcs (no platform-specific cert files).
# secure_channel_establish_impl uses crypto_key_exchange; cert signing is never
# reached for the out-of-range pk_host_len inputs that BCW-15 exercises.
# secure_channel_cipher is exercised for BCW-08 without any cert dependency.
if system == 'darwin' or system == 'linux'
  secure_channel_fuzz = executable('secure-channel-fuzz',
    ['src/secure_channel_fuzz.cc',
     'src/secure_channel_common.c',
     'src/secure_channel_nfc.c'],
    cpp_args : fuzz_args,
    link_args : fuzz_args,
    include_directories : ['inc', 'inc-private', 'src', security_config_includes],
    dependencies : [crypto_deps['posix'], secutils_dep, log_dep, wstd_dep,
                    rtos_dep, fff_dep, fuzzed_data_provider_dep],
  )
endif
