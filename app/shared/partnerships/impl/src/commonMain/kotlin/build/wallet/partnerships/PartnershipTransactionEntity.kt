package build.wallet.partnerships

import build.wallet.database.sqldelight.PartnershipTransactionEntity

/**
 * Convert the local partnership transaction model to its database entity.
 */
internal fun PartnershipTransaction.toEntity(): PartnershipTransactionEntity {
  return PartnershipTransactionEntity(
    transactionId = id,
    type = type,
    status = status,
    context = context,
    partnerId = partnerInfo.partnerId,
    partnerLogoUrl = partnerInfo.logoUrl,
    partnerName = partnerInfo.name,
    cryptoAmount = cryptoAmount,
    txid = txid,
    fiatAmount = fiatAmount,
    fiatCurrency = fiatCurrency,
    paymentMethod = paymentMethod,
    created = created,
    updated = updated
  )
}

/**
 * Convert the database entity to the local partnership transaction model.
 */
internal fun PartnershipTransactionEntity.toModel(): PartnershipTransaction {
  return PartnershipTransaction(
    id = transactionId,
    type = type,
    status = status,
    context = context,
    partnerInfo = PartnerInfo(
      partnerId = partnerId,
      logoUrl = partnerLogoUrl,
      name = partnerName
    ),
    cryptoAmount = cryptoAmount,
    txid = txid,
    fiatAmount = fiatAmount,
    fiatCurrency = fiatCurrency,
    paymentMethod = paymentMethod,
    created = created,
    updated = updated
  )
}
