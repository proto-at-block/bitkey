#pragma once

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

// This is a macro so that line numbers on failing unit tests line up.
#define cr_util_cmp_buffers(_a, _b, _size)                  \
  ({                                                        \
    struct cr_mem __mem_arr1 = {.data = _a, .size = _size}; \
    struct cr_mem __mem_arr2 = {.data = _b, .size = _size}; \
    cr_assert(eq(mem, __mem_arr1, __mem_arr2));             \
  })
