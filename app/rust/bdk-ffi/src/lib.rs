mod bitcoin;
mod descriptor;
mod electrum;
mod electrum_bitkey_ext;
mod error;
mod esplora;
mod keys;
mod kyoto;
mod macros;
mod store;
mod tx_builder;
mod types;
mod wallet;

#[cfg(test)]
mod tests;

use crate::bitcoin::FeeRate;
use crate::bitcoin::OutPoint;

uniffi::setup_scaffolding!("bdk");
