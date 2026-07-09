deps = [
  bip32_dep,
  wallet_dep,
  bitlog_dep,
  helpers_dep,
  policy_dep,
  log_dep,
  ipc_dep,
  wstd_dep,
]

grant_lib = library('grant',
  'src/grant_protocol.c',
  'src/grant_protocol_storage.c',
  include_directories : ['inc', 'src'],
  dependencies : deps,
)

grant_dep = declare_dependency(
  link_with : grant_lib,
  include_directories : ['inc'],
  dependencies : deps,
)

grant_test = executable('grant-test',
  'grant_protocol_test.c',
  include_directories : ['inc', 'src'],
  dependencies : [crypto_deps['posix'], grant_dep, test_deps],
)
test('grant test', grant_test)

# BCW: grant_protocol verification fuzzer.
# Compiles grant_protocol.c directly so storage functions can be stubbed
# inline in the .cc harness without overriding symbols from grant_protocol_storage.c.
# FUZZ_BUILD flag (in fuzz_args) makes ASSERT_LOG a no-op and ASSERT use
# __builtin_trap() instead of exit/abort.
grant_protocol_fuzz = executable('grant-protocol-fuzz',
  ['grant_protocol_fuzz.cc', 'src/grant_protocol.c'],
  include_directories : ['inc', 'src'],
  # Rename IPC sends only in the fuzz-compiled copy of grant_protocol.c. This
  # avoids GNU ld's --wrap, which is unsupported by macOS ld64.
  c_args   : fuzz_args + [
    '-D_ipc_send=grant_fuzz_ipc_send',
    '-Dsecure_glitch_random_delay=grant_fuzz_secure_glitch_random_delay',
    '-Dsecure_glitch_detect=grant_fuzz_secure_glitch_detect',
  ],
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [
    bip32_dep,
    wallet_dep,
    bitlog_dep,
    helpers_dep,
    policy_dep,
    log_dep,
    ipc_dep,
    wstd_dep,
    crypto_deps['posix'],
    fff_dep,
    fuzzed_data_provider_dep,
  ],
)
