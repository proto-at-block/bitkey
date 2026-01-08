#include "langpack.h"

#include "assert.h"
#include "attributes.h"
#include "tlv.h"

#include <stddef.h>
#include <stdint.h>

static struct {
  /**
   * @brief TLV for the loaded language pack data.
   */
  tlv_t db;
} langpack_priv SHARED_TASK_DATA = {0};

void __attribute__((weak)) langpack_load_default(void) {
  // No-op.
}

void langpack_load(const uint8_t* langpack_mem, size_t langpack_size) {
  ASSERT(langpack_mem != NULL);
  ASSERT(langpack_size >= sizeof(langpack_hdr_v1_t));

  const langpack_hdr_v1_t* hdr = (const langpack_hdr_v1_t*)langpack_mem;

  // Validate header fields before using them.
  ASSERT(hdr->version == 1);
  ASSERT(hdr->hdr_len <= langpack_size);
  ASSERT(hdr->type == LANGPACK_TYPE_ASCII);

  const uint8_t* data = &langpack_mem[hdr->hdr_len];
  const size_t data_size = langpack_size - hdr->hdr_len;
  const tlv_result_t result = tlv_init(&langpack_priv.db, (uint8_t*)(uintptr_t)data, data_size);
  ASSERT(result == TLV_SUCCESS);
}

const char* langpack_get_string(langpack_string_id_t id) {
  const uint8_t* string = NULL;
  uint16_t string_len = 0;
  const tlv_result_t result = tlv_lookup(&langpack_priv.db, id, &string, &string_len);
  if ((result != TLV_SUCCESS) || (string_len == 0)) {
#ifndef CONFIG_PROD
    ASSERT(false);
#endif
    return "";
  }
  return (const char*)string;
}
