# Bitcoin Fee Estimation

This module handles Bitcoin transaction fee estimation with multiple data sources and robust fallback mechanisms.

## Fee Sources (Priority Order)

1. **Augur Fee API** (Primary when enabled)
   - Endpoint: `https://pricing.bitcoin.block.xyz/fees`
   - Uses probabilistic confidence-based estimates
   - More sophisticated statistical modeling than mempool estimates

2. **Mempool.space API** (Primary when Augur disabled, Fallback when Augur fails)
   - Endpoint: `https://bitkey.mempool.space/api/v1/fees/recommended`
   - Traditional mempool-based estimates

3. **BDK/Electrum** (Final Fallback)
   - Uses Bitcoin Core's `estimatesmartfee` RPC via Electrum
   - Returns `1 sat/vB` when all else fails
