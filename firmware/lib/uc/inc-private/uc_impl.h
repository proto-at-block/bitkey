#pragma once

#include "mempool.h"
#include "rtos.h"
#include "uc.h"
#ifndef UC_HOST_MODE
#error "UC_HOST_MODE not defined"
#else
#if (UC_HOST_MODE == 1)
#include "uc_cfg_host.h"
#else
#include "uc_cfg_device.h"
#endif
#endif

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Initial seed for computing the CRC16 (CRC-CCITT).
 */
#define UC_CRC_SEED 0xFFFFu

/**
 * @brief Little-endian polynomial for CRC16 (CRC-CCITT).
 */
#define UC_CRC_POLYNOMIAL 0x1021u

/**
 * @brief Maximum number of times a message can be re-transmitted.
 */
#define UC_RETRANSMIT_MAX_COUNT 3u

/**
 * @brief Number of milliseconds to wait before sending a pure ACK message.
 *
 * @details Upon receipt of a message, the recipient must send an ACK once this
 * timeout expires if no ACK has been sent yet.
 */
#define UC_ACK_TIMEOUT_MS 200u

/**
 * @brief Number of milliseconds to wait before re-transmitting a message.
 *
 * @details Upon sending a message, the sender should attempt to re-transmit
 * once this timeout expires if no ACK has been received yet. Should be
 * strictly bigger than ACK timeout and account for latency and system load.
 */
#define UC_RETRANSMIT_TIMEOUT_MS 250u

/**
 * @brief Maximum number of messages for each of send and receive buffers.
 */
#define UC_MSG_MAX_NUM 2u

/**
 * @brief Size of a message header in bytes.
 *
 * @note This should NOT be changed.
 */
#define UC_MSG_HDR_SIZE 14u

_Static_assert(14u == UC_MSG_HDR_SIZE, "Header size cannot change.");

/**
 * @brief Overhead for encrypting a message, including signature.
 *
 * @todo TODO(W-14107): Specify real padding.
 */
#define UC_ENCRYPTION_PADDING 47u

/**
 * @brief Overhead for transmitting a message (header + AES encryption).
 */
#define UC_MSG_OVERHEAD (UC_MSG_HDR_SIZE + UC_ENCRYPTION_PADDING)

/**
 * @brief Number of milliseconds to wait for the message buffer data to be free.
 */
#define UC_SEM_TIMEOUT_MS 5000u

_Static_assert((UC_RETRANSMIT_TIMEOUT_MS > UC_ACK_TIMEOUT_MS),
               "Retransmit timeout must be > ACK timeout.");

/**
 * @brief Calculates the COBS overhead for encoding / decoding.
 *
 * @details COBS requires a minimum of 1 byte overhead + ``n/254`` (rounded-up)
 * bytes for ``n`` bytes of data.
 */
#define UC_COBS_OVERHEAD(n) (1 + ((n) / (0xFF - 1u)) + (n))

/**
 * @brief Computes the size of a message with the overhead.
 */
#define UC_MSG_SIZE(n) (UC_MSG_OVERHEAD + (n))

/**
 * @brief Size of the internal UC buffer used for encoding/decoding messages.
 *
 * @note The value 1024 is chosen as a safe default for legacy compatibility and
 * to accommodate the maximum expected protocol message size. Static assertions
 * below ensure this buffer is always large enough for current protocol requirements.
 * If protocol buffer sizes increase, this value should be revisited.
 */
#define UC_MSG_BUFFER_SIZE 1024

/**
 * @brief Size of the buffer used receiving a COBS encoded message.
 */
#define UC_COBS_RD_ENC_BUFFER_SIZE UC_COBS_OVERHEAD(UC_MSG_SIZE(UC_CFG_RD_BUFFER_SIZE))

/**
 * @brief Size of the buffer used COBS encoding a message for sending.
 */
#define UC_COBS_WR_ENC_BUFFER_SIZE UC_COBS_OVERHEAD(UC_MSG_SIZE(UC_CFG_WR_BUFFER_SIZE))

_Static_assert(UC_CFG_RD_BUFFER_SIZE >= sizeof(UC_CFG_DEC_PROTO_TYPE),
               "Proto can no longer fit in buffer.");

