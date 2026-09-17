UPDATE `assistant_workflow_version`
SET `experiment_candidate` = 0
WHERE `workflow_version` IN (
  'ASSISTANT_TOOLS_NO_RAG_V1',
  'ASSISTANT_TOOLS_RAG_V1'
);
