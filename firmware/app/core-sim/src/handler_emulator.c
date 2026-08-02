/**
 * @file handler_emulator.c
 * @brief Emulator introspection and control for core-sim
 */

#include "handler_emulator.h"

#include "auth_sim.h"
#include "bio_sim.h"
#include "confirmation_manager.h"
#include "device_state.h"
#include "display_controller.h"
#include "display_controller_internal.h"
#include "stdio_defs.h"
#include "ui_events.h"
#include "unlock.h"
#include "unlock_impl.h"

#include <string.h>
#include <unistd.h>

extern unlock_ctx_t unlock_ctx;

static void set_bool_response(uint8_t* rsp, uint32_t* rsp_len, bool success) {
  rsp[0] = success ? 1 : 0;
  *rsp_len = 1;
}

static bool wait_for_enrollment_start(uint32_t timeout_ms) {
  const uint32_t step_ms = 5;
  uint32_t waited_ms = 0;
  while (waited_ms < timeout_ms) {
    if (emu_enrollment_in_progress()) {
      return true;
    }
    usleep(step_ms * 1000);
    waited_ms += step_ms;
  }
  return emu_enrollment_in_progress();
}

enum {
  SCREEN_ID_NONE = 0,
  SCREEN_ID_MENU = 2,
  SCREEN_ID_FWUP_PROGRESS = 3,
  SCREEN_ID_FINGERPRINT_ENROLL = 6,
  SCREEN_ID_TRANSACTION_SIGNING = 11,
  SCREEN_ID_LOCKED = 13,
  SCREEN_ID_AUTH_PROMPT = 14,
  SCREEN_ID_ERROR = 16,
  SCREEN_ID_SCAN = 18,
  SCREEN_ID_ONBOARDING = 19,
  SCREEN_ID_BRIGHTNESS = 20,
  SCREEN_ID_ABOUT = 21,
  SCREEN_ID_REGULATORY = 22,
  SCREEN_ID_MENU_FINGERPRINTS = 23,
  SCREEN_ID_SUCCESS = 25,
  SCREEN_ID_MFG = 26,
  SCREEN_ID_APP_DOWNLOAD = 27,
};

static uint32_t map_proto_tag_to_screen_id(uint32_t proto_tag) {
  switch (proto_tag) {
    case 5:
      return SCREEN_ID_MENU;
    case 6:
      return SCREEN_ID_ABOUT;
    case 7:
      return SCREEN_ID_BRIGHTNESS;
    case 8:
      return SCREEN_ID_REGULATORY;
    case 9:
      return SCREEN_ID_MFG;
    case 10:
      return SCREEN_ID_TRANSACTION_SIGNING;
    case 11:
      return SCREEN_ID_ONBOARDING;
    case 12:
      return SCREEN_ID_SUCCESS;
    case 13:
      return SCREEN_ID_ERROR;
    case 14:
      return SCREEN_ID_SCAN;
    case 15:
      return SCREEN_ID_FINGERPRINT_ENROLL;
    case 16:
      return SCREEN_ID_LOCKED;
    case 17:
      return SCREEN_ID_MENU_FINGERPRINTS;
    case 19:
      return SCREEN_ID_FWUP_PROGRESS;
    case 26:
      return SCREEN_ID_AUTH_PROMPT;
    case 27:
      return SCREEN_ID_APP_DOWNLOAD;
    default:
      return SCREEN_ID_NONE;
  }
}

extern display_controller_t controller;

void stdio_emulator_init(void) {
  controller.battery_percent = 100;
  controller.is_charging = false;

  LOG("Emulator handler initialized");
}

