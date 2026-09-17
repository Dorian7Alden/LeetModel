ALTER TABLE `evaluation_run_attempt`
  ADD COLUMN `idempotency_key` VARCHAR(128) NULL AFTER `experiment_run_id`;

UPDATE `evaluation_run_attempt`
SET `idempotency_key` = CONCAT('evaluation:', `task_id`, ':', `slot_key`, ':attempt:', `attempt_no`)
WHERE `idempotency_key` IS NULL;

ALTER TABLE `evaluation_run_attempt`
  MODIFY COLUMN `idempotency_key` VARCHAR(128) NOT NULL,
  ADD UNIQUE INDEX `uk_evaluation_attempt_idempotency` (`idempotency_key`);
