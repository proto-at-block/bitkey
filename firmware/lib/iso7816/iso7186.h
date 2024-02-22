#pragma once

#include "assert.h"
#include "bitops.h"

#define CLA (0)  // Instruction class
#define INS (1)  // Instruction code
#define P1  (2)  // Parameter 1
#define P2  (3)  // Parameter 2
#define LC  (4)  // Data length

#define SW_SIZE (2)

#define ISO7816_SET_STATUS(rsp, off, _sw1, _sw2) \
  ({                                             \
    rsp[off] = _sw1;                             \
    rsp[off + 1] = _sw2;                         \
  })

// ISO/IEC 7816-4 Section 5.1.3
#define RSP_OK(rsp, off)                  ISO7816_SET_STATUS(rsp, off, 0x90, 0x00)
#define RSP_UNSUPPORTED_INS(rsp, off)     ISO7816_SET_STATUS(rsp, off, 0x68, 0x00)
#define RSP_FILE_NOT_FOUND(rsp, off)      ISO7816_SET_STATUS(rsp, off, 0x6A, 0x82)
#define RSP_FCI_GENERIC_FAILURE(rsp, off) ISO7816_SET_STATUS(rsp, off, 0x6F, 0x00)
#define RSP_FCP_END_OF_FILE(rsp, off)     ISO7816_SET_STATUS(rsp, off, 0x68, 0x82)
#define RSP_WRONG_PARAMS(rsp, off)        ISO7816_SET_STATUS(rsp, off, 0x6B, 0x00)
#define RSP_BYTES_REMAIN(rsp, off)        ISO7816_SET_STATUS(rsp, off, 0x61, 0x00)

// Table 20: Coding of Le field
static inline uint16_t le_to_int(uint8_t* buf, bool extended_lc) {
  ASSERT(!extended_lc);  // Not supported
  if (buf[0] != 0) {     // Short coding
    return buf[0];
  } else {  // Extended coding
    return ntohs(*(uint16_t*)&buf[1]);
  }
}

// Table 19: Coding of Lc field
static inline uint16_t lc_to_int(uint8_t* buf) {
  if (buf[0] != 0) {  // Short coding
    return buf[0];
  } else {  // Extended coding
    return ntohs(*(uint16_t*)&buf[1]);
  }
}

static inline bool is_short_coding(uint16_t value) {
  return value >= 1 && value <= 255;
}
