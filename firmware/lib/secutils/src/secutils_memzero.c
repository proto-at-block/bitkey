#include "secutils.h"

// Based on
// https://github.com/jedisct1/libsodium/blob/1647f0d53ae0e370378a9195477e3df0a792408f/src/libsodium/sodium/utils.c#L102-L130
//
// NOTE: leave this function in a separate file to prevent it being optimized away (with no LTO).
void memzero(void* const pnt, const size_t len) {
  if (!pnt) {
    return;
  }

  volatile unsigned char* volatile pnt_ = (volatile unsigned char* volatile)pnt;
  size_t i = (size_t)0U;

  while (i < len) {
    pnt_[i++] = 0U;
  }
}
