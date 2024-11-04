srcs = [
  'bl_secureboot.c',
]

includes = ['.', gecko_sdk_bootloader_includes]
deps = [crypto_bl_dep, secutils_bl_dep, log_dep]

bootloader_lib = library('bootloader',
  srcs,
  include_directories : [includes],
  dependencies : deps,
)
bootloader_dep = declare_dependency(
  link_with : bootloader_lib,
  include_directories : [includes],
  dependencies : deps,
)

bootloader_test = executable('bootloader-test',
  'bl_secureboot_test.c',
  include_directories : [includes],
  dependencies : [bootloader_dep, test_deps],
)
test('bootloader test', bootloader_test)
