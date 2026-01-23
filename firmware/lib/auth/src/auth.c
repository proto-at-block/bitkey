#include "auth.h"

#include "attributes.h"
#include "rtos.h"
#include "secutils.h"
#include "sleep.h"

typedef struct {
  secure_bool_t authenticated;
  secure_bool_t allow_fingerprint_enrollment;
  auth_unlock_method_t method;
  uint8_t fingerprint_index;
  rtos_mutex_t lock;
  rtos_timer_t timer;
  auth_callbacks_t callbacks;
  uint32_t expiry_ms;
} auth_ctx_t;

static auth_ctx_t auth SHARED_TASK_DATA = {
  .authenticated = SECURE_FALSE,
  .allow_fingerprint_enrollment = SECURE_FALSE,
  .method = AUTH_METHOD_UNSPECIFIED,
  .fingerprint_index = AUTH_INVALID_FINGERPRINT_INDEX,
  .lock = {0},
  .timer = {.name = "auth"},
  .callbacks = {0},
  .expiry_ms = 0,
};

static NO_OPTIMIZE void auth_timer_callback(rtos_timer_handle_t UNUSED(timer)) {
  SECURE_DO({ deauthenticate(); });
}

void auth_init(auth_config_t config, auth_callbacks_t callbacks) {
  rtos_mutex_create(&auth.lock);
  rtos_timer_create_static(&auth.timer, auth_timer_callback);
  auth.expiry_ms = config.expiry_ms;
  auth.callbacks = callbacks;
}

NO_OPTIMIZE secure_bool_t is_authenticated(void) {
#if AUTOMATED_TESTING
  return SECURE_TRUE;
#else
  rtos_mutex_lock(&auth.lock);
  secure_bool_t result = SECURE_FALSE;
  SECURE_DO_FAILOUT(auth.authenticated == SECURE_TRUE, { result = SECURE_TRUE; });
  rtos_mutex_unlock(&auth.lock);
  return result;
#endif
}

NO_OPTIMIZE secure_bool_t is_allowing_fingerprint_enrollment(void) {
  rtos_mutex_lock(&auth.lock);
  secure_bool_t result = SECURE_FALSE;
  SECURE_DO_FAILOUT(auth.allow_fingerprint_enrollment == SECURE_TRUE, { result = SECURE_TRUE; });
  rtos_mutex_unlock(&auth.lock);
  return result;
}

static NO_OPTIMIZE void do_authenticate(auth_unlock_method_t method, uint8_t fingerprint_index) {
  rtos_mutex_lock(&auth.lock);
  auth.authenticated = SECURE_TRUE;
  auth.method = method;
  auth.fingerprint_index = fingerprint_index;
  rtos_mutex_unlock(&auth.lock);

  // Start/restart the auth expiry timer
  rtos_timer_stop(&auth.timer);
  rtos_timer_start(&auth.timer, auth.expiry_ms);

  // Stop the power timer while authenticated
  sleep_stop_power_timer();
}

NO_OPTIMIZE void auth_authenticate_biometrics(uint8_t fingerprint_index) {
  do_authenticate(AUTH_METHOD_BIOMETRICS, fingerprint_index);
}

NO_OPTIMIZE void auth_authenticate_unlock_secret(void) {
  do_authenticate(AUTH_METHOD_UNLOCK_SECRET, AUTH_INVALID_FINGERPRINT_INDEX);
}

static void do_deauthenticate(bool show_animation) {
  rtos_mutex_lock(&auth.lock);
  secure_bool_t was_authenticated = auth.authenticated;
  auth.authenticated = SECURE_FALSE;
  auth.method = AUTH_METHOD_UNSPECIFIED;
  auth.fingerprint_index = AUTH_INVALID_FINGERPRINT_INDEX;
  auth.allow_fingerprint_enrollment = SECURE_FALSE;
  rtos_mutex_unlock(&auth.lock);

  rtos_timer_stop(&auth.timer);

  // Restart the power timer now that we're locked
  sleep_start_power_timer();

  // Notify task for animation and cleanup
  if (auth.callbacks.on_lock) {
    auth.callbacks.on_lock(show_animation && (was_authenticated == SECURE_TRUE));
  }
}

NO_OPTIMIZE void deauthenticate(void) {
  do_deauthenticate(true);
}

void deauthenticate_without_animation(void) {
  do_deauthenticate(false);
}

NO_OPTIMIZE secure_bool_t refresh_auth(void) {
  secure_bool_t authed = is_authenticated();
  SECURE_IF_FAILOUT(authed == SECURE_TRUE) {
    rtos_timer_stop(&auth.timer);
    rtos_timer_start(&auth.timer, auth.expiry_ms);
    return SECURE_TRUE;
  }
  return SECURE_FALSE;
}

void present_grant_for_fingerprint_enrollment(void) {
  rtos_mutex_lock(&auth.lock);
  auth.allow_fingerprint_enrollment = SECURE_TRUE;
  rtos_mutex_unlock(&auth.lock);
}

auth_unlock_method_t auth_get_unlock_method(void) {
  rtos_mutex_lock(&auth.lock);
  auth_unlock_method_t method = auth.method;
  rtos_mutex_unlock(&auth.lock);
  return method;
}

uint8_t auth_get_fingerprint_index(void) {
  rtos_mutex_lock(&auth.lock);
  uint8_t index = auth.fingerprint_index;
  rtos_mutex_unlock(&auth.lock);
  return index;
}
