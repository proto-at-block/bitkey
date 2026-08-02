/**
 * @file device_state.c
 * @brief Centralized device/emulator state management for core-sim
 *
 * State is divided into persistent (survives process restart) and transient:
 * - Persistent: auth_mode
 * - Transient: authenticated, auth_expiry, enrollment_in_progress
 *
 * Note: Unlock secret persistence is now handled by real unlock_storage.c
 * which persists to wallet_fs via LittleFS.
 * Onboarding completion is derived from wallet/unlock/bio storage, matching
 * firmware behavior instead of shadowing that state in simulator memory.
 */

#include "device_state.h"

#include "auth.h"
#include "generated/stdio_auth_check.h"
#include "handlers.h"
#include "rtos_thread.h"
#include "secutils.h"
#include "sim_persistence.h"

// For onboarding_wipe_state() implementation
#include "bio.h"
#include "kv.h"
#include "unlock.h"
#include "wallet.h"
#include "wallet_emulator.h"

#include <string.h>

#define STATE_FILE    "device_state.bin"
#define STATE_VERSION 3

typedef struct {
  uint32_t version;
  emu_auth_mode_t auth_mode;
} persistent_state_t;

static uint32_t g_simulated_time_offset_ms = 0;

static emulator_state_t g_state = {
  .allow_enrollment = true,
  .auth_mode = EMU_AUTH_MODE_INSTANT,
};

static void save_persistent_state(void) {
  if (!sim_persistence_enabled()) {
    return;
  }
  persistent_state_t ps = {
    .version = STATE_VERSION,
    .auth_mode = g_state.auth_mode,
  };
  sim_persistence_save(STATE_FILE, &ps, sizeof(ps));
}

static bool load_persistent_state(void) {
  if (!sim_persistence_enabled()) {
    return false;
  }
  persistent_state_t ps;
  if (!sim_persistence_load(STATE_FILE, &ps, sizeof(ps))) {
    return false;
  }
  if (ps.version != STATE_VERSION) {
    return false;
  }
  g_state.auth_mode = ps.auth_mode;
  return true;
}

bool emu_state_init(void) {
  sim_persistence_init();
  bool loaded = load_persistent_state();
  if (loaded) {
    LOG("Loaded persistent device state");
  }
  return loaded;
}

void emu_state_save(void) {
  save_persistent_state();
}

void emu_state_wipe(void) {
  sim_persistence_wipe_all();
}

/* Helper: safely copy a label string with null termination */
static void copy_label(char* dest, const char* src) {
  if (src) {
    strncpy(dest, src, EMU_FINGERPRINT_LABEL_SIZE - 1);
    dest[EMU_FINGERPRINT_LABEL_SIZE - 1] = '\0';
  } else {
    dest[0] = '\0';
  }
}

emulator_state_t* emu_state_get(void) {
  return &g_state;
}

void emu_state_reset(void) {
  memset(&g_state, 0, sizeof(g_state));
  g_state.allow_enrollment = true;
  g_state.auth_mode = EMU_AUTH_MODE_INSTANT;
  g_simulated_time_offset_ms = 0;
  save_persistent_state();
}

bool emu_get_authenticated(void) {
  /* Query real auth library state - this ensures lock_device is reflected */
  secure_bool_t lib_auth = is_authenticated();
  bool is_authed = (lib_auth == SECURE_TRUE);

  /* Sync local state if out of sync (auth library is source of truth) */
  if (g_state.authenticated != is_authed) {
    g_state.authenticated = is_authed;
    if (!is_authed) {
      g_state.auth_expiry_timestamp = 0;
    }
  }

  return is_authed;
}

void emu_set_authenticated(bool authenticated) {
  g_state.authenticated = authenticated;
  if (authenticated) {
    /* In REALISTIC mode, set auth expiry timestamp */
    if (g_state.auth_mode == EMU_AUTH_MODE_REALISTIC) {
      g_state.auth_expiry_timestamp = emu_get_current_time() + EMU_AUTH_EXPIRY_MS;
    }
    /* Also update real auth library state for auth_task */
    auth_authenticate_biometrics(0);
  } else {
    g_state.auth_expiry_timestamp = 0;
    /* Also update real auth library state for auth_task */
    deauthenticate_without_animation();
  }
}

bool emu_get_onboarding_complete(void) {
  bool unlock_secret_setup = false;
  if (unlock_secret_exists(&unlock_secret_setup) != UNLOCK_OK) {
    unlock_secret_setup = false;
  }

  return wallet_is_initialized() || unlock_secret_setup || bio_fingerprint_exists();
}

bool emu_get_allow_enrollment(void) {
  return g_state.allow_enrollment;
}

