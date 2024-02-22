use bdk_utils::bdk::bitcoin::block::Version;
use bdk_utils::bdk::bitcoin::{
    blockdata::block::Bip34Error, Block as BdkBlock, BlockHash, Network,
};
use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};

#[derive(Deserialize, Serialize, PartialEq, Debug, Clone)]
pub struct Block {
    pub block_hash: BlockHash, // Partition Key
    pub prev_hash: BlockHash,
    pub height: u64, // GSI Sort Key
    pub version: Version,
    pub time: u32,
    pub network: Network, // GSI Partition Key
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
}

impl Block {
    pub fn from_bdk_block(bdk_block: &BdkBlock, network: Network) -> Result<Self, Bip34Error> {
        Ok(Block {
            block_hash: bdk_block.block_hash(),
            prev_hash: bdk_block.header.prev_blockhash,
            height: bdk_block.bip34_block_height()?,
            version: bdk_block.header.version,
            time: bdk_block.header.time,
            created_at: OffsetDateTime::now_utc(),
            network,
        })
    }
}
