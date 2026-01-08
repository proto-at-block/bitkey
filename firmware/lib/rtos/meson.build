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
  assert_dep,
  freertos_dep,
  mcu_dep,
]

includes = [
  rtos_includes,
  printf_includes,
  assert_includes,
  helpers_includes,
  memfault_sdk_includes,
]

rtos_hardware_configs = {
  'efr32mg24': {
    'embedded': true,
  },
  'stm32u5xx': {
    'embedded': true,
  },
  'linux': {
    'embedded': false,
  },
  'darwin': {
    'embedded': false,
  }
}

rtos_cmd_libs = {}
rtos_libs = {}
rtos_cmd_deps = {}
rtos_deps = {}

foreach hardware : rtos_hardware_configs.keys()
  rtos_config = rtos_hardware_configs[hardware]

  rtos_libs += {
    hardware: library('rtos_' + hardware,
      srcs,
      include_directories : ['inc', 'src', memfault_sdk_includes],
      dependencies : deps,
    ),
  }

  if rtos_config['embedded']
    # We split out RTOS shell commands from the RTOS core so that we
    # can link only the shell commands with whole-archive (which is needed so the
    # initcall-style registrations don't get removed due to non-use during linking .o's).
    # Otherwise, we'd run into multiple definition errors for libraries which link
    # against librtos.
    rtos_cmd_libs += {
      hardware: library('rtos_cmd_' + hardware,
        'src/rtos_cmd.c',
        include_directories : ['inc', 'src', memfault_sdk_includes],
        dependencies : [deps, shell_dep],
      )
    }

    rtos_cmd_deps += {
      hardware: declare_dependency(
        link_whole : rtos_cmd_libs[hardware],
        dependencies : [deps, shell_dep]
      )
    }

    rtos_deps += {
      hardware: declare_dependency(
        link_with : rtos_libs[hardware],
        dependencies : [deps, shell_dep]
      )
    }
  else
    rtos_deps += {
      hardware: declare_dependency(
        link_with : rtos_libs[hardware],
        dependencies : [deps, shell_dep]
      )
    }
  endif
endforeach

# Header-only dependencies.
rtos_dep = declare_dependency(
  include_directories : includes,
  dependencies : deps,
)

rtos_cmd_dep = declare_dependency(
  include_directories : includes,
)