_Static_assert(UC_CFG_WR_BUFFER_SIZE >= sizeof(UC_CFG_ENC_PROTO_TYPE),
               "Proto can no longer fit in buffer.");

_Static_assert(UC_MSG_BUFFER_SIZE >= UC_CFG_WR_BUFFER_SIZE,
               "Message buffer too small to encode proto.");

_Static_assert(UC_MSG_BUFFER_SIZE >= UC_CFG_RD_BUFFER_SIZE,
               "Message buffer too small to decode proto.");

_Static_assert(((UC_MSG_BUFFER_SIZE % sizeof(uint64_t)) == 0),
               "Buffer size must be double-word aligned.");

_Static_assert(((UC_CFG_RD_BUFFER_SIZE % sizeof(uint64_t)) == 0),
               "Buffer size must be double-word aligned.");

_Static_assert(((UC_CFG_WR_BUFFER_SIZE % sizeof(uint64_t)) == 0),
               "Buffer size must be double-word aligned.");

/**
 * @brief Header prefixed to each proto payload sent over the serial comms
 * interface.
 */
typedef struct __attribute__((packed)) {
  /**
   * @brief Identifier of the proto message.
   */
  uint32_t proto_tag;

  /**
   * @brief Sequence number of this message.
   */
  uint8_t send_seq_num;

  /**
   * @brief Sequence number of the message being acknowledged.
   */
  uint8_t ack_seq_num;

  /**
   * @brief CRC16 of the message header and data.
   */
  uint16_t crc;

  /**
   * @brief Message protocol related flags (see #uc_msg_hdr_flags_t).
   */
  uint16_t flags;

  /**
   * @brief Reserved for future use.
   */
  uint16_t reserved;

  /**
   * @brief Length of the message data in bytes.
   */
  uint16_t payload_len;
} uc_msg_hdr_t;

/**
 * @brief UC message.
 */
typedef struct __attribute__((packed)) {
  /**
   * @brief Message header.
   */
  uc_msg_hdr_t hdr;

  /**
   * @brief Message payload.
   */
  uint8_t payload[];
} uc_msg_t;

_Static_assert(sizeof(uc_msg_hdr_t) == UC_MSG_HDR_SIZE, "Header size cannot change.");

/**
 * @brief Flags set in the message header.
 */
typedef enum {
  /**
   * @brief Indicates that the last received message is being ACK'd.
   *
   * @details The `ack_seq_num` should be reviewed to see which message
   * is being ACK'd.
   */
  UC_MSG_HDR_FLAGS_ACK = (1 << 0),

  /**
   * @brief Indicates that the last received message is being NACK'd.
   */
  UC_MSG_HDR_FLAGS_NACK = (1 << 1),

  /**
   * @brief Indicates a message payload is encrypted.
   */
  UC_MSG_HDR_FLAGS_ENCRYPTED = (1 << 2),

  /**
   * @brief Used to signal that this is the first message being sent, and so
   * the sequence numbers should be reset.
   */
  UC_MSG_HDR_FLAGS_FIRST_MSG = (1 << 3),

  /**
   * @brief Signals that the message should be ACK'd immediately without
   * waiting for the ACK timeout to expire.
   */
  UC_MSG_HDR_FLAGS_IMMEDIATE = (1 << 4),

  /**
   * @brief Reserved for future use.
   */
  UC_MSG_HDR_FLAGS_RESERVED2 = (1 << 5),

  /**
   * @brief Reserved for future use.
   */
  UC_MSG_HDR_FLAGS_RESERVED3 = (1 << 6),

  /**
   * @brief Reserved for future use.
   */
  UC_MSG_HDR_FLAGS_RESERVED4 = (1 << 7),

  /**
   * @brief Reserved for future use.
   */
  UC_MSG_HDR_FLAGS_RESERVED5 = (1 << 8),

  /**
   * @brief Reserved for future use.
   */
  UC_MSG_HDR_FLAGS_RESERVED6 = (1 << 9),

  /**
   * @brief Reserved for future use.
   */
  UC_MSG_HDR_FLAGS_RESERVED7 = (1 << 10),

  /**
   * @brief Reserved for future use.
   */
  UC_MSG_HDR_FLAGS_RESERVED8 = (1 << 11),

  /**
   * @brief Reserved for future use.
   */
  UC_MSG_HDR_FLAGS_RESERVED9 = (1 << 12),

  /**
   * @brief Reserved for future use.
   */
  UC_MSG_HDR_FLAGS_RESERVED10 = (1 << 13),

  /**
   * @brief Reserved for future use.
   */
  UC_MSG_HDR_FLAGS_RESERVED11 = (1 << 14),

  /**
   * @brief Reserved for future use.
   */
  UC_MSG_HDR_FLAGS_RESERVED12 = (1 << 15),
} uc_msg_hdr_flags_t;

