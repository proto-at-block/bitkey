#pragma once

#include "attributes.h"

#include <stddef.h>
#include <stdint.h>

typedef struct {
  const uintptr_t base_addr;   //<! Base address of the application image.
  const uintptr_t boot_addr;   //<! Starting address of code in the application image.
  const uintptr_t sig_addr;    //<! Address of the application image signature.
  const uintptr_t props_addr;  //<! Address of the application properties.
  const size_t program_size;   //<! Size of the application code in bytes.
  const size_t sig_size;       //<! Size of the application signature in bytes.
  const size_t total_size;     //<! Total size of the application code + signature in bytes.
} bootload_img_t;

/**
 * @brief Bootloader to application trampoline.
 *
 * @param addr  Address of the application image.
 */
NO_RETURN void bootload_trampoline(uint8_t* addr);
