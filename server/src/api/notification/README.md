# Notifications

## Scheduled Notifications vs. Customer Notifications
Broadly, a Scheduled Notification is defined by the following:
1. An account ID
2. A notification payload + schedule + validator
3. An execution datetime
4. A reschedule interval
5. A status in [New, Complete, Error]
A Customer Notification, on the other hand, is:
1. A touchpoint ID
2. A notification payload
3. A status in [Enqueued, Complete, Error]

## How Notifications are Sent

### Once Immediately

#### API
A consumer that wants to send a notification immediately uses the `NotificationService.SendNotifications` call. This call takes an account ID, a specified payload, and an optional set of destination touchpoints. The service then:
1. Iterates through the touchpoints on the account & decides if each is a valid target for the notification (either because it's in the optional set of destination touchpoints provided by the caller or because it is active and of a channel in [Email, Push, SMS] that has a defined payload on the input)
2. Creates the associated Customer Notification row in DDB for each valid target touchpoint
3. Enqueues the message for each valid target touchpoint

#### Worker
A Customer Notification worker dequeues each message, sends the message via an external vendor (e.g. Twilio), and marks it Complete in DDB.

### N Times according to a Schedule

#### API
A consumer that wants to send a notification on a schedule uses the `NotificationService.ScheduleNotifications` call. This call takes an account ID and a specified payload (which itself has a defined schedule and validator). The service then creates the associated Scheduled Notification row in DDB.

#### Worker
A Scheduled Notification worker periodically queries DDB for Scheduled Notifications that are scheduled to execute in a recent time window. For each one, it:
1. Executes the validator and aborts if it fails
2. Calls `NotificationService.SendNotifications` for the payload
3. Marks the Scheduled Notification Complete in DDB
4. Reschedules the notification for its next occurrence

Simplified graphic [here](https://docs.google.com/document/d/1wKeu53x9XUKtfDb9sK3mj56obMkpg7Ibi-aMghkdmNc/edit?usp=sharing)
