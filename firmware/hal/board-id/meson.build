srcs = [
  'board_id.c'
]
board_id_includes = include_directories('.')

deps = [assert_dep, helpers_dep, mcu_dep]

board_id_lib = library('board-id',
  srcs,
  include_directories : board_id_includes,
  dependencies : deps,
)
board_id_dep = declare_dependency(
  link_with : board_id_lib,
  dependencies : deps,
  include_directories : board_id_includes
)
