srcs = [
  'src/battery_rescale.c',
  'src/display_controller.c',
  'src/flows/scan.c',
  'src/flows/menu.c',
  'src/flows/money_movement.c',
  'src/flows/brightness.c',
  'src/flows/info.c',
  'src/flows/onboarding.c',
  'src/flows/fingerprint.c',
  'src/flows/fingerprint_menu.c',
  'src/flows/firmware_update.c',
  'src/flows/locked.c',
  'src/flows/privileged_action.c',
  'src/flows/confirmation.c',
  'src/flows/onboarding_complete.c',
  'src/flows/game.c',
  'src/flows/power_off.c',
]

mfg_srcs = srcs + [
  'src/flows/mfg.c',
]

# Private includes for building the library
private_includes = include_directories('inc', 'inc-private')

# Public includes for consumers of the library
public_includes = include_directories('inc')

deps = [
  assert_dep,
  auth_dep,
  confirmation_dep,
  langpack_dep,
  log_dep,
  protos_dep,
  uc_dep,
]

c_args = []
if embedded_build
  deps += [
    bio_dep,
    hal_nfc_deps['embedded'],
    ipc_dep,
    onboarding_dep,
    power_dep,
    ui_messaging_dep,
  ]
  c_args += ['-DEMBEDDED_BUILD']
else
  deps += [ui_events_dep]
endif

display_controller_lib = library('display_controller',
  srcs,
  include_directories : [private_includes],
  dependencies : deps,
  c_args : c_args,
)

display_controller_dep = declare_dependency(
  link_with : display_controller_lib,
  include_directories : [public_includes],
  dependencies : deps,
)

display_controller_mfgtest_lib = library('display_controller_mfgtest',
  mfg_srcs,
  include_directories : private_includes,
  dependencies : deps,
  c_args : c_args + ['-DMFGTEST=1'],
)

display_controller_mfgtest_dep = declare_dependency(
  link_with : display_controller_mfgtest_lib,
  include_directories : public_includes,
  dependencies : deps,
)

# Unit tests
battery_rescale_test = executable('battery-rescale-test',
  'src/battery_rescale_test.c',
  'src/battery_rescale.c',
  include_directories: [private_includes],
  dependencies: [test_deps, log_dep],
)
test('battery rescale test', battery_rescale_test)

display_controller_test = executable('display-controller-test',
  'src/display_controller_test.c',
  include_directories: [private_includes],
  dependencies: [display_controller_dep, test_deps],
)
test('display controller test', display_controller_test)
