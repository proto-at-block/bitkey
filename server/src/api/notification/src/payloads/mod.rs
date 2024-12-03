use time::Duration;

pub mod comms_verification;
pub mod inheritance_claim_canceled;
pub mod inheritance_claim_period_completed;
pub mod inheritance_claim_period_initiated;
pub mod payment;
pub mod privileged_action_canceled_delay_period;
pub mod privileged_action_completed_delay_period;
pub mod privileged_action_pending_delay_period;
pub mod push_blast;
pub mod recovery_canceled_delay_period;
pub mod recovery_completed_delay_period;
pub mod recovery_pending_delay_period;
pub mod recovery_relationship_benefactor_invitation_pending;
pub mod recovery_relationship_deleted;
pub mod recovery_relationship_invitation_accepted;
pub mod social_challenge_response_received;
pub mod test_notification;

pub fn format_duration(duration: Duration) -> String {
    if duration.whole_hours() > 18 {
        // Call anything above 18 hours 1 day
        let whole_days = duration.whole_days().max(1);
        format!(
            "{} day{}",
            whole_days,
            if whole_days != 1 { "s" } else { "" }
        )
    } else if duration.whole_minutes() > 45 {
        // Call anything above 45 minutes 1 hour
        let whole_hours = duration.whole_hours().max(1);
        format!(
            "{} hour{}",
            whole_hours,
            if whole_hours != 1 { "s" } else { "" }
        )
    } else {
        let whole_minutes = duration.whole_minutes();
        format!(
            "{} minute{}",
            whole_minutes,
            if whole_minutes != 1 { "s" } else { "" }
        )
    }
}
