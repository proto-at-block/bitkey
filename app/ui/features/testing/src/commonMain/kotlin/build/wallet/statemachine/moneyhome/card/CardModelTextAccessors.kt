package build.wallet.statemachine.moneyhome.card

/**
 * Test-only convenience accessor for the card title as a plain String, when the variant has one.
 *
 * Returns the title for [CardModel.Hero], [CardModel.Status], [CardModel.DrillList],
 * [CardModel.PendingClaim], and [CardModel.Callout]; returns null for [CardModel.BitcoinPrice],
 * which has no top-level title.
 */
val CardModel.titleString: String?
  get() = when (this) {
    is CardModel.Hero -> title.string
    is CardModel.Status -> title
    is CardModel.DrillList -> title.string
    is CardModel.PendingClaim -> title
    is CardModel.Callout -> callout.title
    is CardModel.BitcoinPrice -> null
  }

/**
 * Test-only convenience accessor for the card subtitle, when the variant has one.
 */
val CardModel.subtitleString: String?
  get() = when (this) {
    is CardModel.Hero -> subtitle
    is CardModel.Status -> subtitle
    is CardModel.PendingClaim -> subtitle
    is CardModel.Callout -> callout.subtitle?.string
    is CardModel.DrillList, is CardModel.BitcoinPrice -> null
  }
