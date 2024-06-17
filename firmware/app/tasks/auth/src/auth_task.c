#include "auth_task.h"

#include "animation.h"
#include "assert.h"
#include "attributes.h"
#include "auth.h"
#include "bio.h"
#include "feature_flags.h"
#include "filesystem.h"
#include "hex.h"
#include "ipc.h"
#include "log.h"
#include "onboarding.h"
#include "proto_helpers.h"
#include "rtos.h"
#include "secure_channel.h"
#include "sleep.h"
#include "sysevent.h"
#include "unlock.h"

#define INVALID_FINGERPRINT_INDEX 0xFF

#define RATE_LIMIT_MS      (1000)
#define RATE_LIMIT_FAIL_MS (1000)

typedef struct {
  bool enrollment_in_progress;
  secure_bool_t enroll_ok;
  bio_template_id_t index;
  char label[BIO_LABEL_MAX_LEN];
} enrollment_ctx_t;

typedef enum {
  UNSPECIFIED = 0,
  BIOMETRICS = 1,
  UNLOCK_SECRET = 2,
} unlock_method_t;

typedef struct {
  secure_bool_t authenticated;
  // Below state is only valid if authenticated is true. It's purely informational,
  // and should not be used for any security sensitive operations.
  unlock_method_t method;
  bio_template_id_t fingerprint_index;
} auth_state_t;

// TODO(W-6614): Remove SHARED_TASK_DATA and replace with IPC.
auth_state_t auth_state SHARED_TASK_DATA = {
  .authenticated = SECURE_FALSE,
  .method = UNSPECIFIED,
  .fingerprint_index = BIO_TEMPLATE_ID_INVALID,
};

static struct {
  rtos_thread_t* main_thread_handle;
  rtos_thread_t* matching_thread_handle;
  rtos_queue_t* queue;
  uint32_t auth_expiry_ms;
  rtos_timer_t unlock_timer;
  rtos_mutex_t auth_lock;
  enrollment_ctx_t current_enrollment_ctx;
  bio_enroll_stats_t stats;
  uint32_t timestamp;
} auth_priv SHARED_TASK_DATA = {
  .main_thread_handle = NULL,
  .queue = NULL,
  .auth_expiry_ms = 60000,  // Expire after 60 seconds.
  .unlock_timer = {0},
  .auth_lock = {0},
  .current_enrollment_ctx =
    {
      .enrollment_in_progress = false,
      .enroll_ok = SECURE_FALSE,
      .index = BIO_TEMPLATE_ID_INVALID,
      .label = {0},
    },
  .stats = {0},
  .timestamp = 0,
};

static const uint32_t bio_lib_retry_ms = 5000u;

// TODO(W-3920): Write a proper animation state machine.
extern bool fwup_started;

static fwpb_status unlock_err_to_fwpb_status(unlock_err_t err) {
  switch (err) {
    case UNLOCK_OK:
      return fwpb_status_SUCCESS;
    case UNLOCK_WRONG_SECRET:
      return fwpb_status_WRONG_SECRET;
    case UNLOCK_STORAGE_ERR:
      return fwpb_status_STORAGE_ERR;
    case UNLOCK_NO_SECRET_PROVISIONED:
      return fwpb_status_NO_SECRET_PROVISIONED;
    case UNLOCK_WAITING_ON_DELAY:
      return fwpb_status_WAITING_ON_DELAY;
    default:
      return fwpb_status_ERROR;
  }
}

