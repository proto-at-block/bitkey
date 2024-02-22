import { Environment } from "./common/environments";

const DEFAULT_LOW_PRIORITY_RECIPIENTS = ["@slack-Block-w1-alerts"];
const DEFAULT_HIGH_PRIORITY_RECIPIENTS = ["@slack-Block-w1-alerts", "@pagerduty-fromagerie"];
const DEFAULT_STAGING_RECIPIENTS = ["@slack-Block-w1-alerts-staging"];

export const getRecipients = (environment: Environment, highPriority?: boolean) => {
    if (environment === Environment.PRODUCTION) {
        if (highPriority) {
            return DEFAULT_HIGH_PRIORITY_RECIPIENTS;
        } else {
            return DEFAULT_LOW_PRIORITY_RECIPIENTS;
        }
    } else {
        return DEFAULT_STAGING_RECIPIENTS;
    }
}