#include "msgpack.h"

#include "assert.h"

#include <string.h>

static bool cmp_mem_reader(struct cmp_ctx_s* ctx, void* data, size_t len);
static size_t cmp_mem_writer(struct cmp_ctx_s* ctx, const void* data, size_t len);
static size_t cmp_mem_writer_ro(struct cmp_ctx_s* ctx, const void* data, size_t len);

void msgpack_mem_access_ro_init(cmp_ctx_t* cmp, msgpack_mem_access_t* m, const void* buf,
                                size_t size) {
  ASSERT(cmp != NULL);
  ASSERT(m != NULL);
  ASSERT(buf != NULL);

  m->buf = (char*)buf;
  m->size = size;
  m->index = 0;
  cmp_init(cmp, m, cmp_mem_reader, NULL, cmp_mem_writer_ro);
}

void msgpack_mem_access_rw_init(cmp_ctx_t* cmp, msgpack_mem_access_t* m, const void* buf,
                                size_t size) {
  ASSERT(cmp != NULL);
  ASSERT(m != NULL);
  ASSERT(buf != NULL);

  m->buf = (char*)buf;
  m->size = size;
  m->index = 0;
  cmp_init(cmp, m, cmp_mem_reader, NULL, cmp_mem_writer);
}

static bool cmp_mem_reader(struct cmp_ctx_s* ctx, void* data, size_t len) {
  msgpack_mem_access_t* mem = (msgpack_mem_access_t*)ctx->buf;
  if (mem->index + len <= mem->size) {
    memcpy(data, &mem->buf[mem->index], len);
    mem->index += len;
    return true;
  } else {
    return false;
  }
}

static size_t cmp_mem_writer(struct cmp_ctx_s* ctx, const void* data, size_t len) {
  msgpack_mem_access_t* mem = (msgpack_mem_access_t*)ctx->buf;
  if (mem->index + len <= mem->size) {
    memcpy(&mem->buf[mem->index], data, len);
    mem->index += len;
    return len;
  } else {
    return 0;
  }
}

static size_t cmp_mem_writer_ro(struct cmp_ctx_s* ctx, const void* data, size_t len) {
  (void)ctx;
  (void)data;
  (void)len;
  return 0;
}
