deps = [helpers_dep, iso7816_dep, log_dep, fff_dep]
includes = ['inc']

t4t_lib = library('t4t',
  # We don't have a use for NDEF, so we use a stub t4t
  # implementation. We still have to respond to NDEF APDUs
  # because iOS will send them when background tag reading.
  'src/t4t_stub.c',
  include_directories : includes,
  dependencies : deps,
)
t4t_dep = declare_dependency(
  link_with : t4t_lib,
  include_directories : includes,
  dependencies : deps,
)
