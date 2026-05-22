srcs = [
  'src/log.c',
]

deps = [assert_dep, memfault_log_dep, printf_dep, rtos_dep]
includes = ['inc', shell_includes]

log_lib = library('log',
  srcs,
  include_directories : includes,
  dependencies : deps,
)
log_dep = declare_dependency(
  link_with : log_lib,
  include_directories : includes,
  dependencies : deps,
)

# Tokenized UART logging lives in a separate library so that bootloader/loader
# binaries (which use log_dep but cannot link RTOS/serial/nanocobs) are not
# affected. Applications that enable `log_tokenized` link this dep explicitly.
# The actual library is only built on embedded targets; on host (unit tests,
# fuzz) we declare an empty stub so app/*/application/meson.build can list
# `log_uart_dep` unconditionally without breaking the host build configure.
if embedded_build
  log_uart_lib = library('log-uart',
    'src/log_uart.c',
    include_directories : includes,
    dependencies : deps + [nanocobs_dep, serial_dep],
  )
  log_uart_dep = declare_dependency(
    link_with : log_uart_lib,
    include_directories : includes,
    dependencies : deps + [nanocobs_dep, serial_dep],
  )
else
  log_uart_dep = declare_dependency()
endif

log_noprintf_lib = library('log-noprintf',
  srcs,
  c_args : ['-DDISABLE_PRINTF=1'],
  include_directories : includes,
  dependencies : deps,
)
log_noprintf_dep = declare_dependency(
  link_with : log_noprintf_lib,
  include_directories : includes,
  dependencies : deps,
)
