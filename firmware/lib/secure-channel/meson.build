deps = [helpers_dep, log_dep, rtos_dep, crypto_dep, secutils_dep]
includes = ['.']

secure_channel_lib = library('secure-channel',
  'secure_channel.c',
  include_directories : includes,
  dependencies : deps,
)

secure_channel_dep = declare_dependency(
  link_with : secure_channel_lib,
  include_directories : includes,
  dependencies : deps,
)