void stdio_handle_emulator_command(uint8_t cmd, const uint8_t* payload, uint32_t payload_len,
                                   uint8_t* rsp, uint32_t* rsp_len) {
  switch (cmd) {
    case UI_CMD_GET_CURRENT_SCREEN: {
      uint32_t proto_tag = (uint32_t)controller.show_screen.which_params;
      uint32_t screen = map_proto_tag_to_screen_id(proto_tag);
      memcpy(rsp, &screen, 4);
      *rsp_len = 4;
      LOG("GET_CURRENT_SCREEN: proto_tag=%u -> screen_id=%u", proto_tag, screen);
      break;
    }

    case UI_CMD_GET_CURRENT_FLOW: {
      uint32_t flow = (uint32_t)controller.current_flow;
      memcpy(rsp, &flow, 4);
      *rsp_len = 4;
      LOG("GET_CURRENT_FLOW: %u", flow);
      break;
    }

    case UI_CMD_GET_BATTERY_STATE: {
      rsp[0] = controller.battery_percent;
      rsp[1] = controller.is_charging ? 1 : 0;
      *rsp_len = 2;
      LOG("GET_BATTERY_STATE: %u%%, charging=%d", rsp[0], rsp[1]);
      break;
    }

    case UI_CMD_SET_AUTHENTICATED: {
      bool authenticated = (payload_len > 0) && (payload[0] != 0);
      emu_set_authenticated(authenticated);
      set_bool_response(rsp, rsp_len, true);
      LOG("SET_AUTHENTICATED: %d", authenticated);
      break;
    }

    case UI_CMD_RESET_EMULATOR: {
      emu_set_authenticated(false);
      LOG("RESET_EMULATOR: setting auth_mode to INSTANT");
      emu_set_auth_mode(EMU_AUTH_MODE_INSTANT);  // Reset to instant mode
      // Delete all fingerprint templates via bio.h
      for (bio_template_id_t i = 0; i < TEMPLATE_MAX_COUNT; i++) {
        bio_storage_delete_template(i);
      }
      emu_enrollment_cancel();
      bio_sim_reset();
      display_controller_init();
      // Wipe unlock secret and retry counter via real unlock library
      unlock_wipe_state();
      set_bool_response(rsp, rsp_len, true);
      LOG("RESET_EMULATOR: cleared fingerprints, bio_sim reset, unlock state wiped");
      break;
    }

    case UI_CMD_ACTION_APPROVE: {
      display_controller_handle_action_approve();
      confirmation_manager_approve();
      set_bool_response(rsp, rsp_len, true);
      LOG("ACTION_APPROVE");
      break;
    }

    case UI_CMD_ACTION_CANCEL: {
      display_controller_handle_action_cancel();
      set_bool_response(rsp, rsp_len, true);
      LOG("ACTION_CANCEL");
      break;
    }

    case UI_CMD_ACTION_BACK: {
      display_controller_handle_action_back();
      set_bool_response(rsp, rsp_len, true);
      LOG("ACTION_BACK");
      break;
    }

    case UI_CMD_START_ENROLLMENT: {
      display_controller_handle_action_start_enrollment();
      core_sim_start_fingerprint_enrollment(0, "test");
      if (!wait_for_enrollment_start(1000)) {
        LOG("START_ENROLLMENT: WARNING - auth_task did not acknowledge start within 1000ms");
      }
      set_bool_response(rsp, rsp_len, true);
      LOG("START_ENROLLMENT");
      break;
    }

    case UI_CMD_TICK: {
      display_controller_tick();
      set_bool_response(rsp, rsp_len, true);
      break;
    }

    case UI_CMD_ACTION_EXIT: {
      display_controller_handle_action_exit();
      set_bool_response(rsp, rsp_len, true);
      LOG("ACTION_EXIT");
      break;
    }

    case UI_CMD_SET_AUTH_MODE: {
      if (payload_len == 0) {
        set_bool_response(rsp, rsp_len, false);
        break;
      }
      emu_auth_mode_t mode = (payload[0] == 0) ? EMU_AUTH_MODE_INSTANT : EMU_AUTH_MODE_REALISTIC;
      emu_set_auth_mode(mode);
      set_bool_response(rsp, rsp_len, true);
      LOG("SET_AUTH_MODE: %s", mode == EMU_AUTH_MODE_REALISTIC ? "REALISTIC" : "INSTANT");
      break;
    }

    case UI_CMD_GET_AUTH_MODE: {
      rsp[0] = (uint8_t)emu_get_auth_mode();
      *rsp_len = 1;
      LOG("GET_AUTH_MODE: %s", rsp[0] == 1 ? "REALISTIC" : "INSTANT");
      break;
    }

    case UI_CMD_SIMULATE_FINGER_TOUCH: {
      LOG("SIMULATE_FINGER_TOUCH: signaling finger DOWN");
      bio_sim_signal_finger(BIO_FINGER_DOWN);

      usleep(10000);

      LOG("SIMULATE_FINGER_TOUCH: signaling finger UP");
      bio_sim_signal_finger(BIO_FINGER_UP);

      set_bool_response(rsp, rsp_len, true);
      break;
    }

    case UI_CMD_GET_UNLOCK_STATE: {
      uint32_t remaining = 0;
      uint32_t retry_counter = 0;
      bool secret_exists = false;
      unlock_secret_exists(&secret_exists);

      if (secret_exists) {
        if (retry_counter_read(&retry_counter) != UNLOCK_OK) {
          retry_counter = 0;
        }
        if (!rtos_timer_expired(&unlock_ctx.delay_timer)) {
          remaining = rtos_timer_remaining_ms(&unlock_ctx.delay_timer);
        }
      }

      rsp[0] = (uint8_t)(retry_counter > 255 ? 255 : retry_counter);
      memcpy(&rsp[1], &remaining, 4);
      *rsp_len = 5;
      LOG("GET_UNLOCK_STATE: attempts=%u, remaining_delay=%u ms", rsp[0], remaining);
      break;
    }

    case UI_CMD_SET_UNLOCK_SECRET: {
      if (payload_len == 0 || payload_len > sizeof(unlock_secret_t)) {
        set_bool_response(rsp, rsp_len, false);
        break;
      }
      // Copy payload into unlock_secret_t structure
      unlock_secret_t secret = {0};
      memcpy(secret.bytes, payload, payload_len);

      unlock_err_t err = unlock_provision_secret(&secret);
      set_bool_response(rsp, rsp_len, err == UNLOCK_OK);
      LOG("SET_UNLOCK_SECRET: provisioned %u bytes, result=%d", payload_len, err);
      break;
    }

    case UI_CMD_ADVANCE_TIME: {
      if (payload_len < 4) {
        set_bool_response(rsp, rsp_len, false);
        break;
      }
      uint32_t ms;
      memcpy(&ms, payload, 4);
      emu_advance_time(ms);
      set_bool_response(rsp, rsp_len, true);
      LOG("ADVANCE_TIME: %u ms", ms);
      break;
    }

    default:
      LOG("Unknown emulator command: 0x%02x", cmd);
      set_bool_response(rsp, rsp_len, false);
      break;
  }
}
