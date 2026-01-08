/**
 * @file langpack.h
 *
 * @brief Language Package
 *
 * @{
 */

#pragma once

#include "langpack_ids.h"

#include <stddef.h>
#include <stdint.h>

typedef enum {
  /**
   * @brief Unused.
   */
  LANGPACK_TYPE_NONE = 0,

  /**
   * @brief Strings are ASCII.
   */
  LANGPACK_TYPE_ASCII = 1,
} langpack_type_t;

typedef struct __attribute__((packed)) {
  /**
   * @brief Version identifier (1).
   */
  uint8_t version;

  /**
   * @brief Size of the header in bytes.
   */
  uint8_t hdr_len;

  /**
   * @brief Language pack type.
   */
  uint8_t type;
} langpack_hdr_v1_t;

/**
 * @brief Loads the default language pack compiled with the target.
 *
 * @note This is a no-op if no language pack is compiled in.
 */
void langpack_load_default(void);

/**
 * @brief Loads the specified language pack.
 *
 * @param langpack_mem   Pointer to where the langpack strings are stored.
 * @param langpack_size  Size of the @p langpack_mem in bytes.
 */
void langpack_load(const uint8_t* langpack_mem, size_t langpack_size);

/**
 * @brief Retrieves a string from the langpack database.
 *
 * @param id  Identifier of the langpack string.
 *
 * @return Pointer to the string in memory on success, otherwise an empty
 * string if the string ID is not found or the language pack has not been
 * loaded.
 */
const char* langpack_get_string(langpack_string_id_t id);

/** @} */
