w1_mpu_lib = library('w1_mpu',
  'mpu_init.c',
  include_directories : [mpu_regions_includes],
  dependencies : [mpu_regions_dep]
)

w1_mpu_dep = declare_dependency(
  link_with : w1_mpu_lib,
)

subdir('application')
subdir('loader')
