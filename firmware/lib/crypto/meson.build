crypto_platform_configs = {
  'efr32mg24' : {
    'sources' : [
      'src/efr32/aes.c',
      'src/efr32/attestation.c',
      'src/efr32/ecc.c',
      'src/efr32/hash.c',
      'src/efr32/hkdf.c',
      'src/efr32/key_management.c',
      'src/efr32/secure_rng.c',
    ],
    'dependencies' : [
      printf_dep,
      rtos_dep,
      secure_engine_dep,
      secutils_dep,
    ],
    'bl_dependencies' : [
      printf_dep,
      rtos_dep,
      secure_engine_bl_dep,
      secutils_bl_dep,
    ],
    'cmd_sources' : [
      'src/efr32/crypto_cmd.c',
    ],
    'tests' : [],
  },
  'stm32u5xx' : {
    'sources' : [
      'src/stm32/aes.c',
      'src/stm32/attestation.c',
      'src/stm32/crypto_stm32_common.c',
      'src/stm32/curve25519.c',
      'src/stm32/ecc.c',
      'src/stm32/hash.c',
      'src/stm32/hkdf.c',
      'src/stm32/hmac_drbg.c',
      'src/stm32/key_management.c',
      'src/stm32/rng.c',
    ],
    'dependencies' : [
      log_dep,
      mcu_dep,
      rtos_dep,
      secutils_dep,
    ],
    'bl_dependencies' : [
      mcu_dep,
      rtos_dep,
      secutils_bl_dep,
    ],
    'cmd_sources' : [
      'src/stm32/crypto_cmd.c',
    ],
    'tests' : [
      'src/stm32/crypto_test.c',
    ]
  },
  'posix' : {
    'sources' : [
      'src/posix/aes.c',
      'src/posix/ecc.c',
      'src/posix/hash.c',
      'src/posix/key_management.c',
      'src/posix/secure_rng.c',
    ],
    'dependencies' : [
      secutils_dep,
    ],
    'bl_dependencies' : [],
    'cmd_sources' : [],
    'tests' : [],
  },
}

srcs = [
  'src/crypto_test.c',
  'src/ecc.c',
  'src/key_management.c',
  'src/key_exchange.c',
  'src/ripemd160.c',
]

deps = [
  assert_dep,
  helpers_dep,
  log_dep,
  secp256k1_dep,
]

crypto_libs = {}
crypto_deps = {}
crypto_bl_libs = {}
crypto_bl_deps = {}
crypto_test_libs = {}
crypto_test_deps = {}
crypto_cmd_libs = {}
crypto_cmd_deps = {}

# Header only dependency.
crypto_dep = declare_dependency(
  include_directories : ['inc'],
)

foreach crypto_platform : crypto_platform_configs.keys()
  crypto_config = crypto_platform_configs[crypto_platform]
  dependencies = crypto_config['dependencies']
  bl_dependencies = crypto_config['bl_dependencies']

  if crypto_platform == 'posix'
    if system == 'darwin'
      cc = meson.get_compiler('c')
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

      dependencies += openssl_dep
      bl_dependencies += openssl_dep
    elif system == 'linux'
      cc = meson.get_compiler('c')

      openssl_lib = cc.find_library('crypto')
      openssl_dep = declare_dependency(
        dependencies : openssl_lib,
        include_directories : '/usr/include/'
      )

      dependencies += openssl_dep
      bl_dependencies += openssl_dep
    endif
  endif

  crypto_libs += {
    crypto_platform : library('crypto-' + crypto_platform,
      srcs + crypto_config['sources'],
      include_directories : ['inc', 'inc-private', 'src'],
      c_args : ['-DIMAGE_TYPE_APPLICATION=1'],
      dependencies : deps + dependencies,
    )
  }
  crypto_deps += {
    crypto_platform : declare_dependency(
      link_with : crypto_libs[crypto_platform],
      include_directories : ['inc'],
      dependencies : dependencies,
    )
  }

  crypto_bl_libs += {
    crypto_platform : library('crypto-bl-' + crypto_platform,
      srcs + crypto_config['sources'],
      include_directories : ['inc', 'inc-private', 'src'],
      c_args : ['-DIMAGE_TYPE_BOOTLOADER=1'],
      dependencies : deps + bl_dependencies,
    )
  }
  crypto_bl_deps += {
    crypto_platform : declare_dependency(
      link_with : crypto_bl_libs[crypto_platform],
      include_directories : ['inc'],
    )
  }

  if crypto_config['tests'].length() > 0
    crypto_test_libs += {
      crypto_platform : library('crypto-test-' + crypto_platform,
        crypto_config['tests'],
        include_directories : [
          include_directories('inc'),
          include_directories('inc-private'),
        ],
        dependencies : [assert_dep, helpers_dep, log_dep, mcu_dep, secutils_dep],
      )
    }
    crypto_test_deps += {
      crypto_platform : declare_dependency(
        link_with : crypto_test_libs[crypto_platform],
        include_directories : include_directories('inc'),
        dependencies : [crypto_deps[crypto_platform]],
      )
    }
  endif

  if crypto_config['cmd_sources'].length() > 0
    # Only include cmd_sources, not tests. If tests exist, depend on the test library.
    cmd_deps = [crypto_dep, deps, secutils_dep, shell_dep]
    if crypto_config['tests'].length() > 0
      cmd_deps += [crypto_test_deps[crypto_platform]]
    endif
    crypto_cmd_libs += {
      crypto_platform : library('crypto-cmd-' + crypto_platform,
        crypto_config['cmd_sources'],
        include_directories : ['inc', 'inc-private', 'src'],
        dependencies : cmd_deps,
      )
    }
    crypto_cmd_deps += {
      crypto_platform : declare_dependency(
        link_whole : crypto_cmd_libs[crypto_platform],
      )
    }
  endif
endforeach

crypto_test = executable('crypto-test-posix',
  'src/posix/crypto_test.c',
  include_directories : ['inc', 'src'],
  dependencies : [crypto_deps['posix'], deps, secutils_dep, test_deps],
)
test('crypto test', crypto_test)
