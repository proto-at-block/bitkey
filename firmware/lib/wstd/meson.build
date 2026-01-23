srcs = [
  'src/wstring.c',
]

includes = ['inc']

wstd_lib = library('wstd',
  srcs,
  include_directories : includes,
)

wstd_dep = declare_dependency(
  link_with : wstd_lib,
  include_directories : includes,
)
