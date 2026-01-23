#include "uc.h"

#include "assert.h"
#include "bitlog.h"
#include "cobs.h"
#include "log.h"
#include "mempool.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include "rtos.h"
#include "uc_impl.h"
#include "uc_route_impl.h"
#include "uxc.pb.h"

#include <limits.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

uc_state_priv_t _uc_state_priv SHARED_TASK_DATA = {0};

static void* _uc_get_msg_buffer(size_t* msg_buffer_size) {
  ASSERT(msg_buffer_size != NULL);
  ASSERT(rtos_mutex_lock(&_uc_state_priv.msg_mutex));
  memset(_uc_state_priv.msg_buffer, 0u, sizeof(_uc_state_priv.msg_buffer));
  *msg_buffer_size = sizeof(_uc_state_priv.msg_buffer);
  return (void*)_uc_state_priv.msg_buffer;
}

static uint32_t _uc_get_proto_tag(void* proto) {
  ASSERT(proto != NULL);
  return ((UC_CFG_ENC_PROTO_TYPE*)proto)->which_msg;
}

static void _uc_put_msg_buffer(void* buffer) {
  (void)buffer;
  ASSERT(rtos_mutex_unlock(&_uc_state_priv.msg_mutex));
}

static uc_err_t _uc_cobs_to_err(cobs_ret_t ret) {
  switch (ret) {
    case COBS_RET_SUCCESS:
      return UC_ERR_NONE;

    case COBS_RET_ERR_BAD_ARG:
      return UC_ERR_INVALID_ARG;

    case COBS_RET_ERR_EXHAUSTED:
      return UC_ERR_DECODE_TOO_SMALL;

    case COBS_RET_ERR_BAD_PAYLOAD:
      /* 'break' intentionally omitted. */

    default:
      return UC_ERR_DECODE_FAILED;
  }
}

static void _uc_ack_timer_expired(rtos_timer_handle_t handle) {
  (void)handle;
  rtos_event_group_set_bits(&_uc_state_priv.ack_events, UC_EVENT_ACK_TIMER);
}

static void _uc_on_proto(void* context, uint32_t proto_tag, void* proto, size_t proto_len) {
  (void)context;

  // Attempt to allocate memory for the received proto so we can free the read
  // buffer.
  ASSERT(rtos_semaphore_take(&_uc_state_priv.recv_sem, UC_SEM_TIMEOUT_MS));
  void* mem = mempool_alloc(_uc_state_priv.mempool, proto_len);
  ASSERT(mem != NULL);

  memcpy(mem, proto, proto_len);

  // Route allocated message. Either `uc_route()` or the registered proto tag
  // listener will free the associated memory.
  uc_route(proto_tag, mem);
}

static bool _uc_send(void* proto, uint16_t flags) {
  if (_uc_state_priv.send_cb == NULL) {
    return false;
  }

  const uint32_t proto_tag = _uc_get_proto_tag(proto);
  uc_err_t err = uc_encode(proto_tag, proto, _uc_state_priv.enc_proto_size, flags,
                           _uc_state_priv.send_cb, _uc_state_priv.context);

  uc_free_send_proto(proto);

  if (err != UC_ERR_NONE) {
    BITLOG_EVENT(uc_err, err);
    LOGW("uc_send failed: proto_tag=0x%lx err=%d", proto_tag, err);
    return false;
  }
  return true;
}

uint16_t uc_compute_crc(uc_msg_t* msg) {
  uint16_t crc = UC_CRC_SEED;

  uc_msg_hdr_t hdr = {0};
  memcpy(&hdr, &msg->hdr, sizeof(hdr));
  hdr.crc = 0;

  // Iterate through each of the bytes of data being transmitted in order to
  // compute the CRC across the header and payload.
  uint8_t* input_buffers[] = {(uint8_t*)&hdr, msg->payload};
  uint16_t input_buffer_lens[] = {sizeof(hdr), hdr.payload_len};
  for (uint8_t i = 0; i < 2; i++) {
    uint8_t* data = input_buffers[i];
    uint16_t data_len = input_buffer_lens[i];
    for (uint16_t i = 0; i < data_len; i++) {
      crc ^= (uint16_t)data[i] << 8;
      for (size_t j = 0; j < 8u; j++) {
        if (crc & 0x8000u) {
          crc = (crc << 1) ^ UC_CRC_POLYNOMIAL;
        } else {
          crc <<= 1;
        }
      }
    }
  }
  return crc;
}