static void finger_down_animation(void) {
  // We play different animations here because the prior LED state is different.
  // When we're unlocked already, we fade from green to white, then to either green or red.
  // When we're locked, we fade from off to white, then to either green or red.
  //
  // NOTE: Strictly speaking, we should also check if a red animation is playing, and if so, play a
  // different fade. But in practice, the rate limit for bad fingerprints is high enough that the
  // transition from fading-out red to white doesn't look bad.
  if (auth_state.authenticated == SECURE_TRUE) {
    static led_start_animation_t LED_TASK_DATA msg = {
      .animation = (uint32_t)ANI_FINGER_DOWN_FROM_UNLOCKED, .immediate = true};
    ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
  } else {
    static led_start_animation_t LED_TASK_DATA msg = {
      .animation = (uint32_t)ANI_FINGER_DOWN_FROM_LOCKED, .immediate = true};
    ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
  }
}

static void lock_animation(bool previously_authenticated) {
  if (!previously_authenticated) {
    // Don't play any animation if the device was already locked when
    // this function was called.
    return;
  }

  // TODO(W-4580)
  static led_set_rest_animation_t LED_TASK_DATA rest_msg = {.animation = (uint32_t)ANI_OFF};
  ipc_send(led_port, &rest_msg, sizeof(rest_msg), IPC_LED_SET_REST_ANIMATION);

  static led_start_animation_t LED_TASK_DATA msg = {.immediate = true};

  if (fwup_started) {
    msg.animation = (uint32_t)ANI_LOCKED_FROM_FWUP;
  } else if (auth_priv.current_enrollment_ctx.enrollment_in_progress) {
    msg.animation = (uint32_t)ANI_LOCKED_FROM_ENROLLMENT;
  } else {
    msg.animation = (uint32_t)ANI_LOCKED;
  }

  ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
}

NO_OPTIMIZE void set_authenticated_with_animation(secure_bool_t state) {
  rtos_mutex_lock(&auth_priv.auth_lock);

  secure_bool_t prev_state = auth_state.authenticated;

  auth_state.authenticated = state;

  SECURE_IF_FAILOUT(state == SECURE_TRUE) {
    // Unlocked
    rtos_timer_stop(&auth_priv.unlock_timer);
    rtos_timer_start(&auth_priv.unlock_timer, auth_priv.auth_expiry_ms);
  }
  else {
    // Locked
    lock_animation((prev_state == SECURE_TRUE));
    rtos_timer_stop(&auth_priv.unlock_timer);
  }

  rtos_mutex_unlock(&auth_priv.auth_lock);
}

NO_OPTIMIZE secure_bool_t refresh_auth(void) {
  secure_bool_t authed = is_authenticated();
  SECURE_IF_FAILOUT(authed == SECURE_TRUE) {
    set_authenticated_with_animation(SECURE_TRUE);  // Refresh timer
    return SECURE_TRUE;
  }
  return SECURE_FALSE;
}

NO_OPTIMIZE secure_bool_t is_authenticated(void) {
#if AUTOMATED_TESTING
  return SECURE_TRUE;
#else
  rtos_mutex_lock(&auth_priv.auth_lock);
  secure_bool_t result = SECURE_FALSE;
  SECURE_DO_FAILOUT(auth_state.authenticated == SECURE_TRUE, { result = SECURE_TRUE; });
  rtos_mutex_unlock(&auth_priv.auth_lock);
  return result;
#endif
}

static void reset_auth_state(void) {
  auth_state.method = UNSPECIFIED;
  auth_state.fingerprint_index = BIO_TEMPLATE_ID_INVALID;
}

NO_OPTIMIZE void deauthenticate(void) {
  set_authenticated_with_animation(SECURE_FALSE);
  reset_auth_state();
}

void deauthenticate_without_animation(void) {
  auth_state.authenticated = SECURE_FALSE;
  reset_auth_state();
  rtos_timer_stop(&auth_priv.unlock_timer);
}

