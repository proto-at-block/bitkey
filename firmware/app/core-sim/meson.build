# core-sim: POSIX firmware simulator with real crypto and wallet support
#
# This server reads length-prefixed WCA APDUs from stdin and writes
# responses to stdout. Routes proto commands to REAL firmware handlers.
#
# Only builds on POSIX platforms (darwin/linux).

if system != 'darwin' and system != 'linux'
  subdir_done()
endif

# OpenSSL dependency for sim_provisioning (certificate generation, ECDSA signing)
openssl_dep = dependency('openssl', required: true)

# Include paths for wallet_emulator.c (needs private headers for SEED_PATH, etc.)
wallet_private_includes = include_directories('../../lib/wallet/src')

# Include paths for display_controller_internal.h
display_controller_private_includes = include_directories('../../lib/display_controller/inc-private')

# Include paths for compiling unlock.c with simulator storage.
unlock_private_includes = include_directories('../../lib/unlock/inc', '../../lib/unlock/src')

# IPC library provides ports[], num_ipc_ports, and ipc_proto_route() via ipc_internal.c
# We include the directory for ipc_impl.h and message headers
ipc_includes = include_directories('../../lib/ipc/inc', '../../lib/ipc/src', '../../lib/ipc/generated')

# Core-sim links its own simulator WCA transport so lib/wca stays production-identical.
wca_includes = include_directories('../../lib/wca/inc', '../../lib/wca/src')

# Include paths for task implementation headers (for port files and core task files)
task_private_includes = include_directories(
  '../../app/tasks/key_manager/inc-private',
  '../../app/tasks/fwup/inc-private',
  '../../app/tasks/sysinfo/inc-private',
  '../../lib/protobuf/helpers'
)

# Include paths for task public headers (for task_create functions)
task_public_includes = include_directories(
  '../../app/tasks/key_manager/inc',
  '../../app/tasks/fwup/inc',
  '../../app/tasks/sysinfo/inc',
  '../../app/tasks/auth/inc',
)

# Workaround for clang's strict enforcement of C11 goto+cleanup semantics.
# key_manager_task.c and crypto_task.c use __attribute__((__cleanup__(...)))
# with goto statements that jump past the cleanup variable declarations.
# clang treats this as a hard error. We force-include a header that redefines
# __cleanup__ to __unused__ before any code uses it.
cleanup_workaround_header = meson.current_source_dir() / '../../lib/rtos/inc/posix/posix_cleanup_workaround.h'

# Common sources for core-sim (shared between W1 and W3 modes)
core_sim_common_srcs = [
  'src/main.c',
  'src/ipc_ports.c',
  'src/stdio_tasks.c',
  'src/auth_sim.c',
  'src/handler_device.c',
  'src/handler_emulator.c',
  'src/wallet_emulator.c',
  'src/uxc_socket_server.c',
  'src/uxc_transport.c',
  'src/device_state.c',
  'src/sim_persistence.c',
  # POSIX platform implementations (ports, stubs, wca)
  'src/posix/bio_sim.c',
  'src/posix/ui_execute_command.c',
  'src/posix/wca_sim.c',
  'src/posix/wca_glue.c',
  'src/posix/stubs.c',
  'src/posix/task_stubs.c',
  'src/posix/key_manager_task_port.c',
  'src/posix/fwup_addr.c',
  'src/posix/log.c',
  # MCU flash shim for indexfs (used by real unlock_storage)
  'src/posix/mcu_flash_shim.c',
  # Provisioning support for attestation testing (P-256 ECDSA, X.509 certs)
  'src/sim_provisioning.c',
  'src/sim_dev_certs.c',
  # Proto helpers for sysinfo_task_request_coproc_metadata
  '../../lib/protobuf/helpers/proto_helpers.c',
  # Real firmware task core implementations
  '../../app/tasks/key_manager/src/core/key_manager_task.c',
  '../../app/tasks/key_manager/src/core/crypto_task.c',
  # W1 hardware port for key_manager: implements the full port interface with
  # FEATURE_NOT_SUPPORTED replies for W3-only flows and delegates the shared
  # flows (derive_and_sign, unseal_csek, fingerprint reset) to core impls.
  # The simulator-specific crypto shims live in src/posix/key_manager_task_port.c.
  '../../app/tasks/key_manager/src/w1/key_manager_task_port.c',
  '../../app/tasks/sysinfo/src/core/sysinfo_task.c',
  '../../app/tasks/fwup/src/core/fwup_task.c',
  # Real W1 hardware ports for fwup and sysinfo (no coprocessor, like the
  # simulator). UXC-targeted coproc requests get W1 semantics: fwup asserts,
  # sysinfo replies ERROR. The platform headers they compile against
  # (power.h, metadata.h, ...) are the POSIX stubs in lib/platform/inc.
  '../../app/tasks/fwup/src/w1-core/fwup_task_port.c',
  '../../app/tasks/sysinfo/src/w1/sysinfo_task_port.c',
  '../../app/tasks/auth/src/auth_task.c',
  '../../lib/unlock/src/unlock.c',
]

# Common include directories for core-sim
core_sim_generated_includes = include_directories('.')

