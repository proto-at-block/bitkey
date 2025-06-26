package build.wallet.grants

// Constants based on `firmware/python/bitkey/grant_protocol.py`
const val GRANT_VERSION_LEN = 1
const val GRANT_DEVICE_ID_LEN = 8
const val GRANT_CHALLENGE_LEN = 16
const val GRANT_ACTION_LEN = 1
const val GRANT_SIGNATURE_LEN = 64

/** Total length of a serialized GrantRequest object, calculated from its parts */
internal const val SERIALIZED_GRANT_REQUEST_LENGTH =
  GRANT_VERSION_LEN +
    GRANT_DEVICE_ID_LEN +
    GRANT_CHALLENGE_LEN +
    GRANT_ACTION_LEN +
    GRANT_SIGNATURE_LEN
