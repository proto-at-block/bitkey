#pragma once

#include "assert.h"

#ifdef __BYTE_ORDER__

#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__

#define __bk_bswap16(x) ((uint16_t)((((uint16_t)(x)&0xff00) >> 8) | (((uint16_t)(x)&0x00ff) << 8)))
#define __bk_bswap32(x)                                                                \
  ((uint32_t)((((uint32_t)(x)&0xff000000) >> 24) | (((uint32_t)(x)&0x00ff0000) >> 8) | \
              (((uint32_t)(x)&0x0000ff00) << 8) | (((uint32_t)(x)&0x000000ff) << 24)))
#define __bk_bswap64(x)                                           \
  ((__uint64_t)((((__uint64_t)(x)&0xff00000000000000ULL) >> 56) | \
                (((__uint64_t)(x)&0x00ff000000000000ULL) >> 40) | \
                (((__uint64_t)(x)&0x0000ff0000000000ULL) >> 24) | \
                (((__uint64_t)(x)&0x000000ff00000000ULL) >> 8) |  \
                (((__uint64_t)(x)&0x00000000ff000000ULL) << 8) |  \
                (((__uint64_t)(x)&0x0000000000ff0000ULL) << 24) | \
                (((__uint64_t)(x)&0x000000000000ff00ULL) << 40) | \
                (((__uint64_t)(x)&0x00000000000000ffULL) << 56)))

#ifndef __APPLE__
#define ntohs(x)  __bk_bswap16(x)
#define htons(x)  __bk_bswap16(x)
#define ntohl(x)  __bk_bswap32(x)
#define htonl(x)  __bk_bswap32(x)
#define htonll(x) __bk_bswap64(x)
#endif

#elif __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__

#define ntohl(x)  ((uint32_t)(x))
#define ntohs(x)  ((uint16_t)(x))
#define htonl(x)  ((uint32_t)(x))
#define htons(x)  ((uint16_t)(x))
#define htonll(x) ((uint64_t)(x))

#else
#error "PDP-endianness not supported!"
#endif

#else
#error "Can't determine target platform endianness"
#endif

// Change the bit at position `pos` in `val` to 1 if target is 1, and 0 if target is 0.
#define BIT_CHANGE(val, pos, target)       \
  ({                                       \
    ASSERT(target == 0 || target == 1);    \
    val ^= (-target ^ val) & (1UL << pos); \
  })

#define IS_POWER_OF_TWO(val) ({ (val != 0) && ((val & (val - 1)) == 0); })
