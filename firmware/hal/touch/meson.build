touch_includes = include_directories('inc')

touch_srcs = [
  'src/touch.c',
  'src/touch_fwup.c',
]

touch_lib = library('touch', touch_srcs,
  include_directories : ['inc', 'inc-private', 'src'],
  dependencies : [exti_dep, helpers_dep, log_dep, mcu_dep, rtos_dep],
)

touch_dep = declare_dependency(
  link_with : touch_lib,
  include_directories : ['inc'],
  dependencies : [mcu_dep],
)

# MFGTEST variant — includes mfgtest I2C wrappers and compiles with MFGTEST define
touch_mfgtest_lib = library('touch-mfgtest',
  touch_srcs + ['src/touch_mfgtest.c'],
  include_directories : ['inc', 'inc-private', 'src'],
  dependencies : [exti_dep, helpers_dep, log_dep, mcu_dep, rtos_dep],
  c_args : ['-DMFGTEST=1'],
)

touch_mfgtest_dep = declare_dependency(
  link_with : touch_mfgtest_lib,
  include_directories : ['inc'],
  dependencies : [mcu_dep],
)

# BCW-40: FT3169 touch-data decode fuzzer.
# Reproduces _touch_decode_data() switch pattern with heap-allocated exact-size
# buffers.  When num_points == 0 the allocation holds only the 2-byte header;
# accessing touch[0] in the switch is a heap-buffer-overflow caught by ASAN.
touch_decode_fuzz = executable('touch-decode-fuzz',
  'src/touch_decode_fuzz.cc',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  include_directories : ['inc-private'],
  dependencies : [helpers_dep, fuzzed_data_provider_dep],
)
