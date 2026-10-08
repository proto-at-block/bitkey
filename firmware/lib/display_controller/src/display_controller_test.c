#include "display.pb.h"
#include "display_controller.h"
#include "display_controller_internal.h"
#include "rtos.h"
#include "secutils.h"

#include <criterion/criterion.h>

#include <stdio.h>
#include <string.h>

static fwpb_display_command last_command;
static uint32_t ui_execute_command_call_count = 0;
static secure_bool_t stub_onboarding_complete = SECURE_FALSE;
static uint32_t fingerprint_delete_call_count;
static uint8_t last_deleted_fingerprint;
static bool fingerprint_delete_send_succeeds;
static uint32_t fingerprint_status_query_count;

typedef struct {
  uint8_t count;
  uint8_t indices[FINGERPRINT_SLOT_COUNT];
  char labels[FINGERPRINT_SLOT_COUNT][32];
} fingerprint_status_response_t;

extern display_controller_t controller;

bool rtos_in_isr(void) {
  return false;
}

uint32_t rtos_thread_systime(void) {
  return 0;
}

void rtos_mutex_create(rtos_mutex_t* mutex) {
  (void)mutex;
}
void rtos_mutex_destroy(rtos_mutex_t* mutex) {
  (void)mutex;
}
bool rtos_mutex_lock(rtos_mutex_t* mutex) {
  (void)mutex;
  return true;
}
bool rtos_mutex_unlock(rtos_mutex_t* mutex) {
  (void)mutex;
  return true;
}
bool rtos_mutex_take(rtos_mutex_t* mutex, uint32_t timeout_ms) {
  (void)mutex;
  (void)timeout_ms;
  return true;
}
bool rtos_mutex_lock_from_isr(rtos_mutex_t* mutex) {
  (void)mutex;
  return true;
}
bool rtos_mutex_unlock_from_isr(rtos_mutex_t* mutex) {
  (void)mutex;
  return true;
}
bool rtos_mutex_owner(rtos_mutex_t* mutex) {
  (void)mutex;
  return true;
}

void rtos_timer_create_static(rtos_timer_t* timer, rtos_timer_callback_t callback) {
  (void)timer;
  (void)callback;
}
bool rtos_timer_expired(rtos_timer_t* timer) {
  (void)timer;
  return false;
}
void rtos_timer_start(rtos_timer_t* timer, uint32_t duration_ms) {
  (void)duration_ms;
  if (timer) {
    timer->active = true;
  }
}
void rtos_timer_stop(rtos_timer_t* timer) {
  if (timer) {
    timer->active = false;
  }
}
void rtos_timer_restart(rtos_timer_t* timer) {
  if (timer) {
    timer->active = true;
  }
}
uint32_t rtos_timer_remaining_ms(rtos_timer_t* timer) {
  (void)timer;
  return 0;
}

fwpb_display_result ui_execute_command(const fwpb_display_command* cmd) {
  if (cmd) {
    memcpy(&last_command, cmd, sizeof(last_command));
    ui_execute_command_call_count++;
  }

  return fwpb_display_result_DISPLAY_RESULT_SUCCESS;
}

secure_bool_t onboarding_complete(void) {
  return stub_onboarding_complete;
}

void onboarding_wipe_state(void) {}

void display_controller_query_fingerprint_status(void) {
  fingerprint_status_query_count++;
}

bool display_controller_delete_fingerprint(uint8_t slot_index) {
  fingerprint_delete_call_count++;
  last_deleted_fingerprint = slot_index;
  return fingerprint_delete_send_succeeds;
}

static void boot_controller(void) {
  display_controller_init();
  display_controller_show_initial_screen();

  device_info_t info = {0};
  info.brightness_percent = 80;
  display_controller_handle_ui_event(UI_EVENT_SET_DEVICE_INFO, &info, sizeof(info));
}

static void advance_ticks(uint32_t ticks) {
  for (uint32_t i = 0; i < ticks; i++) {
    display_controller_tick();
  }
}

