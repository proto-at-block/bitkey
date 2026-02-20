pub use balance::balance;
use bdk_wallet::bitcoin::Amount;
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

pub(crate) fn psbt_from(
    wallet: &mut bdk_wallet::Wallet,
    recipient: bdk_wallet::bitcoin::Address,
    amount: u64,
) -> Result<bdk_wallet::bitcoin::psbt::Psbt, bdk_wallet::error::CreateTxError> {
    let mut builder = wallet.build_tx();
    builder.add_recipient(recipient.script_pubkey(), Amount::from_sat(amount));
    let psbt = builder.finish()?;
    Ok(psbt)
}
