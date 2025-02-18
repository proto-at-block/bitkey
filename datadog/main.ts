import { Construct } from "constructs";
import { App, S3Backend, TerraformStack } from "cdktf";
import { AuthMonitors } from "./monitors/auth";
import { DatadogProvider } from "@cdktf/provider-datadog/lib/provider";
import { FromagerieMonitors } from "./monitors/server";
import { WsmApiMonitors } from "./monitors/wsm";
import { AnalyticsMonitors } from "./monitors/analytics";
import { Environment } from "./monitors/common/environments";
import { PartnershipsMonitors } from "./monitors/partnerships";
import { ShopApiMonitors } from "./monitors/web/shop-api";
import { ShopApiOrderUpdateJobMonitors } from "./monitors/web/shop-api-order-update-job";
import { ShopApiRevenueReportingJobMonitors } from "./monitors/web/shop-api-revenue-reporting-job";
import { RecoveryMonitors } from "./monitors/recovery";
import { MoneyMovementMonitors } from "./monitors/moneyMovement";
import { NotificationsMonitors } from "./monitors/notifications";
import { SecurityAlertMonitors } from "./monitors/securityAlerts";
import { WorkerMonitors } from "./monitors/worker";
import { ShopApiStuckOrdersJobMonitors } from "./monitors/web/shop-api-stuck-orders-job";

class MonitorsStack extends TerraformStack {
  constructor(scope: Construct, id: string) {
    super(scope, id);

    new DatadogProvider(this, "datadog", {})

    new WsmApiMonitors(this, Environment.STAGING)
    new WsmApiMonitors(this, Environment.PRODUCTION)

    new FromagerieMonitors(this, Environment.STAGING)
    new FromagerieMonitors(this, Environment.PRODUCTION)

    new NotificationsMonitors(this, Environment.STAGING)
    new NotificationsMonitors(this, Environment.PRODUCTION)

    new AnalyticsMonitors(this, Environment.STAGING)
    new AnalyticsMonitors(this, Environment.PRODUCTION)

    new PartnershipsMonitors(this, Environment.STAGING)
    new PartnershipsMonitors(this, Environment.PRODUCTION)

    new RecoveryMonitors(this, Environment.STAGING)
    new RecoveryMonitors(this, Environment.PRODUCTION)

    new MoneyMovementMonitors(this, Environment.STAGING)
    new MoneyMovementMonitors(this, Environment.PRODUCTION)

    new AuthMonitors(this, Environment.STAGING)
    new AuthMonitors(this, Environment.PRODUCTION)

    new ShopApiMonitors(this, Environment.STAGING)
    new ShopApiMonitors(this, Environment.PRODUCTION)

    new ShopApiOrderUpdateJobMonitors(this, Environment.STAGING)
    new ShopApiOrderUpdateJobMonitors(this, Environment.PRODUCTION)

    new ShopApiRevenueReportingJobMonitors(this, Environment.STAGING)
    new ShopApiRevenueReportingJobMonitors(this, Environment.PRODUCTION)

    new ShopApiStuckOrdersJobMonitors(this, Environment.STAGING)
    new ShopApiStuckOrdersJobMonitors(this, Environment.PRODUCTION)

    new SecurityAlertMonitors(this, Environment.STAGING)
    new SecurityAlertMonitors(this, Environment.PRODUCTION)

    new WorkerMonitors(this, Environment.STAGING)
    new WorkerMonitors(this, Environment.PRODUCTION)
  }
}

const app = new App();
const stack = new MonitorsStack(app, "datadog");
new S3Backend(stack, {
  bucket: "w1-datadog-terraform-state",
  key: "datadog",
  dynamodbTable: "datadog_terraform_locks",
  region: "us-west-2",
})

app.synth();
