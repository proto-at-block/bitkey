use std::pin::Pin;

pub type CommandFn<T, E> =
    Pin<Box<dyn next_gen::prelude::Generator<Vec<u8>, Yield = Vec<u8>, Return = Result<T, E>>>>;

#[derive(Debug, PartialEq, Eq)]
pub enum State<T> {
    Data { response: Vec<u8> },
    Result { value: T },
}

pub trait Command<T, E> {
    fn next(&self, response: Vec<u8>) -> Result<State<T>, E>;
}

/// The command! macro wraps a next_gen::prelude::Generator with a concrete Command trait that's exportable via UniFFI.
///
/// See https://docs.rs/next-gen/latest/next_gen/index.html#macro-syntax for more information on how a Generator and the `generator` macro is used.
///
/// # Arguments
///
/// * `struct_name`             - the name of the struct / type to create from the generator function (e.g. Version, SignTransaction)
/// * `generator_name`          - the name of the generator function to wrap
/// * `generator_return_type`   - the return type of the _Result_ of the generator function (n.b. this is ugly and we know it, something something monad transformers)
///
/// # Example
///
/// ```
/// use next_gen::generator;
/// use wca::command_interface::command;
/// use wca::errors::CommandError;
///
/// #[generator(yield(Vec<u8>), resume(Vec<u8>))]
/// fn my_example() -> Result<bool, CommandError> {
///   let data = yield_!("this is returned to the app".into());
///   match std::str::from_utf8(&data) {
///     Ok("this came from the app") => Ok(true),
///     _ => Err(CommandError::InvalidResponse),
///   }
/// }
/// command!(Example = my_example -> bool);
/// ```
#[macro_export]
macro_rules! command {
    ($struct_name:ident = $generator_name:ident -> $generator_return_type:ty) => {
        pub struct $struct_name {
            _lock: std::sync::RwLock<Vec<Vec<u8>>>,
        }

        impl $struct_name {
            pub fn new() -> Self {
                Self { _lock: Default::default() }
            }

            fn generator(&self) -> $crate::command_interface::CommandFn<$generator_return_type, CommandError> {
                next_gen::generator_fn::CallBoxed::call_boxed($generator_name, ())
            }
        }

        impl Default for $struct_name {
            fn default() -> Self {
                Self::new()
            }
        }

        impl $crate::command_interface::Command<$generator_return_type, CommandError> for $struct_name {
            command!(next_impl $generator_return_type);
        }
    };

    ($struct_name:ident = $generator_name:ident -> $generator_return_type:ty, $($argname:ident: $type:ty),*) => {
        pub struct $struct_name {
            _lock: std::sync::RwLock<Vec<Vec<u8>>>,
            $( $argname: $type ),*
        }

        impl $struct_name {
            pub fn new($($argname: $type),*) -> Self {
                Self { _lock: Default::default(), $($argname),* }
            }

            fn generator(&self) -> $crate::command_interface::CommandFn<$generator_return_type, CommandError> {
                next_gen::generator_fn::CallBoxed::call_boxed($generator_name, ($(self.$argname.to_owned()),*,))
            }
        }

        impl $crate::command_interface::Command<$generator_return_type, CommandError> for $struct_name {
            command!(next_impl $generator_return_type);
        }
    };

    (next_impl $generator_return_type:ty) => {
        fn next(&self, response: Vec<u8>) -> std::result::Result<$crate::command_interface::State<$generator_return_type>, $crate::errors::CommandError> {
            let mut generator = self.generator();
            let mut responses = self._lock.write()?;
            for input in responses.iter() {
                generator.as_mut().resume(input.to_owned());
            }

            responses.push(response.clone());
            let response = generator.as_mut().resume(response);

            match response {
                next_gen::generator::GeneratorState::Yielded(response) => Ok($crate::command_interface::State::Data { response }),
                next_gen::generator::GeneratorState::Returned(value) => Ok($crate::command_interface::State::Result { value: value? }),
            }
        }
    };
}
pub use command;

#[macro_export]
macro_rules! yield_from_ {
    ($generator_name:ident($x:expr)) => {
        yield_from_!(yield_from_impl $generator_name(($x,)))
    };

    ($generator_name:ident($($x:expr),*)) => {
        yield_from_!(yield_from_impl $generator_name(($($x),*)))
    };

    (yield_from_impl $generator_name:ident($args:expr)) => {{
        let mut gen = next_gen::generator_fn::CallBoxed::call_boxed($generator_name, $args);
        let mut result = Default::default();
        loop {
            match gen.as_mut().resume(result) {
                next_gen::generator::GeneratorState::Yielded(v) => result = yield_!(v),
                next_gen::generator::GeneratorState::Returned(v) => break v,
            }
        }
    }};
}

#[cfg(test)]
mod tests {
    use super::command;
    use super::{Command, State};
    use crate::errors::CommandError;
    use next_gen::generator;

    #[generator(yield(Vec<u8>), resume(Vec<u8>))]
    fn nullary_generator() -> Result<Vec<u8>, CommandError> {
        Ok(yield_!("nullary".into()))
    }
    command!(Nullary = nullary_generator -> Vec<u8>);

    #[test]
    fn nullary() -> Result<(), CommandError> {
        assert_eq!(
            Nullary::new().next(vec![])?,
            State::Data {
                response: "nullary".into()
            }
        );
        Ok(())
    }

    #[generator(yield(Vec<u8>), resume(Vec<u8>))]
    fn unary_generator(first: String) -> Result<Vec<u8>, CommandError> {
        Ok(yield_!(format!("unary {first}").into()))
    }
    command!(Unary = unary_generator -> Vec<u8>, first: String);

    #[test]
    fn unary() -> Result<(), CommandError> {
        assert_eq!(
            Unary::new("first argument".into()).next(vec![])?,
            State::Data {
                response: "unary first argument".into()
            }
        );
        Ok(())
    }

    #[generator(yield(Vec<u8>), resume(Vec<u8>))]
    fn binary_generator(first: String, second: Vec<String>) -> Result<Vec<u8>, CommandError> {
        Ok(yield_!(format!("unary {first} {second:?}").into()))
    }
    command!(Binary = binary_generator -> Vec<u8>, first: String, second: Vec<String>);

    #[test]
    fn binary() -> Result<(), CommandError> {
        assert_eq!(
            Binary::new("first argument".into(), vec!["second argument".into()]).next(vec![])?,
            State::Data {
                response: "unary first argument [\"second argument\"]".into()
            }
        );
        Ok(())
    }
}
