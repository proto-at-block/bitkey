use std::{collections::HashSet, hash::Hash};

use serde::{Deserialize, Serialize};
use strum::IntoEnumIterator;
use strum_macros::{Display as StrumDisplay, EnumIter};
use time::{serde::rfc3339, OffsetDateTime};
use utoipa::ToSchema;

#[derive(
    Deserialize,
    Serialize,
    StrumDisplay,
    Clone,
    Debug,
    Copy,
    PartialEq,
    Eq,
    Hash,
    ToSchema,
    EnumIter,
)]
pub enum NotificationChannel {
    #[serde(alias = "email")]
    Email,
    #[serde(alias = "push")]
    Push,
    #[serde(alias = "s_m_s")]
    Sms,
}

#[derive(Deserialize, Serialize, Clone, Debug, Copy, PartialEq, Eq, Hash, EnumIter)]
pub enum NotificationCategory {
    AccountSecurity,
    MoneyMovement,
    ProductMarketing,
}

#[derive(Clone, Debug)]
pub struct NotificationsPreferencesDiff {
    pub subscribes: Vec<(NotificationCategory, NotificationChannel)>,
    pub unsubscribes: Vec<(NotificationCategory, NotificationChannel)>,
}

#[derive(Deserialize, Serialize, Clone, Debug, PartialEq, Eq, Default, ToSchema)]
pub struct NotificationsPreferences {
    pub account_security: HashSet<NotificationChannel>,
    pub money_movement: HashSet<NotificationChannel>,
    pub product_marketing: HashSet<NotificationChannel>,
}

impl NotificationsPreferences {
    pub fn get_email_notification_categories(&self) -> HashSet<NotificationCategory> {
        NotificationCategory::iter()
            .filter(|category| match category {
                NotificationCategory::AccountSecurity => {
                    self.account_security.contains(&NotificationChannel::Email)
                }
                NotificationCategory::MoneyMovement => {
                    self.money_movement.contains(&NotificationChannel::Email)
                }
                NotificationCategory::ProductMarketing => {
                    self.product_marketing.contains(&NotificationChannel::Email)
                }
            })
            .collect()
    }

    pub fn with_enabled(
        &self,
        notification_category: NotificationCategory,
        notification_channel: NotificationChannel,
    ) -> Self {
        let mut result = self.clone();
        match notification_category {
            NotificationCategory::AccountSecurity => {
                result.account_security.insert(notification_channel);
            }
            NotificationCategory::MoneyMovement => {
                result.money_movement.insert(notification_channel);
            }
            NotificationCategory::ProductMarketing => {
                result.product_marketing.insert(notification_channel);
            }
        }
        result
    }
}

#[derive(Deserialize, Serialize, Clone, Debug, PartialEq, Eq)]
pub struct NotificationsPreferencesState {
    pub account_security: HashSet<NotificationChannel>,
    pub money_movement: HashSet<NotificationChannel>,
    pub product_marketing: HashSet<NotificationChannel>,
    #[serde(default = "OffsetDateTime::now_utc")]
    pub email_updated_at: OffsetDateTime,
}

impl Default for NotificationsPreferencesState {
    fn default() -> Self {
        Self {
            account_security: HashSet::new(),
            money_movement: HashSet::new(),
            product_marketing: HashSet::new(),
            email_updated_at: OffsetDateTime::now_utc(),
        }
    }
}

impl NotificationsPreferencesState {
    pub fn get_email_notification_categories(&self) -> HashSet<NotificationCategory> {
        NotificationCategory::iter()
            .filter(|category| match category {
                NotificationCategory::AccountSecurity => {
                    self.account_security.contains(&NotificationChannel::Email)
                }
                NotificationCategory::MoneyMovement => {
                    self.money_movement.contains(&NotificationChannel::Email)
                }
                NotificationCategory::ProductMarketing => {
                    self.product_marketing.contains(&NotificationChannel::Email)
                }
            })
            .collect()
    }

    pub fn with_email_notification_categories(
        &self,
        email_notification_categories: HashSet<NotificationCategory>,
    ) -> Self {
        let mut result = self.clone();
        for category in NotificationCategory::iter() {
            match category {
                NotificationCategory::AccountSecurity => {
                    if email_notification_categories.contains(&category) {
                        result.account_security.insert(NotificationChannel::Email);
                    } else {
                        result.account_security.remove(&NotificationChannel::Email);
                    }
                }
                NotificationCategory::MoneyMovement => {
                    if email_notification_categories.contains(&category) {
                        result.money_movement.insert(NotificationChannel::Email);
                    } else {
                        result.money_movement.remove(&NotificationChannel::Email);
                    }
                }
                NotificationCategory::ProductMarketing => {
                    if email_notification_categories.contains(&category) {
                        result.product_marketing.insert(NotificationChannel::Email);
                    } else {
                        result.product_marketing.remove(&NotificationChannel::Email);
                    }
                }
            }
        }
        result
    }

    pub fn update(&self, notifications_preferences: &NotificationsPreferences) -> Self {
        Self {
            account_security: notifications_preferences.account_security.clone(),
            money_movement: notifications_preferences.money_movement.clone(),
            product_marketing: notifications_preferences.product_marketing.clone(),
            email_updated_at: if self.get_email_notification_categories()
                != notifications_preferences.get_email_notification_categories()
            {
                OffsetDateTime::now_utc()
            } else {
                self.email_updated_at
            },
        }
    }

    pub fn is_enabled(&self, category: NotificationCategory, channel: NotificationChannel) -> bool {
        match category {
            NotificationCategory::AccountSecurity => self.account_security.contains(&channel),
            NotificationCategory::MoneyMovement => self.money_movement.contains(&channel),
            NotificationCategory::ProductMarketing => self.product_marketing.contains(&channel),
        }
    }

    pub fn diff(&self, new: &Self) -> NotificationsPreferencesDiff {
        let (subscribes, unsubscribes) = NotificationCategory::iter().fold(
            (Vec::new(), Vec::new()),
            |(mut subscribes, mut unsubscribes), category| {
                for channel in NotificationChannel::iter() {
                    if self.is_enabled(category, channel) && !new.is_enabled(category, channel) {
                        unsubscribes.push((category, channel));
                    } else if !self.is_enabled(category, channel)
                        && new.is_enabled(category, channel)
                    {
                        subscribes.push((category, channel));
                    }
                }

                (subscribes, unsubscribes)
            },
        );

        NotificationsPreferencesDiff {
            subscribes,
            unsubscribes,
        }
    }
}

impl From<NotificationsPreferencesState> for NotificationsPreferences {
    fn from(state: NotificationsPreferencesState) -> Self {
        Self {
            account_security: state.account_security,
            money_movement: state.money_movement,
            product_marketing: state.product_marketing,
        }
    }
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum NotificationsTriggerType {
    SecurityHubWalletAtRisk,
}

#[derive(Deserialize, Serialize, Clone, Debug, PartialEq)]
pub struct NotificationsTrigger {
    pub trigger_type: NotificationsTriggerType,
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
}

impl NotificationsTrigger {
    pub fn new(
        trigger_type: NotificationsTriggerType,
        created_at: OffsetDateTime,
        updated_at: OffsetDateTime,
    ) -> Self {
        Self {
            trigger_type,
            created_at,
            updated_at,
        }
    }
}
