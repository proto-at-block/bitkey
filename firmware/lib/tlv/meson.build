tlv_lib = library('tlv', [
    'src/tlv.c',
  ],
  include_directories : ['inc', 'inc-private'],
)
tlv_dep = declare_dependency(
  link_with : tlv_lib,
  include_directories : ['inc'],
)

tlv_test = executable('tlv-test',
  'src/tlv_test.c',
  include_directories: ['inc', 'inc-private'],
  dependencies : [tlv_dep, test_deps],
)
test('tlv test', tlv_test)
