#include "nfc_control.h"

#include "attributes.h"
#include "hal_nfc.h"
#include "log.h"
#include "rtos.h"

typedef struct {
  bool active;
  uint32_t expiry_ms;   // Absolute systime of expiry. 0 = no timeout.
  uint32_t generation;  // Incremented on each reuse; embedded in the token.
} nfc_disable_slot_t;

// Token layout: low 2 bits = slot index, upper 30 bits = generation.
#define TOKEN_SLOT(token)     ((token) & (NFC_CONTROL_MAX_TOKENS - 1))
#define TOKEN_GEN(token)      ((token) >> 2)
#define MAKE_TOKEN(slot, gen) (((gen) << 2) | (slot))

static struct {
  rtos_mutex_t lock;
  nfc_disable_slot_t slots[NFC_CONTROL_MAX_TOKENS];
  hal_nfc_mode_t saved_mode;  // Mode to restore when all tokens are released.
  volatile bool disabled;     // True while at least one token is active.
} nfc_ctrl SHARED_TASK_DATA = {0};

static void expire_slots_locked(void);
static bool any_active_locked(void);

void nfc_control_init(void) {
  rtos_mutex_create(&nfc_ctrl.lock);
}

nfc_disable_token_t nfc_disable(uint32_t timeout_ms) {
  rtos_mutex_lock(&nfc_ctrl.lock);

  uint32_t slot = NFC_CONTROL_MAX_TOKENS;
  for (uint32_t i = 0; i < NFC_CONTROL_MAX_TOKENS; i++) {
    if (!nfc_ctrl.slots[i].active) {
      slot = i;
      break;
    }
  }

  if (slot == NFC_CONTROL_MAX_TOKENS) {
    LOGW("No NFC disable slots available");
    rtos_mutex_unlock(&nfc_ctrl.lock);
    return NFC_CONTROL_INVALID_TOKEN;
  }

  if (!nfc_ctrl.disabled) {
    nfc_ctrl.saved_mode = hal_nfc_get_mode();
    nfc_ctrl.disabled = true;
  }

  nfc_ctrl.slots[slot].active = true;
  nfc_ctrl.slots[slot].expiry_ms = (timeout_ms > 0) ? rtos_thread_systime() + timeout_ms : 0;
  nfc_ctrl.slots[slot].generation++;

  hal_nfc_set_mode(HAL_NFC_MODE_NONE);

  nfc_disable_token_t token = MAKE_TOKEN(slot, nfc_ctrl.slots[slot].generation);
  rtos_mutex_unlock(&nfc_ctrl.lock);
  return token;
}

void nfc_enable(nfc_disable_token_t token) {
  if (token == NFC_CONTROL_INVALID_TOKEN) {
    return;
  }

  uint32_t slot = TOKEN_SLOT(token);
  if (slot >= NFC_CONTROL_MAX_TOKENS) {
    return;
  }

  rtos_mutex_lock(&nfc_ctrl.lock);

  // Reject stale tokens: the slot may have expired and been reused.
  if (!nfc_ctrl.slots[slot].active || nfc_ctrl.slots[slot].generation != TOKEN_GEN(token)) {
    rtos_mutex_unlock(&nfc_ctrl.lock);
    return;
  }

  nfc_ctrl.slots[slot].active = false;

  if (nfc_ctrl.disabled && !any_active_locked()) {
    hal_nfc_set_mode(nfc_ctrl.saved_mode);
    nfc_ctrl.disabled = false;
  }

  rtos_mutex_unlock(&nfc_ctrl.lock);
}

void nfc_control_poll(void) {
  if (!nfc_ctrl.disabled) {
    return;
  }

  rtos_mutex_lock(&nfc_ctrl.lock);

  expire_slots_locked();

  if (!any_active_locked()) {
    hal_nfc_set_mode(nfc_ctrl.saved_mode);
    nfc_ctrl.disabled = false;
  }

  rtos_mutex_unlock(&nfc_ctrl.lock);
}

static void expire_slots_locked(void) {
  uint32_t now = rtos_thread_systime();
  for (uint32_t i = 0; i < NFC_CONTROL_MAX_TOKENS; i++) {
    if (nfc_ctrl.slots[i].active && nfc_ctrl.slots[i].expiry_ms != 0) {
      if ((int32_t)(now - nfc_ctrl.slots[i].expiry_ms) >= 0) {
        nfc_ctrl.slots[i].active = false;
      }
    }
  }
}

static bool any_active_locked(void) {
  for (uint32_t i = 0; i < NFC_CONTROL_MAX_TOKENS; i++) {
    if (nfc_ctrl.slots[i].active) {
      return true;
    }
  }
  return false;
}
