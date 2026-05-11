#include "display_controller.h"
#include "display_controller_internal.h"
#include "langpack_ids.h"

#ifdef EMBEDDED_BUILD
#include "confirmation_manager.h"
#include "ipc.h"
#include "nfc_control.h"
#endif

#include "auth.h"
#include "rtos_thread.h"

#include <stdio.h>
#include <string.h>

// Maximum time to keep the receive address visible before locking (5 minutes).
#define RECEIVE_ADDRESS_TIMEOUT_MS (300000)

// Handoff phase constants
#define HANDOFF_PHASE_NORMAL  0
#define HANDOFF_PHASE_SIGNING 1

#define SIGNING_DURATION_TICKS MS_TO_DISPLAY_TICKS(3000)

// Per-input signing cost is ~100-200ms (BIP32 derivation + ECDSA + flash I/O).
// With the 200-UTXO limit, worst case is ~40s; 120s provides headroom.
#define SIGNING_NFC_DISABLE_TIMEOUT (120000)

#ifdef EMBEDDED_BUILD
// NFC disable token held during the send confirmation + signing flow.
// Acquired when the flow enters for a send, released when the handoff
// timer transitions to the scan screen (or on flow exit as a safety net).
static nfc_disable_token_t send_nfc_token = NFC_CONTROL_INVALID_TOKEN;
#endif

// Populate screen parameters with transaction data
static void update_screen_params(display_controller_t* controller) {
  // Clear existing params
  memset(&controller->show_screen.params.money_movement, 0,
         sizeof(controller->show_screen.params.money_movement));

  // Copy flow type directly
  controller->show_screen.params.money_movement.flow = controller->nav.money_movement.flow;

  // Populate address - Display MCU will calculate pages
  bool is_receive =
    controller->nav.money_movement.flow == fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_RECEIVE;
  const char* address = is_receive ? controller->nav.money_movement.receive_data.address
                                   : controller->nav.money_movement.send_data.address;
  strncpy(controller->show_screen.params.money_movement.address, address,
          sizeof(controller->show_screen.params.money_movement.address) - 1);

  // Populate amount data for send flows (SEND or SELF_SEND)
  if (!is_receive) {
    strncpy(controller->show_screen.params.money_movement.amount_sats,
            controller->nav.money_movement.send_data.amount_sats,
            sizeof(controller->show_screen.params.money_movement.amount_sats) - 1);
    strncpy(controller->show_screen.params.money_movement.fee_sats,
            controller->nav.money_movement.send_data.fee_sats,
            sizeof(controller->show_screen.params.money_movement.fee_sats) - 1);

    // Forward display preferences to display MCU
    controller->show_screen.params.money_movement.btc_display_unit =
      controller->nav.money_movement.send_data.btc_display_unit;
  }

  // Note: step field no longer set - Display MCU manages all navigation
}

void display_controller_money_movement_on_enter(display_controller_t* controller,
                                                const void* entry_data) {
  // Extract transaction data from entry parameters
  if (entry_data) {
    memset(&controller->nav.money_movement, 0, sizeof(controller->nav.money_movement));

    const flow_transaction_entry_data_t* entry = (const flow_transaction_entry_data_t*)entry_data;
    controller->nav.money_movement.flow = entry->flow;
    if (entry->flow == fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_RECEIVE) {
      memcpy(&controller->nav.money_movement.receive_data, &entry->data.receive,
             sizeof(receive_transaction_data_t));
      controller->nav.money_movement.receive_start_time = rtos_thread_systime();
    } else {
      memcpy(&controller->nav.money_movement.send_data, &entry->data.send,
             sizeof(send_transaction_data_t));
    }
  } else {
    // Some MFG/debug entry paths seed nav.money_movement directly before flow
    // entry and do not pass entry_data. Preserve that payload and reset only
    // transient handoff state.
    controller->nav.money_movement.handoff_phase = HANDOFF_PHASE_NORMAL;
    controller->nav.money_movement.handoff_timer = 0;
  }

  update_screen_params(controller);

  controller->show_screen.which_params = fwpb_display_show_screen_money_movement_tag;
}

void display_controller_money_movement_on_exit(display_controller_t* controller) {
#ifdef EMBEDDED_BUILD
  nfc_enable(send_nfc_token);
  send_nfc_token = NFC_CONTROL_INVALID_TOKEN;
#endif
  // Clean up transaction data
  memset(&controller->nav.money_movement, 0, sizeof(controller->nav.money_movement));
}

