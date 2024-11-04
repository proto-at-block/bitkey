srcs = [
  'src/ecc.c',
  'src/key_management.c',
  'src/ripemd160.c',
]
crypto_includes = ['inc']
deps = [
  secp256k1_dep,
  helpers_dep,
  secutils_dep,
  assert_dep,
  log_dep,
]

if system == 'efr32mg24'
  srcs += [
    'src/efr32/aes.c',
    'src/efr32/ecc.c',
    'src/efr32/secure_rng.c',
    'src/efr32/key_management.c',
    'src/efr32/hash.c',
    'src/efr32/attestation.c',
    'src/efr32/key_exchange.c',
    'src/efr32/hkdf.c',
  ]

  deps += [
    printf_dep,
    secure_engine_dep,
    rtos_dep,
  ]

  crypto_lib = library('crypto',
    srcs,
    include_directories : [crypto_includes, 'src', '.'],
    c_args : ['-DIMAGE_TYPE_APPLICATION=1'],
    dependencies : deps,
  )
  crypto_dep = declare_dependency(
    link_with : crypto_lib,
    include_directories : crypto_includes,
  )

  crypto_bl_lib = library('crypto-bl',
    srcs,
    include_directories : [crypto_includes, 'src', '.'],
    c_args : ['-DIMAGE_TYPE_BOOTLOADER=1'],
    dependencies : deps,
  )
  crypto_bl_dep = declare_dependency(
    link_with : crypto_bl_lib,
    include_directories : crypto_includes,
  )

elif system == 'darwin' or system == 'linux'
  srcs += [
    'src/posix/aes.c',
    'src/posix/secure_rng.c',
    'src/posix/hash.c',
    'src/posix/key_management.c',
    'src/posix/ecc.c',
  ]

  cc = meson.get_compiler('c')
  if system == 'darwin'
    # Use brew to find the installed version of openssl
    # Calling brew is a bit slow, but it keeps the call to find_library future proof
    cellar_cmd = run_command('brew', '--cellar', 'openssl', check : true)
    prefix_cmd = run_command('brew', 'list', '--versions', 'openssl', check : true)
    openssl_path =  cellar_cmd.stdout().strip() + '/' + prefix_cmd.stdout().strip().split(' ')[1]

    openssl_lib = cc.find_library('crypto', dirs : [openssl_path + '/lib/'])
    openssl_dep = declare_dependency(
      dependencies : openssl_lib,
      include_directories : openssl_path + '/include/'
    )
  elif system == 'linux'
    openssl_lib = cc.find_library('crypto')
    openssl_dep = declare_dependency(
      dependencies : openssl_lib,
      include_directories : '/usr/include/'
    )
  endif

  deps += [openssl_dep]

  crypto_lib = library('crypto',
    srcs,
    include_directories : [crypto_includes, 'src', '.'],
    dependencies : deps,
  )
  crypto_dep = declare_dependency(
    link_with : crypto_lib,
    include_directories : crypto_includes,
    dependencies : deps,
  )
  crypto_bl_lib = library('crypto-bl',
    srcs,
    include_directories : [crypto_includes, 'src', '.'],
    c_args : ['-DIMAGE_TYPE_BOOTLOADER=1'],
    dependencies : deps,
  )
  crypto_bl_dep = declare_dependency(
    link_with : crypto_bl_lib,
    include_directories : crypto_includes,
  )

endif

crypto_cmd_lib = library('crypto-cmd',
  'src/crypto_cmd.c',
  include_directories : includes,
  dependencies : [crypto_dep, deps, shell_dep],
)
crypto_cmd_dep = declare_dependency(
  link_whole : crypto_cmd_lib,
  dependencies : [crypto_dep, deps, shell_dep],
)

crypto_test = executable('crypto-test',
  'src/crypto_test.c',
  include_directories : [crypto_includes, '.'],
  dependencies : [crypto_dep, test_deps],
)
test('crypto test', crypto_test)
