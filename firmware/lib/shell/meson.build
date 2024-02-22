srcs = [
  'src/shell.c',
  'src/shell_cmd.c',
  'src/shell_help.c',
  'src/shell_argparse.c',
]
private_includes = ['src']

shell_includes = include_directories('inc')

includes = [private_includes, shell_includes]

shell_lib = library('shell',
  srcs,
  include_directories : includes,
  dependencies : [printf_dep, uthash_dep, helpers_dep]
)
shell_dep = declare_dependency(
  link_with : shell_lib,
  include_directories : ['inc'],
)
