deps = [helpers_dep, log_dep, rtos_dep, crypto_dep, secutils_dep, picocert_dep, wstd_dep]
includes = ['.']

secure_channel_lib = library('secure-channel',
  'src/secure_channel_common.c',
  'src/secure_channel_nfc.c',
  'src/secure_channel_uart.c',
  include_directories : ['inc', 'src'],
  dependencies : deps,
)

secure_channel_dep = declare_dependency(
  link_with : secure_channel_lib,
  include_directories : ['inc'],
  dependencies : deps,
)
