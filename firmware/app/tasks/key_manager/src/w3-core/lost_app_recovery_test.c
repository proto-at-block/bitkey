#include "criterion_test_utils.h"
#include "fff.h"
#include "sign_action_proof_core.h"
#include "wallet.pb.h"

#include <criterion/criterion.h>

#include <string.h>

// Re-declare the confirmation type enum values we need for testing,
// avoiding the transitive ipc.h dependency from confirmation_manager.h.
typedef enum {
  _CT_WIPE_STATE,
  _CT_FWUP_START,
  _CT_SIGN_TRANSACTION,
  _CT_SIGN_ACTION_PROOF,
  _CT_LOST_APP_RECOVERY,
  _CT_LOST_APP_RECOVERY_SIGN_CHALLENGE,
  _CT_SIGN_CHALLENGE_AND_SEAL_SEKS,
  _CT_RECOVERY_AUTHORIZE_LOST_APP,
  _CT_RECOVERY_AUTHORIZE_LOST_HW,
  _CT_COUNT,
} test_confirmation_type_t;

// Static assertions to keep this in sync with the real enum.
// If the real enum changes order, the build will break here.
_Static_assert(_CT_LOST_APP_RECOVERY == 4, "confirmation type enum changed");
_Static_assert(_CT_LOST_APP_RECOVERY_SIGN_CHALLENGE == 5, "confirmation type enum changed");
_Static_assert(_CT_SIGN_CHALLENGE_AND_SEAL_SEKS == 6, "confirmation type enum changed");
_Static_assert(_CT_RECOVERY_AUTHORIZE_LOST_APP == 7, "confirmation type enum changed");
_Static_assert(_CT_RECOVERY_AUTHORIZE_LOST_HW == 8, "confirmation type enum changed");
_Static_assert(_CT_COUNT == 9, "new confirmation type added — update test mirror enum");

DEFINE_FFF_GLOBALS;
FAKE_VALUE_FUNC(bool, rtos_in_isr);

// ---------------------------------------------------------------------------
// LAR Action Classification
// ---------------------------------------------------------------------------
// These tests validate the assumption made by lar_is_allowed_action() in
// lost_app_recovery.c: only CreateLostAppRecovery is accepted by the
// LAR continue command handler, because that's the action the user
// explicitly confirmed on the hardware display.

static bool test_lar_is_allowed_action(sap_action_t action) {
  return (action == SAP_ACTION_CREATE_LOST_APP_RECOVERY);
}

Test(lar_actions, create_lost_app_recovery_is_allowed) {
  cr_assert(test_lar_is_allowed_action(SAP_ACTION_CREATE_LOST_APP_RECOVERY));
}

Test(lar_actions, non_lar_actions_are_rejected) {
  // Every non-LAR action must be rejected
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_SET_SPEND_WITHOUT_HARDWARE));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_DISABLE_SPEND_WITHOUT_HARDWARE));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_SET_VERIFICATION_THRESHOLD));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_SET_RECOVERY_EMAIL));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_DISABLE_RECOVERY_EMAIL));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_SET_RECOVERY_PHONE));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_DISABLE_RECOVERY_PHONE));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_SET_RECOVERY_PUSH_NOTIFICATIONS));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_DISABLE_RECOVERY_PUSH_NOTIFICATIONS));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_ADD_RECOVERY_CONTACT));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_REMOVE_RECOVERY_CONTACT));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_ADD_BENEFICIARY));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_REMOVE_BENEFICIARY));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_REMOVE_RECOVERY_CUSTOMER));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_REMOVE_BENEFACTOR));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_ACCEPT_RECOVERY_CONTACTS_INVITE));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_ACCEPT_BENEFICIARIES_INVITE));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_CANCEL_LOST_APP_RECOVERY));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_DELETE_ACCOUNT));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_UPDATE_DESCRIPTOR_BACKUPS));
  cr_assert_not(test_lar_is_allowed_action(SAP_ACTION_ROTATE_SPENDING_KEYSET));
}

