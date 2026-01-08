src_dir = '../../libew'
libew_includes = include_directories(src_dir)

libew_lib = library(
  'ew',
  src_dir / 'ew.c',
  include_directories: libew_includes,
  dependencies: [libwally_dep],
)

libew_dep = declare_dependency(
  include_directories: libew_includes,
  link_whole: libew_lib,
)
