srcs = [
  'src/power.c',
  'src/power_charger_ui.c',
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

max17262_test = executable('max17262-test',
  [
    'src/max17262_test.c',
    'src/max17262.c',
    'src/battery_configs.c',
  ],
  include_directories : [
    power_includes,
    'inc_priv',
    assert_includes,
    helpers_includes,
    perf_includes,
    memfault_event_defs,
    shell_includes,
    config_includes,
    include_directories('../../hal/sysevent'),
    include_directories('../..', '../../lib/log/inc', '../../lib/bitlog/inc', '../../lib/filesystem',
                        '../../third-party/littlefs'),
  ],
  dependencies : [
    test_deps,
    mcu_dep,
    rtos_dep,
  ],
  c_args : test_defines + [
    '-Dfuel_gauge_validate_err=1',
    '-Dfuel_gauge_por_init_err=2',
    '-Dfuel_gauge_reg_read_err=3',
    '-Dfuel_gauge_reg_write_err=4',
  ],
)
test('max17262 test', max17262_test)

power_charger_ui_test = executable('power-charger-ui-test',
  [
    'src/power_charger_ui_test.c',
    'src/power_charger_ui.c',
  ],
  include_directories : [power_includes, 'inc_priv'],
  dependencies : [
    test_deps,
    mcu_dep,
    exti_dep,
  ],
)
test('power charger ui test', power_charger_ui_test)
