cfg_w1a_dev_board_rev1_lib = library('cfg-w1a-dev-board-rev-1',
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

# Note: don't export transitive deps here so that the main application
# image has to explicitly pull in drivers (not all app images will want *all* drivers)
# necessarily.
cfg_w1a_dev_board_rev1_dep = declare_dependency(
  link_with : cfg_w1a_dev_board_rev1_lib,
)

cfg_w1a_dev_board_rev1_args = []
