pub use opentelemetry::KeyValue;

pub mod error;
pub mod factory;
pub mod system;

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
        if let Err(_) = self {
            err_counter.add(1, attributes);
        }

        self
    }
}
