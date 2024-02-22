src_dir = '../../littlefs'

# Note: We don't declare a littlefs dependency here because
# we provide custom config that uses our LOG macros. Since we build
# third party dependencies first in our top-level meson, these sources
# are pulled into lib/filesystem.

lfs_srcs = files(
  src_dir/'lfs.c',
  src_dir/'lfs_util.c',
)

lfs_includes = include_directories(src_dir)

lfs_testbd_lib = library('testbd',
  src_dir/'bd/lfs_emubd.c',
  src_dir/'bd/lfs_filebd.c',
  src_dir/'bd/lfs_rambd.c',
  include_directories : lfs_includes,
)
lfs_testbd_dep = declare_dependency(
  link_with : lfs_testbd_lib,
  include_directories : lfs_includes,
)
