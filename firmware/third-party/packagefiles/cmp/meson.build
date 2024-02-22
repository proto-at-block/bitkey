src_dir = '../../cmp'

cmp_srcs = files(
  src_dir/'cmp.c',
)

cmp_includes = include_directories(src_dir)

cmp_lib = library('cmp',
  cmp_srcs,
  c_args : [],
  include_directories : cmp_includes,
)
cmp_dep = declare_dependency(
  link_with : cmp_lib,
  include_directories : cmp_includes,
)
