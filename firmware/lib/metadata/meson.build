srcs = [
  'src/metadata.c',
]

includes = [
  'inc',
]

deps = [
  assert_dep,
  printf_dep,
  msgpack_dep,
  cmp_dep,
  log_dep,
  protos_dep
]

metadata_lib = library('metadata',
  srcs,
  include_directories : includes,
  dependencies : deps,
)
metadata_dep = declare_dependency(
  link_with : metadata_lib,
  include_directories : ['inc'],
)

metadata_cmd_lib = library('metadata-cmd',
  'src/metadata_cmd.c',
  include_directories : includes,
  dependencies : [deps, shell_dep],
)
metadata_cmd_dep = declare_dependency(
  link_whole : metadata_cmd_lib,
  dependencies : [deps, shell_dep]
)

metadata_fuzz = executable('metadata-fuzz',
  'src/metadata_fuzz.cc',
  include_directories : includes,
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [metadata_dep, protos_dep, fff_dep, fuzzed_data_provider_dep],
)
