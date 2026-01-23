#pragma once

#include <stddef.h>

/**
 * memzero() - Zeroize memory
 *
 * @pnt: Pointer to the starting address of the region to zeroize
 * @len: The length in bytes to zeroize
 */
void memzero(void* const pnt, const size_t len);

/**
 * memcmp_s() - Compare two regions of memory in constant time
 *
 * @b1: Pointer to the first region of memory
 * @b2: Pointer to the second region of memory
 * @len: The length in bytes to compare
 *
 * Implementation taken directly from https://github.com/chmike/cst_time_memcmp
 */
int memcmp_s(const void* b1, const void* b2, size_t len);
