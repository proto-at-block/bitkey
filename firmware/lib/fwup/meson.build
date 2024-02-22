srcs = [
  'src/fwup.c',
  'src/fwup_delta.c',
  'src/fwup_addr.c',
]

deps = [
  wallet_dep,
  mempool_dep,
  protos_dep,
  log_dep,
  crypto_dep,
  perf_dep,
  bitlog_dep,
  bootloader_dep,
  detools_dep,
  fs_dep,
]

if system == 'efr32mg24'
  deps += [perf_dep, mcu_efr32xg24_dep]
  srcs += ['src/efr32/fwup_flash.c']
else
  deps += [perf_stubs_dep, fff_dep]
  srcs += ['src/posix/fwup_flash.c']
endif

fwup_includes = [
  'inc',
  'src',
  gecko_sdk_bootloader_includes,
  security_config_includes,
]

fwup_lib = library('fwup',
  srcs,
  c_args : detools_c_flags,
  include_directories : fwup_includes,
  dependencies : deps,
)
fwup_dep = declare_dependency(
  link_with : fwup_lib,
  include_directories : 'inc',
  dependencies : deps,
)

fwup_fuzz = executable('fwup-fuzz',
  'fwup_fuzz.cc',
  include_directories : fwup_includes,
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [fwup_dep, fff_dep, fuzzed_data_provider_dep],
)

fwup_test = executable('fwup-test',
  'fwup_test.c',
  include_directories : fwup_includes,
  dependencies : [fwup_dep, test_deps],
)
test('fwup test', fwup_test)

