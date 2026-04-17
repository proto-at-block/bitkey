sap_core_srcs = ['src/sign_action_proof_core.c']

sap_core_lib = library('sign-action-proof-core',
  sap_core_srcs,
  include_directories : ['inc'],
)
sap_core_dep = declare_dependency(
  link_with : sap_core_lib,
  include_directories : include_directories('inc'),
)

# Firmware sign implementation (requires crypto, wallet, protobuf deps)
sap_sign_lib = library('sap-sign',
  'src/sap_sign.c',
  include_directories : ['inc'],
  dependencies : [sap_core_dep, wallet_dep, protos_dep, log_dep],
)
sap_dep = declare_dependency(
  link_with : [sap_core_lib, sap_sign_lib],
  include_directories : include_directories('inc'),
)

sap_test = executable('sign-action-proof-test',
  'src/sign_action_proof_test.c',
  dependencies : [sap_core_dep, test_deps],
)
test('sign_action_proof test', sap_test)

sap_fuzz = executable('sap-fuzz',
  'sap_fuzz.cc',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [sap_core_dep, fuzzed_data_provider_dep],
)