typedef struct {
  /**
   * @brief Pointer to proto fields structure to use for encoding.
   */
  const void* enc_proto_fields;

  /**
   * @brief Size of the decoded proto message type in bytes.
   */
  size_t enc_proto_size;

  /**
   * @brief Pointer to proto fields structure to use for decoding.
   */
  const void* dec_proto_fields;

  /**
   * @brief Size of the decoded proto message type in bytes.
   */
  size_t dec_proto_size;

  /**
   * @brief Sequence number to attach to the next sent message.
   */
  uint8_t send_seq_num;

  /**
   * @brief Sequence number of the last received message.
   */
  uint8_t recv_seq_num;

  /**
   * @brief True if at least one message has been sent.
   */
  bool sent_first_msg;

  /**
   * @brief Buffer used as an intermediary for encoding/decoding protobuf messages.
   */
  uint8_t msg_buffer[UC_MSG_BUFFER_SIZE] __attribute__((aligned(sizeof(uint64_t))));

  /**
   * @brief Mutex used to provide exclusive access to the message buffer.
   */
  rtos_mutex_t msg_mutex;

  /**
   * @brief Offset in the encoded read buffer in which to write encoded data
   * received from the serial interface.
   */
  uint16_t rd_index;

  /**
   * @brief Buffer of received encoded data.
   */
  uint8_t rd_enc_buffer[UC_COBS_RD_ENC_BUFFER_SIZE] __attribute__((aligned(sizeof(uint64_t))));

  /**
   * @brief Mutex for exclusive access to the encoded write buffer.
   */
  rtos_mutex_t wr_mutex;

  /**
   * @brief Buffer of encoded write data.
   */
  uint8_t wr_enc_buffer[UC_COBS_WR_ENC_BUFFER_SIZE] __attribute__((aligned(sizeof(uint64_t))));

  /**
   * @brief Event group used for signaling/waiting on the receipt of an ACK.
   */
  rtos_event_group_t ack_events;

  /**
   * @brief Acknowledgement timer for sending pure ACK messages.
   */
  rtos_timer_t ack_timer;

  /**
   * @brief Callback to invoke to send data.
   */
  uc_send_callback_t send_cb;

  /**
   * @brief Context pointer to pass to the registered callbacks.
   */
  void* context;

  /**
   * @brief Memory pool used for allocating send/response messages.
   */
  mempool_t* mempool;

  /**
   * @brief Semaphore for received protos.
   */
  rtos_semaphore_t recv_sem;

  /**
   * @brief Semaphore for send protos.
   */
  rtos_semaphore_t send_sem;
} uc_state_priv_t;

/**
 * @brief Enumeration of events posted to the UC event group.
 */
typedef enum {
  /**
   * @brief ACK received from recipient.
   */
  UC_EVENT_ACK = (1 << 0),

  /**
   * @brief NACK received from recipient.
   */
  UC_EVENT_NACK = (1 << 1),

  /**
   * @brief ACK was not received before re-transmit timeout expired.
   */
  UC_EVENT_TIMEOUT = (1 << 2),

  /**
   * @brief ACK timer expired.
   */
  UC_EVENT_ACK_TIMER = (1 << 3),
} uc_events_t;