void enroll_failed_animation(void) {
  if (onboarding_auth_is_setup()) {
    static led_set_rest_animation_t LED_TASK_DATA rest_msg = {.animation = (uint32_t)ANI_OFF};
    ipc_send(led_port, &rest_msg, sizeof(rest_msg), IPC_LED_SET_REST_ANIMATION);
    static led_start_animation_t LED_TASK_DATA msg = {.animation = (uint32_t)ANI_ENROLLMENT_FAILED,
                                                      .immediate = true};
    ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
  } else {
    static led_set_rest_animation_t end_fail_msg = {.animation = (uint32_t)ANI_REST};
    ipc_send(led_port, &end_fail_msg, sizeof(end_fail_msg), IPC_LED_SET_REST_ANIMATION);
    ipc_send_empty(led_port, IPC_LED_STOP_ANIMATION);
  }
}

// Helper method to set the authenticated state plus the unlock method.
NO_OPTIMIZE static void set_authenticated_via_fingerprint(void) {
  set_authenticated_with_animation(SECURE_TRUE);
  auth_state.method = BIOMETRICS;
  // fingerprint_index is set in the matching thread.
}

NO_OPTIMIZE void handle_match_fail(void) {
  LOGE("Failed when trying to match fingerprint");
  deauthenticate_without_animation();
  rtos_thread_sleep(RATE_LIMIT_FAIL_MS);
}

NO_OPTIMIZE void unlock_timer_callback(rtos_timer_handle_t UNUSED(timer)) {
  SECURE_DO({ deauthenticate(); });
}

NO_OPTIMIZE void handle_start_fingerprint_enrollment(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_start_fingerprint_enrollment_rsp_tag;

  if (!cmd->msg.start_fingerprint_enrollment_cmd.has_handle) {
    LOGD("Handle not set; using defaults");
    auth_priv.current_enrollment_ctx.index = 0;
    memset(auth_priv.current_enrollment_ctx.label, 0, BIO_LABEL_MAX_LEN);
  } else {
    auth_priv.current_enrollment_ctx.index = cmd->msg.start_fingerprint_enrollment_cmd.handle.index;
    strncpy(auth_priv.current_enrollment_ctx.label,
            cmd->msg.start_fingerprint_enrollment_cmd.handle.label, BIO_LABEL_MAX_LEN);
  }

  auth_priv.current_enrollment_ctx.enrollment_in_progress = true;
  SECURE_DO({ auth_priv.current_enrollment_ctx.enroll_ok = SECURE_FALSE; });

  static led_start_animation_t LED_TASK_DATA msg = {.animation = (uint32_t)ANI_ENROLLMENT,
                                                    .immediate = true};
  ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);

  // Immediately reply -- enrollment doesn't happen within the NFC field.
  // This indicates to the caller that enrollment will begin.
  rsp->msg.start_fingerprint_enrollment_rsp.rsp_status =
    fwpb_start_fingerprint_enrollment_rsp_start_fingerprint_enrollment_rsp_status_SUCCESS;

  proto_send_rsp(cmd, rsp);
}

