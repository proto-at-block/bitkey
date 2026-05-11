srcs = ['src/nfc_control.c']
incs = ['inc']
base_deps = [rtos_dep, log_dep, helpers_dep]

nfc_control_deps = {}

foreach variant : ['embedded', 'posix']
  nfc_control_lib = static_library('nfc-control-' + variant,
    srcs,
    include_directories : incs,
    dependencies : base_deps + [hal_nfc_deps[variant]],
  )
  nfc_control_deps += {
    variant : declare_dependency(
      link_with : nfc_control_lib,
      include_directories : incs,
      dependencies : base_deps + [hal_nfc_deps[variant]],
    )
  }
endforeach

nfc_control_test = executable('nfc-control-test',
  'src/nfc_control_test.c',
  'src/nfc_control.c',
  include_directories : [incs, '../../hal/nfc/inc'],
  dependencies : [rtos_dep, log_dep, helpers_dep, perf_stubs_dep, test_deps],
)
test('nfc-control test', nfc_control_test)
