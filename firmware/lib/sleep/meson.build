deps = [helpers_dep, log_dep, rtos_dep]
includes = ['.']

sleep_lib = library('sleep',
  'sleep.c',
  include_directories : includes,
  dependencies : deps,
)

sleep_dep = declare_dependency(
  link_with : sleep_lib,
  include_directories : includes,
  dependencies : deps,
)

sleep_test = executable('sleep-test',
  'sleep_test.c',
  include_directories : includes,
  dependencies : [sleep_dep, test_deps],
)
test('sleep test', sleep_test)
