use crate::keys::PublicKey;
pub use bitcoin::hashes::sha256::Hash as Sha256;
use lightning_invoice::Invoice as LNInvoice;
use std::sync::Mutex;

/// Errors that can be thrown by [`Invoice`](crate::invoice::Invoice)
#[derive(Debug, PartialEq, Eq, thiserror::Error)]
pub enum InvoiceError {
    #[error("Invalid invoice format.")]
    InvalidInvoiceFormat,
    #[error("Invalid payment hash.")]
    InvalidPaymentHash,
}

pub struct Invoice {
    invoice_mutex: Mutex<LNInvoice>,
}

impl Invoice {
    pub fn new(invoice_string: String) -> Result<Self, InvoiceError> {
        let parsed_invoice = invoice_string
            .parse::<LNInvoice>()
            .map_err(|_| InvoiceError::InvalidInvoiceFormat)?;
        Ok(Self {
            invoice_mutex: Mutex::new(parsed_invoice),
        })
    }

    pub fn payment_hash(&self) -> Sha256 {
        *self.invoice_mutex.lock().unwrap().payment_hash()
    }

    pub fn payee_pubkey(&self) -> Option<PublicKey> {
        let invoice_mutex = self.invoice_mutex.lock().unwrap();

        // Attempt to see if payee pubkey is included in invoice. If not, we call
        // `recover_payee_pub_key()` to attempt to recover it from the signature.
        match invoice_mutex.payee_pub_key() {
            Some(pubkey) => Some(*pubkey),
            None => Some(invoice_mutex.recover_payee_pub_key()),
        }
    }

    pub fn is_expired(&self) -> bool {
        self.invoice_mutex.lock().unwrap().is_expired()
    }

    pub fn amount_msat(&self) -> Option<u64> {
        self.invoice_mutex.lock().unwrap().amount_milli_satoshis()
    }
}

#[cfg(test)]
mod tests {
    use crate::invoice::{Invoice, InvoiceError};
    use bitcoin::hashes::hex::ToHex;

    #[test]
    fn test_valid_invoice_with_amount() {
        match Invoice::new(
            "lnbc2500u1pvjluezsp5zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg\
        3zyg3zyg3zygspp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdq5xysxxatsyp3k7enxv4js\
        xqzpu9qrsgquk0rl77nj30yxdy8j9vdx85fkpmdla2087ne0xh8nhedh8w27kyke0lp53ut353s06fv3qfegext0eh0\
        ymjpf39tuven09sam30g4vgpfna3rh"
                .to_string(),
        ) {
            Ok(lightning_invoice) => {
                assert_eq!(
                    lightning_invoice.payment_hash().to_hex(),
                    "0001020304050607080900010203040506070809000102030405060708090102"
                );
                assert!(lightning_invoice.is_expired());
                assert_eq!(lightning_invoice.amount_msat(), Some(250_000_000));
                assert_eq!(
                    lightning_invoice.payee_pubkey().unwrap().to_string(),
                    "03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad"
                );
            }
            Err(_) => {
                panic!("Invoice should be valid.")
            }
        }
    }

    #[test]
    fn test_valid_invoice_no_amount() {
        match Invoice::new(
            "lnbc1pvjluezsp5zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3\
        zyg3zygspp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdpl2pkx2ctnv5sxxmmwwd5kgetjy\
        peh2ursdae8g6twvus8g6rfwvs8qun0dfjkxaq9qrsgq357wnc5r2ueh7ck6q93dj32dlqnls087fxdwk8qakdyafkq\
        3yap9us6v52vjjsrvywa6rt52cm9r9zqt8r2t7mlcwspyetp5h2tztugp9lfyql"
                .to_string(),
        ) {
            Ok(lightning_invoice) => {
                assert_eq!(
                    lightning_invoice.payment_hash().to_string(),
                    "0001020304050607080900010203040506070809000102030405060708090102"
                );
                assert!(lightning_invoice.is_expired());
                assert_eq!(lightning_invoice.amount_msat(), None);
                assert_eq!(
                    lightning_invoice.payee_pubkey().unwrap().to_string(),
                    "03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad"
                );
            }
            Err(_) => {
                panic!("Invoice should be valid.")
            }
        }
    }

    #[test]
    fn test_invalid_invoice() {
        match Invoice::new(
            "lnbc25m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdq5v\
            dhkven9v5sxyetpdeessp5zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygs9q4psqqqqqqqq\
            qqqqqqqqsgqtqyx5vggfcsll4wu246hz02kp85x4katwsk9639we5n5yngc3yhqkm35jnjw4len8vrnqnf5ejh0\
            mzj9n3vz2px97evektfm2l6wqccp3y7372"
                .to_string(),
        ) {
            Ok(_lightning_invoice) => {
                panic!("Invoice should be invalid.")
            }
            Err(err) => {
                assert_eq!(err, InvoiceError::InvalidInvoiceFormat)
            }
        }
    }
}