void emu_set_allow_enrollment(bool allow) {
  g_state.allow_enrollment = allow;
}

void emu_set_timestamp(uint32_t timestamp) {
  g_state.timestamp = timestamp;
}

uint32_t emu_get_timestamp(void) {
  return g_state.timestamp;
}

void emu_enrollment_start(uint8_t index, const char* label) {
  g_state.enrollment_in_progress = true;
  g_state.enrollment_index = index;
  g_state.enrollment_pass_count = 0;
  g_state.enrollment_fail_count = 0;
  copy_label(g_state.enrollment_label, label);
}

void emu_enrollment_cancel(void) {
  g_state.enrollment_in_progress = false;
  g_state.enrollment_index = 0;
  g_state.enrollment_label[0] = '\0';
  g_state.enrollment_pass_count = 0;
  g_state.enrollment_fail_count = 0;
}

bool emu_enrollment_in_progress(void) {
  return g_state.enrollment_in_progress;
}

void emu_enrollment_add_pass(void) {
  if (g_state.enrollment_in_progress) {
    g_state.enrollment_pass_count++;
  }
}

void emu_enrollment_add_fail(void) {
  if (g_state.enrollment_in_progress) {
    g_state.enrollment_fail_count++;
  }
}

uint32_t emu_enrollment_get_pass_count(void) {
  return g_state.enrollment_pass_count;
}

uint32_t emu_enrollment_get_fail_count(void) {
  return g_state.enrollment_fail_count;
}

bool emu_enrollment_complete(void) {
  /* Check if enough passes have been collected */
  return g_state.enrollment_in_progress &&
         g_state.enrollment_pass_count >= emu_enrollment_required_passes();
}

bool device_state_check_command_auth(uint32_t proto_tag) {
  bool is_onboarded = emu_get_onboarding_complete();
  bool is_authenticated = emu_get_authenticated();
  return stdio_check_auth(proto_tag, is_onboarded, is_authenticated);
}

bool device_state_build_unauth_response(uint32_t proto_tag, uint8_t* rsp, uint32_t* rsp_size) {
  fwpb_wallet_rsp response = fwpb_wallet_rsp_init_default;
  response.which_msg = proto_tag;
  response.status = fwpb_status_UNAUTHENTICATED;
  return encode_wallet_response(rsp, rsp_size, *rsp_size, &response);
}

void emu_set_auth_mode(emu_auth_mode_t mode) {
  g_state.auth_mode = mode;
  g_state.auth_expiry_timestamp = 0;
  g_state.last_bio_fail_timestamp = 0;
  save_persistent_state();
}

emu_auth_mode_t emu_get_auth_mode(void) {
  return g_state.auth_mode;
}

static bool is_realistic_mode(void) {
  return g_state.auth_mode == EMU_AUTH_MODE_REALISTIC;
}

uint32_t emu_get_current_time(void) {
  return rtos_thread_systime() + g_simulated_time_offset_ms;
}

void emu_advance_time(uint32_t ms) {
  g_simulated_time_offset_ms += ms;
}

void emu_reset_time(void) {
  g_simulated_time_offset_ms = 0;
}

bool emu_auth_is_expired(void) {
  return is_realistic_mode() && g_state.authenticated && g_state.auth_expiry_timestamp != 0 &&
         emu_get_current_time() >= g_state.auth_expiry_timestamp;
}

void emu_auth_refresh_expiry(void) {
  if (is_realistic_mode() && g_state.authenticated) {
    g_state.auth_expiry_timestamp = emu_get_current_time() + EMU_AUTH_EXPIRY_MS;
  }
}

bool emu_bio_rate_limit_check(void) {
  if (!is_realistic_mode() || g_state.last_bio_fail_timestamp == 0) {
    return true;
  }
  return emu_get_current_time() >= g_state.last_bio_fail_timestamp + EMU_BIO_RATE_LIMIT_MS;
}

void emu_bio_record_fail(void) {
  g_state.last_bio_fail_timestamp = emu_get_current_time();
}

uint32_t emu_enrollment_required_passes(void) {
  return is_realistic_mode() ? EMU_ENROLLMENT_REQUIRED_PASSES : 3;
}

// ========================================================================
// Onboarding API - overrides lib/onboarding for core-sim
// ========================================================================

secure_bool_t onboarding_complete(void) {
  return emu_get_onboarding_complete() ? SECURE_TRUE : SECURE_FALSE;
}

void onboarding_wipe_state(void) {
  kv_wipe_state();
  wallet_remove_files();
  unlock_wipe_state();
  bio_wipe_state();
  wallet_fs_wipe();
  LOG("onboarding_wipe_state: complete");
}
