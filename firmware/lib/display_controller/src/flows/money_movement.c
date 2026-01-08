#include "display_controller.h"
#include "display_controller_internal.h"
#include "log.h"

#include <stdio.h>
#include <string.h>

// Constants
#define MAX_CHARS_PER_SCREEN             60  // 3 groups x 4 chars x 5 lines
#define CHARS_PER_PAGE_WITH_CONTINUATION 56  // 60 - 4 for ellipsis

// Wrapper structure for transaction flow data
typedef struct {
  transaction_type_t type;
  union {
    send_transaction_data_t send;
    receive_transaction_data_t receive;
  } data;
} money_movement_flow_data_t;

static UI_TASK_DATA send_transaction_data_t send_transaction_data;
static UI_TASK_DATA receive_transaction_data_t receive_transaction_data;
static UI_TASK_DATA bool is_receive_flow = false;

// Navigation state extension for money movement
typedef struct {
  int total_address_pages;
  int current_address_page;
  int total_pages;  // Total including amount and confirm
  int current_page;
  bool on_confirm_page;
  uint8_t button_selected;  // 0 = verify, 1 = cancel, 2 = menu
} money_movement_nav_t;

static UI_TASK_DATA money_movement_nav_t nav_state;

// Calculate number of address pages needed
static int calculate_address_pages(const char* address) {
  int addr_len = strlen(address);

  if (addr_len <= MAX_CHARS_PER_SCREEN) {
    return 1;  // Fits on single screen
  }

  // For multi-page addresses, account for continuation overlap
  int chars_after_first = addr_len - MAX_CHARS_PER_SCREEN;
  int additional_pages =
    (chars_after_first + CHARS_PER_PAGE_WITH_CONTINUATION - 1) / CHARS_PER_PAGE_WITH_CONTINUATION;

  return 1 + additional_pages;
}

// Update screen parameters based on current navigation state
static void update_screen_params(display_controller_t* controller) {
  // Clear existing params
  memset(&controller->show_screen.params.money_movement, 0,
         sizeof(controller->show_screen.params.money_movement));

  // Always set flow type and page indicator info
  controller->show_screen.params.money_movement.is_receive_flow = is_receive_flow;
  controller->show_screen.params.money_movement.total_pages = nav_state.total_pages;

  // For page indicator, ensure we use the right index
  // When on_confirm_page is true, we're on the last page
  if (nav_state.on_confirm_page) {
    controller->show_screen.params.money_movement.current_page_index = nav_state.total_pages - 1;
  } else {
    controller->show_screen.params.money_movement.current_page_index = nav_state.current_page;
  }

  // Always populate address for page calculation in UI
  const char* address =
    is_receive_flow ? receive_transaction_data.address : send_transaction_data.address;
  strncpy(controller->show_screen.params.money_movement.address, address,
          sizeof(controller->show_screen.params.money_movement.address) - 1);

  if (nav_state.current_page < nav_state.total_address_pages) {
    // Address page - always use "ADDRESS" title regardless of flow type
    controller->show_screen.params.money_movement.step =
      fwpb_display_params_money_movement_display_params_money_movement_step_ADDRESS;
    controller->show_screen.params.money_movement.current_address_page =
      (nav_state.total_address_pages == 1 ? 0 : nav_state.current_page);

  } else if (!is_receive_flow && nav_state.current_page == nav_state.total_address_pages) {
    // Amount page (only for send flow)
    strncpy(controller->show_screen.params.money_movement.amount_sats,
            send_transaction_data.amount_sats,
            sizeof(controller->show_screen.params.money_movement.amount_sats) - 1);
    strncpy(controller->show_screen.params.money_movement.amount_usd,
            send_transaction_data.amount_usd,
            sizeof(controller->show_screen.params.money_movement.amount_usd) - 1);
    strncpy(controller->show_screen.params.money_movement.fee_sats, send_transaction_data.fee_sats,
            sizeof(controller->show_screen.params.money_movement.fee_sats) - 1);
    strncpy(controller->show_screen.params.money_movement.fee_usd, send_transaction_data.fee_usd,
            sizeof(controller->show_screen.params.money_movement.fee_usd) - 1);
    controller->show_screen.params.money_movement.step =
      fwpb_display_params_money_movement_display_params_money_movement_step_AMOUNT;

  } else if (nav_state.on_confirm_page || nav_state.current_page == nav_state.total_pages - 1) {
    // Confirm page
    controller->show_screen.params.money_movement.step =
      fwpb_display_params_money_movement_display_params_money_movement_step_CONFIRM;
    controller->show_screen.params.money_movement.button_selection = nav_state.button_selected;
  }
}