static void reset_stubs(void) {
  memset(&last_command, 0, sizeof(last_command));
  ui_execute_command_call_count = 0;
  stub_onboarding_complete = SECURE_FALSE;
  fingerprint_delete_call_count = 0;
  last_deleted_fingerprint = UINT8_MAX;
  fingerprint_delete_send_succeeds = true;
  fingerprint_status_query_count = 0;
}

static void setup(void) {
  reset_stubs();
}

TestSuite(display_controller, .init = setup);

static void send_fingerprint_status(uint8_t enrolled_mask) {
  fingerprint_status_response_t status = {0};
  for (uint8_t slot = 0; slot < FINGERPRINT_SLOT_COUNT; slot++) {
    if (enrolled_mask & (1u << slot)) {
      status.indices[status.count] = slot;
      snprintf(status.labels[status.count], sizeof(status.labels[status.count]), "Fingerprint %u",
               slot + 1u);
      status.count++;
    }
  }
  display_controller_handle_ui_event(UI_EVENT_FINGERPRINT_STATUS, &status, sizeof(status));
}

static void open_fingerprint_menu(uint8_t enrolled_mask) {
  stub_onboarding_complete = SECURE_TRUE;
  boot_controller();
  display_controller_handle_ui_event(UI_EVENT_AUTH_SUCCESS, NULL, 0);
  advance_ticks(4);
  display_controller_handle_action_menu();
  display_controller_handle_action_exit_with_data(
    fwpb_display_menu_item_DISPLAY_MENU_ITEM_FINGERPRINTS);
  send_fingerprint_status(enrolled_mask);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_menu_fingerprints_tag);
}

static void complete_fingerprint_removal(uint8_t remaining_mask) {
  display_controller_handle_ui_event(UI_EVENT_FINGERPRINT_DELETED, NULL, 0);
  send_fingerprint_status(remaining_mask);
}

static void show_fingerprint_menu_after_action(uint8_t remaining_slot) {
  fingerprint_auth_data_t auth_data = {.template_index = remaining_slot};
  display_controller_handle_ui_event(UI_EVENT_AUTH_SUCCESS, &auth_data, sizeof(auth_data));
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_menu_fingerprints_tag);
}

Test(display_controller, removes_third_fingerprint_when_second_slot_is_empty) {
  open_fingerprint_menu(0x5);
  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[1],
                   "Fingerprint 3");

  display_controller_handle_action_delete_fingerprint(1);
  cr_assert_eq(fingerprint_delete_call_count, 1);
  cr_assert_eq(last_deleted_fingerprint, 2);
  complete_fingerprint_removal(0x1);

  cr_assert(last_command.command.show_screen.params.menu_fingerprints.enrolled[0]);
  cr_assert_not(last_command.command.show_screen.params.menu_fingerprints.enrolled[1]);
  cr_assert_not(last_command.command.show_screen.params.menu_fingerprints.enrolled[2]);
  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[0],
                   "Fingerprint 1");
}

Test(display_controller, removes_second_fingerprint_when_first_slot_is_empty) {
  open_fingerprint_menu(0x6);
  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[0],
                   "Fingerprint 2");

  display_controller_handle_action_delete_fingerprint(0);
  cr_assert_eq(fingerprint_delete_call_count, 1);
  cr_assert_eq(last_deleted_fingerprint, 1);
  complete_fingerprint_removal(0x4);

  cr_assert(last_command.command.show_screen.params.menu_fingerprints.enrolled[0]);
  cr_assert_not(last_command.command.show_screen.params.menu_fingerprints.enrolled[1]);
  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[0],
                   "Fingerprint 3");
}

Test(display_controller, removes_third_fingerprint_when_all_slots_are_enrolled) {
  open_fingerprint_menu(0x7);

  display_controller_handle_action_delete_fingerprint(2);
  cr_assert_eq(fingerprint_delete_call_count, 1);
  cr_assert_eq(last_deleted_fingerprint, 2);
  complete_fingerprint_removal(0x3);

  cr_assert(last_command.command.show_screen.params.menu_fingerprints.enrolled[0]);
  cr_assert(last_command.command.show_screen.params.menu_fingerprints.enrolled[1]);
  cr_assert_not(last_command.command.show_screen.params.menu_fingerprints.enrolled[2]);
  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[1],
                   "Fingerprint 2");
}

