srcs = ['tamper.c', 'tamper_mpu.c']
incs = ['.', mpu_regions_includes]
deps = [mcu_dep, secure_engine_dep, bitlog_dep, log_dep, memfault_dep, secutils_dep]

tamper_lib = library('tamper',
  srcs,
  include_directories : incs,
  dependencies : deps,
)
tamper_dep = declare_dependency(
  link_with : tamper_lib,
  include_directories : ['.'],
  dependencies : deps,
)
