import Shared
import core

typealias FFILightningInvoice = core.Invoice

public final class LightningInvoiceParserImpl: LightningInvoiceParser {
    public init () { }

    public func parse(invoiceString: String) -> LightningInvoice? {
        do {
            let invoice = try FFILightningInvoice(invoiceString: invoiceString)
            return invoice.lightningInvoice
        } catch {
            return nil
        }
    }
}

extension FFILightningInvoice {
    fileprivate var lightningInvoice: LightningInvoice {
        return LightningInvoice(
            payeePubKey: payeePubkey(),
            paymentHash: paymentHash(),
            isExpired: isExpired(),
            amountMsat: amountMsat().flatMap { KotlinULong(value: $0) }
        )
    }
}
