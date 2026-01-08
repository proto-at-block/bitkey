cfg_w3a_uxc_evt_lib = library('cfg-w3a-uxc-evt',
  'platform.c',
  include_directories : [
    platform_includes,
    display_includes,
    gfx_includes,
    serial_includes,
    touch_includes,
  ],
  dependencies : [
    mcu_deps[system],
  ]
)

cfg_w3a_uxc_evt_dep = declare_dependency(
  link_with : cfg_w3a_uxc_evt_lib,
)

cfg_w3a_uxc_evt_args = [
  '-DCFG_EVT=1',
]
