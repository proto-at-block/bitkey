/**
 * @file tlv_impl.h
 *
 * @{
 */

#pragma once

/**
 * @brief Returned if a TLV tag is not found.
 */
#define TLV_TAG_NOT_FOUND (-1)

/**
 * @brief Individual TVL tags written to the TLV buffer.
 *
 * @note TLV format: `[Tag (4 bytes)] [Length (2 bytes)] [Value (Length bytes)]`
 */
typedef struct __attribute__((packed)) {
  uint32_t tag;    /**< Tag identifier. */
  uint16_t length; /**< Tag value length (max 65536). */
  uint8_t value[]; /**< Tag value (unbounded). */
} tlv_tag_t;

_Static_assert((sizeof(tlv_tag_t) == 6u),
               "TLV tag size cannot change, doing so will prevent existing tags from being read.");

/** @} */
