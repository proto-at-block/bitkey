src_dir = '../../FreeRTOS'
config_dir = '../../../config/'
mcu_dir = '../../../mcu/'
lib_rtos_dir = '../../../lib/rtos/'

# Common ARM CM33 configuration for embedded platforms
arm_cm33_config = {
  'embedded' : true,
  'sources' : [
    #src_dir/'portable/GCC/ARM_CM33_NTZ/non_secure/port.c', # replaced by a local diff in lib_rtos_dir
    lib_rtos_dir/'src/ports/port.c',
    src_dir/'portable/GCC/ARM_CM33_NTZ/non_secure/portasm.c',
    # src_dir/'portable/GCC/ARM_CM33/secure/secure_context_port.c',
    # src_dir/'portable/GCC/ARM_CM33/secure/secure_context.c',
    # src_dir/'portable/GCC/ARM_CM33/secure/secure_init.c',
  ],
  'includes' : [
    src_dir/'include/',
    src_dir/'portable/GCC/ARM_CM33_NTZ/non_secure/',
    # src_dir/'portable/GCC/ARM_CM33/secure/',
  ],
}

freertos_platform_configs = {
  'w1'      : arm_cm33_config + {'hardware' : 'efr32mg24'},
  'w3-core' : arm_cm33_config + {'hardware' : 'efr32mg24'},
  'w3-uxc'  : arm_cm33_config + {'hardware' : 'stm32u5xx'},
  'linux' : {
    'hardware' : 'linux',
    'embedded' : false,
    'sources' : [
      src_dir/'portable/ThirdParty/GCC/Posix/port.c',
      src_dir/'portable/ThirdParty/GCC/Posix/utils/wait_for_event.c',
    ],
    'includes' : [
      src_dir/'portable/ThirdParty/GCC/Posix/',
      src_dir/'portable/ThirdParty/GCC/Posix/utils/',
    ],
  },
  'darwin' : {
    'hardware' : 'darwin',
    'embedded' : false,
    'sources' : [
      src_dir/'portable/ThirdParty/GCC/Posix/port.c',
      src_dir/'portable/ThirdParty/GCC/Posix/utils/wait_for_event.c',
    ],
    'includes' : [
      src_dir/'portable/ThirdParty/GCC/Posix/',
      src_dir/'portable/ThirdParty/GCC/Posix/utils/',
    ],
  },
}

freertos_libs = {}
freertos_deps = {}

foreach platform_name : freertos_platform_configs.keys()
  platform_config = freertos_platform_configs[platform_name]
  lib_name = 'freertos_' + platform_name
  rtos_config_dir = '../../../config/rtos/' + platform_name

  _freertos_srcs = files(
    src_dir/'event_groups.c',
    src_dir/'list.c',
    src_dir/'queue.c',
    src_dir/'stream_buffer.c',
    src_dir/'tasks.c',
    src_dir/'timers.c',
    src_dir/'portable/MemMang/heap_4.c',
    src_dir/'portable/Common/mpu_wrappers.c', # due to setting configUSE_MPU_WRAPPERS_V1 to 1
  ) + platform_config['sources']

  _freertos_platform_includes = []
  if platform_config['embedded']
    # No standard-library available on embedded targets.
    _freertos_platform_includes += [
      printf_includes
    ]
  endif

  _freertos_includes = include_directories([
    src_dir/'include/',
    config_dir,
    rtos_config_dir,
    mcu_dir/'inc/'
  ] + platform_config['includes'])

  freertos_libs += {
    platform_name: library(lib_name,
      _freertos_srcs,
      c_args : ['-Wno-unused-parameter', '-mcmse'],
      include_directories : [_freertos_includes, _freertos_platform_includes, memfault_sdk_includes],
    )
  }

  freertos_deps += {
    platform_name: declare_dependency(
      link_with : freertos_libs[platform_name],
      include_directories : [_freertos_includes, _freertos_platform_includes],
    )
  }

  # If the current system matches the target hardware, then we define a header
  # only dependency to allow for targets to depend on without linking.
  if system == platform_config['hardware']
    freertos_dep = declare_dependency(
      include_directories : [_freertos_includes, _freertos_platform_includes],
    )
  endif
endforeach
