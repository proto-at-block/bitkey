w3_core_mpu_lib = library('w3_core_mpu',
  'mpu_init.c',
  include_directories : [mpu_regions_includes],
  dependencies : [mpu_regions_dep]
)

w3_core_mpu_dep = declare_dependency(
  link_with : w3_core_mpu_lib,
)

subdir('application')
subdir('loader')
