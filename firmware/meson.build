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

if get_option('log_tokenized')
  add_project_arguments('-DLOG_TOKENIZED=1', language: 'c')
  message('Tokenized UART logging enabled (compact log frames)')
endif

fs = import('fs')
py = import('python').find_installation('python3', required : true)

sysview_peripherals_desc = files('lib/sysview/Description/SYSVIEW_Peripherals.txt')
sysview_desc_script = files('lib/sysview/scripts/description_to_h.py')
sysview_peripherals_header = custom_target(
  'sysview-peripherals-header',
  input : sysview_peripherals_desc,
  output : 'SYSVIEW_Peripherals.h',
  command : [
    py,
    sysview_desc_script,
    '--runtime-module-offset',
    '-desc',
    '@INPUT@',
    '-header',
    '@OUTPUT@',
  ],
  depend_files : [sysview_desc_script],
  build_by_default : true,
)
sysview_generated_dep = declare_dependency(
  include_directories : [
    include_directories('.'),
    include_directories('lib/sysview/include'),
    include_directories('lib/sysview/SEGGER'),
  ],
  sources : [sysview_peripherals_header],
)

system = meson.get_external_property('system', build_machine.system())
embedded_build = meson.get_external_property('embedded_build', false)
platform = meson.get_external_property('platform', build_machine.system())

message('Building for ' + system)

if get_option('enable_sysview')
  if get_option('config_prod')
    # Defense-in-depth for direct `meson setup` use. The inv build wrapper
    # (python/bitkey/meson.py) already drops sysview from prod/mfgtest build
    # passes, so a normal `inv build ... --sysview` never reaches this.
    error('SystemView cannot be enabled for production builds (config_prod=true)')
  elif system != 'stm32u5xx'
    # SystemView is only wired up for stm32u5xx (UXC). Skip it here rather than
    # failing, so a multi-platform build (e.g. `inv build.platforms --sysview`)
    # still builds w1 / w3-core normally instead of aborting the whole run.
    warning('SystemView is only supported on stm32u5xx; skipping for ' + system)
  else
    add_project_arguments([
      '-DUSE_SYSVIEW=1',
      '-DUSE_RTT=1',
      '-DSYSVIEW_APP_NAME="wallet"',
      '-DSYSVIEW_DEVICE_NAME="STM32U585"',
      # Place the SEGGER RTT control block + buffers (and therefore the
      # SystemView event buffer) in SRAM4 (16 KiB at 0x28000000) so the buffer
      # lives at a fixed absolute address and does not eat into the main
      # 768 KiB SRAM partition. The linker script (stm32u5xx.jinja.ld) pins
      # .sram4.rtt_cb to the start of SRAM4, so _SEGGER_RTT is *guaranteed*
      # to live at 0x28000000 regardless of buffer/CB layout. The host
      # capture tool relies on this address.
      '-DSEGGER_RTT_SECTION=".sram4.rtt_cb"',
      '-DSEGGER_RTT_BUFFER_SECTION=".sram4.rtt_buf"',
      # Force the SystemView up-buffer into .sram4.rtt_buf as well.
      # ConfDefaults.h falls back to SEGGER_RTT_SECTION (not _BUFFER_SECTION),
      # which would otherwise pile the SystemView buffer into .sram4.rtt_cb
      # and shove _SEGGER_RTT past 0x28003000.
      # 10 KiB leaves room for the sysview resource/marker registries in SRAM4.
      '-DSEGGER_SYSVIEW_SECTION=".sram4.rtt_buf"',
      '-DSEGGER_SYSVIEW_RTT_BUFFER_SIZE=10240',
      '-DSEGGER_RTT_MAX_NUM_UP_BUFFERS=2',
      '-DSEGGER_RTT_MAX_NUM_DOWN_BUFFERS=2',
      '-DBUFFER_SIZE_UP=256',
      # Wallet has 8 application tasks + IDLE + Tmr Svc, plus headroom for
      # any future tasks. The default cap of 8 in SEGGER_SYSVIEW_FreeRTOS.h
      # silently drops the timer-service task and shows it as "Task 0xXXXX".
      '-DSYSVIEW_FREERTOS_MAX_NOF_TASKS=32',
    ], language: 'c')

    message('SEGGER SystemView tracing enabled')
  endif
endif

get_version = [py, '-m', 'python.bitkey.fw_version']
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
rtos_includes = include_directories('lib/rtos/inc', 'lib/sysview/inc')
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
subdir('lib/platform')
subdir('lib/secutils')
subdir('hal/secure-engine')
subdir('hal/memfault/memfault-log')
# hal/serial provides the UART transport used by the tokenized log path. It is
# pulled in before lib/log so log_uart_lib can depend on serial_dep (log_lib
# itself does not).
subdir('hal/serial')
subdir('lib/log')
subdir('hal/led')
subdir('lib/protobuf')
subdir('hal/sysevent')
subdir('lib/perf')
memfault_event_defs = include_directories('hal/memfault/defs')
subdir('lib/bitlog')
subdir('lib/bio-flash-storage')
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
subdir('hal/opt')
subdir('hal/tamper')
subdir('lib/telemetry-translator')

subdir('lib/mempool')
subdir('lib/msgpack')
subdir('lib/kv')
subdir('lib/metadata')
subdir('lib/iso7816')
subdir('lib/t4t')
subdir('lib/bip32')
subdir('lib/psbt')
subdir('lib/wallet')
subdir('lib/bootloader')
subdir('lib/fwup')
subdir('lib/indexfs')
subdir('lib/tlv')
subdir('lib/langpack')


subdir('lib/onboarding')
subdir('lib/ui_messaging')
# IPC (uses onboarding_dep, ui_events_dep)
subdir('lib/ipc')
subdir('lib/ui_messaging/lib')
subdir('lib/ui_messaging/cmd')

# Confirmation depends on ipc_dep
subdir('lib/confirmation')

subdir('lib/sap')

subdir('lib/protobuf/helpers')
subdir('lib/mpu_regions')
subdir('lib/wca')
subdir('lib/temperature')
subdir('lib/animation')
subdir('hal/exti')
subdir('hal/nfc')
subdir('lib/nfc-control')
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