uc_err_t uc_process_msg(uc_msg_t* msg, size_t msg_len, void* proto_buffer, size_t proto_buffer_len,
                        uc_recv_callback_t recv_callback, void* context) {
  ASSERT(msg != NULL);

  uc_msg_hdr_t* hdr = &msg->hdr;

  do {
    if (msg_len != (sizeof(*hdr) + hdr->payload_len)) {
      // Size mismatch.
      return UC_ERR_SIZE_MISMATCH;
    }

    if (hdr->crc != uc_compute_crc(msg)) {
      // CRC mismatch.
      return UC_ERR_CRC_MISMATCH;
    }

    if (proto_buffer_len < hdr->payload_len) {
      // Buffer is too small to fit the entire decoded message.
      return UC_ERR_DECODE_TOO_SMALL;
    }

    if (((hdr->flags & UC_MSG_HDR_FLAGS_ACK) || (hdr->flags & UC_MSG_HDR_FLAGS_NACK)) &&
        (hdr->ack_seq_num >= _uc_state_priv.send_seq_num)) {
      // Notify the blocked task (if any) of an ACK/NACK.
      const uint32_t flags = (hdr->flags & UC_MSG_HDR_FLAGS_ACK ? UC_EVENT_ACK : UC_EVENT_NACK);
      rtos_event_group_set_bits(&_uc_state_priv.ack_events, flags);
    }

    if (hdr->flags & UC_MSG_HDR_FLAGS_ENCRYPTED) {
      // TODO(W-13812): Support decryption (in-place).
    }

    if ((hdr->flags & UC_MSG_HDR_FLAGS_FIRST_MSG) && (hdr->send_seq_num == 1)) {
      // Reset the receive sequence number on the first message.
      _uc_state_priv.recv_seq_num = 0;
    }

    if ((_uc_state_priv.recv_seq_num != hdr->send_seq_num) && (hdr->payload_len > 0)) {
      ASSERT(_uc_state_priv.dec_proto_fields != NULL);

      // Update the received sequence number.
      _uc_state_priv.recv_seq_num = hdr->send_seq_num;

      // Start timer to send a pure ACK.
      if (hdr->flags & UC_MSG_HDR_FLAGS_IMMEDIATE) {
        // If the immediate flag is specified, then we must send an ACK ASAP.
        if (!rtos_timer_expired(&_uc_state_priv.ack_timer)) {
          rtos_timer_stop(&_uc_state_priv.ack_timer);
        }

        // Immediately set the ACK flag.
        rtos_event_group_set_bits(&_uc_state_priv.ack_events, UC_EVENT_ACK_TIMER);
      } else {
        if (!rtos_timer_expired(&_uc_state_priv.ack_timer)) {
          // If timer is running, then just restart it.
          rtos_timer_restart(&_uc_state_priv.ack_timer);
        } else {
          rtos_timer_start(&_uc_state_priv.ack_timer, UC_ACK_TIMEOUT_MS);
        }
      }

      // Message has payload data, so decode it.
      pb_istream_t istream;
      istream = pb_istream_from_buffer(msg->payload, hdr->payload_len);
      if (pb_decode(&istream, _uc_state_priv.dec_proto_fields, proto_buffer)) {
        const uint32_t proto_tag = hdr->proto_tag;

        // Must return the message buffer before calling the callback.
        _uc_put_msg_buffer(msg);

        if (recv_callback != NULL) {
          recv_callback(context, proto_tag, proto_buffer, _uc_state_priv.dec_proto_size);
        }
      } else {
        return UC_ERR_PROTO_DECODE_FAILED;
      }
    } else {
      // Either we:
      //   1. Received a duplicate message.
      //   2. Received an ACK message.
      // In either case, no process is required, so we just return the
      // message buffer.
      _uc_put_msg_buffer(msg);
    }
  } while (0);

  return UC_ERR_NONE;
}