void handle_get_fingerprint_enrollment_status(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_get_fingerprint_enrollment_status_rsp_tag;

  uint32_t existing_template_count = 0;
  bio_storage_get_template_count(&existing_template_count);
  LOGD("Template count: %lu", existing_template_count);

  bool enroll_ok = false;
  if (cmd->msg.get_fingerprint_enrollment_status_cmd.app_knows_about_this_field) {
    // The app has been updated to a version that knows about this field, so we can use
    // the new fingerprint enrollment status logic.
    enroll_ok = auth_priv.current_enrollment_ctx.enroll_ok == SECURE_TRUE;
  } else {
    enroll_ok = existing_template_count > 0;
  }

  if (enroll_ok) {
    rsp->msg.get_fingerprint_enrollment_status_rsp.fingerprint_status =
      fwpb_get_fingerprint_enrollment_status_rsp_fingerprint_enrollment_status_COMPLETE;

    rsp->msg.get_fingerprint_enrollment_status_rsp.pass_count = auth_priv.stats.pass_count;
    rsp->msg.get_fingerprint_enrollment_status_rsp.fail_count = auth_priv.stats.fail_count;
    rsp->msg.get_fingerprint_enrollment_status_rsp.has_diagnostics = true;

    rsp->msg.get_fingerprint_enrollment_status_rsp.diagnostics.finger_coverage_valid =
      auth_priv.stats.diagnostics.finger_coverage_valid;
    rsp->msg.get_fingerprint_enrollment_status_rsp.diagnostics.finger_coverage =
      auth_priv.stats.diagnostics.finger_coverage;
    rsp->msg.get_fingerprint_enrollment_status_rsp.diagnostics.common_mode_noise_valid =
      auth_priv.stats.diagnostics.common_mode_noise_valid;
    rsp->msg.get_fingerprint_enrollment_status_rsp.diagnostics.common_mode_noise =
      auth_priv.stats.diagnostics.common_mode_noise;
    rsp->msg.get_fingerprint_enrollment_status_rsp.diagnostics.image_quality_valid =
      auth_priv.stats.diagnostics.image_quality_valid;
    rsp->msg.get_fingerprint_enrollment_status_rsp.diagnostics.image_quality =
      auth_priv.stats.diagnostics.image_quality;
    rsp->msg.get_fingerprint_enrollment_status_rsp.diagnostics.sensor_coverage_valid =
      auth_priv.stats.diagnostics.sensor_coverage_valid;
    rsp->msg.get_fingerprint_enrollment_status_rsp.diagnostics.sensor_coverage =
      auth_priv.stats.diagnostics.sensor_coverage;
    rsp->msg.get_fingerprint_enrollment_status_rsp.diagnostics.template_data_update_valid =
      auth_priv.stats.diagnostics.template_data_update_valid;

    goto out;
  }

  if (!auth_priv.current_enrollment_ctx.enrollment_in_progress) {
    rsp->msg.get_fingerprint_enrollment_status_rsp.fingerprint_status =
      fwpb_get_fingerprint_enrollment_status_rsp_fingerprint_enrollment_status_NOT_IN_PROGRESS;
  } else {
    rsp->msg.get_fingerprint_enrollment_status_rsp.fingerprint_status =
      fwpb_get_fingerprint_enrollment_status_rsp_fingerprint_enrollment_status_INCOMPLETE;
  }

out:
  rsp->msg.get_fingerprint_enrollment_status_rsp.rsp_status =
    fwpb_get_fingerprint_enrollment_status_rsp_get_fingerprint_enrollment_status_rsp_status_SUCCESS;
  proto_send_rsp(cmd, rsp);
}

NO_OPTIMIZE void handle_query_authentication(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_query_authentication_rsp_tag;
  SECURE_DO_FAILOUT(is_authenticated() == SECURE_TRUE, {
    rsp->msg.query_authentication_rsp.rsp_status =
      fwpb_query_authentication_rsp_query_authentication_rsp_status_AUTHENTICATED;
    goto out;
  });

  rsp->msg.query_authentication_rsp.rsp_status =
    fwpb_query_authentication_rsp_query_authentication_rsp_status_UNAUTHENTICATED;

out:
  proto_send_rsp(cmd, rsp);
}

