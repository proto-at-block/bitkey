import { Environment } from "../common/environments";

const PROD_ERROR_RECIPIENTS = ["@slack-Block-bitkey-robots"];
const STAGE_RECIPIENTS = ["@slack-Block-bitkey-alerts-staging"];

// Error: goes to robots channel (oncall should check during biz hours)
export const getErrorRecipients = (environment: Environment) => {
    if (environment === Environment.PRODUCTION) {
        return PROD_ERROR_RECIPIENTS;
    } else {
        return STAGE_RECIPIENTS;
    }
}