flow_action_result_t display_controller_money_movement_on_tick(display_controller_t* controller) {
  bool is_receive =
    controller->nav.money_movement.flow == fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_RECEIVE;

  // The receive screen is passive — no touch interaction to trigger screen
  // updates (which normally refresh auth). Keep auth alive for up to 5
  // minutes so the user can verify the address, then let the auth timer
  // expire and lock the device.
  if (is_receive) {
    if (RTOS_DEADLINE(controller->nav.money_movement.receive_start_time,
                      RECEIVE_ADDRESS_TIMEOUT_MS)) {
      return flow_result_lock();
    }
    refresh_auth();
  }

#ifdef EMBEDDED_BUILD
  // Poll for confirmation expiry so the flow exits promptly if the user
  // idles too long between pages. The confirmation is already approved once
  // the handoff sequence starts, so only expire while still on the main flow.
  if (controller->nav.money_movement.handoff_phase == HANDOFF_PHASE_NORMAL &&
      confirmation_manager_is_expired()) {
    confirmation_manager_clear();
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  }
#endif

  // Drive the signing → confirm scan phase machine.
  // handoff_phase stays SIGNING after the scan screen is shown so that the
  // confirmation expiry guard above (which fires on HANDOFF_PHASE_NORMAL)
  // does not kick the user out while they are tapping their phone.
  if (controller->nav.money_movement.handoff_phase == HANDOFF_PHASE_SIGNING) {
    if (display_controller_tick_handoff_to_scan(controller,
                                                &controller->nav.money_movement.handoff_timer)) {
#ifdef EMBEDDED_BUILD
      // Scan screen shown — re-enable NFC so the phone can retrieve signatures.
      nfc_enable(send_nfc_token);
      send_nfc_token = NFC_CONTROL_INVALID_TOKEN;
#endif
    }
  }

  return flow_result_handled();
}

flow_action_result_t display_controller_money_movement_on_event(display_controller_t* controller,
                                                                ui_event_type_t event,
                                                                const void* data, uint32_t len) {
  (void)controller;
  (void)data;
  (void)len;
  (void)event;

  // Transaction events handled by display controller before flow entry
  return flow_result_handled();
}

flow_action_result_t display_controller_money_movement_on_action(
  display_controller_t* controller, fwpb_display_action_display_action_type action, uint32_t data) {
  (void)data;

  if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_APPROVE) {
    bool is_receive =
      controller->nav.money_movement.flow == fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_RECEIVE;

#ifdef EMBEDDED_BUILD
    // Only approve/sign if a sign-transaction confirmation is actually pending.
    bool should_sign = !is_receive && confirmation_manager_is_pending() &&
                       confirmation_manager_get_type() == CONFIRMATION_TYPE_SIGN_TRANSACTION;
    if (should_sign) {
      confirmation_manager_approve();
      send_nfc_token = nfc_disable(SIGNING_NFC_DISABLE_TIMEOUT);
    }
#else
    bool should_sign = !is_receive;
#endif

    if (should_sign) {
      // Set up the loading screen and handoff timer before waking the
      // key_manager — it runs at higher priority and could preempt
      // immediately after ipc_send_empty.
      memset(&controller->show_screen.params, 0, sizeof(controller->show_screen.params));
      controller->show_screen.params.confirmation.mode =
        fwpb_display_params_confirmation_display_params_confirmation_mode_DISPLAY_PARAMS_CONFIRMATION_MODE_LOADING;
      controller->show_screen.params.confirmation.text_id = LANGPACK_ID_CONFIRMATION_SIGNING;
      display_controller_show_screen(controller, fwpb_display_show_screen_confirmation_tag,
                                     fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                     TRANSITION_DURATION_STANDARD);
      controller->nav.money_movement.handoff_phase = HANDOFF_PHASE_SIGNING;
      controller->nav.money_movement.handoff_timer = SIGNING_DURATION_TICKS;
#ifdef EMBEDDED_BUILD
      ipc_send_empty(key_manager_port, IPC_KEY_MANAGER_SIGN_DEFERRED);
#endif
      return flow_result_handled();
    }

    if (is_receive) {
      // Receive verification is a passive, scrollable screen with no approve
      // handoff; only explicit back/cancel exits the flow.
      return flow_result_handled();
    }

    // Non-streaming send path (no pending sign confirmation) — just exit.
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  } else if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_CANCEL ||
             action == fwpb_display_action_display_action_type_DISPLAY_ACTION_BACK) {
    // Exit the flow - clear any pending confirmation and return to previous screen
#ifdef EMBEDDED_BUILD
    if (confirmation_manager_is_pending() &&
        confirmation_manager_get_type() == CONFIRMATION_TYPE_SIGN_TRANSACTION) {
      confirmation_manager_clear();
    }
#endif
    return flow_result_exit_with_transition(fwpb_display_transition_DISPLAY_TRANSITION_FADE,
                                            TRANSITION_DURATION_STANDARD);
  } else if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_MENU) {
    // Menu button on confirm screen - clear any pending confirmation before navigating away
#ifdef EMBEDDED_BUILD
    if (confirmation_manager_is_pending() &&
        confirmation_manager_get_type() == CONFIRMATION_TYPE_SIGN_TRANSACTION) {
      confirmation_manager_clear();
    }
#endif
    return flow_result_navigate(FLOW_MENU, fwpb_display_transition_DISPLAY_TRANSITION_FADE);
  } else if (action == fwpb_display_action_display_action_type_DISPLAY_ACTION_PAGE_CONFIRMED) {
    // The multi-page confirmation UI needs more time. Refresh the timestamp so
    // in-progress holds and newly visible steps do not consume the user's
    // confirmation budget.
#ifdef EMBEDDED_BUILD
    if (confirmation_manager_is_pending() &&
        confirmation_manager_get_type() == CONFIRMATION_TYPE_SIGN_TRANSACTION) {
      confirmation_manager_refresh_timestamp();
    }
#endif
    return flow_result_handled();
  }

  return flow_result_handled();
}
