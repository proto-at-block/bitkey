#include "tlv.h"

#include "tlv_impl.h"

#include <sys/types.h>

#include <stdint.h>
#include <string.h>

/**
 * @brief Validates a TLV structure.
 *
 * @param[in]   buffer    Pointer to the TLV data.
 * @param[in]   capacity  Length of @p buffer in bytes.
 * @param[out]  out_size  Output pointer to store calculated TLV buffer size.
 *
 * @return #TLV_SUCCESS on successful validation.
 */
static tlv_result_t _tlv_validate(const uint8_t* buffer, size_t capacity, size_t* out_size);

/**
 * @brief Finds a tag in a TLV buffer.
 *
 * @param[in] tlv  Pointer to the TLV instance.
 * @param[in] tag  The tag to find.
 *
 * @return Offset in the memory buffer for the tag on success, otherwise
 * #TLV_TAG_NOT_FOUND.
 */
static ssize_t _tlv_find_tag(const tlv_t* tlv, uint32_t tag);

tlv_result_t tlv_init(tlv_t* tlv, void* buffer, size_t capacity) {
  if ((tlv == NULL) || (buffer == NULL) || (capacity == 0)) {
    return TLV_ERR_INVALID_PARAM;
  }

  tlv->buffer = (uint8_t*)buffer;
  tlv->capacity = capacity;

  // Validate and calculate size of existing TLV data.
  size_t size = 0;
  const tlv_result_t result = _tlv_validate(tlv->buffer, tlv->capacity, &size);
  if (result != TLV_SUCCESS) {
    tlv->size = 0;
    return result;
  }

  tlv->size = size;
  return TLV_SUCCESS;
}

tlv_result_t tlv_lookup(const tlv_t* tlv, uint32_t tag, const uint8_t** value, uint16_t* length) {
  if ((tlv == NULL) || (value == NULL) || (length == NULL)) {
    return TLV_ERR_INVALID_PARAM;
  }

  const ssize_t offset = _tlv_find_tag(tlv, tag);
  if (offset < 0) {
    return TLV_ERR_NOT_FOUND;
  }

  const tlv_tag_t* tag_ptr = (const tlv_tag_t*)&tlv->buffer[offset];

  *length = tag_ptr->length;
  if (tag_ptr->length > 0) {
    *value = tag_ptr->value;
  } else {
    *value = NULL;
  }

  return TLV_SUCCESS;
}

tlv_result_t tlv_add(tlv_t* tlv, uint32_t tag, const void* value, uint16_t length) {
  if ((tlv == NULL) || (value == NULL) || (length == 0)) {
    return TLV_ERR_INVALID_PARAM;
  }

  if (tag == TLV_TAG_END_SENTINEL) {
    // Sentinel tag value is reserved.
    return TLV_ERR_INVALID_PARAM;
  }

  // Check if tag already exists.
  if (_tlv_find_tag(tlv, tag) != TLV_TAG_NOT_FOUND) {
    return TLV_ERR_DUPLICATE;
  }

  // Check if we have enough space.
  const size_t required_space = sizeof(tlv_tag_t) + length;
  if ((tlv->size + required_space) > tlv->capacity) {
    return TLV_ERR_NO_SPACE;
  }

  // Write tag and length.
  tlv_tag_t* tag_ptr = (tlv_tag_t*)&tlv->buffer[tlv->size];
  tag_ptr->tag = tag;
  tag_ptr->length = length;
  memcpy(tag_ptr->value, value, length);

  // Update size.
  tlv->size += required_space;

  return TLV_SUCCESS;
}

