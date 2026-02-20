# Confirmation manager library for W3 two-tap confirmation flows

if embedded_build
  srcs = [
    'src/confirmation_manager.c',
  ]

  deps = [
    crypto_dep,
    ipc_dep,
    log_dep,
    protos_dep,
    rtos_dep,
    secutils_dep,
  ]

  confirmation_lib = static_library('confirmation',
    srcs,
    include_directories : ['inc'],
    dependencies : deps,
  )

  confirmation_dep = declare_dependency(
    link_with : confirmation_lib,
    include_directories : include_directories('inc'),
  )
else
  confirmation_dep = declare_dependency(
    include_directories : include_directories('inc'),
    dependencies : [ipc_dep],
  )
endif
