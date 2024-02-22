deps = [helpers_dep, iso7816_dep, log_dep, fff_dep]
includes = ['inc']

t4t_lib = library('t4t',
  # We don't have a use for NDEF, so we use a stub t4t
  # implementation. We still have to respond to NDEF APDUs
  # because iOS will send them when background tag reading.
  'src/t4t_stub.c',
  include_directories : includes,
  dependencies : deps,
)
t4t_dep = declare_dependency(
  link_with : t4t_lib,
  include_directories : includes,
  dependencies : deps,
)

# This code is unused except for being fuzzed, but maybe
# we'll use it someday.
#
# IMPORTANT: It has security issues, so those need to be
# fixed before using.
t4t_real_lib = library('t4t-real',
  'src/t4t.c',
  include_directories : includes,
  dependencies : deps,
)
t4t_real_dep = declare_dependency(
  link_with : t4t_real_lib,
  include_directories : includes,
  dependencies : deps,
)

t4t_fuzz = executable('t4t-fuzz',
  'src/t4t_fuzz.cc',
  'src/t4t_impl.h',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [t4t_real_dep, fuzzed_data_provider_dep],
)

t4t_test = executable('t4t-test',
  'src/t4t_test.c',
  include_directories : [includes, 'src'],
  dependencies : [t4t_real_dep, test_deps],
)
test('t4t test', t4t_test)
