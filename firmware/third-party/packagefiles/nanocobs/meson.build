nanocobs_dir = '../../nanocobs/'

srcs = [
  nanocobs_dir/'cobs.c',
]
nanocobs_incs = include_directories(nanocobs_dir)

nanocobs_lib = library('nanocobs',
  srcs,
  include_directories : nanocobs_incs,
)

nanocobs_dep = declare_dependency(
  link_with : nanocobs_lib,
  include_directories : nanocobs_incs,
)
