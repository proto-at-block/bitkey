nanopb_dir = '../../nanopb/'

srcs = [
  nanopb_dir/'pb_encode.c',
  nanopb_dir/'pb_decode.c',
  nanopb_dir/'pb_common.c',
]
nanopb_incs = include_directories(nanopb_dir)

nanopb_lib = library('nanopb',
  srcs,
  include_directories : nanopb_incs,
)
nanopb_dep = declare_dependency(
  link_with : nanopb_lib,
  include_directories : nanopb_incs,
)
