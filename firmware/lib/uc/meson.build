deps = [assert_dep, bitlog_dep, helpers_dep, log_dep, mempool_dep, nanocobs_dep, protos_dep, rtos_dep]

srcs = ['src/uc.c', 'src/uc_route.c']

# Header-only dependency.
uc_dep = declare_dependency(
  include_directories : ['inc'],
  dependencies : deps,
)

uc_host_lib = library('uc-host',
  srcs,
  c_args : ['-DUC_HOST_MODE=1'],
  include_directories : ['inc', 'inc-private'],
  dependencies : deps,
)
uc_host_dep = declare_dependency(
  link_with : uc_host_lib,
  include_directories : ['inc'],
  dependencies : deps,
)

uc_device_lib = library('uc-device',
  srcs,
  c_args : ['-DUC_HOST_MODE=0'],
  include_directories : ['inc', 'inc-private'],
  dependencies : deps,
)
uc_device_dep = declare_dependency(
  link_with : uc_device_lib,
  include_directories : ['inc'],
  dependencies : deps,
)

uc_host_test = executable('uc-host-test',
  'src/uc_test.c',
  c_args : ['-DUC_HOST_MODE=1'],
  include_directories : ['inc', 'inc-private'],
  dependencies : [fff_dep, test_deps, uc_device_dep],
)
test('uc host test', uc_host_test)

uc_device_test = executable('uc-device-test',
  'src/uc_test.c',
  c_args : ['-DUC_HOST_MODE=0'],
  include_directories : ['inc', 'inc-private'],
  dependencies : [fff_dep, test_deps, uc_device_dep],
)
test('uc device test', uc_device_test)
