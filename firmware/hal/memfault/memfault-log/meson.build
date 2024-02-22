memfault_log_lib = library('memfault-log',
  memfault_log_sdk_srcs,
  include_directories : memfault_sdk_includes,
  dependencies : [printf_dep],
)

memfault_log_dep = declare_dependency(
  link_with : memfault_log_lib,
  include_directories : memfault_sdk_includes,
  dependencies : [printf_dep],
)