core_sim_common_includes = [
  # POSIX RTOS/platform headers from shared libraries (must come first)
  '../../lib/rtos/inc/posix',   # POSIX FreeRTOS types, rtos.h, rtos_thread.h, etc.
  '../../lib/platform/inc',     # Platform header stubs (power.h, bio.h, etc.)
  security_config_includes,
  gecko_sdk_bootloader_includes,
  wallet_private_includes,
  display_controller_private_includes,
  unlock_private_includes,
  ipc_includes,  # For ipc.h, ipc_impl.h, and generated headers
  wca_includes,  # For wca.h and the simulator-local wca_impl.h consumer
  task_private_includes,  # For task_*_impl.h and proto_helpers.h (for port files)
  task_public_includes,
  # Canonical headers reached through the lib/platform/inc passthroughs
  '../../hal/biometrics/inc',       # bio.h, bio_storage.h
  '../../third-party/fpc-bep/api/inc',  # FPC BEP sensor headers used by bio.h
  '../../hal/exti/inc',             # exti.h used by power.h
  memfault_sdk_includes,            # memfault/core/trace_reason_user.h via bitlog.h
  memfault_event_defs,              # trace reason defs via bitlog.h
  core_sim_generated_includes,  # For generated/stdio_auth_check.h
  'src',  # For wallet_emulator.h and stdio_defs.h
  'src/posix',  # For posix platform headers (wca.h, stubs.h)
]

# Common dependencies for core-sim
core_sim_common_deps = [
  crypto_deps['posix'],
  bip32_dep,
  fwup_deps['posix'],
  fff_dep,
  openssl_dep,  # For sim_provisioning (certificate generation, ECDSA signing)
  rtos_deps[system],  # POSIX RTOS (pthread-based) from lib/rtos
  bootloader_app_deps['app-cert'],
  # Additional dependencies for wallet_emulator.c (real wallet infrastructure)
  wallet_dep,
  helpers_dep,
  iso7816_dep,
  mempool_dep,
  fs_dep,
  libbase58_dep,
  lfs_testbd_dep,  # Provides lfs_emubd for RAM-backed filesystem
  # Nanopb protobuf library with generated headers
  protos_dep,
  # Real display_controller for high-fidelity UI testing
  display_controller_dep,
  # IPC library (provides ports[], num_ipc_ports, ipc_proto_route())
  ipc_dep,
  # Dependencies for real firmware tasks (key_manager, fwup, sysinfo)
  sleep_dep,  # Real sleep library (uses rtos_timer_t)
  sysevent_dep,
  proto_helpers_dep,
  onboarding_dep,
  policy_dep,
  grant_dep,
  auth_dep,
  # W1 variant: empty product cert table, so secure_channel_cert_init() is a
  # no-op (real W1 behavior — matches the W1 task ports above). Don't use
  # secure_channel_dep here: the foreach in lib/secure-channel/meson.build
  # shadows the global `platform`, so for native builds the convenience dep
  # silently resolves to w3-uxc, whose cert generation asserts at boot.
  secure_channel_deps['w1'],
  ui_messaging_dep,
  # Real unlock_storage.c (persists to wallet_fs via LittleFS). Compile
  # unlock.c directly above so POSIX unlock_dep does not pull fake storage.
  unlock_storage_dep,
  secutils_dep,
  indexfs_dep,
  mcu_dep,
  # Headless UI task for draining ui_port in tests
  ui_task_headless_dep,
  # Real confirmation manager (uses rtos_mutex, rtos_thread_systime)
  confirmation_dep,
]

# Common C arguments for core-sim
core_sim_common_c_args = [
  '-include', cleanup_workaround_header,  # Must come before any code that uses __cleanup__
  # Redirect active_slot to our POSIX-specific variable with the correct slot value
  '-Dactive_slot=posix_active_slot',
]

# W3 emulator (default - with confirmation protocol UI flow)
# Uses posix-w3 platform which returns CONFIRMATION_PENDING
core_sim_w3 = executable('core-sim-w3',
  core_sim_common_srcs,
  include_directories : core_sim_common_includes + [
    '../../config/platform/posix-w3',  # W3 confirmation policy (requires UI approval)
  ],
  c_args : core_sim_common_c_args + [
    '-include', meson.current_source_dir() / '../../config/platform/posix-w3/platform_hwrev.h',
  ],
  dependencies : core_sim_common_deps,
)

# W1 emulator (screenless - auto-confirms immediately)
# Uses posix-w1 platform which auto-approves and returns SUCCESS
core_sim_w1 = executable('core-sim-w1',
  core_sim_common_srcs,
  include_directories : core_sim_common_includes + [
    '../../config/platform/posix-w1',  # W1 confirmation policy (auto-approve)
  ],
  c_args : core_sim_common_c_args + [
    '-include', meson.current_source_dir() / '../../config/platform/posix-w1/platform_hwrev.h',
  ],
  dependencies : core_sim_common_deps,
)

# Alias for backwards compatibility - core_sim variable points to W3 version
core_sim = core_sim_w3

# Create 'core-sim' alias target for backwards compatibility with build scripts
alias_target('core-sim', core_sim_w3)

# Unit test for sim_provisioning format validation
test_sim_provisioning = executable(
  'test_sim_provisioning',
  [
    'test/test_sim_provisioning.c',
    'src/sim_provisioning.c',
    'src/sim_dev_certs.c',
    'src/sim_persistence.c',  # For persistence API used by sim_provisioning
  ],
  include_directories : [
    'src',  # For sim_provisioning.h, sim_dev_certs.h, sim_persistence.h
  ],
  dependencies : [
    openssl_dep,
  ],
)

test('sim_provisioning_format', test_sim_provisioning,
  env : ['CORE_SIM_PROVISION=1'],
)
