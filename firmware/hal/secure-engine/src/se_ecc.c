#include "secure_engine.h"
#include "sli_se_manager_internal.h"

sl_status_t se_ecc_verify(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                          sl_se_hash_type_t hash_alg, bool hashed_message,
                          const unsigned char* message, size_t message_len,
                          const unsigned char* signature, size_t signature_len) {
  if (cmd_ctx == NULL || key == NULL || message == NULL || signature == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  // Key needs to contain public key in order to verify signatures
  if (!(key->flags & SL_SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY)) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  SE_Command_t* se_cmd = &cmd_ctx->command;
  sl_status_t status;
  uint32_t command_word = SLI_SE_COMMAND_SIGNATURE_VERIFY;

  if ((key->type & SL_SE_KEY_TYPE_ALGORITHM_MASK) == SL_SE_KEY_TYPE_ECC_EDDSA) {
    command_word = SLI_SE_COMMAND_EDDSA_VERIFY;
  } else {
    if (hashed_message == false) {
      switch (hash_alg) {
        case SL_SE_HASH_SHA1:
          command_word |= SLI_SE_COMMAND_OPTION_HASH_SHA1;
          break;

        case SL_SE_HASH_SHA224:
          command_word |= SLI_SE_COMMAND_OPTION_HASH_SHA224;
          break;

        case SL_SE_HASH_SHA256:
          command_word |= SLI_SE_COMMAND_OPTION_HASH_SHA256;
          break;

#if (_SILICON_LABS_SECURITY_FEATURE == _SILICON_LABS_SECURITY_FEATURE_VAULT)
        case SL_SE_HASH_SHA384:
          command_word |= SLI_SE_COMMAND_OPTION_HASH_SHA384;
          break;

        case SL_SE_HASH_SHA512:
          command_word |= SLI_SE_COMMAND_OPTION_HASH_SHA512;
          break;

#endif
        default:
          return SL_STATUS_INVALID_PARAMETER;
      }
    }
  }

  // Setup SE command and parameters
  sli_se_command_init(cmd_ctx, command_word);
  // Add key parameters to command
  sli_add_key_parameters(cmd_ctx, key, status);
  // Message size (number of bytes)
  SE_addParameter(se_cmd, message_len);
  // Add key metadata block to command
  sli_add_key_metadata(cmd_ctx, key, status);
  // Add key input block to command
  sli_add_key_input(cmd_ctx, key, status);

  SE_DataTransfer_t message_buffer = SE_DATATRANSFER_DEFAULT(message, message_len);
  SE_DataTransfer_t signature_buffer = SE_DATATRANSFER_DEFAULT(signature, signature_len);

  if ((key->type & SL_SE_KEY_TYPE_ALGORITHM_MASK) == SL_SE_KEY_TYPE_ECC_EDDSA) {
    SE_addDataInput(se_cmd, &signature_buffer);
    SE_addDataInput(se_cmd, &message_buffer);
  } else {
    SE_addDataInput(se_cmd, &message_buffer);
    SE_addDataInput(se_cmd, &signature_buffer);
  }

  return sli_se_execute_and_wait(cmd_ctx);
}

sl_status_t se_ecc_sign(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                        sl_se_hash_type_t hash_alg, bool hashed_message,
                        const unsigned char* message, size_t message_len,
                        const unsigned char* signature, size_t signature_len) {
  if (cmd_ctx == NULL || key == NULL || message == NULL || signature == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  SE_Command_t* se_cmd = &cmd_ctx->command;
  sl_status_t status;
  uint32_t command_word = SLI_SE_COMMAND_SIGNATURE_SIGN;

  if ((key->type & SL_SE_KEY_TYPE_ALGORITHM_MASK) == SL_SE_KEY_TYPE_ECC_EDDSA) {
    command_word = SLI_SE_COMMAND_EDDSA_SIGN;
  } else {
    if (hashed_message == false) {
      switch (hash_alg) {
        case SL_SE_HASH_SHA1:
          command_word |= SLI_SE_COMMAND_OPTION_HASH_SHA1;
          break;
        case SL_SE_HASH_SHA256:
          command_word |= SLI_SE_COMMAND_OPTION_HASH_SHA256;
          break;
        case SL_SE_HASH_SHA512:
          command_word |= SLI_SE_COMMAND_OPTION_HASH_SHA512;
          break;
        default:
          return SL_STATUS_INVALID_PARAMETER;
      }
    }
  }

  // Setup SE command and parameters
  sli_se_command_init(cmd_ctx, command_word);
  // Add key parameters to command
  sli_add_key_parameters(cmd_ctx, key, status);
  // Message size (number of bytes)
  SE_addParameter(se_cmd, message_len);
  // Add key metadata block to command
  sli_add_key_metadata(cmd_ctx, key, status);
  // Add key input block to command
  sli_add_key_input(cmd_ctx, key, status);

  SE_DataTransfer_t message_buffer = SE_DATATRANSFER_DEFAULT(message, message_len);
  SE_addDataInput(se_cmd, &message_buffer);

  // EdDSA requires the message twice
  SE_DataTransfer_t repeated_message_buffer = SE_DATATRANSFER_DEFAULT(message, message_len);
  if ((key->type & SL_SE_KEY_TYPE_ALGORITHM_MASK) == SL_SE_KEY_TYPE_ECC_EDDSA) {
    SE_addDataInput(se_cmd, &repeated_message_buffer);
  }

  SE_DataTransfer_t signature_buffer = SE_DATATRANSFER_DEFAULT(signature, signature_len);
  SE_addDataOutput(se_cmd, &signature_buffer);

  // return sli_se_execute_and_wait(cmd_ctx);
  return sli_se_execute_and_wait(cmd_ctx);
}
