#include "criterion_test_utils.h"
#include "fff.h"
#include "ipc.h"
#include "ipc_impl.h"
#include "ringbuf.h"
#include "secutils.h"

#include <criterion/criterion.h>

#include <stdio.h>

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VOID_FUNC(refresh_auth);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);
FAKE_VOID_FUNC(rtos_timer_create_static, rtos_timer_t*, rtos_timer_callback_t);
FAKE_VOID_FUNC(rtos_timer_start, rtos_timer_t*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_stop, rtos_timer_t*);

secure_bool_t onboarding_auth_is_setup(void) {
  return SECURE_TRUE;
}
secure_bool_t is_authenticated(void) {
  return SECURE_TRUE;
}

static ringbuf_t ringbuf;
static uint8_t backing_buf[4096];

static ipc_port_t foo_port = 0;

static uint8_t fake_rtos_queue[8];

static mempool_t* pool;

void init(void) {
#define REGIONS(X)                   \
  X(p, refs, sizeof(ipc_ref_t*), 13) \
  X(p, tiny_msgs, 16, 4)             \
  X(p, small_msgs, 64, 4)            \
  X(p, med_msgs, 256, 3)             \
  X(p, big_msgs, 512, 2)
  pool = mempool_create(p);
#undef REGIONS

  ipc_register_port(
    foo_port, &fake_rtos_queue);  // RTOS queue not actually used in testing, but can't be NULL

  ipc_proto_register_api(pool, NULL, NULL);

  ringbuf.buf = backing_buf;
  ringbuf.max_size = sizeof(backing_buf);
  ringbuf.head = 0;
  ringbuf.tail = 0;
  ringbuf.lock = NULL;
  ringbuf.full = false;
}

bool rtos_queue_send(rtos_queue_t* queue, void* object, uint32_t timeout_ms) {
  (void)ringbuf_push_buf(&ringbuf, object, sizeof(ipc_ref_t));
  return true;
}

bool rtos_queue_recv(rtos_queue_t* queue, void* object, uint32_t timeout_ms) {
  (void)ringbuf_pop_buf(&ringbuf, object, sizeof(ipc_ref_t));
  return true;
}

Test(ipt_test, in_line, .init = init) {
  uint32_t tag = 1;

  char msg[16] = {0};
  memset(msg, 'a', sizeof(msg));

  bool ok = ipc_send(foo_port, msg, sizeof(msg), tag);
  cr_assert(ok);

  ipc_ref_t recv_ref = {0};
  ok = ipc_recv(foo_port, &recv_ref);
  cr_assert(ok);

  cr_assert_eq(recv_ref.tag, tag);
  cr_assert_eq(recv_ref.length, sizeof(msg));
  cr_assert_eq(recv_ref.object, msg);
  cr_util_cmp_buffers(recv_ref.object, msg, sizeof(msg));
}

Test(ipc_test, take_ownership, .init = init) {
  uint32_t tag = 1;

  char msg[16] = {0};
  memset(msg, 'a', sizeof(msg));

  bool ok = ipc_send_cp(foo_port, msg, sizeof(msg), tag);
  cr_assert(ok);

  ipc_ref_t recv_ref = {0};
  ok = ipc_recv(foo_port, &recv_ref);
  cr_assert(ok);

  cr_assert_eq(recv_ref.tag, tag);
  cr_assert_eq(recv_ref.length, sizeof(msg));
  cr_assert_neq(recv_ref.object, msg);
  cr_util_cmp_buffers(recv_ref.object, msg, sizeof(msg));

  ipc_release(&recv_ref);
}
