srcs = [
  'display_driver.c',
]

includes = [
  '.',
  '../..',
]

deps = [
  display_ui_dep,
  gfx_dep,
  log_dep,
  lvgl_dep,
  touch_dep,
]

display_driver_lib = library('display_driver',
  srcs,
  include_directories : includes,
  dependencies : deps,
)

display_driver_dep = declare_dependency(
  link_with : display_driver_lib,
  include_directories : includes,
  dependencies : deps,
)

mfgtest_deps = [
  display_ui_mfgtest_dep,
  gfx_dep,
  log_dep,
  lvgl_dep,
  touch_dep,
]

display_driver_mfgtest_lib = library('display_driver_mfgtest',
  srcs,
  include_directories : includes,
  dependencies : mfgtest_deps,
)

display_driver_mfgtest_dep = declare_dependency(
  link_with : display_driver_mfgtest_lib,
  include_directories : includes,
  dependencies : mfgtest_deps,
)
