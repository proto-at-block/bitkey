#pragma once

#include "confirmation_manager.h"
#include "sign_action_proof_core.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Unseal AES-256-GCM sealed data using the hardware sealing key.
 *
 * Validates size constraints, then calls wallet_csek_decrypt.
 * Used by EEK restoration, FACBR, lost app recovery, and recovery composites.
 *
 * @param sealed Sealed data (ciphertext + nonce + tag)
 * @param output Output buffer for the unsealed plaintext
 * @param output_size Size of the output buffer
 * @return true on success, false on size error or decryption failure
 */
bool sealed_data_unseal(const fwpb_sealed_data* sealed, uint8_t* output, size_t output_size);

/**
 * Session state for a confirmable sealed-data unseal flow.
 * Each call site owns one of these in static BSS.
 */
typedef struct {
  fwpb_sealed_data sealed_key;
  bool valid;
} sealed_unseal_session_t;

/**
 * @brief Begin a confirmable sealed-data unseal flow.
 *
 * Validates sealed key dimensions, stores into [session], creates confirmation handles,
 * shows the on-device prompt for [sap_action], and populates [rsp] with status =
 * CONFIRMATION_PENDING + handles. The caller must set rsp->which_msg before calling.
 *
 * @param sealed_key Sealed data from the cmd
 * @param sap_action SAP action ID controlling device prompt copy
 * @param confirmation_type CONFIRMATION_TYPE_* for confirmation_manager
 * @param session Caller-owned session storage
 * @param rsp Response to populate
 * @return true on success (rsp set to CONFIRMATION_PENDING + handles), false on error.
 *   On error, [rsp]->status is set appropriately and the session is cleared.
 */
bool sealed_unseal_begin(const fwpb_sealed_data* sealed_key, sap_action_t sap_action,
                         confirmation_type_t confirmation_type, sealed_unseal_session_t* session,
                         fwpb_wallet_rsp* rsp);

/**
 * @brief Complete a confirmable sealed-data unseal flow on the second tap.
 *
 * Validates the response/confirmation handles in [cmd], checks the session is valid,
 * decrypts the sealed key into [unsealed_key_out]. Populates [rsp]->status only;
 * the caller is responsible for setting rsp->which_msg, the inner which_result tag,
 * and copying [unsealed_key_out] into the call-specific result field on success.
 *
 * @param session Session populated by [sealed_unseal_begin]
 * @param cmd Wallet cmd carrying the get_confirmation_result_cmd handles
 * @param rsp Response (status only is set here)
 * @param unsealed_key_out 32-byte output buffer (caller zeroizes)
 * @return true if the unseal succeeded, false otherwise.
 */
bool sealed_unseal_finish(sealed_unseal_session_t* session, const fwpb_wallet_cmd* cmd,
                          fwpb_wallet_rsp* rsp, uint8_t unsealed_key_out[32]);

/** Clear a sealed_unseal_session_t and the associated confirmation_manager state. */
void sealed_unseal_clear_state(sealed_unseal_session_t* session);
