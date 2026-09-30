srcs = files(
  'src/memfault_platform_port.c',
  'src/memfault_coredump_storage.c',
)
srcs += memfault_sdk_srcs

deps = [
  helpers_dep,
  log_dep,
  mcu_dep,
  memfault_log_dep,
  printf_dep,
  rtos_dep,
  sysinfo_dep,
  telemetry_storage_dep,
]

memfault_includes = [
  memfault_sdk_includes,
  include_directories('inc'),
  memfault_event_defs,
]

memfault_system_configs = {
  'efr32mg24': {
    'embedded'         : true,
    'dependencies'     : deps,
    'cmd_dependencies' : [shell_dep],
  },
  'stm32u5xx': {
    'embedded'         : true,
    'dependencies'     : deps,
    'cmd_dependencies' : [shell_dep],
  },
  'darwin': {
    'embedded'         : false,
    'dependencies'     : [],
    'cmd_dependencies' : [],
  },
  'linux': {
    'embedded'         : false,
    'dependencies'     : [],
    'cmd_dependencies' : [],
  },
}

# We define a header-only dependency that can be leveraged outside of the
# main application.
memfault_dep = declare_dependency(
  include_directories : memfault_includes
)

memfault_deps = {}
memfault_libs = {}
memfault_cmd_deps = {}
memfault_cmd_libs = {}

foreach memfault_system : memfault_system_configs.keys()
  memfault_config = memfault_system_configs[memfault_system]

  if memfault_config['embedded']
    memfault_libs += {
      memfault_system: library('memfault_lib_' + memfault_system,
        srcs + [
          memfault_system + '/memfault_platform_translate.c',
        ],
        include_directories : memfault_includes + include_directories('inc-private'),
        dependencies: memfault_config['dependencies'],
      ),
    }

    memfault_cmd_libs += {
      memfault_system: library('memfault_lib_cmd_' + memfault_system,
        'src/memfault_cmd.c',
        include_directories : memfault_includes,
        dependencies : memfault_config['dependencies'] + [shell_dep],
      ),
    }
  else
    lib_name = 'memfault_lib_' + memfault_system
    memfault_libs += {
      memfault_system : library(lib_name,
        'posix/memfault_platform_translate.c',
        include_directories : memfault_includes,
      ),
    }

    cmd_lib_name = 'memfault_lib_cmd_' + memfault_system
    memfault_cmd_libs += {
      memfault_system: library(cmd_lib_name,
        'posix/memfault_platform_translate.c',
        include_directories : memfault_includes,
      )
    }
  endif

  memfault_deps += {
    memfault_system : declare_dependency(
      link_with : memfault_libs[memfault_system],
      dependencies : memfault_config['dependencies'],
    ),
  }

  memfault_cmd_deps += {
    memfault_system : declare_dependency(
      link_whole : memfault_cmd_libs[memfault_system],
      dependencies : memfault_config['cmd_dependencies'] + [memfault_dep],
    ),
  }
endforeach
