src_dir = '../../detools/c'

srcs = [
  src_dir/'detools.c',
  src_dir/'heatshrink/heatshrink_decoder.c',
]

detools_c_flags = [
  '-DDETOOLS_CONFIG_FILE_IO=0',
  '-DDETOOLS_CONFIG_COMPRESSION_NONE=0',
  '-DDETOOLS_CONFIG_COMPRESSION_LZMA=0',
  '-DDETOOLS_CONFIG_COMPRESSION_CRLE=0',
  '-DDETOOLS_CONFIG_COMPRESSION_HEATSHRINK=1',
]

includes = [
  src_dir,
  src_dir/'heatshrink',
]

detools_lib = library('detools',
  srcs,
  c_args : detools_c_flags,
  include_directories : includes,
)
detools_dep = declare_dependency(
  link_with : detools_lib,
  include_directories : includes,
)
