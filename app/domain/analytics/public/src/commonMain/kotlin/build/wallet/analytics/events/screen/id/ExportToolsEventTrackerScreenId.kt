package build.wallet.analytics.events.screen.id

enum class ExportToolsEventTrackerScreenId : EventTrackerScreenId {
  /**
   * Screen for users to choose what data they would like to export.
   */
  EXPORT_TOOLS_SCREEN,

  /**
   * Showing bottom sheet for exporting transaction history
   */
  EXPORT_TRANSACTION_HISTORY_SHEET,

  /**
   * Showing bottom sheet for exporting wallet descriptor
   */
  EXPORT_WALLET_DESCRIPTOR_SHEET,
}