Test(display_controller, removes_selected_fingerprint_after_menu_reorders) {
  open_fingerprint_menu(0x7);
  display_controller_handle_action_delete_fingerprint(0);
  cr_assert_eq(last_deleted_fingerprint, 0);
  complete_fingerprint_removal(0x6);
  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[1],
                   "Fingerprint 3");

  display_controller_handle_action_delete_fingerprint(1);
  cr_assert_eq(fingerprint_delete_call_count, 2);
  cr_assert_eq(last_deleted_fingerprint, 2);
  complete_fingerprint_removal(0x2);

  cr_assert(last_command.command.show_screen.params.menu_fingerprints.enrolled[0]);
  cr_assert_not(last_command.command.show_screen.params.menu_fingerprints.enrolled[1]);
  cr_assert_not(last_command.command.show_screen.params.menu_fingerprints.enrolled[2]);
  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[0],
                   "Fingerprint 2");
}

Test(display_controller, keeps_fingerprint_order_while_removal_is_pending) {
  open_fingerprint_menu(0x7);
  display_controller_handle_action_delete_fingerprint(0);
  show_fingerprint_menu_after_action(2);

  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[0],
                   "Fingerprint 1");
  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[1],
                   "Fingerprint 2");
  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[2],
                   "Fingerprint 3");
}

Test(display_controller, repeated_removal_does_not_change_the_displayed_fingerprint_order) {
  open_fingerprint_menu(0x7);
  display_controller_handle_action_delete_fingerprint(0);
  display_controller_handle_action_delete_fingerprint(1);
  cr_assert_eq(fingerprint_delete_call_count, 1);
  cr_assert_eq(last_deleted_fingerprint, 0);
  show_fingerprint_menu_after_action(2);

  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[0],
                   "Fingerprint 1");
  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[1],
                   "Fingerprint 2");
  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[2],
                   "Fingerprint 3");
}

Test(display_controller, waits_for_deletion_reply_and_status_before_accepting_another_removal) {
  open_fingerprint_menu(0x7);
  display_controller_handle_action_delete_fingerprint(0);

  // An earlier status query must not acknowledge the in-flight delete.
  send_fingerprint_status(0x7);
  display_controller_handle_action_delete_fingerprint(1);
  cr_assert_eq(fingerprint_delete_call_count, 1);

  uint32_t queries_before_reply = fingerprint_status_query_count;
  display_controller_handle_ui_event(UI_EVENT_FINGERPRINT_DELETED, NULL, 0);
  cr_assert_eq(fingerprint_status_query_count, queries_before_reply + 1);
  display_controller_handle_action_delete_fingerprint(1);
  cr_assert_eq(fingerprint_delete_call_count, 1);

  send_fingerprint_status(0x6);
  display_controller_handle_action_delete_fingerprint(0);
  cr_assert_eq(fingerprint_delete_call_count, 2);
  cr_assert_eq(last_deleted_fingerprint, 1);
}

Test(display_controller, failed_removal_keeps_the_fingerprint_and_allows_retry_after_refresh) {
  open_fingerprint_menu(0x5);
  display_controller_handle_action_delete_fingerprint(1);
  cr_assert_eq(last_deleted_fingerprint, 2);

  display_controller_handle_ui_event(UI_EVENT_FINGERPRINT_DELETE_FAILED, NULL, 0);
  display_controller_handle_action_delete_fingerprint(1);
  cr_assert_eq(fingerprint_delete_call_count, 1);

  send_fingerprint_status(0x5);
  cr_assert_str_eq(last_command.command.show_screen.params.menu_fingerprints.labels[1],
                   "Fingerprint 3");
  display_controller_handle_action_delete_fingerprint(1);
  cr_assert_eq(fingerprint_delete_call_count, 2);
  cr_assert_eq(last_deleted_fingerprint, 2);
}

