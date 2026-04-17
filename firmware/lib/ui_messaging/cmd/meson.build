# Debug command for testing UI events
ui_cmd_lib = library('ui-cmd',
  '../src/ui_cmd.c',
  include_directories : ['../inc'],
  dependencies : [ui_messaging_dep, ipc_dep, shell_dep],
)

ui_cmd_dep = declare_dependency(
  link_whole : ui_cmd_lib,
  dependencies : [ui_messaging_dep, ipc_dep, shell_dep],
)
