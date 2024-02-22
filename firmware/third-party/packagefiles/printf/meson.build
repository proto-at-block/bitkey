src_dir = '../../printf'

printf_srcs = files(
  src_dir/'printf.c',
)

printf_includes = include_directories(src_dir)

printf = library('printf',
  printf_srcs,
  c_args : [],
  include_directories : printf_includes,
)
printf_dep = declare_dependency(
  link_with : printf,
  include_directories : printf_includes,
)