/**
 * @brief Callback passed to #uc_decode() to invoke if a proto is decoded.
 *
 * @param context    User-supplied pointer to pass to the callback.
 * @param proto_tag  Proto tag identifier.
 * @param proto      Pointer to the decoded protobuf.
 * @param proto_len  Length of the decoded protobuf in bytes.
 */
typedef void (*uc_recv_callback_t)(void* context, uint32_t proto_tag, void* proto,
                                   size_t proto_len);

/**
 * @brief Computes the CRC16 of a UC message using CRC-CCITT.
 *
 * @details The CRC is computed over the header and message payload. The CRC in
 * the payload is treated as being `0` for the purposes of computation.
 *
 * @param msg  Pointer to the UC message.
 *
 * @returns CRC16.
 */
uint16_t uc_compute_crc(uc_msg_t* msg);

/**
 * @brief Processes a received message.
 *
 * @param msg               Pointer to the received message.
 * @param msg_len           Total length of the message, @p msg, (including header) in bytes.
 * @param proto_buffer      Buffer to use for decoding a protobuf.
 * @param proto_buffer_len  Length of the @p proto_buffer in bytes.
 * @param recv_callback     Callback to invoke if a proto is decoded.
 * @param context           Pointer to pass to the bound callback.
 *
 @return `UC_ERR_NONE` on success.
 *       `UC_ERR_CRC_MISMATCH` if CRC check fails on received message.
 *       `UC_ERR_SIZE_MISMATCH` if message length differs from length specified in message header.
 *       `UC_ERR_DECODE_TOO_SMALL` if the provided buffer is too small to decode the message.
 *       `UC_ERR_PROTO_DECODE_FAILED` if decoding the protobuf fails.
 */
uc_err_t uc_process_msg(uc_msg_t* msg, size_t msg_len, void* proto_buffer, size_t proto_buffer_len,
                        uc_recv_callback_t recv_callback, void* context);

/**
 * @brief Encodes data to be sent over the serial interface.
 *
 * @param proto_tag  Proto tag identifier.
 * @param proto      Pointer to the protobuf to encode for sending.
 * @param proto_len  Length of the proto message in bytes.
 * @param flags      Flags to set in the message header.
 * @param send_cb    Callback to invoke to send encoded data.
 * @param context    User-supplied pointer to pass to given callback.
 *
 * @return #uc_err_t.
 */
uc_err_t uc_encode(uint32_t proto_tag, void* proto, uint16_t proto_len, uint16_t flags,
                   uc_send_callback_t send_cb, void* context);

/**
 * @brief Attempts to decode data from an input buffer, invoking a callback when
 * a proto is successfully decoded.
 *
 * @details On return, the @p bytes_consumed indicates the number of bytes
 * consumed from the input buffer @p data. This function will return without
 * having read @p data_len bytes in the case of an error. The caller is
 * responsible for handling that, which may include a successive call with
 * the remaining data.
 *
 * @param[in]  data            Pointer to the received read data.
 * @param[in]  data_len        Length of the @p data in bytes.
 * @param[in]  recv_callback   Callback to invoke if a proto is decoded.
 * @param[in]  context         Pointer to pass to the bound callback.
 * @param[out] bytes_consumed  Indicates the number of bytes consumed from @p data.
 *
 * @return #uc_err_t.
 */
uc_err_t uc_decode(const uint8_t* data, uint32_t data_len, uc_recv_callback_t recv_callback,
                   void* context, uint32_t* bytes_consumed);

/**
 * @brief Initializes the UC library for the specific mode it is being used in.
 *
 * @param enc_proto_fields  Pointer to the proto fields to use for encoding messages.
 * @param enc_proto_size    Size of the encoded proto message type in bytes.
 * @param dec_proto_fields  Pointer to the proto fields to use for decoding messages.
 * @param dec_proto_size    Size of the decoded proto message type in bytes.
 * @param send_cb           Callback to invoke to send data in #uc_send() and #uc_ack().
 * @param context           Context to pass to the @p send_cb.
 */
void uc_init_mode(const void* enc_proto_fields, size_t enc_proto_size, const void* dec_proto_fields,
                  size_t dec_proto_size, uc_send_callback_t send_cb, void* context);
