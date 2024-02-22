import Shared
import LightningDevKitNode

final class LdkNodeImpl: Shared.LdkNode {
    let ffiNode: LightningDevKitNode.Node

    init(ffiNode: LightningDevKitNode.Node) {
        self.ffiNode = ffiNode
    }
    
    
    func start() -> LdkResult<KotlinUnit> {
        return LdkResult {
            try ffiNode.start()
            return KotlinUnit()
        }
    }
    
    func stop() -> LdkResult<KotlinUnit> {
        return LdkResult {
            try ffiNode.stop()
            return KotlinUnit()
        }
    }
    
    
    func syncWallets() -> LdkResult<KotlinUnit> {
        return LdkResult {
            try ffiNode.syncWallets()
            return KotlinUnit()
        }
    }
    
    func nextEvent() -> LdkEvent {
        ffiNode.nextEvent().ldkEvent
    }
    
    func nodeId() -> String {
        ffiNode.nodeId()
    }
    
    func doNewFundingAddress() -> LdkResult<NSString> {
        return LdkResult {
            try ffiNode.newFundingAddress() as NSString
        }
    }
    
    func spendableOnchainBalanceSats() -> LdkResult<BignumBigInteger> {
        return LdkResult {
            let balance = try ffiNode.spendableOnchainBalanceSats()
            return BignumBigInteger(long: Int64(balance))
        }
    }
    
    func totalOnchainBalanceSats() -> LdkResult<BignumBigInteger> {
        return LdkResult {
            let balance = try ffiNode.totalOnchainBalanceSats()
            return BignumBigInteger(long: Int64(balance))
        }
    }
    
    func connectOpenChannel(nodePublicKey: String, address: String, channelAmountSats: BignumBigInteger, announceChannel: Bool) -> LdkResult<KotlinUnit> {
        return LdkResult {
            try ffiNode.connectOpenChannel(
                nodeId: nodePublicKey,
                address: address,
                channelAmountSats: channelAmountSats.ulongValue(exactRequired: true),
                // Used to push some liquidity to counterparty on channel open so we can start out with
                // some balance on the other side of the channel. We set this to `null` by default.
                pushToCounterpartyMsat: nil,
                announceChannel: announceChannel
            )
            return KotlinUnit()
        }
    }
    
    func closeChannel(channelId: String, counterpartyNodeId: String) -> LdkResult<KotlinUnit> {
        return LdkResult {
            try ffiNode.closeChannel(channelId: channelId, counterpartyNodeId: counterpartyNodeId)
            return KotlinUnit()
        }
    }
    
    
    func sendPayment(invoice: String) -> LdkResult<NSString> {
        return LdkResult {
            try ffiNode.sendPayment(invoice: invoice) as NSString
        }
    }
    
    func sendPaymentUsingAmount(invoice: String, amountMsat: BignumBigInteger) -> LdkResult<NSString> {
        return LdkResult {
            try ffiNode.sendPaymentUsingAmount(
                invoice: invoice,
                amountMsat: amountMsat.ulongValue(exactRequired: true)
            ) as NSString
        }
    }
    
    func sendSpontaneousPayment(amountMsat: BignumBigInteger, nodeId: String) -> LdkResult<NSString> {
        return LdkResult {
            try ffiNode.sendSpontaneousPayment(
                amountMsat: amountMsat.ulongValue(exactRequired: true),
                nodeId: nodeId
            ) as NSString
        }
    }
    
    func receivePayment(amountMsat: BignumBigInteger, description: String, expirySecs: Int64) -> LdkResult<NSString> {
        return LdkResult {
            try ffiNode.receivePayment(
                amountMsat: amountMsat.ulongValue(exactRequired: true),
                description: description,
                expirySecs: UInt32(expirySecs)
            ) as NSString
        }
    }
    
    func receiveVariableAmountPayment(description: String, expirySecs: Int64) -> LdkResult<NSString> {
        return LdkResult {
            try ffiNode.receiveVariableAmountPayment(
                description: description,
                expirySecs: UInt32(expirySecs)
            ) as NSString
        }
    }

    
    func listChannels() -> [Shared.ChannelDetails] {
        return ffiNode.listChannels().map{ $0.ldkChannelDetails }
    }

    
    func paymentInfo(paymentHash: String) -> Shared.PaymentDetails? {
        ffiNode.payment(paymentHash: paymentHash)?.ldkPaymentDetails
    }
    
    func connectPeer(nodeId: String, address: String, permanently: Bool) -> LdkResult<KotlinUnit> {
        return LdkResult {
            try ffiNode.connect(nodeId: nodeId, address: address, permanently: permanently)
            return KotlinUnit()
        }
    }
}
