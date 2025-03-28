use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};
use utoipa::ToSchema;

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema, PartialEq, Eq)]
#[serde(tag = "name")]
pub enum State {
    Triage {},
    #[serde(rename = "Waiting for Customer")]
    WaitingForCustomer {},
    Backlog {},
    Todo {},
    #[serde(rename = "Ready for Copy")]
    ReadyForCopy {},
    #[serde(rename = "Ready for Design")]
    ReadyForDesign {},
    #[serde(rename = "Ready for Eng")]
    ReadyForEng {},
    Blocked {},
    #[serde(rename = "In Progress")]
    InProgress {},
    #[serde(rename = "In Review")]
    InReview {},
    Done {},
    Canceled {},
    Investigating {},
    #[serde(other)]
    Other,
}

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema, PartialEq, Eq)]
#[serde(tag = "type")]
pub enum Actor {
    User {},
    #[serde(other)]
    Other,
}

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema, PartialEq, Eq)]
#[serde(tag = "name")]
pub enum Label {
    #[serde(rename = "CS Escalation")]
    CsEscalation {},
    #[serde(other)]
    Other,
}

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Data {
    #[serde(default)]
    pub state: Option<State>,
    #[serde(default)]
    pub labels: Option<Vec<Label>>,
    #[serde(default, with = "rfc3339::option")]
    pub sla_breaches_at: Option<OffsetDateTime>,
    #[serde(default, with = "rfc3339::option")]
    pub updated_at: Option<OffsetDateTime>,
}

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct IssueUpdate {
    #[serde(default)]
    pub actor: Option<Actor>,
    pub data: Data,
    pub updated_from: Data,
}

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema, PartialEq, Eq)]
#[serde(tag = "action", rename_all = "camelCase")]
pub enum Issue {
    Update(IssueUpdate),
    #[serde(other)]
    Other,
}

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct IssueSlaBreached {
    pub issue_data: Data,
}

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema, PartialEq, Eq)]
#[serde(tag = "action", rename_all = "camelCase")]
pub enum IssueSla {
    Breached(IssueSlaBreached),
    #[serde(other)]
    Other,
}

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema, PartialEq, Eq)]
#[serde(tag = "type")]
pub enum Webhook {
    Issue(Issue),
    #[serde(rename = "IssueSLA")]
    IssueSla(IssueSla),
    #[serde(other)]
    Other,
}

#[cfg(test)]
mod tests {
    use time::{format_description::well_known::Rfc3339, OffsetDateTime};

    use crate::entities::{Data, Issue, IssueSla, IssueSlaBreached, IssueUpdate, Webhook};