void handle_unlock_secret(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_send_unlock_secret_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  if (!feature_flags_get(fwpb_feature_flag_FEATURE_FLAG_UNLOCK)) {
    rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;
    goto out;
  }

  if (cmd->msg.send_unlock_secret_cmd.secret.ciphertext.size >
      sizeof(cmd->msg.send_unlock_secret_cmd.secret.ciphertext.bytes)) {
    goto out;
  }

  _Static_assert(sizeof(cmd->msg.send_unlock_secret_cmd.secret.nonce.bytes) == AES_GCM_IV_LENGTH,
                 "nonce size mismatch");
  _Static_assert(sizeof(cmd->msg.send_unlock_secret_cmd.secret.mac.bytes) == AES_GCM_TAG_LENGTH,
                 "mac size mismatch");

  unlock_secret_t decrypted_secret = {0};
  if (secure_channel_decrypt(
        cmd->msg.send_unlock_secret_cmd.secret.ciphertext.bytes, decrypted_secret.bytes,
        cmd->msg.send_unlock_secret_cmd.secret.ciphertext.size,
        cmd->msg.send_unlock_secret_cmd.secret.nonce.bytes,
        cmd->msg.send_unlock_secret_cmd.secret.mac.bytes) != SECURE_CHANNEL_OK) {
    LOGE("Failed to decrypt unlock secret");
    rsp->status = fwpb_status_SECURE_CHANNEL_ERROR;
    goto out;
  }

  unlock_err_t err =
    unlock_check_secret(&decrypted_secret, &rsp->msg.send_unlock_secret_rsp.remaining_delay_ms,
                        &rsp->msg.send_unlock_secret_rsp.retry_counter);
  if (err == UNLOCK_OK) {
    static led_start_animation_t LED_TASK_DATA msg = {.animation = (uint32_t)ANI_UNLOCKED,
                                                      .immediate = true};
    ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
    auth_state.method = UNLOCK_SECRET;
  }
  rsp->status = unlock_err_to_fwpb_status(err);

out:
  proto_send_rsp(cmd, rsp);
}

void handle_provision_unlock_secret(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_provision_unlock_secret_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  if (!feature_flags_get(fwpb_feature_flag_FEATURE_FLAG_UNLOCK)) {
    rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;
    goto out;
  }

  if (cmd->msg.provision_unlock_secret_cmd.secret.ciphertext.size >
      sizeof(cmd->msg.provision_unlock_secret_cmd.secret.ciphertext.bytes)) {
    goto out;
  }

  _Static_assert(
    sizeof(cmd->msg.provision_unlock_secret_cmd.secret.nonce.bytes) == AES_GCM_IV_LENGTH,
    "nonce size mismatch");
  _Static_assert(
    sizeof(cmd->msg.provision_unlock_secret_cmd.secret.mac.bytes) == AES_GCM_TAG_LENGTH,
    "mac size mismatch");

  unlock_secret_t decrypted_secret = {0};
  if (secure_channel_decrypt(
        cmd->msg.provision_unlock_secret_cmd.secret.ciphertext.bytes, decrypted_secret.bytes,
        cmd->msg.provision_unlock_secret_cmd.secret.ciphertext.size,
        cmd->msg.provision_unlock_secret_cmd.secret.nonce.bytes,
        cmd->msg.provision_unlock_secret_cmd.secret.mac.bytes) != SECURE_CHANNEL_OK) {
    LOGE("Failed to decrypt unlock secret");
    rsp->status = fwpb_status_SECURE_CHANNEL_ERROR;
    goto out;
  }

  unlock_err_t err = unlock_provision_secret(&decrypted_secret);
  rsp->status = unlock_err_to_fwpb_status(err);

out:
  proto_send_rsp(cmd, rsp);
}

void handle_unlock_limit_response(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_configure_unlock_limit_response_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  if (!feature_flags_get(fwpb_feature_flag_FEATURE_FLAG_UNLOCK)) {
    rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;
    goto out;
  }

  unlock_limit_response_t limit_response = DEFAULT_LIMIT_RESPONSE;

  switch (cmd->msg.configure_unlock_limit_response_cmd.unlock_limit_response) {
    case fwpb_configure_unlock_limit_response_cmd_response_cfg_UNLOCK_LIMIT_RESPONSE_DELAY:
      limit_response = RESPONSE_DELAY;
      break;
    case fwpb_configure_unlock_limit_response_cmd_response_cfg_UNLOCK_LIMIT_RESPONSE_WIPE_STATE:
      limit_response = RESPONSE_WIPE_STATE;
      break;
    default:
      LOGE("Unknown limit response %d",
           cmd->msg.configure_unlock_limit_response_cmd.unlock_limit_response);
      goto out;
  }

  if (unlock_set_configured_limit_response(limit_response) != UNLOCK_OK) {
    LOGE("Failed to set limit response");
    goto out;
  }

  rsp->status = fwpb_status_SUCCESS;

out:
  proto_send_rsp(cmd, rsp);
}

