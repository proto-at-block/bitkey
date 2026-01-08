/**
 * @file
 *
 * @brief UC (UXC COBS)
 */

#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Errors returned by the UC public APIs.
 */
typedef enum {
  /**
   * @brief Success.
   */
  UC_ERR_NONE = 0,

  /**
   * @brief Mismatch in CRC.
   */
  UC_ERR_CRC_MISMATCH = 1,

  /**
   * @brief Length of received data is larger than supported.
   */
  UC_ERR_TOO_LARGE = 2,

  /**
   * @brief Not all data written.
   */
  UC_ERR_WR_FAILED = 3,

  /**
   * @brief Invalid argument passed.
   */
  UC_ERR_INVALID_ARG = 4,

  /**
   * @brief COBS decoding failed.
   */
  UC_ERR_DECODE_FAILED = 5,

  /**
   * @brief COBS decoding failed due to buffer being too small.
   */
  UC_ERR_DECODE_TOO_SMALL = 6,

  /**
   * @brief Size mismatch between the decoded data and the message length
   * (including header, encryption and signing).
   */
  UC_ERR_SIZE_MISMATCH = 7,

  /**
   * @brief Retried sending data the maximum amount of times without receiving
   * an ACK.
   */
  UC_ERR_MAX_RETRANSMITS = 8,

  /**
   * @brief Failed to encode proto data.
   */
  UC_ERR_PROTO_ENCODE_FAILED = 9,

  /**
   * @brief Failed to decode proto data.
   */
  UC_ERR_PROTO_DECODE_FAILED = 10,

  /**
   * @brief Failed to lock mutex.
   */
  UC_ERR_MUTEX_FAILED = 11,

  /**
   * @brief Ran out of memory posting to task queue.
   */
  UC_ERR_Q_MAX = 12,
} uc_err_t;

/**
 * @brief Callback passed to #uc_send() for sending data.
 *
 * @param context   User-supplied pointer to pass to the callback.
 * @param data      Pointer to the buffer of data to write.
 * @param data_len  Length of the @p data in bytes.
 *
 * @return Number of bytes written.
 *
 * @note If zero bytes are written, then the send will be aborted.
 */
typedef uint32_t (*uc_send_callback_t)(void* context, const uint8_t* data, size_t data_len);

/**
 * @brief Initializes the internal state used by the UC library.
 *
 * @param send_cb  Callback to invoke to send data.
 * @param context  User-supplied context pointer.
 */
void uc_init(uc_send_callback_t send_cb, void* context);

/**
 * @brief Performs an idle check for the UC library, sending an ACK if necessary.
 *
 * @param context  Unused.
 */
void uc_idle(void* context);

/**
 * @brief Sends a pure ACK message.
 *
 * @return #uc_err_t.
 */
uc_err_t uc_ack(void);

/**
 * @brief Sends a proto to the companion MCU.
 *
 * @details Upon receipt, the recipient has up to #UC_ACK_TIMEOUT_MS to send
 * a message, otherwise they will send a pure ACK in response.
 *
 * @param proto  Pointer to the protobuf allocated by #uc_alloc_send_proto().
 *
 * @return `true` if proto was sent successfully, otherwise `false`.
 *
 * @note After this method is called, @p proto can no longer be used.
 *
 * @note This message will block up to #UC_RETRANSMIT_TIMEOUT_MS * #UC_RETRANSMIT_MAX_COUNT
 * milliseconds.
 */
bool uc_send(void* proto);

/**
 * @brief Sends a proto to the companion MCU.
 *
 * @details Unlike #uc_send(), the recipient will immediately send an ACK upon
 * receipt of the message. This API allows for high throughput at the
 * expense of more serial transfers being performed, meaning higher CPU load.
 * This API should only be used for time sensitive information exchange.
 *
 * @param proto  Pointer to the protobuf allocated by #uc_alloc_send_proto().
 *
 * @return `true` if proto was sent successfully, otherwise `false`.
 *
 * @note After this method is called, @p proto can no longer be used.
 *
 * @note This message will block up to #UC_RETRANSMIT_TIMEOUT_MS * #UC_RETRANSMIT_MAX_COUNT
 * milliseconds.
 */
bool uc_send_immediate(void* proto);

/**
 * @brief Passes raw data to the UXC library.
 *
 * @param data      Pointer to the received data.
 * @param data_len  Length of the @p data in bytes.
 * @param context   User-supplied context pointer (unused).
 */
void uc_handle_data(const uint8_t* data, uint32_t data_len, void* context);

/**
 * @brief Allocates memory for a UXC proto to send to the companion MCU.
 *
 * @return Pointer to the allocated memory.
 *
 * @note The caller is responsible for free'ing the memory, either by calling
 * #uc_free_send_proto() or by calling #uc_send().
 */
void* uc_alloc_send_proto(void);

/**
 * @brief Frees memory allocated for a UXC send proto.
 *
 * @param proto_buffer  Pointer to the allocated proto memory.
 */
void uc_free_send_proto(void* proto_buffer);

/**
 * @brief Frees memory allocated for a received UXC proto.
 *
 * @param proto_buffer  Pointer to the allocated proto memory.
 *
 * @note This is memory for a proto that is passed to a registered proto
 * route callback.
 */
void uc_free_recv_proto(void* proto_buffer);
