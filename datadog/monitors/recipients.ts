import { Environment } from "./common/environments";

const PROD_CRITICAL_RECIPIENTS = ["@slack-Block-bitkey-alerts", "@pagerduty-fromagerie"];
const PROD_ERROR_RECIPIENTS = ["@slack-Block-bitkey-robots"];
const PROD_WARNING_RECIPIENTS = ["@slack-Block-w1-noise"];
const STAGE_RECIPIENTS = ["@slack-Block-bitkey-alerts-staging"];

// Critical: goes to alerts channel and pages (oncall should address at any hour)
export const getCriticalRecipients = (environment: Environment) => {
    if (environment === Environment.PRODUCTION) {
        return PROD_CRITICAL_RECIPIENTS;
    } else {
        return STAGE_RECIPIENTS;
    }
}

// Error: goes to robots channel (oncall should check during biz hours)
export const getErrorRecipients = (environment: Environment) => {
    if (environment === Environment.PRODUCTION) {
        return PROD_ERROR_RECIPIENTS;
    } else {
        return STAGE_RECIPIENTS;
    }
}

// Warning: goes to noise channel (neither of above, maybe useful for debugging / investigations)
export const getWarningRecipients = (environment: Environment) => {
    if (environment === Environment.PRODUCTION) {
        return PROD_WARNING_RECIPIENTS;
    } else {
        return STAGE_RECIPIENTS;
    }
}
