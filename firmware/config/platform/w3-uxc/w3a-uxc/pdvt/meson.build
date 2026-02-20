cfg_w3a_uxc_pdvt_lib = library('cfg-w3a-uxc-pdvt',
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

cfg_w3a_uxc_pdvt_dep = declare_dependency(
  link_with : cfg_w3a_uxc_pdvt_lib,
)

cfg_w3a_uxc_pdvt_args = [
  '-DCFG_PDVT=1',
]