void display_controller_money_movement_on_enter(display_controller_t* controller,
                                                const void* data) {
  // Check transaction type and store data
  if (data) {
    // First check if it has a type field
    const transaction_type_t* type_ptr = (const transaction_type_t*)data;

    if (*type_ptr == TRANSACTION_TYPE_RECEIVE) {
      // Receive transaction
      is_receive_flow = true;
      const struct {
        transaction_type_t type;
        receive_transaction_data_t data;
      }* receive_wrapper = data;

      // Copy receive transaction data
      memcpy(&receive_transaction_data, &receive_wrapper->data, sizeof(receive_transaction_data_t));
    } else if (*type_ptr == TRANSACTION_TYPE_SEND) {
      // Send transaction
      is_receive_flow = false;
      const struct {
        transaction_type_t type;
        send_transaction_data_t data;
      }* send_wrapper = data;
      memcpy(&send_transaction_data, &send_wrapper->data, sizeof(send_transaction_data_t));
    } else {
      // Invalid transaction type
      LOGE("[MoneyMovement] ERROR: Invalid transaction type: %d", *type_ptr);
      return;
    }
  }

  // Initialize navigation state
  memset(&nav_state, 0, sizeof(nav_state));
  const char* address =
    is_receive_flow ? receive_transaction_data.address : send_transaction_data.address;
  nav_state.total_address_pages = calculate_address_pages(address);

  if (is_receive_flow) {
    // Receive flow: address pages + confirm (no amount page)
    nav_state.total_pages = nav_state.total_address_pages + 1;
  } else {
    // Send flow: address pages + amount + confirm
    nav_state.total_pages = nav_state.total_address_pages + 2;
  }

  nav_state.current_page = 0;
  nav_state.on_confirm_page = false;
  nav_state.button_selected = 0;  // Start with verify selected

  // Set up first screen
  update_screen_params(controller);
  controller->show_screen.which_params = fwpb_display_show_screen_money_movement_tag;
}

flow_action_t display_controller_money_movement_on_button_press(
  display_controller_t* controller, const button_event_payload_t* event) {
  if (event->type != BUTTON_PRESS_SINGLE) {
    return FLOW_ACTION_NONE;
  }

  // Handle both buttons pressed on confirm page
  if (nav_state.on_confirm_page && event->button == BUTTON_BOTH) {
    // Confirm selection
    if (nav_state.button_selected == 0) {
      return FLOW_ACTION_APPROVE;  // Verify & Sign
    } else if (nav_state.button_selected == 1) {
      return FLOW_ACTION_CANCEL;  // Cancel
    } else if (nav_state.button_selected == 2) {
      return FLOW_ACTION_EXIT;  // Menu - exit to menu
    }
  }

  // Single button press handling
  if (nav_state.on_confirm_page) {
    // On confirm page - handle button selection and navigation
    if (event->button == BUTTON_LEFT) {
      if (nav_state.button_selected == 0) {
        // If on verify button, go back to previous page
        nav_state.on_confirm_page = false;
        // For receive flow, go back to last address page
        // For send flow, go back to amount page
        if (is_receive_flow) {
          nav_state.current_page = nav_state.total_address_pages - 1;
        } else {
          nav_state.current_page = nav_state.total_pages - 2;  // Back to amount
        }
        update_screen_params(controller);
        return FLOW_ACTION_REFRESH;
      } else if (nav_state.button_selected == 1) {
        // Move from cancel to verify button
        nav_state.button_selected = 0;
        update_screen_params(controller);
        return FLOW_ACTION_REFRESH;
      } else if (nav_state.button_selected == 2) {
        // Move from menu to cancel button (going up)
        nav_state.button_selected = 1;
        update_screen_params(controller);
        return FLOW_ACTION_REFRESH;
      }
    } else if (event->button == BUTTON_RIGHT) {
      if (nav_state.button_selected == 0) {
        // Move from verify to cancel button
        nav_state.button_selected = 1;
        update_screen_params(controller);
        return FLOW_ACTION_REFRESH;
      } else if (nav_state.button_selected == 1) {
        // Move from cancel to menu button (going down)
        nav_state.button_selected = 2;
        update_screen_params(controller);
        return FLOW_ACTION_REFRESH;
      } else if (nav_state.button_selected == 2) {
        // Already on menu, can't go further
        return FLOW_ACTION_NONE;
      }
    }

  } else {
    // On address or amount pages - handle page navigation
    if (event->button == BUTTON_LEFT) {
      // Previous page
      if (nav_state.current_page > 0) {
        nav_state.current_page--;
        update_screen_params(controller);
        return FLOW_ACTION_REFRESH;
      }

    } else if (event->button == BUTTON_RIGHT) {
      // Next page
      if (is_receive_flow) {
        // Receive flow navigation
        if (nav_state.current_page < nav_state.total_address_pages - 1) {
          // Move to next address page
          nav_state.current_page++;
          update_screen_params(controller);
          return FLOW_ACTION_REFRESH;
        } else if (nav_state.current_page == nav_state.total_address_pages - 1) {
          // From last address page, go to confirm page
          nav_state.on_confirm_page = true;
          nav_state.current_page++;
          nav_state.button_selected = 0;  // Start with verify selected
          update_screen_params(controller);
          return FLOW_ACTION_REFRESH;
        }
      } else {
        // Send flow navigation
        if (nav_state.current_page < nav_state.total_pages - 2) {
          // Move to next address or amount page
          nav_state.current_page++;
          update_screen_params(controller);
          return FLOW_ACTION_REFRESH;
        } else if (nav_state.current_page == nav_state.total_pages - 2) {
          // From amount page, go to confirm page
          nav_state.on_confirm_page = true;
          nav_state.current_page++;
          nav_state.button_selected = 0;  // Start with verify selected
          update_screen_params(controller);
          return FLOW_ACTION_REFRESH;
        }
      }
    }
  }

  return FLOW_ACTION_NONE;
}

void display_controller_money_movement_on_exit(display_controller_t* controller) {
  (void)controller;
  // Clean up transaction data
  memset(&send_transaction_data, 0, sizeof(send_transaction_data));
  memset(&receive_transaction_data, 0, sizeof(receive_transaction_data));
  memset(&nav_state, 0, sizeof(nav_state));
}

void display_controller_money_movement_on_tick(display_controller_t* controller) {
  (void)controller;
}
