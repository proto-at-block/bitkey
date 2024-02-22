srcs = [
  'src/rtos_thread.c',
  'src/rtos_mutex.c',
  'src/rtos_semaphore.c',
  'src/rtos_notification.c',
  'src/rtos_timer.c',
  'src/rtos_queue.c',
  'src/rtos_event_groups.c',
]

deps = [
  mcu_efr32xg24_dep,
]
includes = [
  rtos_includes,
  freertos_includes,
  printf_includes,
  assert_includes,
  helpers_includes,
]


if system == 'efr32mg24'
rtos_lib = library('rtos',
  srcs,
  include_directories : ['inc', 'src'],
  dependencies : deps,
)

# We split out RTOS shell commands from the RTOS core so that we
# can link only the shell commands with whole-archive (which is needed so the
# initcall-style registrations don't get removed due to non-use during linking .o's).
# Otherwise, we'd run into multiple definition errors for libraries which link
# against librtos.
rtos_cmd_lib = library('rtos-cmd',
  'src/rtos_cmd.c',
  include_directories : ['inc', 'src'],
  dependencies : [deps, shell_dep]
)
# The main application image must depend on this.
rtos_cmd_dep = declare_dependency(
  link_whole : rtos_cmd_lib,
  dependencies : [deps, shell_dep]
)

rtos_dep = declare_dependency(
  link_with : rtos_lib,
  include_directories : includes,
  dependencies : deps,
)
endif

if not embedded_build
rtos_dep = declare_dependency(
  include_directories : includes,
)
rtos_cmd_dep = declare_dependency(
  include_directories : includes,
)
endif
