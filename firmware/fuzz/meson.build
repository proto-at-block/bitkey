fuzzed_data_provider_dep = declare_dependency(
  include_directories : ['.'],
)

fuzz_args = ['-fsanitize=fuzzer,address,signed-integer-overflow', '-DFUZZ_BUILD']

# nanocobs COBS framing encode/decode fuzzer (no hardware deps).
# Placed here rather than in third-party/packagefiles/nanocobs/ because
# fuzz_args and fuzzed_data_provider_dep are defined in this file and
# third-party/ is processed before fuzz/.
nanocobs_fuzz = executable('nanocobs-fuzz',
  '../third-party/packagefiles/nanocobs/nanocobs_fuzz.cc',
  include_directories : include_directories('../third-party/nanocobs'),
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [nanocobs_dep, fuzzed_data_provider_dep],
)

# picocert certificate chain validation fuzzer (header-only, no link deps).
# Placed here for the same reason as nanocobs_fuzz above.
picocert_fuzz = executable('picocert-fuzz',
  '../third-party/packagefiles/picocert/picocert_fuzz.cc',
  include_directories : include_directories('../third-party/picocert'),
  cpp_args : fuzz_args,
  link_args : fuzz_args,
  dependencies : [picocert_dep, fuzzed_data_provider_dep],
)
