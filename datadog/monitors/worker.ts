import { Construct } from "constructs";
import { getCriticalRecipients } from "./recipients";
import { Environment } from "./common/environments";
import { Monitor } from "./common/monitor";

export class WorkerMonitors extends Construct {
  constructor(scope: Construct, environment: Environment) {
    super(scope, `workers_${environment}`);

    const criticalRecipients = getCriticalRecipients(environment);
    let alertConfig = {
        recipients: criticalRecipients,
        type: "query alert"
    };
    let alertWindow = "1h";
    
    new Monitor(this, "mainnet-blockchain-tip-height-lag", {
        query: `
            avg(last_1h):bitkey.bdk_utils.blockchain_poller_in_sync{env:${environment},network:bitcoin} == 0
        `,
        name: `[Workers] Mainnet Blockchain Poller is lagging on env: ${environment}`,
        message: `
            The blockchain poller is lagging behind the current tip height.
            This means the poller isn't pulling the newest confirmed blocks and our customers are not receiving confirmed transactions notifications.
            See the runbook for more details: https://docs.wallet.build/runbooks/apps/workers/blockchain-poller/#monitoring-poller-out-of-sync
        `,
        tags: [`mainnet_blockchain_poller_lag_${environment}`],
        notifyNoData: true,
        noDataTimeframe: 60, // in minutes
        ...alertConfig
    });
    }
}
