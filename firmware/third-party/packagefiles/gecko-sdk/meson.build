src_dir = '../../gecko-sdk'

gecko_sdk_includes = include_directories(
  src_dir/'platform/CMSIS/Include/',
  src_dir/'platform/Device/SiliconLabs/EFR32MG24/Include/',
  src_dir/'platform/common/inc/',
  src_dir/'platform/emlib/inc/',
  src_dir/'platform/peripheral/inc/',
  src_dir/'platform/service/device_init/inc/',
  src_dir/'platform/service/sleeptimer/inc/',
  src_dir/'platform/bootloader/api/',
  src_dir/'platform/emdrv/common/inc',
  src_dir/'platform/emdrv/spidrv/inc',
  src_dir/'platform/emdrv/spidrv/config/',
  src_dir/'platform/emdrv/dmadrv/inc',
  src_dir/'platform/emdrv/dmadrv/config/',
)

gecko_sdk_bootloader_includes = include_directories(
  src_dir/'platform/bootloader/api/',
)

gecko_sdk_driver_srcs = files(
  src_dir/'platform/emdrv/spidrv/src/spidrv.c',
  src_dir/'platform/emlib/src/em_usart.c',
  src_dir/'platform/emlib/src/em_eusart.c',
  src_dir/'platform/emlib/src/em_ldma.c',
  src_dir/'platform/emlib/src/em_gpio.c',  # Have to include because spidrv has internal 'gpio' dependency
)

se_manager_dir = src_dir/'util/third_party/crypto/sl_component/se_manager/'
se_manager_includes = include_directories(
  se_manager_dir/'inc'
)
se_manager_sources = files(
  se_manager_dir/'src/sl_se_manager_util.c',
  se_manager_dir/'src/sl_se_manager_cipher.c',
  se_manager_dir/'src/sl_se_manager_key_handling.c',
  se_manager_dir/'src/sl_se_manager_key_derivation.c',
  se_manager_dir/'src/sl_se_manager_entropy.c',
)
