cfg_w1a_dvt_lib = library('cfg-w1a-dvt',
  'platform.c',
  include_directories : [
    exti_includes,
    mcu_efr32xg24_includes,
    bio_platform_includes,
    board_id_includes,
    hal_nfc_includes,
    led_includes,
    power_includes,
    serial_includes,
    platform_includes,
  ],
  dependencies : [
    mcu_efr32xg24_dep,
  ]
)

cfg_w1a_dvt_dep = declare_dependency(
  link_with : cfg_w1a_dvt_lib,
)

cfg_w1a_dvt_args = [
  '-DCFG_DVT=1',
]
