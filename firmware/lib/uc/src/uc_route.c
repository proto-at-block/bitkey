#include "uc_route.h"

#include "arithmetic.h"
#include "assert.h"
#include "attributes.h"
#include "bitlog.h"
#include "rtos.h"
#include "uc.h"
#include "uc_route_impl.h"

#include <stdbool.h>
#include <stdint.h>

typedef struct {
  uint32_t proto_tag;
  uc_route_callback_t cb;
  void* context;
  bool initialized;
} uc_route_registration_t;

uc_route_registration_t _uc_route_registrations[UC_ROUTE_MAX_NUM_REGISTRATIONS] SHARED_TASK_DATA = {
  {0}};

static void _uc_route_post_to_queue(void* proto, void* context);

void uc_route(uint32_t proto_tag, void* proto) {
  uc_route_registration_t* reg;
  for (uint8_t i = 0; i < ARRAY_SIZE(_uc_route_registrations); i++) {
    reg = &_uc_route_registrations[i];
    if (reg->initialized && (reg->proto_tag == proto_tag)) {
      ASSERT(reg->cb != NULL);
      reg->cb(proto, reg->context);
      return;
    }
  }

  // No registered callback, so just free.
  uc_free_recv_proto(proto);
}

void uc_route_register(uint32_t proto_tag, uc_route_callback_t cb, void* context) {
  ASSERT(cb != NULL);

  uc_route_registration_t* reg;
  for (uint8_t i = 0; i < ARRAY_SIZE(_uc_route_registrations); i++) {
    reg = &_uc_route_registrations[i];
    if (!reg->initialized) {
      reg->initialized = true;
      reg->proto_tag = proto_tag;
      reg->cb = cb;
      reg->context = context;
      return;
    }

    // Validate an existing registration does not exist for the tag.
    ASSERT(reg->proto_tag != proto_tag);
  }

  // Ran out of registration slots.
  abort();
}

void uc_route_register_queue(uint32_t proto_tag, rtos_queue_t* queue) {
  ASSERT(queue != NULL);
  uc_route_register(proto_tag, _uc_route_post_to_queue, queue);
}

void* uc_route_pend_queue(rtos_queue_t* queue) {
  ASSERT(queue != NULL);

  void* proto;
  ASSERT(rtos_queue_recv(queue, (void*)&proto, RTOS_QUEUE_TIMEOUT_MAX));
  ASSERT(proto != NULL);

  return proto;
}

static void _uc_route_post_to_queue(void* proto, void* context) {
  rtos_queue_t* queue = context;
  ASSERT(queue != NULL);
  if (!rtos_queue_send(queue, (void*)&proto, RTOS_QUEUE_TIMEOUT_MAX)) {
    BITLOG_EVENT(uc_err, UC_ERR_Q_MAX);
  }
}
