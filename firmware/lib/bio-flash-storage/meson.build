bio_flash_storage_lib = library('bio-flash-storage',
  'src/bio_flash_storage.c',
  include_directories : include_directories('inc'),
  dependencies : [assert_dep, helpers_dep, mcu_dep],
)
bio_flash_storage_dep = declare_dependency(
  link_with : bio_flash_storage_lib,
  include_directories : include_directories('inc'),
)

bio_flash_storage_test = executable('bio-flash-storage-test',
  ['src/bio_flash_storage_test.c', 'src/bio_flash_storage.c'],
  include_directories : include_directories('inc'),
  c_args : ['-DFLASH_PAGE_SIZE=0x00002000UL'],
  dependencies : [helpers_dep, log_dep, mcu_dep, test_deps],
)
test('bio-flash-storage test', bio_flash_storage_test)
