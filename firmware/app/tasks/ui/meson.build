common_srcs = [
  'src/ui_task.c',
]

common_deps = [
  assert_dep,
  ipc_dep,
  log_dep,
  rtos_dep,
  secutils_dep,
  sysevent_dep,
  ui_messaging_dep,
]

includes = ['inc', mpu_regions_includes, helpers_includes]

# LED backend variant
ui_task_led_lib = library('ui_task_led',
  common_srcs + ['src/backends/ui_backend_led.c', 'src/ui_task_led_mpu.c'],
  include_directories : includes,
  dependencies : common_deps + [led_dep, animation_dep, power_dep, helpers_dep],
  c_args : ['-DUI_BACKEND_LED'],
)
ui_task_led_dep = declare_dependency(
  link_with : ui_task_led_lib,
  include_directories : includes,
  dependencies : common_deps + [led_dep, animation_dep, power_dep, helpers_dep],
)

# LED backend variant for MFGTEST builds
ui_task_led_mfgtest_lib = library('ui_task_led_mfgtest',
  common_srcs + ['src/backends/ui_backend_led.c', 'src/ui_task_led_mpu.c'],
  include_directories : includes,
  dependencies : common_deps + [led_dep, animation_dep, power_dep, helpers_dep],
  c_args : ['-DUI_BACKEND_LED', '-DMFGTEST=1'],
)
ui_task_led_mfgtest_dep = declare_dependency(
  link_with : ui_task_led_mfgtest_lib,
  include_directories : includes,
  dependencies : common_deps + [led_dep, animation_dep, power_dep, helpers_dep],
)

# Display backend variant
ui_task_display_lib = library('ui_task_display',
  common_srcs + ['src/backends/ui_backend_display.c', 'src/ui_task_display_mpu.c'],
  include_directories : includes,
  dependencies : common_deps + [display_controller_dep, button_dep, coproc_dep, uc_host_dep],
  c_args : ['-DUI_BACKEND_DISPLAY'],
)
ui_task_display_dep = declare_dependency(
  link_with : ui_task_display_lib,
  include_directories : includes,
  dependencies : common_deps + [display_controller_dep, button_dep, uc_host_dep],
)

# Display backend variant for MFGTEST builds
ui_task_display_mfgtest_lib = library('ui_task_display_mfgtest',
  common_srcs + ['src/backends/ui_backend_display.c', 'src/ui_task_display_mpu.c'],
  include_directories : includes,
  dependencies : common_deps + [display_controller_mfgtest_dep, button_dep, coproc_dep, uc_host_dep],
  c_args : ['-DUI_BACKEND_DISPLAY', '-DMFGTEST=1'],
)
ui_task_display_mfgtest_dep = declare_dependency(
  link_with : ui_task_display_mfgtest_lib,
  include_directories : includes,
  dependencies : common_deps + [display_controller_mfgtest_dep, button_dep, uc_host_dep],
)

# Headless backend variant (for core-sim testing)
# POSIX rtos headers must come first to override embedded rtos_mpu.h
posix_rtos_includes = include_directories('../../../lib/rtos/inc/posix')
headless_includes = [posix_rtos_includes] + includes

ui_task_headless_lib = library('ui_task_headless',
  common_srcs + ['src/backends/ui_backend_headless.c'],
  include_directories : headless_includes,
  dependencies : common_deps + [display_controller_dep],
  c_args : ['-DUI_BACKEND_HEADLESS'],
)
ui_task_headless_dep = declare_dependency(
  link_with : ui_task_headless_lib,
  include_directories : headless_includes,
  dependencies : common_deps + [display_controller_dep],
)
