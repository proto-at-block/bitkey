use std::collections::VecDeque;

use bdk_utils::bdk::bitcoin::{
    consensus::encode::deserialize, Block as BdkBlock, BlockHash, Network,
};
use once_cell::sync::Lazy;
use tracing::{event, Level};

use super::Service;
use crate::ChainIndexerError;

const MAX_BLOCKS: usize = 2000;

const BITCOIN_MAINNET_BLOCK_HASHES: Lazy<Vec<BlockHash>> = Lazy::new(std::vec::Vec::new);

impl Service {
    pub async fn get_new_blocks(&self) -> Result<Vec<BdkBlock>, ChainIndexerError> {
        event!(
            Level::INFO,
            "Getting new blocks for network {} using base url {}",
            self.settings.network,
            self.settings.base_url,
        );

        let tip_hash_from_mempool = self.get_tip_hash().await?;
        let tip_hash = if self.settings.network == Network::Bitcoin {
            let mut ret_block_hash = tip_hash_from_mempool;
            for block_hash in BITCOIN_MAINNET_BLOCK_HASHES.iter() {
                if self.repo.fetch(block_hash.to_owned()).await?.is_none() {
                    ret_block_hash = *block_hash;
                    break;
                }
            }
            Ok::<BlockHash, ChainIndexerError>(ret_block_hash)
        } else {
            Ok(tip_hash_from_mempool)
        }?;

        event!(Level::INFO, "Retrieved tip hash {tip_hash} from network");
        let mut new_blocks = VecDeque::with_capacity(MAX_BLOCKS);

        let init_block = self.repo.fetch_init_block(self.settings.network).await?;
        event!(
            Level::INFO,
            "Retrieved init block {init_block:?} from network"
        );
        if let Some(init_block) = init_block {
            let mut current_hash = tip_hash;
            event!(Level::INFO, "Setting current hash to {current_hash}");
            loop {
                // We've already seen the block, so we've found a common parent with the tip.
                if let Some(block_for_hash) = self.repo.fetch(current_hash).await? {
                    // TODO: to detect re-orgs, we should query the DB for any blocks with a
                    // prev_hash that is equal to the current_hash.
                    event!(
                        Level::INFO,
                        "Found block {block_for_hash:?} for hash {current_hash}",
                    );
                    break;
                }

                event!(Level::INFO, "Getting block {current_hash} from network");
                let new_block = self.get_block(&current_hash).await?;
                event!(Level::INFO, "Retrieved block {current_hash} from network");
                let init_block_height = init_block.height;
                let new_block_height = new_block.bip34_block_height()?;
                event!(Level::INFO, "New block height: {}", new_block_height);
                if new_block_height <= init_block_height {
                    // Any blocks below the init block are stale.
                    event!(
                        Level::WARN,
                        "Stale block detected! The init block has a height of \
                        {init_block_height}, but block {current_hash} has a height of \
                        {new_block_height}.",
                    );
                    break;
                }
                current_hash = new_block.header.prev_blockhash;
                event!(Level::INFO, "Setting current hash to {current_hash}");
                event!(
                    Level::INFO,
                    "Added block {} to new blocks",
                    new_block.block_hash()
                );
                if new_blocks.len() > MAX_BLOCKS {
                    new_blocks.pop_front();
                }
                new_blocks.push_back(new_block);
            }
        } else {
            event!(Level::INFO, "No blocks found in database");
            let block = self.get_block(&tip_hash).await?;
            event!(
                Level::INFO,
                "Retrieved block {tip_hash} from network and setting it as the init block."
            );
            new_blocks.push_back(block);
        }

        let blocks = new_blocks.into_iter().rev().collect();
        event!(Level::INFO, "Reversed new blocks");
        Ok(blocks)
    }

    pub async fn get_persisted_tip_block_and_in_sync(
        &self,
        network: Network,
    ) -> Result<(Option<u64>, bool), ChainIndexerError> {
        if let Some(block) = self
            .repo
            .fetch_persisted_tip_block(network)
            .await
            .map_err(ChainIndexerError::DatabaseError)?
        {
            let mempool_tip_hash = self.get_tip_hash().await?;
            Ok((Some(block.height), block.block_hash == mempool_tip_hash))
        } else {
            Ok((None, false))
        }
    }

    pub(crate) async fn get_block(
        &self,
        block_hash: &BlockHash,
    ) -> Result<BdkBlock, ChainIndexerError> {
        Ok(deserialize(
            &self
                .http_client
                .get(&format!(
                    "{}/block/{block_hash}/raw",
                    self.settings.base_url
                ))
                .send()
                .await?
                .bytes()
                .await
                .map(|bytes| bytes.to_vec())?,
        )?)
    }

    pub(crate) async fn get_tip_hash(&self) -> Result<BlockHash, ChainIndexerError> {
        Ok(self
            .http_client
            .get(&format!("{}/blocks/tip/hash", self.settings.base_url))
            .send()
            .await?
            .text()
            .await?
            .parse()?)
    }
}
