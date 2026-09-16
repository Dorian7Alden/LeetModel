UPDATE message_outbox outbox_event
LEFT JOIN submission_lock final_lock
  ON final_lock.submission_id = CAST(outbox_event.aggregate_id AS UNSIGNED)
SET outbox_event.status = 'CANCELLED',
    outbox_event.lease_owner = NULL,
    outbox_event.lease_expires_at = NULL,
    outbox_event.last_error = 'Draft review dispatch cancelled: only locked final submissions are reviewed',
    outbox_event.update_time = CURRENT_TIMESTAMP(3)
WHERE outbox_event.event_type = 'REVIEW_TASK_READY'
  AND outbox_event.status IN ('PENDING', 'SENDING', 'BLOCKED')
  AND final_lock.submission_id IS NULL;
