cfg_w3a_core_evt_lib = library('cfg-w3a-core-evt',
  'platform.c',
  include_directories : [
    exti_includes,
    mcu_common_includes,
    bio_platform_includes,
    board_id_includes,
    hal_nfc_includes,
    button_includes,
    power_includes,
    serial_includes,
    platform_includes,
    coproc_includes,
  ],
  dependencies : [
    mcu_deps[system],
  ]
)

cfg_w3a_core_evt_dep = declare_dependency(
  link_with : cfg_w3a_core_evt_lib,
)

cfg_w3a_core_evt_args = [
  '-DCFG_EVT=1',
]
