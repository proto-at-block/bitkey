cfg_w1a_evt_lib = library('cfg-w1a-evt',
  'platform.c',
  include_directories : [
    exti_includes,
    mcu_common_includes,
    bio_platform_includes,
    board_id_includes,
    hal_nfc_includes,
    led_includes,
    power_includes,
    serial_includes,
    platform_includes,
  ],
  dependencies : [
    mcu_deps[system],
  ]
)

cfg_w1a_evt_dep = declare_dependency(
  link_with : cfg_w1a_evt_lib,
)

cfg_w1a_evt_args = [
  '-DCFG_EVT=1',
]
