pub use balance::balance;
pub use drain::drain;
pub use hardware_send::hardware_send;
pub use receive::receive;
pub use server_send::server_send;
pub use server_status::server_status;
pub use setup_mobile_pay::setup_mobile_pay;
pub use status::status;
pub use transactions::transactions;
pub use utxos::utxos;

mod balance;
mod drain;
mod hardware_send;
mod receive;
pub mod recovery;
mod server_send;
mod server_status;
mod setup_mobile_pay;
mod status;
mod transactions;
mod utxos;

pub(crate) fn psbt_from<D: bdk::database::BatchDatabase>(
    wallet: &bdk::Wallet<D>,
    recipient: bdk::bitcoin::Address,
    amount: u64,
) -> Result<bdk::bitcoin::psbt::PartiallySignedTransaction, bdk::Error> {
    let mut builder = wallet.build_tx();
    builder.add_recipient(recipient.script_pubkey(), amount);
    let (psbt, _) = builder.finish()?;
    Ok(psbt)
}
