use time::OffsetDateTime;

pub trait Clock: Send + Sync {
    fn now_utc(&self) -> OffsetDateTime;
}

pub struct DefaultClock;

impl Clock for DefaultClock {
    fn now_utc(&self) -> OffsetDateTime {
        OffsetDateTime::now_utc()
    }
}