void uc_init_mode(const void* enc_proto_fields, const size_t enc_proto_size,
                  const void* dec_proto_fields, const size_t dec_proto_size,
                  uc_send_callback_t send_cb, void* context) {
  ASSERT(send_cb != NULL);

  _uc_state_priv.enc_proto_fields = enc_proto_fields;
  _uc_state_priv.dec_proto_fields = dec_proto_fields;
  _uc_state_priv.dec_proto_size = dec_proto_size;
  _uc_state_priv.enc_proto_size = enc_proto_size;

  _uc_state_priv.send_seq_num = 0;
  _uc_state_priv.recv_seq_num = 0;
  _uc_state_priv.rd_index = 0;
  _uc_state_priv.send_cb = send_cb;
  _uc_state_priv.context = context;

  rtos_mutex_create(&_uc_state_priv.wr_mutex);
  rtos_mutex_create(&_uc_state_priv.msg_mutex);
  rtos_event_group_create(&_uc_state_priv.ack_events);
  rtos_timer_create_static(&_uc_state_priv.ack_timer, _uc_ack_timer_expired);
  rtos_semaphore_create_counting(&_uc_state_priv.recv_sem, UC_MSG_MAX_NUM, UC_MSG_MAX_NUM);
  rtos_semaphore_create_counting(&_uc_state_priv.send_sem, UC_MSG_MAX_NUM, UC_MSG_MAX_NUM);
}

void uc_init(uc_send_callback_t send_cb, void* context) {
#define REGIONS(X)                                                       \
  X(uc_pool, send_protos, sizeof(UC_CFG_ENC_PROTO_TYPE), UC_MSG_MAX_NUM) \
  X(uc_pool, recv_protos, sizeof(UC_CFG_DEC_PROTO_TYPE), UC_MSG_MAX_NUM)
  _uc_state_priv.mempool = mempool_create(uc_pool);
#undef REGIONS

  uc_init_mode(UC_CFG_ENC_PROTO_FIELDS, sizeof(UC_CFG_ENC_PROTO_TYPE), UC_CFG_DEC_PROTO_FIELDS,
               sizeof(UC_CFG_DEC_PROTO_TYPE), send_cb, context);
}

bool uc_send(void* proto) {
  return _uc_send(proto, 0);
}

bool uc_send_immediate(void* proto) {
  return _uc_send(proto, UC_MSG_HDR_FLAGS_IMMEDIATE);
}

