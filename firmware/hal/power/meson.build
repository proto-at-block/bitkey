srcs = [
  'src/power.c',
  'power_mpu.c',
  'src/battery.c',
  'src/max77734.c',
  'src/max17262.c',
  'src/battery_configs.c',
]
power_includes = [include_directories('inc'), mpu_regions_includes]

deps = [
  mcu_dep,
  log_dep,
  rtos_dep,
  assert_dep,
  exti_dep,
  fs_dep,
  bitlog_dep,
  sysevent_dep,
  ipc_dep,
  ui_messaging_dep,
]

power_lib = library('power',
  srcs,
  include_directories : [power_includes, 'inc_priv'],
  dependencies : deps,
)
power_dep = declare_dependency(
  link_with: power_lib,
  include_directories : power_includes,
  dependencies : deps,
)

cmd_srcs = [
  'src/charger_cmd.c',
  'src/fuel_cmd.c',
]

power_cmd_lib = library('power-cmd',
  cmd_srcs,
  include_directories : [power_includes, 'inc_priv'],
  dependencies : [power_dep, shell_dep],
)
power_cmd_dep = declare_dependency(
  link_whole : power_cmd_lib,
  dependencies : [power_dep, shell_dep],
)
