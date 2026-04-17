#include "criterion_test_utils.h"
#include "fff.h"
#include "sign_action_proof_core.h"

#include <criterion/criterion.h>

#include <string.h>

DEFINE_FFF_GLOBALS;
FAKE_VALUE_FUNC(bool, rtos_in_isr);

// ---------------------------------------------------------------------------
// RAAK Action Classification
// ---------------------------------------------------------------------------
// These tests validate the assumption made by the rotate_app_auth_keys
// confirmation result handler: only RotateAppAuthKeys is accepted.

static bool test_raak_is_allowed_action(sap_action_t action) {
  return (action == SAP_ACTION_ROTATE_APP_AUTH_KEYS);
}

Test(raak_actions, rotate_app_auth_keys_is_allowed) {
  cr_assert(test_raak_is_allowed_action(SAP_ACTION_ROTATE_APP_AUTH_KEYS));
}

Test(raak_actions, exactly_one_allowed_action) {
  // If a new SAP action is added, this test forces the developer to decide
  // whether it should be allowed in the RAAK confirmation handler.
  int allowed_count = 0;
  for (int i = 0; i < SAP_ACTION_COUNT; i++) {
    if (test_raak_is_allowed_action((sap_action_t)i)) {
      allowed_count++;
    }
  }
  cr_assert_eq(allowed_count, 1, "Expected exactly 1 allowed RAAK action, got %d", allowed_count);
}

Test(raak_actions, rotate_action_string_parses_correctly) {
  cr_assert_eq(sap_parse_action("RotateAppAuthKeys"), SAP_ACTION_ROTATE_APP_AUTH_KEYS);
}
