w3_uxc_mpu_lib = library('w3_uxc_mpu',
  'mpu_init.c',
  include_directories : [mpu_regions_includes],
  dependencies : [mpu_regions_dep]
)

w3_uxc_mpu_dep = declare_dependency(
  link_with : w3_uxc_mpu_lib,
)

subdir('application')
subdir('loader')
