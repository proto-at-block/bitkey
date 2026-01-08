/**
 * @file tlv.h
 *
 * @brief TLV (Tag-Length-Value) library
 *
 * ## Overview
 *
 * This library provides a simple interface for encoding and decoding TLV data
 * structures. Each TLV is comprised of a tag, length, and value:
 *
 *  1. Tag: 4 Bytes (Identifier for the Value)
 *  2. Length: 2 Bytes (Length of the Value)
 *  3. Value: Length Bytes (Stored Data)
 *
 * The TLV API supports read, write, update and delete. The API assumes that
 * the memory given to it is present in RAM. Support is currently not available
 * for write, update or delete on flash memory. Doing so will lead to undefined
 * behaviour.
 *
 * @{
 */

#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Ending sentinel for TLV.
 *
 * @details No tag can can have this value. When read, this determines that
 * there are no more TLV tags present in the buffer.
 */
#define TLV_TAG_END_SENTINEL (0u)

/**
 * @brief TLV operation result codes.
 */
typedef enum {
  TLV_SUCCESS = 0,           /**< Operation completed successfully. */
  TLV_ERR_INVALID_PARAM = 1, /**< Invalid parameter passed to function. */
  TLV_ERR_NOT_FOUND = 2,     /**< Tag not found in TLV buffer. */
  TLV_ERR_NO_SPACE = 3,      /**< Insufficient space in buffer. */
  TLV_ERR_DUPLICATE = 4,     /**< Tag already exists (when adding). */
  TLV_ERR_CORRUPTED = 5,     /**< TLV structure is corrupted. */
} tlv_result_t;

/**
 * @brief TLV context structure.
 */
typedef struct {
  uint8_t* buffer; /**< Pointer to the TLV buffer. */
  size_t capacity; /**< Total capacity of the buffer. */
  size_t size;     /**< Current size of used data in buffer. */
} tlv_t;

/**
 * @brief Initialize a TLV structure with a buffer.
 *
 * @param[out] tlv        Pointer to TLV context structure.
 * @param[in]  buffer     Pointer to memory buffer to use for TLV data.
 * @param[in]  capacity   Size of the buffer in bytes.
 *
 * @return
 *   - #TLV_SUCCESS on success.
 *   - #TLV_ERR_INVALID_PARAM if @p tlv or @p buffer is `NULL`, or @p capacity is `0`.
 *   - #TLV_ERR_CORRUPTED if existing TLV data is corrupted.
 *
 * @note If the buffer contains existing TLV data, it will be validated and
 * the size will be set accordingly. If the buffer is empty or invalid,
 * the size will be set to 0.
 */
tlv_result_t tlv_init(tlv_t* tlv, void* buffer, size_t capacity);

/**
 * @brief Look up a tag and retrieve its value and length.
 *
 * @param[in]  tlv     Pointer to TLV context structure.
 * @param[in]  tag     Tag to search for.
 * @param[out] value   Output pointer to the value data (set to `NULL` if not needed).
 * @param[out] length  Output pointer to the length of the value (set to `NULL` if not needed).
 *
 * @return
 *   - #TLV_SUCCESS on success.
 *   - #TLV_ERR_INVALID_PARAM if @p tlv, @p value or @p length is `NULL`.
 *   - #TLV_ERR_NOT_FOUND if @p tag does not exist.
 *   - #TLV_ERR_CORRUPTED if @p tlv structure is corrupted.
 */
tlv_result_t tlv_lookup(const tlv_t* tlv, uint32_t tag, const uint8_t** value, uint16_t* length);

/**
 * @brief Add a new tag with its value.
 *
 * @param[in] tlv     Pointer to TLV context structure.
 * @param[in] tag     Tag to add.
 * @param[in] value   Pointer to value data.
 * @param[in] length  Length of the @p value in bytes.
 *
 * @return
 *   - #TLV_SUCCESS on success.
 *   - #TLV_ERR_INVALID_PARAM if @p tlv is `NULL` or (@p value is `NULL` and @p length > `0`).
 *   - #TLV_ERR_DUPLICATE if @p tag already exists.
 *   - #TLV_ERR_NO_SPACE if insufficient space in buffer.
 */
tlv_result_t tlv_add(tlv_t* tlv, uint32_t tag, const void* value, uint16_t length);

/**
 * @brief Update an existing tag with a new value.
 *
 * @param[in] tlv     Pointer to TLV context structure.
 * @param[in] tag     Tag to update.
 * @param[in] value   Pointer to new value data.
 * @param[in] length  Length of the new value in bytes.
 *
 * @return
 *   - #TLV_SUCCESS on success.
 *   - #TLV_ERR_INVALID_PARAM if @p tlv is `NULL` or (@p value is `NULL` and @p length > `0`).
 *   - #TLV_ERR_NOT_FOUND if @p tag does not exist.
 *   - #TLV_ERR_NO_SPACE if insufficient space in buffer (when new value is larger).
 *
 * @note If the new value is smaller than the old value, the TLV will be compacted.
 * If the new value is larger, the tag may be moved to accommodate the new size.
 */
tlv_result_t tlv_update(tlv_t* tlv, uint32_t tag, const void* value, uint16_t length);

/**
 * @brief Get the total size of used data in the TLV buffer.
 *
 * @param[in] tlv Pointer to TLV context structure
 *
 * @return Size of used data in bytes, or `0` if tlv is `NULL`.
 */
size_t tlv_get_size(const tlv_t* tlv);

/**
 * @brief Get the remaining capacity in the TLV buffer.
 *
 * @param[in] tlv Pointer to TLV context structure
 *
 * @return Remaining capacity in bytes, or `0` if tlv is `NULL`.
 */
size_t tlv_get_remaining_capacity(const tlv_t* tlv);

/**
 * @brief Delete a tag from the TLV buffer.
 *
 * @param[in] tlv     Pointer to TLV context structure.
 * @param[in] tag     Tag to delete.
 *
 * @return
 *   - #TLV_SUCCESS on success.
 *   - #TLV_ERR_INVALID_PARAM if @p tlv is `NULL`.
 *   - #TLV_ERR_NOT_FOUND if @p tag does not exist.
 */
tlv_result_t tlv_delete(tlv_t* tlv, uint32_t tag);

/** @} */
