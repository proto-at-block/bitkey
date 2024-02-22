#include "nfc_task.h"

#include "FreeRTOS.h"
#include "assert.h"
#include "attributes.h"
#include "hal_nfc.h"
#include "log.h"
#include "mempool.h"
#include "rtos.h"
#include "secutils.h"
#include "sysevent.h"
#include "t4t.h"
#include "wallet.pb.h"
#include "wca.h"

#define PROTO_RESPONSE_SEMAPHORE_TIMEOUT_MS (7000)

static struct {
  rtos_semaphore_t rfal_sem;
  rtos_semaphore_t proto_response_sem;
  uint32_t delay_ms;
  rtos_thread_t* isr_thead_handle;
  rtos_thread_t* thread_handle;
  mempool_t* mempool;
} nfc_priv SHARED_TASK_DATA = {
  .rfal_sem = {0},
  .delay_ms = 10,
  .isr_thead_handle = NULL,
  .thread_handle = NULL,
};

static void timer_callback(rtos_timer_handle_t UNUSED(timer)) {
  rtos_semaphore_give(&nfc_priv.rfal_sem);
}

static bool route_nfc_message(uint8_t* in, uint32_t in_len, uint8_t* out, uint32_t* out_len) {
  ASSERT(in && out);

  // Insert a glitch before command handlers to prevent glitching on user-provided input
  secure_glitch_random_delay();

  if (wca_is_valid(in, in_len)) {
    return wca_handle_command(in, in_len, out, out_len);
  } else if (t4t_is_valid(in, in_len)) {
    return t4t_handle_command(in, in_len, out, out_len);
  } else {
    return false;
  }
}

static bool proto_sem_take(void) {
  return rtos_semaphore_take(&nfc_priv.proto_response_sem, PROTO_RESPONSE_SEMAPHORE_TIMEOUT_MS);
}

static bool proto_sem_give(void) {
  return rtos_semaphore_give(&nfc_priv.proto_response_sem);
}

NO_OPTIMIZE void nfc_isr_thread(void* UNUSED(args)) {
  SECURE_ASSERT(rtos_thread_is_privileged());

  for (;;) {
    hal_nfc_wfi();
    // We handle interrupts in a dedicated thread (as opposed to an ISR) because the underlying
    // library reads the ST25's interrupt register over I2C.
    hal_nfc_handle_interrupts();
    rtos_semaphore_give(&nfc_priv.rfal_sem);
  }
}

NO_OPTIMIZE void nfc_thread(void* UNUSED(args)) {
  sysevent_wait(SYSEVENT_POWER_READY, true);
  hal_nfc_init(&timer_callback);

  nfc_priv.isr_thead_handle = rtos_thread_create(nfc_isr_thread, NULL, RTOS_THREAD_PRIORITY_HIGHEST,
                                                 RTOS_STATIC_STACK_DEPTH_DEFAULT);
  ASSERT(nfc_priv.isr_thead_handle);

  SECURE_DO_ONCE({ rtos_thread_reset_privilege(); });  // drop privs after setting up other thread
  SECURE_ASSERT(rtos_thread_is_privileged() == false);

  for (;;) {
    rtos_semaphore_take(&nfc_priv.rfal_sem, nfc_priv.delay_ms);
    hal_nfc_worker(&route_nfc_message);
  }
}

void nfc_task_create(void) {
  nfc_priv.thread_handle = rtos_thread_create(nfc_thread, NULL, RTOS_THREAD_PRIORITY_HIGH, 2048);
  ASSERT(nfc_priv.thread_handle);

#define REGIONS(X)                                    \
  X(nfc_pool, cmd_protos, sizeof(fwpb_wallet_cmd), 4) \
  X(nfc_pool, rsp_protos, sizeof(fwpb_wallet_rsp), 4)
  nfc_priv.mempool = mempool_create(nfc_pool);
#undef REGIONS

  rtos_semaphore_create_counting(&nfc_priv.rfal_sem, HAL_NFC_MAX_TIMERS, HAL_NFC_MAX_TIMERS);
  rtos_semaphore_create(&nfc_priv.proto_response_sem);

  wca_api_t api = {
    .mempool = nfc_priv.mempool,
    .sem_take = &proto_sem_take,
    .sem_give = &proto_sem_give,
  };
  wca_init(&api);
}
