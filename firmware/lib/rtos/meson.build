# Embedded sources - FreeRTOS wrappers
embedded_srcs = [
  'src/rtos_thread.c',
  'src/rtos_mutex.c',
  'src/rtos_semaphore.c',
  'src/rtos_notification.c',
  'src/rtos_timer.c',
  'src/rtos_queue.c',
  'src/rtos_event_groups.c',
]

# POSIX sources - pthread implementations
posix_srcs = [
  'src/posix/rtos_posix.c',
]

embedded_deps = [
  assert_dep,
  freertos_dep,
  mcu_dep,
  sysview_generated_dep,
]

thread_dep = dependency('threads')
posix_deps = [
  assert_dep,
  thread_dep,
]

includes = [
  rtos_includes,
  printf_includes,
  assert_includes,
  helpers_includes,
  memfault_sdk_includes,
]

posix_includes = [
  'inc/posix',
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

  if rtos_config['embedded']
    rtos_srcs = embedded_srcs
    rtos_build_deps = embedded_deps
    rtos_build_includes = ['inc', 'src', '../sysview/inc', memfault_sdk_includes]
  else
    rtos_srcs = posix_srcs
    rtos_build_deps = posix_deps
    rtos_build_includes = ['inc', 'src', '../sysview/inc', memfault_sdk_includes] + posix_includes
  endif

  rtos_libs += {
    hardware: library('rtos_' + hardware,
      rtos_srcs,
      include_directories : rtos_build_includes,
      c_args : ['-DIMAGE_TYPE_APPLICATION=1'],
      dependencies : rtos_build_deps,
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
        include_directories : ['inc', 'src', '../sysview/inc', memfault_sdk_includes],
        c_args : ['-DIMAGE_TYPE_APPLICATION=1'],
        dependencies : [embedded_deps, shell_dep],
      )
    }

    rtos_cmd_deps += {
      hardware: declare_dependency(
        link_whole : rtos_cmd_libs[hardware],
        dependencies : [embedded_deps, shell_dep]
      )
    }

    rtos_deps += {
      hardware: declare_dependency(
        link_with : rtos_libs[hardware],
        dependencies : [embedded_deps, shell_dep]
      )
    }
  else
    rtos_deps += {
      hardware: declare_dependency(
        link_with : rtos_libs[hardware],
        dependencies : posix_deps
      )
    }
  endif
endforeach

# Header-only dependencies.
rtos_dep = declare_dependency(
  include_directories : includes,
  dependencies : embedded_deps,
)

rtos_cmd_dep = declare_dependency(
  include_directories : includes,
)

# Unit tests for the pthread-based POSIX shim (see src/posix/README.md).
if system == 'darwin' or system == 'linux'
  rtos_posix_test = executable('rtos-posix-test',
    'src/posix/rtos_posix_test.c',
    include_directories : ['inc', 'inc/posix', '../sysview/inc', helpers_includes, assert_includes],
    dependencies : [rtos_deps[system], test_deps],
    c_args : test_defines,
  )
  test('rtos posix', rtos_posix_test)
endif