tlv_result_t tlv_update(tlv_t* tlv, uint32_t tag, const void* value, uint16_t length) {
  if ((tlv == NULL) || (value == NULL) || (length == 0)) {
    return TLV_ERR_INVALID_PARAM;
  }

  const ssize_t offset = _tlv_find_tag(tlv, tag);
  if (offset < 0) {
    return TLV_ERR_NOT_FOUND;
  }

  tlv_tag_t* tag_ptr = (tlv_tag_t*)&tlv->buffer[offset];
  const size_t old_entry_size = sizeof(tlv_tag_t) + tag_ptr->length;
  const size_t new_entry_size = sizeof(tlv_tag_t) + length;

  if (tag_ptr->length == length) {
    // Same size - just update the value in place.
    memcpy(tag_ptr->value, value, length);
  } else if (length < tag_ptr->length) {
    // New value is smaller - update and compact.
    tag_ptr->length = length;
    memcpy(tag_ptr->value, value, length);

    // Move the rest of the buffer to compact
    const size_t bytes_after = tlv->size - (offset + old_entry_size);
    if (bytes_after > 0) {
      memmove(&tlv->buffer[offset + new_entry_size], &tlv->buffer[offset + old_entry_size],
              bytes_after);
    }

    tlv->size -= (old_entry_size - new_entry_size);
  } else {
    // New value is larger - check if we have space.
    const size_t additional_space = new_entry_size - old_entry_size;
    if ((tlv->size + additional_space) > tlv->capacity) {
      return TLV_ERR_NO_SPACE;
    }

    // Move the rest of the buffer to make space.
    const size_t bytes_after = tlv->size - (offset + old_entry_size);
    if (bytes_after > 0) {
      memmove(&tlv->buffer[offset + new_entry_size], &tlv->buffer[offset + old_entry_size],
              bytes_after);
    }

    // Update length and value.
    tag_ptr->length = length;
    memcpy(tag_ptr->value, value, length);

    tlv->size += additional_space;
  }

  return TLV_SUCCESS;
}

size_t tlv_get_size(const tlv_t* tlv) {
  if (tlv == NULL) {
    return 0;
  }
  return tlv->size;
}

size_t tlv_get_remaining_capacity(const tlv_t* tlv) {
  if (tlv == NULL) {
    return 0;
  }
  return tlv->capacity - tlv->size;
}

tlv_result_t tlv_delete(tlv_t* tlv, uint32_t tag) {
  if (tlv == NULL) {
    return TLV_ERR_INVALID_PARAM;
  }

  const ssize_t offset = _tlv_find_tag(tlv, tag);
  if (offset < 0) {
    return TLV_ERR_NOT_FOUND;
  }

  const tlv_tag_t* tag_ptr = (const tlv_tag_t*)&tlv->buffer[offset];
  const size_t entry_size = sizeof(*tag_ptr) + tag_ptr->length;

  // Move the rest of the buffer to remove the entry.
  const size_t bytes_after = tlv->size - (offset + entry_size);
  if (bytes_after > 0) {
    memmove(&tlv->buffer[offset], &tlv->buffer[offset + entry_size], bytes_after);
  }

  tlv->size -= entry_size;

  return TLV_SUCCESS;
}

static tlv_result_t _tlv_validate(const uint8_t* buffer, size_t capacity, size_t* out_size) {
  size_t offset = 0;

  while (offset < capacity) {
    // Check if we have enough space for header.
    if (offset + sizeof(tlv_tag_t) > capacity) {
      if (offset == 0) {
        // Empty buffer.
        *out_size = 0;
        return TLV_SUCCESS;
      }
      // Partial header at end.
      return TLV_ERR_CORRUPTED;
    }

    const tlv_tag_t* tag = (tlv_tag_t*)&buffer[offset];

    if (tag->tag == TLV_TAG_END_SENTINEL) {
      *out_size = offset;
      return TLV_SUCCESS;
    }

    // Check if we have enough space for value.
    const size_t tag_size = sizeof(*tag) + tag->length;
    if ((offset + tag_size) > capacity) {
      return TLV_ERR_CORRUPTED;
    }

    offset += tag_size;
  }

  *out_size = offset;
  return TLV_SUCCESS;
}

static ssize_t _tlv_find_tag(const tlv_t* tlv, uint32_t tag) {
  size_t offset = 0;

  while ((offset + sizeof(tlv_tag_t)) <= tlv->size) {
    const tlv_tag_t* tag_ptr = (tlv_tag_t*)&tlv->buffer[offset];

    if (tag_ptr->tag == TLV_TAG_END_SENTINEL) {
      break;
    } else if (tag_ptr->tag == tag) {
      return (ssize_t)offset;
    }

    offset += sizeof(*tag_ptr) + tag_ptr->length;
  }

  return TLV_TAG_NOT_FOUND;
}
