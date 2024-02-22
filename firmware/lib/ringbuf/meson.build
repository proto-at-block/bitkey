srcs = [
  'ringbuf.c',
]

deps = [rtos_dep]

includes = ['.']

ringbuf_lib = library('ringbuf',
  srcs,
  include_directories : [includes],
  dependencies : deps,
)
ringbuf_dep = declare_dependency(
  link_with : ringbuf_lib,
  include_directories : includes,
  dependencies : deps,
)

ringbuf_fuzz = executable('ringbuf-fuzz',
  'ringbuf_fuzz.cc',
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [ringbuf_dep, fff_dep, fuzzed_data_provider_dep],
)
