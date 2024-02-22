src_dir = '../../FreeRTOS'
config_dir = '../../../config/'
mcu_dir = '../../../mcu/'
lib_rtos_dir = '../../../lib/rtos/'

freertos_srcs = files(
  src_dir/'event_groups.c',
  src_dir/'list.c',
  src_dir/'queue.c',
  src_dir/'stream_buffer.c',
  src_dir/'tasks.c',
  src_dir/'timers.c',
  src_dir/'portable/MemMang/heap_4.c',
  src_dir/'portable/Common/mpu_wrappers.c', # due to setting configUSE_MPU_WRAPPERS_V1 to 1
)

freertos_includes = [
  src_dir/'include/'
]

# POSIX
if system == 'darwin' or system == 'linux'
  freertos_srcs += [
    src_dir/'portable/ThirdParty/GCC/Posix/port.c',
    src_dir/'portable/ThirdParty/GCC/Posix/utils/wait_for_event.c',
  ]

  freertos_includes += [
    src_dir/'portable/ThirdParty/GCC/Posix/',
    src_dir/'portable/ThirdParty/GCC/Posix/utils/',
    config_dir,
  ]
  freertos_includes = include_directories(freertos_includes)

# EFR32MG24
elif system == 'efr32mg24'
  freertos_srcs += [
    #src_dir/'portable/GCC/ARM_CM33_NTZ/non_secure/port.c', # replaced by a local diff in lib_rtos_dir
    lib_rtos_dir/'src/ports/port.c',
    src_dir/'portable/GCC/ARM_CM33_NTZ/non_secure/portasm.c',
    # src_dir/'portable/GCC/ARM_CM33/secure/secure_context_port.c',
    # src_dir/'portable/GCC/ARM_CM33/secure/secure_context.c',
    # src_dir/'portable/GCC/ARM_CM33/secure/secure_init.c',
  ]

  _freertos_includes = [
    src_dir/'include/',
    src_dir/'portable/GCC/ARM_CM33_NTZ/non_secure/',
    # src_dir/'portable/GCC/ARM_CM33/secure/',
    config_dir,
    mcu_dir/'efr32xg24/inc/'
  ]
  _freertos_includes = include_directories(_freertos_includes)
  freertos_includes = [ _freertos_includes, printf_includes ]
endif

freertos = library('freertos',
  freertos_srcs,
  c_args : ['-Wno-unused-parameter', '-mcmse'],
  include_directories : [freertos_includes, memfault_sdk_includes],
)
freertos_dep = declare_dependency(
  link_with : freertos,
  include_directories : freertos_includes,
)