Test(display_controller, failed_send_does_not_block_removal_retry) {
  open_fingerprint_menu(0x7);
  fingerprint_delete_send_succeeds = false;
  display_controller_handle_action_delete_fingerprint(0);

  fingerprint_delete_send_succeeds = true;
  display_controller_handle_action_delete_fingerprint(0);
  cr_assert_eq(fingerprint_delete_call_count, 2);
  cr_assert_eq(last_deleted_fingerprint, 0);
}

Test(display_controller, rejects_removal_of_empty_slots_and_the_last_fingerprint) {
  open_fingerprint_menu(0x1);
  display_controller_handle_action_delete_fingerprint(0);
  display_controller_handle_action_delete_fingerprint(1);
  cr_assert_eq(fingerprint_delete_call_count, 0);

  open_fingerprint_menu(0x5);
  display_controller_handle_action_delete_fingerprint(2);
  display_controller_handle_action_delete_fingerprint(UINT8_MAX);
  cr_assert_eq(fingerprint_delete_call_count, 0);
}

Test(display_controller, pending_removal_survives_menu_exit_and_refreshes_while_away) {
  open_fingerprint_menu(0x7);
  display_controller_handle_action_delete_fingerprint(0);
  display_controller_handle_action_back();
  display_controller_handle_action_exit_with_data(
    fwpb_display_menu_item_DISPLAY_MENU_ITEM_FINGERPRINTS);
  send_fingerprint_status(0x7);
  display_controller_handle_action_delete_fingerprint(1);
  cr_assert_eq(fingerprint_delete_call_count, 1);

  display_controller_handle_action_back();
  complete_fingerprint_removal(0x6);
  display_controller_handle_action_exit_with_data(
    fwpb_display_menu_item_DISPLAY_MENU_ITEM_FINGERPRINTS);
  send_fingerprint_status(0x6);
  display_controller_handle_action_delete_fingerprint(0);
  cr_assert_eq(fingerprint_delete_call_count, 2);
  cr_assert_eq(last_deleted_fingerprint, 1);
}

Test(display_controller, uxc_send_failure_threshold_is_one_shot_and_resets_on_success) {
  uint8_t failure_count = 0;

  for (uint8_t i = 0; i < DISPLAY_UXC_RESET_FAILURE_THRESHOLD - 1; i++) {
    cr_assert(!display_controller_update_uxc_send_failures(false, &failure_count));
  }

  cr_assert_eq(DISPLAY_UXC_RESET_FAILURE_THRESHOLD - 1, failure_count);
  cr_assert(display_controller_update_uxc_send_failures(false, &failure_count));
  cr_assert_eq(DISPLAY_UXC_RESET_FAILURE_THRESHOLD, failure_count);

  cr_assert(!display_controller_update_uxc_send_failures(false, &failure_count));
  cr_assert_eq(DISPLAY_UXC_RESET_FAILURE_THRESHOLD, failure_count);

  cr_assert(!display_controller_update_uxc_send_failures(true, &failure_count));
  cr_assert_eq(0, failure_count);
}

