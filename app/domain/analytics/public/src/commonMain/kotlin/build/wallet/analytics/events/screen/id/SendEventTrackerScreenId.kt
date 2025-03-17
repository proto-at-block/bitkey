package build.wallet.analytics.events.screen.id

enum class SendEventTrackerScreenId : EventTrackerScreenId {
  /** Address entry shown in the send flow */
  SEND_ADDRESS_ENTRY,

  /** Full screen shown in the send flow allowing the customer to select a fee speed */
  SEND_SELECT_TRANSACTION_FEE_PRIORITY,

  /** Loading screen shown while we load the fee priority options */
  SEND_LOADING_FEE_OPTIONS,

  /** Bottom sheet shown in the send flow allowing the customer to select a fee speed */
  SEND_FEES_SELECTION_SHEET,

  /** Bottom sheet shown in the send flow showing info about fees */
  SEND_NETWORK_FEES_INFO_SHEET,

  /** Confirmation screen shown in the send flow */
  SEND_CONFIRMATION,

  /** Loading screen shown while creating a PSBT */
  SEND_CREATING_PSBT_LOADING,

  /** Loading screen shown while signing and broadcasting a PSBT */
  SEND_SIGNING_AND_BROADCASTING_LOADING,

  /** Success screen when transfer is initiated */
  SEND_INITIATED_SUCCESS,
}
