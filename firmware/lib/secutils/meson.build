srcs = [
  'src/dudero.c',
  'src/secutils.c',
  'src/secutils_memzero.c',
]

# only add RTOS hooks for embedded app
if embedded_build
  app_srcs = ['port-application/secure_glitch_random_delay.c']
  app_deps = [assert_dep, helpers_dep, mcu_dep, rtos_dep]
else
  app_srcs = ['port-bootloader/secure_glitch_random_delay.c']
  app_deps = [assert_dep, helpers_dep]
endif

bl_srcs = ['port-bootloader/secure_glitch_random_delay.c']
bl_deps = [assert_dep, helpers_dep]

includes = ['inc']
private_includes = ['src']

secutils_lib = library('secutils',
  srcs + app_srcs,
  include_directories : [includes, private_includes],
  dependencies : app_deps,
)
secutils_dep = declare_dependency(
  link_with : secutils_lib,
  include_directories : includes,
  dependencies : app_deps,
)

secutils_bl_lib = library('secutils-bl',
  srcs + bl_srcs,
  include_directories : [includes, private_includes],
  dependencies : bl_deps,
)
secutils_bl_dep = declare_dependency(
  link_with : secutils_bl_lib,
  include_directories : includes,
  dependencies : bl_deps,
)
secutils_bl_dep = declare_dependency(
  link_with : secutils_bl_lib,
  include_directories : includes,
  dependencies : bl_deps,
)
