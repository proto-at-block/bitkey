iso7816_includes = include_directories('.')
iso7816_dep = declare_dependency(
  include_directories : iso7816_includes,
  dependencies : [assert_dep],
)

# Add UBSan alongside ASan for this target: the lc_to_int/le_to_int functions
# use ntohs(*(uint16_t*)&buf[1]) which is an unaligned read (UB) detectable by
# UBSan but not by ASan alone.
iso7816_fuzz_args = fuzz_args + ['-fsanitize=undefined']
iso7816_fuzz = executable('iso7816-fuzz',
  'iso7816_fuzz.cc',
  include_directories : [iso7816_includes, assert_includes],
  cpp_args : iso7816_fuzz_args,
  link_args : iso7816_fuzz_args,
  dependencies : [iso7816_dep, helpers_dep, fuzzed_data_provider_dep],
)
