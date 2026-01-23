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

display_rotate_env = run_command('sh', '-c', 'echo $W3_ENV_DISPLAY_ROTATE_180', check: true).stdout().strip()
if display_rotate_env != ''
  add_project_arguments('-DDISPLAY_ROTATE_180=' + display_rotate_env, language: 'c')
  message('DISPLAY_ROTATE_180=' + display_rotate_env)
endif

fs = import('fs')

system = meson.get_external_property('system', build_machine.system())
embedded_build = meson.get_external_property('embedded_build', false)
platform = meson.get_external_property('platform', build_machine.system())

message('Building for ' + system)

get_version = ['python', '-m', 'python.bitkey.fw_version']
fw_version = run_command(get_version, 'get-fw-version', check: true).stdout()
bl_version = run_command(get_version, 'get-bl-version', check: true).stdout()


message('Firmware version ' + fw_version)
message('Bootloader version ' + bl_version)

# Used by FreeRTOS and MCU hal, so must come early.
memfault_sdk_includes = include_directories(
  'hal/memfault/inc/',
  'third-party/memfault-firmware-sdk/components/include/',
  'third-party/memfault-firmware-sdk/ports/include/',
)

# Strip paths for reproducible builds
# https://reproducible-builds.org/docs/build-path/
buildtype = get_option('buildtype')
if buildtype == 'debug'
  add_project_arguments([ f'-ffile-prefix-map='+meson.project_source_root()+'/=/' ], language: 'c')
endif

# Comes first since it should have no dependencies.
subdir('lib/wstd')

# We need to define this early to be able to use it in the libew build.
secp256k1_dir = 'third-party/secp256k1'
libew_secp256k1_include_dir = include_directories(secp256k1_dir / 'include')
# We need to build transitive dependencies of libew (libwally) before the rest of the project.
subdir('third-party/libew/third-party')

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
config_includes = include_directories(
  'config/rtos/' + platform,
  'config/platform/' + platform,
  'config',
)
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

subdir('lib/ringbuf')
subdir('mcu')
subdir('hal/clock')

# Order of subdir() matters, but libs may depend on drivers, and vice versa:
# so instead of simply subdir'ing the directories, we specify each target subdirectory
# here in order to intermix them.
subdir('lib/shell')
subdir('lib/rtos')
subdir('lib/secutils')
subdir('hal/secure-engine')
subdir('hal/memfault/memfault-log')
subdir('lib/log')
subdir('hal/led')
subdir('lib/protobuf')
subdir('hal/sysevent')
subdir('lib/perf')
memfault_event_defs = include_directories('hal/memfault/defs')
subdir('lib/bitlog')
subdir('lib/filesystem')
subdir('lib/crypto')
subdir('lib/secure-channel')
subdir('lib/faultsim')
subdir('lib/canary')
subdir('lib/sleep')
subdir('lib/auth')
subdir('lib/telemetry-storage')
subdir('lib/feature-flags')

subdir('hal/board-id')
subdir('hal/sysinfo')
subdir('hal/memfault')
subdir('hal/tamper')
subdir('lib/telemetry-translator')

subdir('lib/mempool')
subdir('lib/msgpack')
subdir('lib/kv')
subdir('lib/metadata')
subdir('lib/iso7816')
subdir('lib/t4t')
subdir('lib/bip32')
subdir('lib/wallet')
subdir('lib/psbt')
subdir('lib/bootloader')
subdir('lib/fwup')
subdir('lib/indexfs')
subdir('lib/tlv')
subdir('lib/langpack')


subdir('lib/onboarding')
# IPC (uses onboarding_dep)
subdir('lib/ipc')

subdir('lib/protobuf/helpers')
subdir('lib/mpu_regions')
subdir('lib/wca')
subdir('lib/ui_messaging')
subdir('lib/temperature')
subdir('lib/animation')
subdir('hal/serial')
subdir('hal/exti')
subdir('hal/nfc')
subdir('hal/power')
subdir('hal/biometrics')
subdir('lib/unlock')
subdir('lib/policy')
subdir('lib/grant_protocol')
subdir('hal/button')
subdir('hal/gfx')
subdir('hal/coproc')
subdir('hal/touch')

subdir('lib/uc')
subdir('lib/display')
subdir('lib/display_controller')

if fs.is_dir('ui-simulate')
  subdir('ui-simulate')
endif

subdir('config')
subdir('app')