Test(display_controller, fingerprint_status_rejects_count_beyond_payload_capacity) {
  display_controller_init();

  fingerprint_status_response_t valid = {
    .count = FINGERPRINT_SLOT_COUNT,
    .indices = {0, 1, 2},
    .labels = {"trusted 0", "trusted 1", "trusted 2"},
  };
  display_controller_handle_ui_event(UI_EVENT_FINGERPRINT_STATUS, &valid, sizeof(valid));

  struct {
    fingerprint_status_response_t response;
    char adjacent_label[32];
  } malformed = {0};
  malformed.response.count = FINGERPRINT_SLOT_COUNT + 1;
  malformed.response.indices[0] = 0;
  malformed.response.indices[1] = 1;
  malformed.response.indices[2] = 2;
  malformed.response.labels[0][0] = 2;  // Read as indices[3] by a fourth iteration.
  strncpy(malformed.adjacent_label, "sentinel", sizeof(malformed.adjacent_label));

  display_controller_handle_ui_event(UI_EVENT_FINGERPRINT_STATUS, &malformed.response,
                                     sizeof(malformed.response));

  cr_assert(controller.fingerprint_enrolled[0]);
  cr_assert(controller.fingerprint_enrolled[1]);
  cr_assert(controller.fingerprint_enrolled[2]);
  cr_assert_str_eq(controller.fingerprint_labels[0], "trusted 0");
  cr_assert_str_eq(controller.fingerprint_labels[1], "trusted 1");
  cr_assert_str_eq(controller.fingerprint_labels[2], "trusted 2");
}

Test(display_controller, display_ready_from_power_off_wakes_locked_screen) {
  stub_onboarding_complete = SECURE_TRUE;
  boot_controller();

  display_controller_handle_ui_event(UI_EVENT_POWER_OFF, NULL, 0);
  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_power_off_tag);

  display_controller_show_initial_screen();

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_locked_tag);
}

Test(display_controller, onboarding_root_menu_hides_post_onboarding_items) {
  boot_controller();

  // Simulate onboarding state becoming "complete enough" mid-session without
  // leaving the onboarding UX.
  stub_onboarding_complete = SECURE_TRUE;

  display_controller_handle_action_menu();

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_menu_tag);
  cr_assert_eq(last_command.command.show_screen.params.menu.hide_lock_device, true);
  cr_assert_eq(last_command.command.show_screen.params.menu.hide_fingerprints, true);
}

Test(display_controller, lock_device_action_is_ignored_while_onboarding_context_active) {
  boot_controller();

  // Simulate the seed-only / partial-onboarding case: onboarding_complete()
  // has flipped true, but the controller is still in the onboarding UX.
  stub_onboarding_complete = SECURE_TRUE;

  display_controller_handle_action_menu();
  uint32_t command_count_before_lock = ui_execute_command_call_count;

  display_controller_handle_action_lock_device();

  cr_assert_eq(ui_execute_command_call_count, command_count_before_lock);
  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_menu_tag);
}

Test(display_controller, post_onboarding_menu_shows_items) {
  stub_onboarding_complete = SECURE_TRUE;

  boot_controller();

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_locked_tag);

  display_controller_handle_ui_event(UI_EVENT_AUTH_SUCCESS, NULL, 0);
  for (size_t i = 0; i < 4; i++) {
    display_controller_tick();
  }

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_scan_tag);
  cr_assert_eq(last_command.command.show_screen.params.scan.action,
               fwpb_display_params_scan_display_params_scan_action_TAP);

  display_controller_handle_action_menu();

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_menu_tag);
  cr_assert_eq(last_command.command.show_screen.params.menu.hide_lock_device, false);
  cr_assert_eq(last_command.command.show_screen.params.menu.hide_fingerprints, false);
}

Test(display_controller, onboarding_owned_enrollment_stays_required_from_menu) {
  boot_controller();

  display_controller_handle_action_menu();

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_menu_tag);

  display_controller_handle_ui_event(UI_EVENT_ENROLLMENT_START, NULL, 0);

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_fingerprint_tag);
  cr_assert_eq(last_command.command.show_screen.params.fingerprint.is_required, true);

  uint32_t command_count_before_back = ui_execute_command_call_count;
  display_controller_handle_action_back();

  cr_assert_eq(ui_execute_command_call_count, command_count_before_back);
  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_fingerprint_tag);
}

