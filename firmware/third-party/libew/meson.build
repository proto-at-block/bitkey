project(
  'embedded-wallet',
  'c',
  version: '0.1.0',
  default_options: [
    'warning_level=2',
    'c_std=gnu17',
    'default_library=static',
    'werror=true',
    'b_staticpic=true',
    'b_pie=true',
    'b_lto=true',
  ],
)

# Define libew_secp256k1_include_dir before subdir('third-party') so it's available
# when third-party/meson.build tries to use it
libwally_base_for_secp256k1 = 'third-party/libwally/'
libew_secp256k1_include_dir = include_directories(libwally_base_for_secp256k1 + 'src/secp256k1/include')

subdir('third-party')

libew_includes = include_directories('.')

libew_lib = library(
  'ew',
  'ew.c',
  include_directories: libew_includes,
  dependencies: [libwally_dep],
)

libew_dep = declare_dependency(
  include_directories: libew_includes,
  link_with: libew_lib,
)

# For libew_demo and libew_test, we need to include secp256k1 sources since they use secp256k1
# directly.
# Note: precompute_ecmult.c is excluded as it's a standalone tool with its own main() function
libew_test_secp256k1_srcs = [
  libwally_base_for_secp256k1 / 'src/secp256k1/src/secp256k1.c',
  libwally_base_for_secp256k1 / 'src/secp256k1/src/precomputed_ecmult_gen.c',
  libwally_base_for_secp256k1 / 'src/secp256k1/src/precomputed_ecmult.c',
]

libew_demo = executable(
  'ew-demo',
  'ew-demo.c',
  libew_test_secp256k1_srcs,
  include_directories: [libew_includes, libew_secp256k1_include_dir],
  c_args: [
    '-Wno-unused-function',
    '-DENABLE_MODULE_ECDH=0',
    '-DENABLE_MODULE_RECOVERY=0',
    '-DENABLE_MODULE_EXTRAKEYS=1',
    '-DENABLE_MODULE_SCHNORRSIG=1',
  ],
  dependencies: [libew_dep, libwally_dep],
)

# Test executable (simple test harness)
libew_test = executable(
  'ew-test',
  'ew_test.c',
  libew_test_secp256k1_srcs,
  include_directories: [libew_includes, libew_secp256k1_include_dir],
  c_args: [
    '-Wno-unused-function',
    '-DENABLE_MODULE_EXTRAKEYS=1',
  ],
  dependencies: [libew_dep, libwally_dep],
)
test('ew test', libew_test)
