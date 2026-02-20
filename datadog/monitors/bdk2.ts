import { Construct } from "constructs";
import { Environment } from "./common/environments";
import { Monitor } from "./common/monitor";
import { log_count_query } from "./common/queries";
import { getErrorRecipients } from "./recipients";

export class Bdk2Monitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `bdk2_${environment}`);

    const errorRecipients = getErrorRecipients(environment);
    const tags = [`bdk2_${environment}`];
    const window = "1h";
    const baseQuery = `source:(ios OR android) service:build.wallet env:${environment}`;
    const rumBaseQuery = `@type:resource @context.bdk_version:2 service:world.bitkey.app env:${environment}`;
    const mempoolRumQuery = `${rumBaseQuery} @resource.url:"ssl://bitkey.mempool.space:50002"`;
    const blockstreamRumQuery = `${rumBaseQuery} @resource.url:"ssl://electrum.blockstream.info:50002"`;
    const rumLatencyQuery = (name: string, query: string) => ({
      dataSource: "rum",
      name,
      indexes: ["*"],
      compute: [{ aggregation: "pc95", metric: "@duration" }],
      groupBy: [],
      search: {
        query,
      },
    });

    new Monitor(this, "bdk2_sync_failed", {
      query: log_count_query(
        `${baseQuery} "BDK2 sync failed"`,
        window,
        "5"
      ),
      name: `[BDK2] Wallet sync failures on env:${environment}`,
      message: "BDK2 wallet sync failures detected on mobile clients.",
      tags,
      type: "log alert",
      monitorThresholds: {
        critical: "5",
      },
      recipients: errorRecipients,
    });

    new Monitor(this, "bdk2_psbt_creation_failed", {
      query: log_count_query(
        `${baseQuery} "BDK2 PSBT creation failed" -"error=InsufficientFunds" -"error=OutputBelowDustLimit"`,
        window,
        "5"
      ),
      name: `[BDK2] PSBT creation failures on env:${environment}`,
      message: "BDK2 PSBT creation failures detected on mobile clients.",
      tags,
      type: "log alert",
      monitorThresholds: {
        critical: "5",
      },
      recipients: errorRecipients,
    });

    new Monitor(this, "bdk2_wallet_create_failed", {
      query: log_count_query(
        `${baseQuery} "BDK2 wallet create failed"`,
        window,
        "1"
      ),
      name: `[BDK2] Wallet create failures on env:${environment}`,
      message: "BDK2 wallet create failures detected on mobile clients.",
      tags,
      type: "log alert",
      monitorThresholds: {
        critical: "1",
      },
      recipients: errorRecipients,
    });

    new Monitor(this, "bdk2_manual_fee_bump_invariant_failed", {
      query: log_count_query(
        `${baseQuery} "BDK2 manual fee bump invariant failed"`,
        window,
        "1"
      ),
      name: `[BDK2] Manual fee bump invariant failures on env:${environment}`,
      message: "BDK2 manual fee bump invariants failed on mobile clients.",
      tags,
      type: "log alert",
      monitorThresholds: {
        critical: "1",
      },
      recipients: errorRecipients,
    });

    new Monitor(this, "bdk2_wallet_data_retrieval_failed", {
      query: log_count_query(
        `${baseQuery} ("BDK2 balance retrieval failed" OR "BDK2 transactions retrieval failed" OR "BDK2 UTXO retrieval failed" OR "BDK2 address retrieval failed")`,
        window,
        "5"
      ),
      name: `[BDK2] Wallet data retrieval failures on env:${environment}`,
      message: "BDK2 wallet data retrieval failures detected on mobile clients.",
      tags,
      type: "log alert",
      monitorThresholds: {
        critical: "5",
      },
      recipients: errorRecipients,
    });

    new Monitor(this, "bdk2_psbt_signing_failed", {
      query: log_count_query(
        `${baseQuery} "BDK2 PSBT signing failed"`,
        window,
        "5"
      ),
      name: `[BDK2] PSBT signing failures on env:${environment}`,
      message: "BDK2 PSBT signing failures detected on mobile clients.",
      tags,
      type: "log alert",
      monitorThresholds: {
        critical: "5",
      },
      recipients: errorRecipients,
    });

    new Monitor(this, "bdk2_broadcast_failed", {
      query: log_count_query(
        `${baseQuery} "BDK2 broadcast failed"`,
        window,
        "1"
      ),
      name: `[BDK2] Broadcast failures on env:${environment}`,
      message: "BDK2 broadcast failures detected on mobile clients.",
      tags,
      type: "log alert",
      monitorThresholds: {
        critical: "1",
      },
      recipients: errorRecipients,
    });

    const mempoolSyncVolumeLow = new Monitor(
      this,
      "bdk2_mempool_sync_volume_low",
      {
        query: `rum("${mempoolRumQuery}").rollup("count").last("15m") < 20`,
        name: `[BDK2] Mempool sync volume low on env:${environment}`,
        message:
          "Mempool BDK2 sync volume below 20 in the last 15 minutes (gating).",
        tags,
        type: "rum alert",
        monitorThresholds: {
          critical: "20",
        },
        recipients: [],
      }
    );

    const blockstreamSyncVolumeLow = new Monitor(
      this,
      "bdk2_blockstream_sync_volume_low",
      {
        query: `rum("${blockstreamRumQuery}").rollup("count").last("15m") < 20`,
        name: `[BDK2] Blockstream sync volume low on env:${environment}`,
        message:
          "Blockstream BDK2 sync volume below 20 in the last 15 minutes (gating).",
        tags,
        type: "rum alert",
        monitorThresholds: {
          critical: "20",
        },
        recipients: [],
      }
    );

    const mempoolSyncDurationHistoricalDivergence = new Monitor(
      this,
      "bdk2_mempool_sync_duration_historical_divergence",
      {
        query:
          `formula("moving_rollup(mempool, 900, 'avg') / moving_rollup(mempool, 86400, 'avg')")` +
          `.last("15m") > 1.25`,
        name: `[BDK2] Mempool sync p95 divergence on env:${environment}`,
        message:
          "Mempool sync p95 is >25% higher than its 24h mean for BDK2 clients.",
        tags,
        type: "rum alert",
        monitorThresholds: {
          critical: "1.25",
        },
        recipients: [],
        variables: {
          eventQuery: [rumLatencyQuery("mempool", mempoolRumQuery)],
        },
      }
    );

    const mempoolSyncDurationProviderRatio = new Monitor(
      this,
      "bdk2_mempool_sync_duration_provider_ratio_high",
      {
        query:
          `formula("moving_rollup(mempool, 900, 'avg') / moving_rollup(blockstream, 900, 'avg')")` +
          `.last("15m") > 1.5`,
        name: `[BDK2] Mempool sync p95 vs Blockstream on env:${environment}`,
        message:
          "Mempool sync p95 is >50% higher than Blockstream for BDK2 clients.",
        tags,
        type: "rum alert",
        monitorThresholds: {
          critical: "1.5",
        },
        recipients: [],
        variables: {
          eventQuery: [
            rumLatencyQuery("mempool", mempoolRumQuery),
            rumLatencyQuery("blockstream", blockstreamRumQuery),
          ],
        },
      }
    );

    const blockstreamSyncDurationHistoricalDivergence = new Monitor(
      this,
      "bdk2_blockstream_sync_duration_historical_divergence",
      {
        query:
          `formula("moving_rollup(blockstream, 900, 'avg') / moving_rollup(blockstream, 86400, 'avg')")` +
          `.last("15m") > 1.25`,
        name: `[BDK2] Blockstream sync p95 divergence on env:${environment}`,
        message:
          "Blockstream sync p95 is >25% higher than its 24h mean for BDK2 clients.",
        tags,
        type: "rum alert",
        monitorThresholds: {
          critical: "1.25",
        },
        recipients: [],
        variables: {
          eventQuery: [rumLatencyQuery("blockstream", blockstreamRumQuery)],
        },
      }
    );

    const blockstreamSyncDurationProviderRatio = new Monitor(
      this,
      "bdk2_blockstream_sync_duration_provider_ratio_high",
      {
        query:
          `formula("moving_rollup(blockstream, 900, 'avg') / moving_rollup(mempool, 900, 'avg')")` +
          `.last("15m") > 1.5`,
        name: `[BDK2] Blockstream sync p95 vs Mempool on env:${environment}`,
        message:
          "Blockstream sync p95 is >50% higher than Mempool for BDK2 clients.",
        tags,
        type: "rum alert",
        monitorThresholds: {
          critical: "1.5",
        },
        recipients: [],
        variables: {
          eventQuery: [
            rumLatencyQuery("blockstream", blockstreamRumQuery),
            rumLatencyQuery("mempool", mempoolRumQuery),
          ],
        },
      }
    );

    new Monitor(this, "bdk2_sync_duration_provider_divergence", {
      query:
        `(${mempoolSyncDurationHistoricalDivergence.id} && ` +
        `${mempoolSyncDurationProviderRatio.id} && ` +
        `!${mempoolSyncVolumeLow.id}) || ` +
        `(${blockstreamSyncDurationHistoricalDivergence.id} && ` +
        `${blockstreamSyncDurationProviderRatio.id} && ` +
        `!${blockstreamSyncVolumeLow.id})`,
      name: `[BDK2] Electrum provider sync p95 divergence on env:${environment}`,
      message:
        "BDK2 sync p95 is elevated for one provider vs its 24h mean and the other provider.",
      tags,
      type: "composite",
      recipients: errorRecipients,
    });

    new Monitor(this, "bdk2_sync_duration_high", {
      query:
        `rum(` +
        `"@type:resource @resource.url:(\\"ssl://bitkey.mempool.space:50002\\" OR \\"ssl://electrum.blockstream.info:50002\\") ` +
        `@context.bdk_version:2 service:world.bitkey.app env:${environment}"` +
        `).rollup("avg","@duration").last("15m") > 10000`,
      name: `[BDK2] Wallet sync duration high on env:${environment}`,
      message: "BDK2 wallet sync duration is elevated on mobile clients.",
      tags,
      type: "rum alert",
      monitorThresholds: {
        critical: "10000",
      },
      recipients: errorRecipients,
    });
  }
}