void handle_delete_fingerprint(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_delete_fingerprint_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  uint32_t num_templates = 0;
  bio_storage_get_template_count(&num_templates);

  if (num_templates == 1) {
    rsp->status = fwpb_status_INVALID_STATE;
    goto out;
  }

  bio_err_t err = bio_storage_delete_template(cmd->msg.delete_fingerprint_cmd.index);
  switch (err) {
    case BIO_ERR_NONE:
      rsp->status = fwpb_status_SUCCESS;
      break;
    case BIO_ERR_TEMPLATE_DOESNT_EXIST:
      // intentional fall-through
    case BIO_ERR_LABEL_DOESNT_EXIST:
      rsp->status = fwpb_status_FILE_NOT_FOUND;
      break;
    default:
      break;
  }

out:
  proto_send_rsp(cmd, rsp);
}

void handle_get_enrolled_fingerprints(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_get_enrolled_fingerprints_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  uint32_t count = 0;
  bio_storage_get_template_count(&count);

  rsp->msg.get_enrolled_fingerprints_rsp.handles_count = 0;
  rsp->msg.get_enrolled_fingerprints_rsp.max_count = TEMPLATE_MAX_COUNT;

  uint32_t filled = 0;
  for (uint32_t i = 0; i < TEMPLATE_MAX_COUNT && filled < count; i++) {
    if (bio_fingerprint_index_exists(i)) {
      // Only increment handles_count and retrieve labels for existing templates
      rsp->msg.get_enrolled_fingerprints_rsp.handles[filled].index = i;
      if (!bio_storage_label_retrieve(
            i, rsp->msg.get_enrolled_fingerprints_rsp.handles[filled].label)) {
        LOGE("Failed to retrieve label for index %ld", i);
      }
      filled++;
    }
  }

  // Update handles_count based on actual filled templates
  rsp->msg.get_enrolled_fingerprints_rsp.handles_count = filled;

  if (filled == count) {
    rsp->status = fwpb_status_SUCCESS;
  } else {
    rsp->status = fwpb_status_STORAGE_ERR;
  }

  proto_send_rsp(cmd, rsp);
}

void handle_get_unlock_method(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  // This endpoint requires auth. So, at this point, we're *should* be guaranteed to have some kind
  // of valid state.
  if (auth_state.authenticated != SECURE_TRUE) {
    // Double check that we're authenticated. Not a security sensitive check.
    rsp->status = fwpb_status_UNAUTHENTICATED;
    goto out;
  }

  rsp->which_msg = fwpb_wallet_rsp_get_unlock_method_rsp_tag;
  rsp->status = fwpb_status_SUCCESS;
  rsp->msg.get_unlock_method_rsp.fingerprint_index = BIO_TEMPLATE_ID_INVALID;

  // IMPORTANT: Must match unlock_method in wallet.proto
  rsp->msg.get_unlock_method_rsp.method =
    (fwpb_get_unlock_method_rsp_unlock_method)auth_state.method;

  if (auth_state.method == BIOMETRICS) {
    rsp->msg.get_unlock_method_rsp.fingerprint_index = auth_state.fingerprint_index;
  }

out:
  proto_send_rsp(cmd, rsp);
}

void handle_set_fingerprint_label(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_set_fingerprint_label_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  if (!bio_storage_label_save(cmd->msg.set_fingerprint_label_cmd.handle.index,
                              cmd->msg.set_fingerprint_label_cmd.handle.label)) {
    rsp->status = fwpb_status_STORAGE_ERR;
    goto out;
  }

  rsp->status = fwpb_status_SUCCESS;

out:
  proto_send_rsp(cmd, rsp);
}

