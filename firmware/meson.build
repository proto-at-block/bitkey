project('wallet', 'c', 'cpp',
  version : '1.0',
  default_options : [
    'c_std=c11',
    'cpp_std=c++11',
    'default_library=static',
    'b_staticpic=false',
    ],
  meson_version : '>=1.1.0',
)

if get_option('disable_printf')
  add_project_arguments('-DDISABLE_PRINTF=1', language: 'c')
  message('LOG macros will not call printf')
endif

if get_option('config_prod')
  add_project_arguments('-DCONFIG_PROD=1', language: 'c')
  message('Production config')
endif

fs = import('fs')

system = meson.get_external_property('system', build_machine.system())
embedded_build = meson.get_external_property('embedded_build', false)

message('Building for ' + system)

get_version = ['python', '-m', 'python.bitkey.fw_version']
fw_version = run_command(get_version, 'get-fw-version', check: true).stdout()
bl_version = run_command(get_version, 'get-bl-version', check: true).stdout()


message('Firmware version ' + fw_version)
message('Bootloader version ' + bl_version)

# Used by FreeRTOS and MCU hal, so must come early.
memfault_sdk_includes = include_directories(
  'hal/memfault',
  'third-party/memfault-firmware-sdk/components/include/',
  'third-party/memfault-firmware-sdk/ports/include/',
)

# Strip paths for reproducible builds
# https://reproducible-builds.org/docs/build-path/
buildtype = get_option('buildtype')
if buildtype == 'debug'
  add_project_arguments([ f'-ffile-prefix-map='+meson.project_source_root()+'/=/' ], language: 'c')
endif

# Needs to come early to provide printf_dep
subdir('third-party')
subdir('test')

test_defines = ['-DUNIT_TEST']
if system == 'darwin'
  criterion_dep = dependency('criterion')
  test_deps = [criterion_dep, criterion_test_utils_dep, fff_dep]
elif system == 'linux'
  # Criterion must first be installed with `inv install-test-deps`.
  cc = meson.get_compiler('c')
  base = meson.current_source_dir()
  criterion_lib = cc.find_library('criterion', dirs : [base/'third-party/criterion/lib'])
  criterion_dep = declare_dependency(
    dependencies : criterion_lib,
    include_directories : 'third-party/criterion/include'
  )
  test_deps = [criterion_dep, criterion_test_utils_dep, fff_dep]
else
  test_deps = []
endif

# The following includes must be declared
# before `mcu` since `mcu` depends on the following headers.
rtos_includes = include_directories('lib/rtos/inc')
config_includes = include_directories('config')
security_config_includes = include_directories('config/keys/inc')
perf_includes = include_directories('lib/perf')
mpu_regions_includes = include_directories('lib/mpu_regions/inc')

helpers_includes = include_directories('lib/helpers')
helpers_dep = declare_dependency(
  include_directories : helpers_includes
)

assert_includes = include_directories('lib/assert')
subdir('lib/assert')

subdir('fuzz')
subdir('mcu')
subdir('hal/clock')

subdir('lib/secutils')
subdir('hal/secure-engine')

# Order of subdir() matters, but libs may depend on drivers, and vice versa:
# so instead of simply subdir'ing the directories, we specify each target subdirectory
# here in order to intermix them.
subdir('lib/auth')
subdir('lib/shell')
subdir('hal/memfault/memfault-log')
subdir('lib/log')
subdir('hal/led')
subdir('lib/protobuf')
subdir('lib/rtos')
subdir('hal/sysevent')
subdir('lib/ringbuf')
subdir('lib/perf')
memfault_event_defs = include_directories('hal/memfault/defs')
subdir('lib/bitlog')
subdir('lib/filesystem')
subdir('lib/crypto')
subdir('lib/secure-channel')
subdir('lib/faultsim')
subdir('lib/canary')
subdir('lib/sleep')
subdir('lib/telemetry-storage')
subdir('lib/feature-flags')

subdir('hal/board-id')
subdir('hal/sysinfo')
subdir('hal/memfault')
subdir('hal/tamper')
subdir('lib/telemetry-translator')

subdir('lib/mempool')
subdir('lib/ipc')
subdir('lib/msgpack')
subdir('lib/kv')
subdir('lib/metadata')
subdir('lib/iso7816')
subdir('lib/t4t')
subdir('lib/wca')
subdir('lib/protobuf/helpers')
subdir('lib/bip32')
subdir('lib/wallet')
subdir('lib/bootloader')
subdir('lib/fwup')
subdir('lib/mpu_regions')
subdir('lib/indexfs')

subdir('lib/animation')
subdir('hal/serial')
subdir('hal/exti')
subdir('hal/nfc')
subdir('hal/power')
subdir('hal/biometrics')
subdir('lib/unlock')
subdir('lib/onboarding')

subdir('config')
subdir('app')
