bootloader_platform_configs = {
  'app-cert' : {
    'includes' : [
      gecko_sdk_bootloader_includes
    ],
    'compile_args' : [
      '-DBL_SECUREBOOT_APP_CERT=1'
    ],
    'sources' : [
      'src/app_cert/bl_secureboot_verify.c',
    ],
    'dependencies' : [
    ],
  },
  'picocert' : {
    'includes' : [
    ],
    'compile_args' : [
      '-DBL_SECUREBOOT_PICOCERT=1'
    ],
    'sources' : [
      'src/picocert/bl_secureboot_verify.c'
    ],
    'dependencies' : [
      picocert_dep,
    ]
  }
}

srcs = [
  'src/bl_secureboot.c',
]

bl_deps = [crypto_dep, secutils_bl_dep, log_dep]
app_deps = [crypto_dep, secutils_dep, log_dep]

bootloader_bl_libs = {}
bootloader_bl_deps = {}
bootloader_app_libs = {}
bootloader_app_deps = {}

foreach bootloader_platform : bootloader_platform_configs.keys()
  bootloader_config = bootloader_platform_configs[bootloader_platform]

  incs = ['inc'] + bootloader_config['includes']

  bootloader_bl_libs += {
    bootloader_platform : library('bootloader-' + bootloader_platform,
      srcs + bootloader_config['sources'],
      c_args : bootloader_config['compile_args'],
      include_directories : incs + ['inc-private'],
      dependencies: bl_deps + bootloader_config['dependencies'],
    )
  }
  bootloader_bl_deps += {
    bootloader_platform : declare_dependency(
      link_with : bootloader_bl_libs[bootloader_platform],
      include_directories : incs,
      compile_args : bootloader_config['compile_args'],
      dependencies : bootloader_config['dependencies'],
    )
  }

  bootloader_app_libs += {
    bootloader_platform : library('bootloader-for-app-' + bootloader_platform,
      srcs + bootloader_config['sources'],
      c_args : bootloader_config['compile_args'],
      include_directories : incs + ['inc-private'],
      dependencies : app_deps + bootloader_config['dependencies'],
    )
  }
  bootloader_app_deps += {
    bootloader_platform : declare_dependency(
      link_with : bootloader_app_libs[bootloader_platform],
      include_directories : incs,
      compile_args : bootloader_config['compile_args'],
      dependencies : bootloader_config['dependencies'],
    )
  }
endforeach

bootloader_app_cert_test = executable('bootloader-app-cert-test',
  'bl_secureboot_test.c',
  include_directories : ['inc', 'inc-private', gecko_sdk_bootloader_includes],
  dependencies : [bootloader_bl_deps['app-cert'], crypto_deps['posix'], secutils_bl_dep, test_deps],
)
test('bootloader app-cert test', bootloader_app_cert_test)
