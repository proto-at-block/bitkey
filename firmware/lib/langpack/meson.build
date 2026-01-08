languages = [
  'en',
]

python = find_program('python3')

input_files = []

generated_files = []

# Add the language pack binary file and source file to the generated file
# list for each language.
foreach language : languages
  input_files += 'resources/' + language + '.yml'

  pack_bin_file = language + '.langpack.bin'
  generated_files += pack_bin_file

  src_file = 'langpack_' + language + '.c'
  generated_files += src_file
endforeach

langpack_gen = custom_target('langpack-generate',
  input : input_files,
  output : generated_files,
  command : [
    python,
    meson.current_source_dir() + '/resources/build.py',
    '-o',
    meson.current_build_dir(),
    '@INPUT@'
  ],
  install : false,
)
langpack_gen_dep = declare_dependency(
  sources : langpack_gen,
)

langpack_lib = library('langpack', [
    'src/langpack.c',
  ],
  include_directories : ['inc'],
  dependencies : [assert_dep, helpers_dep, tlv_dep],
)
langpack_dep = declare_dependency(
  link_with : langpack_lib,
  include_directories : ['inc'],
  dependencies : [langpack_gen_dep],
)

# Create language specific packs.
langpack_deps = {}
foreach language : languages
  langpack_deps += {
    language: declare_dependency(
      link_with : langpack_lib,
      sources : langpack_gen,
      include_directories : ['inc'],
      compile_args : '-DLANGPACK_LANG_' + language.to_upper(),
      dependencies : [langpack_gen_dep],
    )
  }
endforeach

langpack_test = executable('langpack-test',
  'src/langpack_test.c',
  dependencies : [langpack_deps['en'], test_deps],
)
test('langpack test', langpack_test)
