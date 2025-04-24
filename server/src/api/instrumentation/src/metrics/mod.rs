pub use opentelemetry::KeyValue;

pub mod error;
pub mod factory;
pub mod system;

pub const APP_ID_KEY: &str = "app_id";

pub trait ResultCounter<T, E> {
    fn count_result(
        self,
        all_counter: &factory::Counter<u64>,
        err_counter: &factory::Counter<u64>,
        attributes: &[KeyValue],
    ) -> Result<T, E>;
}

impl<T, E> ResultCounter<T, E> for Result<T, E> {
    fn count_result(
        self,
        all_counter: &factory::Counter<u64>,
        err_counter: &factory::Counter<u64>,
        attributes: &[KeyValue],
    ) -> Result<T, E> {
        all_counter.add(1, attributes);
        if self.is_err() {
            err_counter.add(1, attributes);
        }

        self
    }
}
