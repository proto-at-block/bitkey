base = '../../libbase58/'

libbase58_incs = include_directories(
  '../../libbase58/',
)
libbase58_srcs = [
  base/'base58.c',
]

libbase58_lib = library('libbase58',
  libbase58_srcs,
  include_directories : libbase58_incs,
)

libbase58_dep = declare_dependency(
  link_with : libbase58_lib,
  include_directories : libbase58_incs,
)