Test(display_controller, onboarding_root_menu_stays_filtered_after_submenu_return) {
  boot_controller();

  display_controller_handle_action_menu();

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_menu_tag);
  cr_assert_eq(last_command.command.show_screen.params.menu.hide_lock_device, true);
  cr_assert_eq(last_command.command.show_screen.params.menu.hide_fingerprints, true);

  // Simulate onboarding becoming "complete enough" while the onboarding-rooted
  // menu session is already in progress.
  stub_onboarding_complete = SECURE_TRUE;

  display_controller_handle_action_exit_with_data(
    fwpb_display_menu_item_DISPLAY_MENU_ITEM_BRIGHTNESS);

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_brightness_tag);

  display_controller_handle_action_back();

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_menu_tag);
  cr_assert_eq(last_command.command.show_screen.params.menu.hide_lock_device, true);
  cr_assert_eq(last_command.command.show_screen.params.menu.hide_fingerprints, true);
}

Test(display_controller, onboarding_owned_submenu_enrollment_stays_required) {
  boot_controller();

  display_controller_handle_action_menu();
  display_controller_handle_action_exit_with_data(
    fwpb_display_menu_item_DISPLAY_MENU_ITEM_BRIGHTNESS);

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_brightness_tag);

  display_controller_handle_ui_event(UI_EVENT_ENROLLMENT_START, NULL, 0);

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_fingerprint_tag);
  cr_assert_eq(last_command.command.show_screen.params.fingerprint.is_required, true);

  uint32_t command_count_before_back = ui_execute_command_call_count;
  display_controller_handle_action_back();

  cr_assert_eq(ui_execute_command_call_count, command_count_before_back);
  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_fingerprint_tag);
}

Test(display_controller, non_onboarding_root_menu_does_not_reuse_stale_onboarding_owner) {
  boot_controller();

  display_controller_handle_action_menu();
  display_controller_handle_action_back();

  stub_onboarding_complete = SECURE_TRUE;

  send_transaction_data_t send = {0};
  send.flow = fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SEND;
  display_controller_handle_ui_event(UI_EVENT_START_SEND_TRANSACTION, &send, sizeof(send));

  display_controller_handle_action_menu();

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_menu_tag);
  cr_assert_eq(last_command.command.show_screen.params.menu.hide_lock_device, false);
  cr_assert_eq(last_command.command.show_screen.params.menu.hide_fingerprints, false);

  display_controller_handle_action_back();

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_scan_tag);
}

Test(display_controller, onboarding_complete_is_ignored_outside_pre_onboarding_context) {
  stub_onboarding_complete = SECURE_TRUE;

  boot_controller();
  display_controller_handle_ui_event(UI_EVENT_AUTH_SUCCESS, NULL, 0);
  advance_ticks(4);

  uint32_t command_count_before = ui_execute_command_call_count;
  display_controller_handle_ui_event(UI_EVENT_SHOW_ONBOARDING_COMPLETE, NULL, 0);

  cr_assert_eq(ui_execute_command_call_count, command_count_before);
  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_scan_tag);
}

Test(display_controller, onboarding_complete_is_accepted_after_required_enrollment_scan_fallback) {
  boot_controller();

  display_controller_handle_ui_event(UI_EVENT_ENROLLMENT_START, NULL, 0);
  display_controller_handle_ui_event(UI_EVENT_ENROLLMENT_COMPLETE, NULL, 0);
  advance_ticks(MS_TO_DISPLAY_TICKS(1000));

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_scan_tag);
  cr_assert_eq(last_command.command.show_screen.params.scan.action,
               fwpb_display_params_scan_display_params_scan_action_CONFIRM);

  display_controller_handle_ui_event(UI_EVENT_SHOW_ONBOARDING_COMPLETE, NULL, 0);

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_onboarding_complete_tag);
}

Test(display_controller, required_enrollment_from_onboarding_menu_returns_to_confirm_scan) {
  boot_controller();

  display_controller_handle_action_menu();
  display_controller_handle_ui_event(UI_EVENT_ENROLLMENT_START, NULL, 0);
  display_controller_handle_ui_event(UI_EVENT_ENROLLMENT_COMPLETE, NULL, 0);
  advance_ticks(MS_TO_DISPLAY_TICKS(1000));

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_scan_tag);
  cr_assert_eq(last_command.command.show_screen.params.scan.action,
               fwpb_display_params_scan_display_params_scan_action_CONFIRM);
}

