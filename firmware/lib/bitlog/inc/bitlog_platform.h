#pragma once

#include <stdint.h>

typedef struct PACKED {
  uint32_t v : 24;
} uint24_t;

#define UINT24(val) \
  { .v = val }

#ifdef UNIT_TEST

#define BITLOG_UNIT_TEST_PC (0x11223344)
#define BITLOG_UNIT_TEST_LR (0xaabbccdd)

#define __GET_PC() ({ (void*)BITLOG_UNIT_TEST_PC; })
#define __GET_LR() ({ (void*)BITLOG_UNIT_TEST_LR; })

#else
#if defined(__arm__)
#define __GET_PC()                \
  ({                              \
    void* pc;                     \
    asm("mov %0, pc" : "=r"(pc)); \
    pc;                           \
  })
#elif defined(__arm64__) || defined(__aarch64__)
#define __GET_PC()               \
  ({                             \
    void* pc;                    \
    asm("adr %0, ." : "=r"(pc)); \
    pc;                          \
  })
#elif defined(__x86_64__)
#define __GET_PC()                                   \
  ({                                                 \
    void* rip;                                       \
    __asm__ volatile("lea (%%rip), %0" : "=r"(rip)); \
    rip;                                             \
  })
#else
#error "unsupported architecture"
#endif

#define __GET_LR() __builtin_return_address(0)

#endif