void handle_cancel_fingerprint_enrollment(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_cancel_fingerprint_enrollment_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  if (!auth_priv.current_enrollment_ctx.enrollment_in_progress) {
    rsp->status = fwpb_status_INVALID_STATE;
    goto out;
  }

  bio_enroll_cancel();

out:
  rsp->status = fwpb_status_SUCCESS;
  proto_send_rsp(cmd, rsp);
}

void auth_main_thread(void* UNUSED(args)) {
  sysevent_wait(SYSEVENT_SLEEP_TIMER_READY | SYSEVENT_FEATURE_FLAGS_READY, true);

  if (feature_flags_get(fwpb_feature_flag_FEATURE_FLAG_UNLOCK)) {
    unlock_init_and_begin_delay();
  } else {
    LOGI("Unlock feature flag is disabled");
  }

  for (;;) {
    ipc_ref_t message = {0};
    ipc_recv(auth_port, &message);

    switch (message.tag) {
      case IPC_PROTO_START_FINGERPRINT_ENROLLMENT_CMD: {
        handle_start_fingerprint_enrollment(&message);
        break;
      }
      case IPC_PROTO_GET_FINGERPRINT_ENROLLMENT_STATUS_CMD: {
        handle_get_fingerprint_enrollment_status(&message);
        break;
      }
      case IPC_PROTO_QUERY_AUTHENTICATION_CMD: {
        handle_query_authentication(&message);
        break;
      }
      case IPC_AUTH_SET_TIMESTAMP: {
        auth_priv.timestamp = ((auth_set_timestamp_t*)message.object)->timestamp;
        break;
      }
      case IPC_PROTO_SEND_UNLOCK_SECRET_CMD: {
        handle_unlock_secret(&message);
        break;
      }
      case IPC_PROTO_PROVISION_UNLOCK_SECRET_CMD: {
        handle_provision_unlock_secret(&message);
        break;
      }
      case IPC_PROTO_CONFIGURE_UNLOCK_LIMIT_RESPONSE_CMD: {
        handle_unlock_limit_response(&message);
        break;
      }
      case IPC_PROTO_DELETE_FINGERPRINT_CMD: {
        handle_delete_fingerprint(&message);
        break;
      }
      case IPC_PROTO_GET_ENROLLED_FINGERPRINTS_CMD: {
        handle_get_enrolled_fingerprints(&message);
        break;
      }
      case IPC_PROTO_GET_UNLOCK_METHOD_CMD: {
        handle_get_unlock_method(&message);
        break;
      }
      case IPC_PROTO_SET_FINGERPRINT_LABEL_CMD: {
        handle_set_fingerprint_label(&message);
        break;
      }
      case IPC_PROTO_CANCEL_FINGERPRINT_ENROLLMENT_CMD: {
        handle_cancel_fingerprint_enrollment(&message);
        break;
      }
      default: {
        LOGE("unknown message %ld", message.tag);
      }
    }
  }
}

