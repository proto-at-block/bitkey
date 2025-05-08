srcs = [
  'bl_secureboot.c',
]

includes = ['.', gecko_sdk_bootloader_includes]
real_deps = [crypto_bl_dep, secutils_bl_dep, log_dep]

bootloader_lib = library('bootloader',
  srcs,
  include_directories : [includes],
  dependencies : real_deps,
)
bootloader_dep = declare_dependency(
  link_with : bootloader_lib,
  include_directories : [includes],
  dependencies : real_deps,
)

# Variant of the bootloader dependency for the app to pull in.
# Functionally the same.
for_app_deps = [crypto_dep, secutils_dep, log_dep]

bootloader_for_app_lib = library('bootloader-for-app',
  srcs,
  include_directories : [includes],
  dependencies : for_app_deps,
)
bootloader_for_app_dep = declare_dependency(
  link_with : bootloader_for_app_lib,
  include_directories : [includes],
  dependencies : for_app_deps,
)

bootloader_test = executable('bootloader-test',
  'bl_secureboot_test.c',
  include_directories : [includes],
  dependencies : [bootloader_dep, test_deps],
)
test('bootloader test', bootloader_test)
