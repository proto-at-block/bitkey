psbt_srcs = [
  'src/psbt.c',
]

psbt_includes = [include_directories('inc')]

psbt_deps = [
  libew_dep,
  crypto_deps['efr32mg24'],
]

psbt_lib = library('psbt',
  psbt_srcs,
  include_directories : psbt_includes,
  dependencies : psbt_deps,
)

psbt_dep = declare_dependency(
  link_with : psbt_lib,
  include_directories : psbt_includes,
  dependencies : psbt_deps,
)
