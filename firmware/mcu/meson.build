mcu_common_includes = include_directories('inc')

subdir('efr32xg24')
subdir('posix')
subdir('stm32u5xx')

# We define a map of the different MCU system configs in order to build up a
# dictionary that can be leveraged by the applications to access the system
# specific MCU dependency to link against.
mcu_system_configs = {
  'efr32mg24': {
    'libraries'       : mcu_efr32xg24_lib,
    'dependencies'    : mcu_efr32xg24_dep,
    'bl_libraries'    : mcu_efr32xg24_bl_lib,
    'bl_dependencies' : mcu_efr32xg24_bl_dep,
    'includes'        : mcu_efr32xg24_includes,
  },
  'stm32u5xx' : {
    'libraries'       : mcu_stm32u5xx_lib,
    'dependencies'    : mcu_stm32u5xx_dep,
    'bl_libraries'    : mcu_stm32u5xx_bl_lib,
    'bl_dependencies' : mcu_stm32u5xx_bl_dep,
    'includes'        : mcu_stm32u5xx_includes,
  },
  'darwin': {
    'libraries'       : mcu_posix_lib,
    'dependencies'    : mcu_posix_dep,
    'bl_libraries'    : mcu_posix_bl_lib,
    'bl_dependencies' : mcu_posix_bl_dep,
    'includes'        : mcu_posix_includes,
  },
  'linux': {
    'libraries'       : mcu_posix_lib,
    'dependencies'    : mcu_posix_dep,
    'bl_libraries'    : mcu_posix_bl_lib,
    'bl_dependencies' : mcu_posix_bl_dep,
    'includes'        : mcu_posix_includes,
  },
}

mcu_bl_libs = {}
mcu_libs = {}
mcu_bl_deps = {}
mcu_deps = {}
mcu_includes = {}

foreach mcu_system : mcu_system_configs.keys()
  mcu_config = mcu_system_configs[mcu_system]

  mcu_includes += {
    mcu_system: mcu_config['includes'],
  }

  mcu_libs += {
    mcu_system: mcu_config['libraries'],
  }

  mcu_deps += {
    mcu_system: mcu_config['dependencies'],
  }

  mcu_bl_libs += {
    mcu_system: mcu_config['bl_libraries'],
  }

  mcu_bl_deps += {
    mcu_system: mcu_config['bl_dependencies'],
  }
endforeach

# We define a header-only dependency that can be leveraged by the HAL.
# Applications will link against their target-specific dependency.
mcu_dep = declare_dependency(
  include_directories : mcu_includes[system],
)