Test(display_controller, confirm_scan_cancel_after_required_enrollment_returns_to_onboarding_scan) {
  boot_controller();

  display_controller_handle_ui_event(UI_EVENT_ENROLLMENT_START, NULL, 0);
  display_controller_handle_ui_event(UI_EVENT_ENROLLMENT_COMPLETE, NULL, 0);
  advance_ticks(MS_TO_DISPLAY_TICKS(1000));

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_scan_tag);
  cr_assert_eq(last_command.command.show_screen.params.scan.action,
               fwpb_display_params_scan_display_params_scan_action_CONFIRM);

  display_controller_handle_action_cancel();

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_onboarding_tag);
  cr_assert_eq(last_command.command.show_screen.params.onboarding.resume_at_scan, true);
}

Test(display_controller, onboarding_complete_suppresses_escape_events_until_lock) {
  boot_controller();

  display_controller_handle_ui_event(UI_EVENT_SHOW_ONBOARDING_COMPLETE, NULL, 0);

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_onboarding_complete_tag);

  uint32_t command_count_before = ui_execute_command_call_count;
  display_controller_handle_ui_event(UI_EVENT_SHOW_MENU, NULL, 0);

  receive_transaction_data_t receive = {0};
  strncpy(receive.address, "bc1qterminaltest", sizeof(receive.address) - 1);
  display_controller_handle_ui_event(UI_EVENT_AUTH_SUCCESS, NULL, 0);
  display_controller_handle_ui_event(UI_EVENT_START_RECEIVE_TRANSACTION, &receive, sizeof(receive));

  cr_assert_eq(ui_execute_command_call_count, command_count_before);
  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_onboarding_complete_tag);
}

Test(display_controller, onboarding_complete_locks_after_timeout) {
  boot_controller();

  display_controller_handle_ui_event(UI_EVENT_SHOW_ONBOARDING_COMPLETE, NULL, 0);
  advance_ticks(MS_TO_DISPLAY_TICKS(6000));

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_locked_tag);
}

Test(display_controller, onboarding_complete_timeout_waits_for_initial_screen) {
  display_controller_init();
  display_controller_show_initial_screen();

  display_controller_handle_ui_event(UI_EVENT_SHOW_ONBOARDING_COMPLETE, NULL, 0);
  advance_ticks(MS_TO_DISPLAY_TICKS(6000) + 50);

  cr_assert_eq(ui_execute_command_call_count, 0);

  device_info_t info = {0};
  info.brightness_percent = 80;
  display_controller_handle_ui_event(UI_EVENT_SET_DEVICE_INFO, &info, sizeof(info));

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_onboarding_complete_tag);

  advance_ticks(MS_TO_DISPLAY_TICKS(6000));

  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_locked_tag);
}

Test(display_controller, ready_after_device_info_shows_initial_screen) {
  display_controller_init();

  device_info_t info = {0};
  info.brightness_percent = 80;
  display_controller_handle_ui_event(UI_EVENT_SET_DEVICE_INFO, &info, sizeof(info));

  cr_assert_eq(ui_execute_command_call_count, 0);

  display_controller_show_initial_screen();

  cr_assert_eq(ui_execute_command_call_count, 1);
  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params,
               fwpb_display_show_screen_onboarding_tag);
}

Test(display_controller, repeated_ready_replays_current_screen) {
  stub_onboarding_complete = SECURE_TRUE;
  boot_controller();

  display_controller_handle_ui_event(UI_EVENT_AUTH_SUCCESS, NULL, 0);
  advance_ticks(4);

  uint32_t command_count_before = ui_execute_command_call_count;

  display_controller_show_initial_screen();

  cr_assert_eq(ui_execute_command_call_count, command_count_before + 1);
  cr_assert_eq(last_command.which_command, fwpb_display_command_show_screen_tag);
  cr_assert_eq(last_command.command.show_screen.which_params, fwpb_display_show_screen_scan_tag);
}
