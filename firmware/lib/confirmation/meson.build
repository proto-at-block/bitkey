# Confirmation manager library for W3 two-tap confirmation flows

srcs = [
  'src/confirmation_manager.c',
]

# Dependencies differ slightly between embedded and POSIX
base_deps = [
  ipc_dep,
  log_dep,
  protos_dep,
  rtos_dep,
  secutils_dep,
]

if embedded_build
  deps = base_deps + [crypto_dep]
else
  # POSIX uses crypto_deps['posix'] which is set up at the top level
  # We use the dependency declared in the parent meson.build
  deps = base_deps
endif

confirmation_lib = static_library('confirmation',
  srcs,
  include_directories : ['inc'],
  dependencies : deps,
)

confirmation_dep = declare_dependency(
  link_with : confirmation_lib,
  include_directories : include_directories('inc'),
  dependencies : deps,
)
