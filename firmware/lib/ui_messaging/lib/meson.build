# Compiled helpers for ui_messaging (processed after ipc_dep is available)
ui_messaging_lib = static_library('ui-messaging',
  '../src/ui_messaging.c',
  include_directories : ['../inc'],
  dependencies : [ipc_dep, protos_dep],
)

ui_messaging_lib_dep = declare_dependency(
  link_whole : ui_messaging_lib,
  include_directories : ['../inc'],
)