NO_OPTIMIZE void auth_matching_thread(void* UNUSED(args)) {
  bio_hal_init();

  bool bio_lib_ready = bio_lib_init();
  for (;;) {
    unsigned int glitch_count = secure_glitch_get_count();
    if (!bio_lib_ready) {
      LOGW("retrying bio_lib_init");
      rtos_thread_sleep(bio_lib_retry_ms);
      bio_lib_reset();
      bio_lib_ready = bio_lib_init();
      continue;
    }

    // Wait indefinitely for an external interrupt from the fingerprint sensor
    // which indicates that there is a finger on the sensor.
    bio_wait_for_finger_blocking(BIO_FINGER_DOWN);

    sleep_refresh_power_timer();

    if (auth_priv.current_enrollment_ctx.enrollment_in_progress &&
        auth_priv.current_enrollment_ctx.index !=
          BIO_TEMPLATE_ID_INVALID) {  // Fingerprint enrollment
      SECURE_DO_ONCE({
        bool res = bio_enroll_finger(auth_priv.current_enrollment_ctx.index,
                                     auth_priv.current_enrollment_ctx.label, &auth_priv.stats);
        SECURE_IF_FAILOUT(res == true) { auth_priv.current_enrollment_ctx.enroll_ok = SECURE_TRUE; }
        else {
          auth_priv.current_enrollment_ctx.enroll_ok = SECURE_FALSE;
          enroll_failed_animation();
        }
      });

      if (auth_priv.current_enrollment_ctx.enroll_ok != SECURE_TRUE) {
        LOGE("Enrollment of id %d failed", auth_priv.current_enrollment_ctx.index);
      }

      uint32_t used_index = auth_priv.current_enrollment_ctx.index;
      auth_priv.current_enrollment_ctx.enrollment_in_progress = false;
      auth_priv.current_enrollment_ctx.index = BIO_TEMPLATE_ID_INVALID;

      SECURE_DO_FAILOUT(auth_priv.current_enrollment_ctx.enroll_ok == SECURE_TRUE, {
        // If enrollment was successful, then unlock the device.
        set_authenticated_via_fingerprint();
        auth_state.fingerprint_index = used_index;
      });
    } else if ((onboarding_auth_is_setup() == SECURE_TRUE)) {  // Fingerprint unlock
      // Give the user some immediate feedback by turning on the LED; the actual matching takes
      // a few hundred ms, so this makes the device feel more responsive.
      finger_down_animation();

      if (!bio_fingerprint_exists()) {
        LOGW("No fingerprint enrolled");
        rtos_thread_sleep(RATE_LIMIT_MS);
        goto wait_for_up;
      }

      // Fingerprint matching.
      secure_bool_t matched = SECURE_FALSE;
      if (bio_authenticate_finger(&matched, &auth_state.fingerprint_index, auth_priv.timestamp) !=
          SECURE_TRUE) {
        handle_match_fail();
        goto wait_for_up;
      }

      // Only update authentication state if match was okay.
      // Also check that no glitches have been detected since the last run of this thread loop.
      SECURE_DO_FAILOUT((matched == SECURE_TRUE) && (glitch_count == secure_glitch_get_count()), {
        set_authenticated_via_fingerprint();

        // Reset retry unlock counter on successful fingerprint match.
        unlock_err_t unlock_err = unlock_reset_retry_counter();
        if ((unlock_err != UNLOCK_OK) && (unlock_err != UNLOCK_NOT_INITIALIZED)) {
          LOGW("Failed to reset retry counter");
        }
      });

      SECURE_DO_FAILIN(matched != SECURE_TRUE, { handle_match_fail(); });
    }

  wait_for_up:
    bio_wait_for_finger_blocking(BIO_FINGER_UP);
  }
}

void auth_task_create(bool no_threads) {
  rtos_mutex_create(&auth_priv.auth_lock);
  rtos_timer_create_static(&auth_priv.unlock_timer, unlock_timer_callback);

  if (no_threads) {
    // An unfortunate workaround, but: the mfgtest image calls functions that require
    // the above objects to be created. However, it can't run the auth threads, because
    // they actively use the sensor, but the mfgtest image needs to use the sensor for
    // self tests.
    return;
  }

  auth_priv.main_thread_handle =
    rtos_thread_create(auth_main_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 2048);
  ASSERT(auth_priv.main_thread_handle);

  auth_priv.matching_thread_handle =
    rtos_thread_create(auth_matching_thread, NULL, RTOS_THREAD_PRIORITY_HIGH, 2048);
  ASSERT(auth_priv.matching_thread_handle);

  auth_priv.queue = rtos_queue_create(auth_queue, ipc_ref_t, 4);
  ASSERT(auth_priv.queue);

  ipc_register_port(auth_port, auth_priv.queue);
}
