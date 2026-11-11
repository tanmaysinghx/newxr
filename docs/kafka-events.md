# Kafka events

When Kafka is configured (setup wizard step 4, or **Administration → Kafka**), Newxr publishes every domain event to the topic **`<prefix>.events`** (default `newxr.events`).

- **Key**: the entity key (`ORB-412`, `HELP-1093`, `OPS-77`, `orbit-mobile#284`) or id, so one entity's events keep their order within a partition.
- **Value**: JSON, below.
- **Delivery**: asynchronous, `acks=all`, idempotent producer. A slow or missing broker never blocks or fails a user's request; failures are logged. Changing the settings recreates the producer without a restart.

Without Kafka the same events still drive in-app notifications.

## Message format

```json
{
  "id": "6f1c2a0e-6a43-4d6f-9b7e-2b0f8d6c1e7a",
  "type": "issue.transitioned",
  "app": "track",
  "entityType": "issue",
  "entityKey": "ORB-412",
  "title": "Offline mode for saved itineraries",
  "link": "/track/issue/ORB-412",
  "actor": { "id": 3, "name": "Ravi Kumar", "email": "ravi@example.com", "initials": "RK", "color": "#2f6fde" },
  "summary": "moved ORB-412 to In review",
  "recipients": [2, 7],
  "data": { "status": "IN_REVIEW" },
  "occurredAt": "2026-11-11T09:41:12.512Z"
}
```

`link` is relative to `NEWXR_PUBLIC_URL`. `recipients` are the user ids notified in the app. `actor` is null for system events (escalations, CI reports).

## Event types

| App | Types |
|---|---|
| Track | `project.created`, `issue.created`, `issue.updated`, `issue.assigned`, `issue.transitioned`, `issue.commented`, `issue.deleted`, `sprint.started`, `sprint.completed` |
| Pages | `page.published`, `page.updated`, `page.commented` |
| Boards | `card.created`, `card.assigned`, `card.commented` |
| Code | `pr.review_requested`, `pr.reviewed`, `pr.commented`, `pr.merged`, `pr.declined`, `pipeline.failed` |
| Desk | `request.created`, `request.assigned`, `request.status`, `request.replied`, `request.customer_replied` |
| Pulse | `alert.escalated`, `alert.assigned`, `incident.declared`, `incident.updated`, `oncall.message` |
| Goals | `goal.created`, `goal.updated` |

New types may be added in minor versions; consumers should ignore types they don't know.
