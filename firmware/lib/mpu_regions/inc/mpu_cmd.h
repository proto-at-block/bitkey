#pragma once

#define MPU_HELPER                                     \
  X(ram_r1_start, ram_addr)                            \
  X(ram_r1_end, __ramfunc_start__)                     \
  X(ramfunc_start, __ramfunc_start__)                  \
  X(ramfunc_end, __ramfunc_end__)                      \
  X(ram_r2_start, __ramfunc_end__)                     \
  X(ram_total_size, ram_size)                          \
  X(bl_prog_start, bl_base_addr)                       \
  X(bl_total_size, bl_slot_size)                       \
  X(fs_start, flash_filesystem_addr)                   \
  X(fs_size, flash_filesystem_size)                    \
  X(app_a_meta_start, app_a_metadata_page)             \
  X(app_a_meta_size, app_a_metadata_size)              \
  X(app_a_prop_start, __application_a_properties_addr) \
  X(app_a_prop_size, __application_a_properties_size)  \
  X(app_a_boot_start, __application_a_boot_addr)       \
  X(app_a_boot_size, __application_a_boot_size)        \
  X(app_a_sig_start, __application_a_signature_addr)   \
  X(app_a_sig_size, __application_a_signature_size)    \
  X(app_b_meta_start, app_b_metadata_page)             \
  X(app_b_meta_size, app_b_metadata_size)              \
  X(app_b_prop_start, __application_b_properties_addr) \
  X(app_b_prop_size, __application_b_properties_size)  \
  X(app_b_boot_start, __application_b_boot_addr)       \
  X(app_b_boot_size, __application_b_boot_size)        \
  X(app_b_sig_start, __application_b_signature_addr)   \
  X(app_b_sig_size, __application_b_signature_size)

#define X(arg1, arg2) extern int arg2;
MPU_HELPER
#undef X
