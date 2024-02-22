use std::fmt::{Display, Formatter, Result};

use time::format_description::well_known::Rfc2822;

use super::{
    DelayAndNotifyRecoveryStatus, Factor, PendingDelayNotify,
    PendingRecoveryForWalletStatusResponse, RecoveryStatusResponse,
};

impl Display for Factor {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            Factor::App => write!(f, "App"),
            Factor::Hw => write!(f, "Hw"),
        }
    }
}

impl Display for PendingRecoveryForWalletStatusResponse {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> std::fmt::Result {
        writeln!(
            formatter,
            "In Recovery: {}",
            match self.in_recovery {
                true => "Yes",
                false => "No",
            }
        )?;
        if !self.in_recovery {
            return Ok(());
        }
        writeln!(
            formatter,
            "Recovery Type: {}",
            self.recovery_type.as_ref().unwrap()
        )?;
        if let Some(delay_and_notify_recovery) = &self.delay_and_notify_recovery {
            writeln!(formatter, "{delay_and_notify_recovery}")?;
        }
        Ok(())
    }
}

impl Display for DelayAndNotifyRecoveryStatus {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> std::fmt::Result {
        write!(
            formatter,
            "Delay Period ends at {}",
            self.delay_end_time.format(&Rfc2822).unwrap()
        )?;
        Ok(())
    }
}

impl Display for RecoveryStatusResponse {
    fn fmt(&self, f: &mut Formatter<'_>) -> Result {
        if let Some(pdn) = &self.pending_delay_notify {
            write!(f, "{pdn}")?;
        } else {
            write!(f, "No pending recovery")?;
        }

        Ok(())
    }
}

impl Display for PendingDelayNotify {
    fn fmt(&self, f: &mut Formatter<'_>) -> Result {
        let readiness = match self.delay_end_time < time::OffsetDateTime::now_utc() {
            false => "PENDING",
            true => "READY TO COMPLETE",
        };

        write!(
            f,
            "Lost {factor} Delay and Notify {readiness} ending at {time}",
            factor = self.lost_factor,
            time = self
                .delay_end_time
                .format(&Rfc2822)
                .expect("malformated datetime"),
        )?;

        Ok(())
    }
}
