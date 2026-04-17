srcs = [
  'msgpack.c',
]

msgpack_lib = library('msgpack',
  srcs,
  include_directories : ['.'],
  dependencies : [assert_dep, cmp_dep]
)
msgpack_dep = declare_dependency(
  link_with : msgpack_lib,
  include_directories : ['.'],
)

msgpack_fuzz = executable('msgpack-fuzz',
  'msgpack_fuzz.cc',
  include_directories : ['.'],
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [msgpack_dep, cmp_dep, fuzzed_data_provider_dep],
)
