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
