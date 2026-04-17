#include "criterion_test_utils.h"
#include "fff.h"
#include "sign_action_proof_core.h"

#include <criterion/criterion.h>

#include <string.h>

DEFINE_FFF_GLOBALS;
FAKE_VALUE_FUNC(bool, rtos_in_isr);

// ---------------------------------------------------------------------------
// Recovery Composite SAP Action Classification
// ---------------------------------------------------------------------------
// The recovery authorize commands (lost app + lost hw) sign exactly two SAP
// proofs: UpdateDescriptorBackups and RotateSpendingKeyset. These tests
// validate that those actions exist and parse correctly.

Test(rc_actions, upload_descriptor_backups_parses) {
  cr_assert_eq(sap_parse_action("UpdateDescriptorBackups"), SAP_ACTION_UPDATE_DESCRIPTOR_BACKUPS);
}

Test(rc_actions, activate_spending_keyset_parses) {
  cr_assert_eq(sap_parse_action("RotateSpendingKeyset"), SAP_ACTION_ROTATE_SPENDING_KEYSET);
}

Test(rc_actions, start_recovery_parses) {
  cr_assert_eq(sap_parse_action("StartRecovery"), SAP_ACTION_START_RECOVERY);
}

Test(rc_actions, approve_app_recovery_parses) {
  cr_assert_eq(sap_parse_action("ApproveAppRecovery"), SAP_ACTION_APPROVE_APP_RECOVERY);
}

Test(rc_actions, complete_wallet_parses) {
  cr_assert_eq(sap_parse_action("CompleteWallet"), SAP_ACTION_COMPLETE_WALLET);
}

Test(rc_actions, both_actions_are_distinct) {
  cr_assert_neq(SAP_ACTION_UPDATE_DESCRIPTOR_BACKUPS, SAP_ACTION_ROTATE_SPENDING_KEYSET);
}

Test(rc_actions, recovery_actions_differ_from_lar_action) {
  cr_assert_neq(SAP_ACTION_UPDATE_DESCRIPTOR_BACKUPS, SAP_ACTION_CREATE_LOST_APP_RECOVERY);
  cr_assert_neq(SAP_ACTION_ROTATE_SPENDING_KEYSET, SAP_ACTION_CREATE_LOST_APP_RECOVERY);
}
