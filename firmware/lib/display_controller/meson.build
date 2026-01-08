srcs = [
  'src/display_controller.c',
  'src/flows/menu.c',
  'src/flows/money_movement.c',
  'src/flows/brightness.c',
  'src/flows/info.c',
  'src/flows/onboarding.c',
  'src/flows/fingerprint.c',
  'src/flows/fingerprint_menu.c',
  'src/flows/fingerprint_remove.c',
  'src/flows/firmware_update.c',
  'src/flows/mfg.c',
  'src/flows/lock_screen.c',
]

# Private includes for building the library
private_includes = include_directories('inc', 'inc-private')

# Public includes for consumers of the library
public_includes = include_directories('inc')

deps = [
  assert_dep,
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
  srcs,
  include_directories : private_includes,
  dependencies : deps,
  c_args : c_args + ['-DMFGTEST=1'],
)

display_controller_mfgtest_dep = declare_dependency(
  link_with : display_controller_mfgtest_lib,
  include_directories : public_includes,
  dependencies : deps,
)
