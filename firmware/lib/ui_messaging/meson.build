ui_messaging_dep = declare_dependency(
  include_directories : ['inc'],
  dependencies: [
    ipc_dep
  ]
)

# Header-only dependency for ui_events.h (enums/types only, no IPC)
ui_events_dep = declare_dependency(
  include_directories : ['inc'],
)

# Debug command for testing UI events
ui_cmd_lib = library('ui-cmd',
  'src/ui_cmd.c',
  include_directories : ['inc'],
  dependencies : [ui_messaging_dep, shell_dep],
)

ui_cmd_dep = declare_dependency(
  link_whole : ui_cmd_lib,
  dependencies : [ui_messaging_dep, shell_dep],
)