    #[test]
    fn test_unknown_type() {
        let payload = r#"{
            "type": "Unknown"
        }"#;
        let webhook: Webhook = serde_json::from_str(payload).unwrap();
        assert_eq!(webhook, Webhook::Other);
    }

    #[test]
    fn test_issue_update() {
        let payload = r#"{
            "action": "update",
            "actor": null,
            "createdAt": "2025-03-13T17:10:02.719Z",
            "data": {
            "id": "",
            "createdAt": "2025-03-13T17:09:34.744Z",
            "updatedAt": "2025-03-13T17:10:02.730Z",
            "number": 0,
            "title": "test",
            "priority": 0,
            "boardOrder": 0,
            "sortOrder": -28.999950084548026,
            "prioritySortOrder": -933.73,
            "startedTriageAt": "2025-03-13T17:09:34.803Z",
            "triagedAt": "2025-03-13T17:10:02.317Z",
            "slaMediumRiskAt": "2025-03-07T17:09:35.482Z",
            "slaHighRiskAt": "2025-03-13T17:09:35.482Z",
            "slaType": "all",
            "addedToTeamAt": "2025-03-13T17:09:34.905Z",
            "labelIds": [
                ""
            ],
            "teamId": "",
            "previousIdentifiers": [],
            "creatorId": "",
            "assigneeId": "",
            "stateId": "",
            "reactionData": [],
            "priorityLabel": "No priority",
            "botActor": null,
            "identifier": "",
            "url": "",
            "subscriberIds": [
                "",
                ""
            ],
            "assignee": {
                "id": "",
                "name": "",
                "email": "",
                "avatarUrl": ""
            },
            "state": {
                "id": "",
                "color": "",
                "name": "Waiting for Customer",
                "type": ""
            },
            "team": {
                "id": "",
                "key": "",
                "name": ""
            },
            "labels": [
                {
                "id": "",
                "color": "",
                "name": "CS Escalation"
                }
            ],
            "description": "test",
            "descriptionData": "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"test\"}]}]}"
            },
            "updatedFrom": {
            "updatedAt": "2025-03-13T17:10:02.328Z",
            "slaStartedAt": "2025-03-13T17:09:35.482Z",
            "slaBreachesAt": "2025-03-14T17:09:35.482Z"
            },
            "url": "",
            "type": "Issue",
            "organizationId": "",
            "webhookTimestamp": 1741885802799,
            "webhookId": ""
        }"#;
        let webhook: Webhook = serde_json::from_str(payload).unwrap();
        assert_eq!(
            webhook,
            Webhook::Issue(Issue::Update(IssueUpdate {
                actor: None,
                data: Data {
                    state: Some(super::State::WaitingForCustomer {}),
                    labels: Some(vec![super::Label::CsEscalation {}]),
                    sla_breaches_at: None,
                    updated_at: Some(
                        OffsetDateTime::parse("2025-03-13T17:10:02.730Z", &Rfc3339).unwrap()
                    )
                },
                updated_from: Data {
                    state: None,
                    labels: None,
                    sla_breaches_at: Some(
                        OffsetDateTime::parse("2025-03-14T17:09:35.482Z", &Rfc3339).unwrap()
                    ),
                    updated_at: Some(
                        OffsetDateTime::parse("2025-03-13T17:10:02.328Z", &Rfc3339).unwrap()
                    )
                },
            }))
        );
    }

    #[test]
    fn test_unknown_issue_state() {
        let payload = r#"{
            "action": "update",
            "actor": null,
            "createdAt": "2025-03-13T17:10:02.719Z",
            "data": {
            "id": "",
            "createdAt": "2025-03-13T17:09:34.744Z",
            "updatedAt": "2025-03-13T17:10:02.730Z",
            "number": 0,
            "title": "test",
            "priority": 0,
            "boardOrder": 0,
            "sortOrder": -28.999950084548026,
            "prioritySortOrder": -933.73,
            "startedTriageAt": "2025-03-13T17:09:34.803Z",
            "triagedAt": "2025-03-13T17:10:02.317Z",
            "slaMediumRiskAt": "2025-03-07T17:09:35.482Z",
            "slaHighRiskAt": "2025-03-13T17:09:35.482Z",
            "slaType": "all",
            "addedToTeamAt": "2025-03-13T17:09:34.905Z",
            "labelIds": [
                ""
            ],
            "teamId": "",
            "previousIdentifiers": [],
            "creatorId": "",
            "assigneeId": "",
            "stateId": "",
            "reactionData": [],
            "priorityLabel": "No priority",
            "botActor": null,
            "identifier": "",
            "url": "",
            "subscriberIds": [
                "",
                ""
            ],
            "assignee": {
                "id": "",
                "name": "",
                "email": "",
                "avatarUrl": ""
            },
            "state": {
                "id": "",
                "color": "",
                "name": "Waiting for Universe",
                "type": ""
            },
            "team": {
                "id": "",
                "key": "",
                "name": ""
            },
            "labels": [
                {
                "id": "",
                "color": "",
                "name": "CS Escalation"
                }
            ],
            "description": "test",
            "descriptionData": "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"test\"}]}]}"
            },
            "updatedFrom": {
            "updatedAt": "2025-03-13T17:10:02.328Z",
            "slaStartedAt": "2025-03-13T17:09:35.482Z",
            "slaBreachesAt": "2025-03-14T17:09:35.482Z"
            },
            "url": "",
            "type": "Issue",
            "organizationId": "",
            "webhookTimestamp": 1741885802799,
            "webhookId": ""
        }"#;
        let webhook: Webhook = serde_json::from_str(payload).unwrap();
        assert_eq!(
            webhook,
            Webhook::Issue(Issue::Update(IssueUpdate {
                actor: None,
                data: Data {
                    state: Some(super::State::Other),
                    labels: Some(vec![super::Label::CsEscalation {}]),
                    sla_breaches_at: None,
                    updated_at: Some(
                        OffsetDateTime::parse("2025-03-13T17:10:02.730Z", &Rfc3339).unwrap()
                    )
                },
                updated_from: Data {
                    state: None,
                    labels: None,
                    sla_breaches_at: Some(
                        OffsetDateTime::parse("2025-03-14T17:09:35.482Z", &Rfc3339).unwrap()
                    ),
                    updated_at: Some(
                        OffsetDateTime::parse("2025-03-13T17:10:02.328Z", &Rfc3339).unwrap()
                    )
                },
            }))
        );
    }

    #[test]
    fn test_unknown_issue_action() {
        let payload = r#"{
            "action": "unknown",
            "type": "Issue"
        }"#;
        let webhook: Webhook = serde_json::from_str(payload).unwrap();
        assert_eq!(webhook, Webhook::Issue(Issue::Other));
    }

    #[test]
    fn test_issue_sla_breached() {
        let payload = r#"{
            "action": "breached",
            "createdAt": "2025-03-13T17:09:34.744Z",
            "issueData": {
            "id": "",
            "createdAt": "2025-03-13T17:09:34.744Z",
            "updatedAt": "2025-03-13T17:09:35.490Z",
            "archivedAt": null,
            "number": 0,
            "title": "test",
            "priority": 0,
            "estimate": null,
            "boardOrder": 0,
            "sortOrder": -29,
            "prioritySortOrder": -933.73,
            "startedAt": null,
            "completedAt": null,
            "startedTriageAt": "2025-03-13T17:09:34.803Z",
            "triagedAt": null,
            "canceledAt": null,
            "autoClosedAt": null,
            "autoArchivedAt": null,
            "dueDate": null,
            "slaStartedAt": "2025-03-13T17:09:35.482Z",
            "slaMediumRiskAt": "2025-03-07T17:09:35.482Z",
            "slaHighRiskAt": "2025-03-13T17:09:35.482Z",
            "slaBreachesAt": "2025-03-14T17:09:35.482Z",
            "slaType": "all",
            "addedToProjectAt": null,
            "addedToCycleAt": null,
            "addedToTeamAt": "2025-03-13T17:09:34.905Z",
            "trashed": null,
            "snoozedUntilAt": null,
            "labelIds": [
                ""
            ],
            "teamId": "",
            "cycleId": null,
            "projectId": null,
            "projectMilestoneId": null,
            "lastAppliedTemplateId": null,
            "recurringIssueTemplateId": null,
            "previousIdentifiers": [],
            "creatorId": "",
            "externalUserCreatorId": null,
            "assigneeId": "",
            "snoozedById": null,
            "stateId": "",
            "subIssueSortOrder": null,
            "reactionData": [],
            "priorityLabel": "No priority",
            "sourceCommentId": null,
            "botActor": null,
            "identifier": "",
            "url": "",
            "subscriberIds": [
                "",
                ""
            ],
            "parentId": null,
            "assignee": {
                "id": "",
                "name": "",
                "email": "",
                "avatarUrl": ""
            },
            "state": {
                "id": "",
                "color": "",
                "name": "Triage",
                "type": ""
            },
            "team": {
                "id": "",
                "key": "",
                "name": ""
            },
            "labels": [
                {
                "id": "",
                "color": "",
                "name": "CS Escalation"
                }
            ],
            "description": "test",
            "descriptionData": "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"test\"}]}]}"
            },
            "url": "https://linear.app/squareup/issue/W-11031",
            "type": "IssueSLA",
            "organizationId": "",
            "webhookTimestamp": 1741885776320,
            "webhookId": ""
        }"#;
        let webhook: Webhook = serde_json::from_str(payload).unwrap();
        assert_eq!(
            webhook,
            Webhook::IssueSla(IssueSla::Breached(IssueSlaBreached {
                issue_data: Data {
                    state: Some(super::State::Triage {}),
                    labels: Some(vec![super::Label::CsEscalation {}]),
                    sla_breaches_at: Some(
                        OffsetDateTime::parse("2025-03-14T17:09:35.482Z", &Rfc3339).unwrap()
                    ),
                    updated_at: Some(
                        OffsetDateTime::parse("2025-03-13T17:09:35.490Z", &Rfc3339).unwrap()
                    )
                },
            }))
        );
    }

    #[test]
    fn test_unknown_issue_sla_action() {
        let payload = r#"{
            "action": "unknown",
            "type": "IssueSLA"
        }"#;
        let webhook: Webhook = serde_json::from_str(payload).unwrap();
        assert_eq!(webhook, Webhook::IssueSla(IssueSla::Other));
    }
}
