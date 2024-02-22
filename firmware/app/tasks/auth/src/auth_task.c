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

static struct {
  rtos_thread_t* main_thread_handle;
  rtos_thread_t* matching_thread_handle;
  rtos_queue_t* queue;
  uint32_t auth_expiry_ms;
  uint32_t rate_limit_ms;
  rtos_timer_t unlock_timer;
  rtos_mutex_t auth_lock;
  secure_bool_t enroll_ok;
  bool enrollment_in_progress;
  secure_bool_t authenticated;
  bio_enroll_stats_t stats;
  uint32_t timestamp;
} auth_priv SHARED_TASK_DATA = {
  .main_thread_handle = NULL,
  .queue = NULL,
  .auth_expiry_ms = 60000,  // Expire after 60 seconds.
  .rate_limit_ms = 2000,    // TODO(W-3894)
  .unlock_timer = {0},
  .auth_lock = {0},
  .enroll_ok = SECURE_FALSE,
  .enrollment_in_progress = false,
  .authenticated = SECURE_FALSE,
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

static void lock_animation(bool previously_authenticated) {
  if (!previously_authenticated) {
    // Don't play any animation if the device was already locked when
    // this function was called.
    return;
  }

  // TODO(W-4580)
  static led_set_rest_animation_t LED_TASK_DATA rest_msg = {.animation = (uint32_t)ANI_OFF};
  ipc_send(led_port, &rest_msg, sizeof(rest_msg), IPC_LED_SET_REST_ANIMATION);

  if (fwup_started) {
    static led_start_animation_t LED_TASK_DATA msg = {.animation = (uint32_t)ANI_LOCKED_FROM_FWUP,
                                                      .immediate = true};
    ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
  } else {
    static led_start_animation_t LED_TASK_DATA msg = {.animation = (uint32_t)ANI_LOCKED,
                                                      .immediate = true};
    ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);
  }
}

NO_OPTIMIZE void set_authenticated(secure_bool_t state) {
  rtos_mutex_lock(&auth_priv.auth_lock);

  secure_bool_t prev_state = auth_priv.authenticated;

  auth_priv.authenticated = state;

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
    set_authenticated(SECURE_TRUE);  // Refresh timer
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
  SECURE_DO_FAILOUT(auth_priv.authenticated == SECURE_TRUE, { result = SECURE_TRUE; });
  rtos_mutex_unlock(&auth_priv.auth_lock);
  return result;
#endif
}

void deauthenticate(void) {
  set_authenticated(SECURE_FALSE);
}

void deauthenticate_without_animation(void) {
  auth_priv.authenticated = SECURE_FALSE;
  rtos_timer_stop(&auth_priv.unlock_timer);
}

NO_OPTIMIZE void unlock_callback(rtos_timer_handle_t UNUSED(timer)) {
  SECURE_DO({ deauthenticate(); });
}

NO_OPTIMIZE void handle_start_fingerprint_enrollment(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_start_fingerprint_enrollment_rsp_tag;

  auth_priv.enrollment_in_progress = true;
  SECURE_DO({ auth_priv.enroll_ok = SECURE_FALSE; });

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
  if (existing_template_count > 0) {
    // Note: This assumes only one fingerprint enrollment will ever occur, which will
    // eventually not be true.
    rsp->msg.get_fingerprint_enrollment_status_rsp.fingerprint_status =
      fwpb_get_fingerprint_enrollment_status_rsp_fingerprint_enrollment_status_COMPLETE;

    rsp->msg.get_fingerprint_enrollment_status_rsp.pass_count = auth_priv.stats.pass_count;
    rsp->msg.get_fingerprint_enrollment_status_rsp.fail_count = auth_priv.stats.fail_count;

    goto out;
  }

  if (!auth_priv.enrollment_in_progress) {
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
    bio_wait_for_finger_blocking();

    sleep_refresh_power_timer();

    if (auth_priv.enrollment_in_progress) {  // Fingerprint enrollment
      // TODO How in the UX should we expose the ability to
      // overwrite older fingerprints? I'll just hardcode 0 for now.

      SECURE_DO_ONCE({
        bool res = bio_enroll_finger(0, &auth_priv.stats);
        SECURE_IF_FAILOUT(res == true) { auth_priv.enroll_ok = SECURE_TRUE; }
        else {
          auth_priv.enroll_ok = SECURE_FALSE;
        }
      });

      if (auth_priv.enroll_ok != SECURE_TRUE) {
        LOGE("Enrollment of id %d failed", 0);
      }
      auth_priv.enrollment_in_progress = false;

      SECURE_DO_FAILOUT(auth_priv.enroll_ok == SECURE_TRUE, {
        // If enrollment was successful, then unlock the device.
        set_authenticated(SECURE_TRUE);
      });
    } else if ((onboarding_auth_is_setup() == SECURE_TRUE) && (is_authenticated() != SECURE_TRUE)) {
      // Give the user some immediate feedback by turning on the LED; the actual matching takes
      // a few hundred ms, so this makes the device feel more responsive.
      static led_start_animation_t LED_TASK_DATA msg = {.animation = (uint32_t)ANI_FINGER_DOWN,
                                                        .immediate = true};
      ipc_send(led_port, &msg, sizeof(msg), IPC_LED_START_ANIMATION);

      if (!bio_fingerprint_exists()) {
        LOGW("No fingerprint enrolled");
        rtos_thread_sleep(auth_priv.rate_limit_ms);
        continue;
      }

      // Fingerprint matching. Only attempt to match if we aren't already
      // unlocked.
      bio_template_id_t id;
      secure_bool_t matched = SECURE_FALSE;
      if (bio_authenticate_finger(&matched, &id, auth_priv.timestamp) != SECURE_TRUE) {
        LOGE("Failed when trying to match fingerprint");
        rtos_thread_sleep(auth_priv.rate_limit_ms);
        continue;
      }

      SECURE_DO_FAILOUT((matched == SECURE_TRUE) && (glitch_count == secure_glitch_get_count()), {
        // Only update authentication state if match was okay. This means that
        // no match does not lead to authenticate state being disabled.
        // Also check that no glitches have been detected since the last run of this thread loop.
        set_authenticated(SECURE_TRUE);

        // Reset retry unlock counter on successful fingerprint match.
        unlock_err_t unlock_err = unlock_reset_retry_counter();
        if ((unlock_err != UNLOCK_OK) && (unlock_err != UNLOCK_NOT_INITIALIZED)) {
          LOGW("Failed to reset retry counter");
        }
      });

      SECURE_DO_FAILIN(matched != SECURE_TRUE, { rtos_thread_sleep(auth_priv.rate_limit_ms); });
    }
  }
}

void auth_task_create(bool no_threads) {
  rtos_mutex_create(&auth_priv.auth_lock);
  rtos_timer_create_static(&auth_priv.unlock_timer, unlock_callback);

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