uc_err_t uc_encode(uint32_t proto_tag, void* proto, uint16_t proto_len, uint16_t flags,
                   uc_send_callback_t send_cb, void* context) {
  // Lock write access (only one thing can write at a given time).
  if (!rtos_mutex_lock(&_uc_state_priv.wr_mutex)) {
    return UC_ERR_MUTEX_FAILED;
  }

  // Grab the message buffer to write to.
  size_t msg_buffer_size;
  uc_msg_t* msg = (uc_msg_t*)_uc_get_msg_buffer(&msg_buffer_size);
  if (proto_len > (msg_buffer_size - sizeof(uc_msg_hdr_t))) {
    // Proto length is too large.
    _uc_put_msg_buffer(msg);
    ASSERT(rtos_mutex_unlock(&_uc_state_priv.wr_mutex));
    return UC_ERR_TOO_LARGE;
  }

  const uint8_t ack_seq_num = _uc_state_priv.recv_seq_num;

  msg->hdr.send_seq_num = ++_uc_state_priv.send_seq_num;
  if (msg->hdr.send_seq_num == 0) {
    msg->hdr.send_seq_num = ++_uc_state_priv.send_seq_num;
  }
  msg->hdr.proto_tag = proto_tag;
  msg->hdr.ack_seq_num = ack_seq_num;
  msg->hdr.flags = UC_MSG_HDR_FLAGS_ACK | flags;
  if ((proto_len > 0) && !_uc_state_priv.sent_first_msg) {
    _uc_state_priv.sent_first_msg = true;
    msg->hdr.flags |= UC_MSG_HDR_FLAGS_FIRST_MSG;
  }

  // TODO(W-13812): Determine if proto needs to be encrypted and then encrypt
  // it in place.
  const bool encrypted = false;
  (void)encrypted;
  if (encrypted) {
    msg->hdr.flags |= UC_MSG_HDR_FLAGS_ENCRYPTED;
  }

  if (proto_len > 0) {
    ASSERT(proto != NULL);
    ASSERT(_uc_state_priv.enc_proto_fields != NULL);

    // Encode protobuf into data buffer.
    pb_ostream_t ostream = pb_ostream_from_buffer(msg->payload, msg_buffer_size - sizeof(msg->hdr));
    if (!pb_encode(&ostream, _uc_state_priv.enc_proto_fields, proto) || !ostream.bytes_written) {
      _uc_put_msg_buffer(msg);
      ASSERT(rtos_mutex_unlock(&_uc_state_priv.wr_mutex));
      return UC_ERR_PROTO_ENCODE_FAILED;
    }

    msg->hdr.payload_len = ostream.bytes_written;
  } else {
    msg->hdr.payload_len = 0;
  }

  // Compute CRC over the message and write it back to the header.
  msg->hdr.crc = uc_compute_crc(msg);

  cobs_enc_ctx_t ctx;
  cobs_ret_t ret =
    cobs_encode_inc_begin(_uc_state_priv.wr_enc_buffer, sizeof(_uc_state_priv.wr_enc_buffer), &ctx);
  if (ret != COBS_RET_SUCCESS) {
    _uc_put_msg_buffer(msg);
    ASSERT(rtos_mutex_unlock(&_uc_state_priv.wr_mutex));
    return _uc_cobs_to_err(ret);
  }

  ret = cobs_encode_inc(&ctx, &msg->hdr, sizeof(msg->hdr));
  if (ret != COBS_RET_SUCCESS) {
    _uc_put_msg_buffer(msg);
    ASSERT(rtos_mutex_unlock(&_uc_state_priv.wr_mutex));
    return _uc_cobs_to_err(ret);
  }

  if (msg->hdr.payload_len) {
    ret = cobs_encode_inc(&ctx, msg->payload, msg->hdr.payload_len);
    if (ret != COBS_RET_SUCCESS) {
      _uc_put_msg_buffer(msg);
      ASSERT(rtos_mutex_unlock(&_uc_state_priv.wr_mutex));
      return _uc_cobs_to_err(ret);
    }
  }

  size_t enc_len;
  ret = cobs_encode_inc_end(&ctx, &enc_len);
  if (ret != COBS_RET_SUCCESS) {
    _uc_put_msg_buffer(msg);
    ASSERT(rtos_mutex_unlock(&_uc_state_priv.wr_mutex));
    return _uc_cobs_to_err(ret);
  }

  // Finally return the message buffer.
  _uc_put_msg_buffer(msg);

  // If the ACK sequence number is still the same, then stop the ACK timer.
  if (_uc_state_priv.recv_seq_num == ack_seq_num) {
    rtos_timer_stop(&_uc_state_priv.ack_timer);
  }

  uint8_t retry_cnt;
  const uint32_t bits = (UC_EVENT_ACK | UC_EVENT_NACK | UC_EVENT_TIMEOUT);
  for (retry_cnt = 0; retry_cnt < UC_RETRANSMIT_MAX_COUNT; retry_cnt++) {
    (void)rtos_event_group_clear_bits(&_uc_state_priv.ack_events, bits);

    uint8_t* wr_buf = _uc_state_priv.wr_enc_buffer;
    size_t remaining_bytes = enc_len;
    while (remaining_bytes > 0) {
      const size_t wr_size = send_cb(context, wr_buf, remaining_bytes);
      if (wr_size == 0) {
        ASSERT(rtos_mutex_unlock(&_uc_state_priv.wr_mutex));
        return UC_ERR_WR_FAILED;
      }

      remaining_bytes -= (wr_size > remaining_bytes ? remaining_bytes : wr_size);
      wr_buf += wr_size;
    }

    if (proto_len == 0) {
      // Pure ACK, so do not wait.
      break;
    }

    const uint32_t flags =
      rtos_event_group_wait_bits(&_uc_state_priv.ack_events, bits, true /* clear on exit */,
                                 false /* wait for any bit */, UC_RETRANSMIT_TIMEOUT_MS);
    if (flags & UC_EVENT_ACK) {
      // Message received and ACK'd.
      break;
    }
  }

  ASSERT(rtos_mutex_unlock(&_uc_state_priv.wr_mutex));
  return (retry_cnt < UC_RETRANSMIT_MAX_COUNT ? UC_ERR_NONE : UC_ERR_MAX_RETRANSMITS);
}

