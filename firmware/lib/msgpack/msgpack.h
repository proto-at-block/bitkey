#pragma once

#include "cmp.h"

typedef struct {
  char* buf;
  size_t index;
  size_t size;
} msgpack_mem_access_t;

void msgpack_mem_access_ro_init(cmp_ctx_t* cmp, msgpack_mem_access_t* m, const void* buf,
                                size_t size);
void msgpack_mem_access_rw_init(cmp_ctx_t* cmp, msgpack_mem_access_t* m, const void* buf,
                                size_t size);
