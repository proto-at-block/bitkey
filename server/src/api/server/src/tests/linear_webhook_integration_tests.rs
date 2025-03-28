use std::sync::LazyLock;

use http::StatusCode;
use linear::routes::TEST_LINEAR_WEBHOOK_SECRET;
use rstest::rstest;
use serde_json::Value;

use super::{gen_services, requests::axum::TestClient};

#[rstest]
#[case::happy_path(VALID_WEBHOOK, Some(TEST_LINEAR_WEBHOOK_SECRET.to_string()), StatusCode::OK)]
#[case::missing_header(VALID_WEBHOOK, None, StatusCode::BAD_REQUEST)]
#[case::incorrect_secret(VALID_WEBHOOK, Some("incorrect-webhook-secret".to_string()), StatusCode::UNAUTHORIZED)]
#[case::invalid_body(INVALID_WEBHOOK, Some(TEST_LINEAR_WEBHOOK_SECRET.to_string()), StatusCode::BAD_REQUEST)]
#[tokio::test]
async fn purchase_config_success(
    #[case] webhook_json: LazyLock<Value>,
    #[case] webhook_secret: Option<String>,
    #[case] expected_status: StatusCode,
) {
    let (_, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let result = client
        .linear_webhook(
            serde_json::to_string(&*webhook_json).unwrap(),
            webhook_secret,
        )
        .await;

    assert_eq!(result.status_code, expected_status);
}

const VALID_WEBHOOK: LazyLock<Value> = LazyLock::new(|| {
    serde_json::from_str(r#"{
        "action": "update",
        "actor": {
            "id": "",
            "name": "",
            "email": "",
            "avatarUrl": "",
            "type": "user"
        },
        "createdAt": "2025-03-13T17:10:02.272Z",
        "data": {
            "id": "",
            "createdAt": "2025-03-13T17:09:34.744Z",
            "updatedAt": "2025-03-13T17:10:02.328Z",
            "number": 0,
            "title": "test",
            "priority": 0,
            "boardOrder": 0,
            "sortOrder": -28.999950084548026,
            "prioritySortOrder": -933.73,
            "startedTriageAt": "2025-03-13T17:09:34.803Z",
            "triagedAt": "2025-03-13T17:10:02.317Z",
            "slaStartedAt": "2025-03-13T17:09:35.482Z",
            "slaMediumRiskAt": "2025-03-07T17:09:35.482Z",
            "slaHighRiskAt": "2025-03-13T17:09:35.482Z",
            "slaBreachesAt": "2025-03-14T17:09:35.482Z",
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
            "updatedAt": "2025-03-13T17:09:35.490Z",
            "sortOrder": -29,
            "triagedAt": null,
            "stateId": ""
        },
        "url": "",
        "type": "Issue",
        "organizationId": "",
        "webhookTimestamp": 1741885802390,
        "webhookId": ""
        }"#).unwrap()
});

const INVALID_WEBHOOK: LazyLock<Value> = LazyLock::new(|| {
    serde_json::from_str(r#"{
        "actor": {
            "id": "",
            "name": "",
            "email": "",
            "avatarUrl": "",
            "type": "user"
        },
        "createdAt": "2025-03-13T17:10:02.272Z",
        "data": {
            "id": "",
            "createdAt": "2025-03-13T17:09:34.744Z",
            "updatedAt": "2025-03-13T17:10:02.328Z",
            "number": 0,
            "title": "test",
            "priority": 0,
            "boardOrder": 0,
            "sortOrder": -28.999950084548026,
            "prioritySortOrder": -933.73,
            "startedTriageAt": "2025-03-13T17:09:34.803Z",
            "triagedAt": "2025-03-13T17:10:02.317Z",
            "slaStartedAt": "2025-03-13T17:09:35.482Z",
            "slaMediumRiskAt": "2025-03-07T17:09:35.482Z",
            "slaHighRiskAt": "2025-03-13T17:09:35.482Z",
            "slaBreachesAt": "2025-03-14T17:09:35.482Z",
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
            "updatedAt": "2025-03-13T17:09:35.490Z",
            "sortOrder": -29,
            "triagedAt": null,
            "stateId": ""
        },
        "url": "",
        "type": "Issue",
        "organizationId": "",
        "webhookTimestamp": 1741885802390,
        "webhookId": ""
        }"#).unwrap()
});