uc_err_t uc_ack(void) {
  (void)rtos_event_group_clear_bits(&_uc_state_priv.ack_events, UC_EVENT_ACK_TIMER);
  return uc_encode(0, NULL, 0, 0, _uc_state_priv.send_cb, _uc_state_priv.context);
}

void uc_handle_data(const uint8_t* data, uint32_t data_len, void* context) {
  while (data_len > 0) {
    uint32_t bytes_consumed;
    uc_err_t err = uc_decode(data, data_len, _uc_on_proto, context, &bytes_consumed);

    if (err != UC_ERR_NONE) {
      BITLOG_EVENT(uc_err, err);
    }

    data += bytes_consumed;
    data_len -= (bytes_consumed > data_len ? data_len : bytes_consumed);
  }
}

uc_err_t uc_decode(const uint8_t* data, uint32_t data_len, uc_recv_callback_t recv_callback,
                   void* context, uint32_t* bytes_consumed) {
  if ((data == NULL) || (data_len == 0) || (bytes_consumed == NULL)) {
    return UC_ERR_INVALID_ARG;
  }

  *bytes_consumed = 0;

  uc_err_t err = UC_ERR_NONE;
  while (data_len > 0) {
    ASSERT(_uc_state_priv.rd_index < sizeof(_uc_state_priv.rd_enc_buffer));

    const uint8_t byte = *data;
    data++;
    data_len--;
    *bytes_consumed += 1;

    _uc_state_priv.rd_enc_buffer[_uc_state_priv.rd_index++] = byte;
    if (byte == COBS_FRAME_DELIMITER) {
      // End of frame found, so decode and process data.
      size_t msg_size;

      size_t msg_buffer_size;
      uint8_t* msg_buffer = _uc_get_msg_buffer(&msg_buffer_size);
      const cobs_ret_t ret = cobs_decode(_uc_state_priv.rd_enc_buffer, _uc_state_priv.rd_index,
                                         msg_buffer, msg_buffer_size, &msg_size);
      if (ret == COBS_RET_SUCCESS) {
        err = uc_process_msg((uc_msg_t*)msg_buffer, msg_size, _uc_state_priv.rd_enc_buffer,
                             sizeof(_uc_state_priv.rd_enc_buffer), recv_callback, context);
        if (err != UC_ERR_NONE) {
          // If `uc_process_msg()` fails, the message buffer will not have been
          // free'd, so we need to free it ourselves.
          _uc_put_msg_buffer(msg_buffer);
          break;
        }
      } else {
        // Ensure message buffer is freed if COBS decode fails.
        _uc_put_msg_buffer(msg_buffer);
      }

      // Clear encoded buffer after processing to prevent data from sitting in
      // memory.
      memset(_uc_state_priv.rd_enc_buffer, 0x00u, _uc_state_priv.rd_index);
      _uc_state_priv.rd_index = 0;
    }
  }

  return err;
}

void uc_idle(void* context) {
  (void)context;
  if (rtos_event_group_get_bits(&_uc_state_priv.ack_events) & UC_EVENT_ACK_TIMER) {
    uc_ack();
  }
}

void* uc_alloc_send_proto(void) {
  ASSERT(rtos_semaphore_take(&_uc_state_priv.send_sem, UC_SEM_TIMEOUT_MS));
  return mempool_alloc(_uc_state_priv.mempool, _uc_state_priv.enc_proto_size);
}

void uc_free_send_proto(void* proto_buffer) {
  ASSERT(proto_buffer != NULL);
  mempool_free(_uc_state_priv.mempool, proto_buffer);
  ASSERT(rtos_semaphore_give(&_uc_state_priv.send_sem));
}

void uc_free_recv_proto(void* proto_buffer) {
  ASSERT(proto_buffer != NULL);
  mempool_free(_uc_state_priv.mempool, proto_buffer);
  ASSERT(rtos_semaphore_give(&_uc_state_priv.recv_sem));
}
