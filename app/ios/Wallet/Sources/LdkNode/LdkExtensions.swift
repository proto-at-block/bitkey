
import Shared
import LightningDevKitNode

typealias FFILdkEvent = LightningDevKitNode.Event
typealias FFIChannelDetails = LightningDevKitNode.ChannelDetails
typealias FFIOutPoint = LightningDevKitNode.OutPoint
typealias FFIPaymentDetails = LightningDevKitNode.PaymentDetails
typealias FFIPaymentDirection = LightningDevKitNode.PaymentDirection
typealias FFIPaymentStatus = LightningDevKitNode.PaymentStatus
typealias FFINodeConfig = LightningDevKitNode.Config

/**
 * Convert FFI type to KMP Type
 */
extension FFILdkEvent {
    var ldkEvent: LdkEvent {
        switch self {
        case .paymentSuccessful(let paymentHash):
            return LdkEvent.PaymentSuccessful(paymentHash: paymentHash)
        case .paymentFailed(let paymentHash):
            return LdkEvent.PaymentFailed(paymentHash: paymentHash)
        case .paymentReceived(let paymentHash, let amountMsat):
            return LdkEvent.PaymentReceived(
                paymentHash: paymentHash,
                amountMsat: BignumBigInteger(long: Int64(amountMsat))
            )
        case .channelReady(let channelId, let userChannelId):
            return LdkEvent.ChannelReady(channelId: channelId, userChannelId: userChannelId)
        case .channelClosed(let channelId, let userChannelId):
            return LdkEvent.ChannelClosed(channelId: channelId, userChannelId: userChannelId)
        case .channelPending(channelId: let channelId, userChannelId: let userChannelId, formerTemporaryChannelId: let formerTemporaryChannelId, counterpartyNodeId: let counterpartyNodeId, fundingTxo: let fundingTxo):
            return LdkEvent.ChannelPending(channelId: channelId, userChannelId: userChannelId, formerTemporaryChannelId: formerTemporaryChannelId, counterpartyNodeId: counterpartyNodeId, fundingTxo: fundingTxo.ldkOutPoint)
        }
    }
}

/**
 * Convert FFI type to KMP Type
 */
extension FFIOutPoint {
    var ldkOutPoint: Shared.OutPoint {
        return Shared.OutPoint(txid: self.txid, index: self.vout)
    }
}

/**
 * Convert FFI type to KMP Type
 */
extension FFIChannelDetails {
    var ldkChannelDetails: Shared.ChannelDetails {
        return Shared.ChannelDetails(
            channelId: self.channelId,
            counterparty: self.counterpartyNodeId,
            fundingTxo: self.fundingTxo?.ldkOutPoint,
            shortChannelId: shortChannelId.map { KotlinULong(unsignedLongLong: $0) },
            channelValueSatoshis: self.channelValueSatoshis,
            balanceMsat: self.balanceMsat,
            outboundCapacityMsat: self.outboundCapacityMsat,
            inboundCapacityMsat: self.inboundCapacityMsat,
            confirmationsRequired: confirmationsRequired.map { KotlinUInt(unsignedInt: $0) },
            confirmations: confirmations.map { KotlinUInt(unsignedInt: $0) },
            isOutbound: self.isOutbound,
            isChannelReady: self.isChannelReady,
            isUsable: self.isUsable,
            isPublic: self.isPublic,
            cltvExpiryDelta: cltvExpiryDelta.map { KotlinUShort(unsignedShort: $0) }
        )
    }
}

/**
 * Convert FFI type to KMP Type
 */
extension FFIPaymentDirection {
    var ldkPaymentDirection: Shared.PaymentDirection {
        switch self {
        case .inbound:
            return PaymentDirection.inbound
        case .outbound:
            return PaymentDirection.outbound
        }
    }
}

/**
 * Convert FFI type to KMP Type
 */
extension FFIPaymentStatus {
    var ldkPaymentStatus: Shared.PaymentStatus {
        switch self {
        case .pending:
            return PaymentStatus.pending
        case .succeeded:
            return PaymentStatus.succeeded
        case .failed:
            return PaymentStatus.failed
        }
    }
}

/**
 * Convert FFI type to KMP Type
 */
extension FFIPaymentDetails {
    var ldkPaymentDetails: Shared.PaymentDetails {
        return PaymentDetails(
            paymentHash: self.hash,
            preimage: self.preimage,
            secret: self.secret,
            amountMsat: {
                if let amountMsat = self.amountMsat {
                    return BignumBigInteger(long: Int64(amountMsat))
                } else {
                    return nil
                }
            }(),
            direction: self.direction.ldkPaymentDirection,
            paymentStatus: self.status.ldkPaymentStatus
        )
    }
}

/**
 * Convert KMP type to FFI Type
 */
extension LdkNodeConfig {
    var ffiNodeConfig: FFINodeConfig {
        return FFINodeConfig(
            storageDirPath: self.storagePath,
            esploraServerUrl: self.esploraServerUrl,
            network: self.network,
            listeningAddress: self.listeningAddress,
            defaultCltvExpiryDelta: UInt32(self.defaultCltvExpiryDelta)
        )
    }
}
