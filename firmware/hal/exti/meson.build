exti_includes = include_directories('inc')

# Base dependencies for all platforms
deps = [ mcu_dep, rtos_dep, perf_dep, log_dep]

# Platform-specific configurations
exti_configs = {
  'efr32mg24': {
    'srcs': [ 'efr32mg24/exti.c' ],
  },
  'stm32u5xx': {
    'srcs': [ 'stm32u5xx/exti.c' ],
  },
}

exti_libs = {}
exti_deps = {}

# Header-only dependency for generic access
exti_dep = declare_dependency(
  dependencies : deps,
  include_directories : exti_includes,
)

# Create platform-specific library variants
foreach exti_platform : exti_configs.keys()
  exti_config = exti_configs[exti_platform]

  exti_libs += {
    exti_platform : library('exti_' + exti_platform,
      exti_config['srcs'],
      include_directories : exti_includes,
      dependencies : deps,
    )
  }

  exti_deps += {
    exti_platform : declare_dependency(
      link_with : exti_libs[exti_platform],
      include_directories : exti_includes,
      dependencies : deps,
    )
  }
endforeach
