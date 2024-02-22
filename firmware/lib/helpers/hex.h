#pragma once

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>  // For sscanf

#ifdef EMBEDDED_BUILD
#include "printf.h"
#else
#include <stdio.h>
#endif

static inline void dumphex(uint8_t* buf, uint32_t size) {
  for (uint32_t i = 0; i < size; i++) {
    printf("%02x", buf[i]);
  }
  printf("\n");
}

static inline size_t parsehex(const char* hex_string, const size_t len, uint8_t* bytes) {
  size_t bytes_parsed = 0;
  for (size_t i = 0; i < len; i += 2) {
    // Note: format _should_ be %2hhx but that converts '88' to 88u for some reason
    sscanf(&hex_string[i], "%02x", (unsigned int*)&bytes[i / 2]);
    bytes_parsed++;
  }
  return bytes_parsed;
}
