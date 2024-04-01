use std::sync::Arc;
use std::{collections::HashMap, marker::PhantomData};

use launchdarkly_server_sdk::{
    AttributeValue, Client, Context, ContextBuilder, EvalError, FlagValue, Reason,
};
use serde::{Deserialize, Serialize};

use crate::{config::Mode, service::Service, Error};

static LD_UNAUTHED_USER_KIND: &str = "app_installation";

#[derive(Clone)]
pub struct Flag<'a, T> {
    pub key: &'a str,
    pub phantom: PhantomData<T>,
}

impl<'a, T> Flag<'a, T>
where
    T: Clone,
{
    pub const fn new(key: &'a str) -> Self {
        Self {
            key,
            phantom: PhantomData,
        }
    }

    pub fn resolver(&self, service: &Service) -> Resolver<T> {
        self.with_context(service, "flag")
            .expect("context should always be valid")
    }

    fn with_context(&self, service: &Service, context: &str) -> Result<Resolver<T>, Error> {
        let context = ContextBuilder::new(context)
            .build()
            .map_err(Error::Context)?;
        Ok(Resolver {
            flag: self.clone(),
            service: service.clone(),
            context,
        })
    }
}

#[derive(Clone)]
pub struct Resolver<'a, T> {
    service: Service,
    context: Context,
    flag: Flag<'a, T>,
}

fn resolve<T: Into<FlagValue> + Clone>(
    client: Arc<Client>,
    context: &Context,
    key: &str,
    default: T,
) -> Result<FlagValue, EvalError> {
    let detail = client.variation_detail(context, key, default);
    match detail.reason {
        Reason::Error { error } => {
            // We can only reach this state
            //
            // * if we use a flag before initializing the LaunchDarkly SDK. This is an invalid
            //   application state and should return an error.
            // * if the flag itself is misconfigured, invalid, or malformed. These are
            //   programming errors or invalid states, and should return an error.
            // * if we are in tests and the flag is not configured. We should return an error and
            //   require that the test initializes the flag properly.
            Err(error)
        }
        _ => Ok(detail
            .value
            .expect("value is always available when reason is not an error")),
    }
}

fn resolve_or_panic<T: Into<FlagValue> + Clone>(
    client: Arc<Client>,
    context: &Context,
    key: &str,
    default: T,
) -> FlagValue {
    // Callers of this method expect the FlagValue.
    // If resolve returns an error, we panic.
    match resolve(client, context, key, default) {
        Ok(v) => v,
        Err(e) => panic!("flag {key}: {e:?}"),
    }
}

impl<'a> Resolver<'a, bool> {
    pub fn resolve(&self) -> bool {
        if let Some(b) = self
            .service
            .override_flags
            .get(self.flag.key)
            .and_then(|f| f.parse().ok())
        {
            return b;
        }
        resolve_or_panic(
            self.service.client.clone(),
            &self.context,
            self.flag.key,
            false,
        )
        .as_bool()
        .expect("flag should always be a bool")
    }
}

impl<'a> Resolver<'a, &str> {
    pub fn resolve(&self) -> String {
        if let Some(f) = self.service.override_flags.get(self.flag.key) {
            return f.to_owned();
        }
        resolve_or_panic(
            self.service.client.clone(),
            &self.context,
            self.flag.key,
            FlagValue::Str("".to_string()),
        )
        .as_string()
        .expect("flag should always be a string")
    }
}

fn resolve_flag_value(
    service: &Service,
    flag_key: &str,
    context: &Context,
) -> Result<FlagValue, EvalError> {
    let overridden_flag_value = service.override_flags.get(flag_key);
    if service.mode == Mode::Test {
        let v = overridden_flag_value.ok_or(EvalError::FlagNotFound)?;
        if let Ok(bool_value) = v.parse::<bool>() {
            return Ok(FlagValue::Bool(bool_value));
        }
        if let Ok(number_value) = v.parse::<f64>() {
            return Ok(FlagValue::Number(number_value));
        }

        return Ok(FlagValue::Str(v.to_owned()));
    }

    let v = resolve(
        service.client.clone(),
        context,
        flag_key,
        FlagValue::Str("".to_string()),
    )?;
    if let Some(f) = service.override_flags.get(flag_key) {
        match v {
            FlagValue::Bool(_) => {
                return Ok(FlagValue::Bool(
                    f.parse().map_err(|_| EvalError::WrongType)?,
                ));
            }
            FlagValue::Str(_) => {
                return Ok(FlagValue::Str(f.to_owned()));
            }
            FlagValue::Number(_) => {
                return Ok(FlagValue::Number(
                    f.parse().map_err(|_| EvalError::WrongType)?,
                ));
            }
            FlagValue::Json(_) => unimplemented!("json flag type not supported"),
        }
    }
    Ok(v)
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
#[serde(untagged)]
pub enum FeatureFlagValue {
    Boolean { boolean: bool },
    Double { double: f64 },
    String { string: String },
}

impl From<FlagValue> for FeatureFlagValue {
    fn from(v: FlagValue) -> Self {
        match v {
            FlagValue::Bool(b) => FeatureFlagValue::Boolean { boolean: b },
            FlagValue::Number(n) => FeatureFlagValue::Double { double: n },
            FlagValue::Str(s) => FeatureFlagValue::String { string: s },
            FlagValue::Json(_) => panic!("json flag type not supported"),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct FeatureFlag {
    pub key: String,
    pub value: FeatureFlagValue,
}

impl FeatureFlag {
    pub fn new(key: String, value: impl Into<FeatureFlagValue>) -> Self {
        Self {
            key,
            value: value.into(),
        }
    }
}

pub enum ContextKey {
    Account(String, HashMap<&'static str, String>),
    AppInstallation(String, HashMap<&'static str, String>),
}

impl TryFrom<ContextKey> for Context {
    type Error = Error;

    fn try_from(key: ContextKey) -> Result<Self, Self::Error> {
        let (mut builder, kind, attrs) = match key {
            ContextKey::Account(account_id, attrs) => {
                (ContextBuilder::new(account_id), None, attrs)
            }
            ContextKey::AppInstallation(app_installation_id, attrs) => (
                ContextBuilder::new(app_installation_id),
                Some(LD_UNAUTHED_USER_KIND.to_owned()),
                attrs,
            ),
        };
        if let Some(kind) = kind {
            builder.set_value("kind", AttributeValue::String(kind));
        };
        attrs
            .into_iter()
            .map(
                |(k, v)| match builder.try_set_value(k, AttributeValue::String(v)) {
                    true => Ok(()),
                    false => Err(Error::Context(format!("Invalid Attribute {k}"))),
                },
            )
            .collect::<Result<Vec<_>, _>>()?;
        builder.build().map_err(Error::Context)
    }
}

pub fn evaluate_flags(
    service: &Service,
    flag_keys: Vec<String>,
    context_key: ContextKey,
) -> Result<Vec<FeatureFlag>, Error> {
    let context = context_key.try_into()?;
    flag_keys
        .into_iter()
        .filter_map(|key| match resolve_flag_value(service, &key, &context) {
            Err(EvalError::FlagNotFound) => None,
            Err(_) => Some(Err(Error::Resolve(key.clone()))),
            Ok(flag_value) => Some(Ok(FeatureFlag::new(key, flag_value))),
        })
        .collect()
}