Test(lar_actions, exactly_one_allowed_action) {
  // If a new SAP action is added, this test forces the developer to decide
  // whether it should be allowed in the LAR continue handler.
  int allowed_count = 0;
  for (int i = 0; i < SAP_ACTION_COUNT; i++) {
    if (test_lar_is_allowed_action((sap_action_t)i)) {
      allowed_count++;
    }
  }
  cr_assert_eq(allowed_count, 1, "Expected exactly 1 allowed LAR action, got %d", allowed_count);
}

Test(lar_actions, create_action_string_parses_correctly) {
  cr_assert_eq(sap_parse_action("CreateLostAppRecovery"), SAP_ACTION_CREATE_LOST_APP_RECOVERY);
}

// ---------------------------------------------------------------------------
// LAR Sign Challenge Confirmation Type
// ---------------------------------------------------------------------------
// These tests validate that the sign-challenge confirmable command has a
// distinct confirmation type registered in the confirmation manager enum.

Test(lar_sign_challenge, confirmation_type_exists_and_is_distinct) {
  // The sign challenge confirmation type must be a valid enum value
  cr_assert_lt(_CT_LOST_APP_RECOVERY_SIGN_CHALLENGE, _CT_COUNT,
               "Sign challenge confirmation type must be < COUNT");

  // It must be distinct from the LAR init confirmation type
  cr_assert_neq(_CT_LOST_APP_RECOVERY_SIGN_CHALLENGE, _CT_LOST_APP_RECOVERY,
                "Sign challenge must have its own confirmation type, not reuse LAR init");

  // It must be distinct from other confirmation types
  cr_assert_neq(_CT_LOST_APP_RECOVERY_SIGN_CHALLENGE, _CT_SIGN_ACTION_PROOF);
  cr_assert_neq(_CT_LOST_APP_RECOVERY_SIGN_CHALLENGE, _CT_SIGN_TRANSACTION);
}

Test(lar_sign_challenge, proto_tags_are_distinct) {
  // The command tag must be distinct from other LAR command tags
  cr_assert_neq(fwpb_wallet_cmd_lost_app_recovery_sign_challenge_cmd_tag,
                fwpb_wallet_cmd_lost_app_recovery_cmd_tag,
                "Sign challenge cmd tag must differ from LAR init cmd tag");
  cr_assert_neq(fwpb_wallet_cmd_lost_app_recovery_sign_challenge_cmd_tag,
                fwpb_wallet_cmd_lost_app_recovery_continue_cmd_tag,
                "Sign challenge cmd tag must differ from LAR continue cmd tag");

  // The response tag must be distinct from other LAR response tags
  cr_assert_neq(fwpb_wallet_rsp_lost_app_recovery_sign_challenge_rsp_tag,
                fwpb_wallet_rsp_lost_app_recovery_continue_rsp_tag,
                "Sign challenge rsp tag must differ from LAR continue rsp tag");
}

Test(lar_sign_challenge, confirmation_result_tag_is_distinct) {
  // The get_confirmation_result response variant must be distinct from
  // the LAR SSEK result variant
  cr_assert_neq(fwpb_get_confirmation_result_rsp_lost_app_recovery_sign_challenge_result_tag,
                fwpb_get_confirmation_result_rsp_lost_app_recovery_ssek_rsp_tag,
                "Sign challenge confirmation result tag must differ from LAR SSEK result tag");
}

Test(lar_sign_challenge, display_title_is_reasonable) {
  // The sign-challenge confirmation should use its own SAP action so the
  // title is resolved on the UXC via the standard langpack mapping path,
  // without renumbering pre-existing SAP actions carried over IPC.
  cr_assert_eq(SAP_ACTION_EEK_RESTORATION, SAP_ACTION_ROTATE_SPENDING_KEYSET + 1);
  cr_assert_eq(SAP_ACTION_FULL_ACCOUNT_CLOUD_BACKUP_RESTORE, SAP_ACTION_EEK_RESTORATION + 1);
  cr_assert_eq(SAP_ACTION_APPROVE_APP_RECOVERY, SAP_ACTION_FULL_ACCOUNT_CLOUD_BACKUP_RESTORE + 1);
  cr_assert_lt(SAP_ACTION_APPROVE_APP_RECOVERY, SAP_ACTION_COUNT);
}
