cfg_w1a_proto0_lib = library('cfg-w1a-proto-0',
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

# Note: don't export transitive deps here so that the main application
# image has to explicitly pull in drivers (not all app images will want *all* drivers)
# necessarily.
cfg_w1a_proto0_dep = declare_dependency(
  link_with : cfg_w1a_proto0_lib,
)

cfg_w1a_proto0_args = [
  '-DCFG_PROTO=1',
]
