## ADDED Requirements

### Requirement: Automatically trigger enrichment from usage success patterns

The system SHALL automatically trigger a skill enrichment analysis when a skill has been matched successfully (agent cycle completed without LLM exception) at least 5 times AND its success rate is ≥ 80%, based on usage tracking data alone. This check runs automatically after each outcome record is written.

#### Scenario: Enrichment threshold reached

- **WHEN** skill "spring-controller-pattern" has been matched 8 times with 7 successful outcomes (87.5% success rate)
- **THEN** the system SHALL: (1) log "enrichment threshold met for skill: spring-controller-pattern", (2) collect the 5 most recent successful usage contexts, (3) call LLM with an enrichment prompt including the current skill body + successful contexts, (4) validate the enrichment result through quality gate, (5) if passed, auto-apply via `SkillManager.updateSkill()`

#### Scenario: Threshold not reached — insufficient matches

- **WHEN** skill "db-migration-helper" has been matched only 3 times (all success, 100% rate)
- **THEN** no enrichment SHALL be triggered (match count < 5)

#### Scenario: Threshold not reached — low success rate

- **WHEN** skill "complex-regex" has been matched 10 times but only 5 success (50%)
- **THEN** no enrichment SHALL be triggered (success rate < 80%)

### Requirement: LLM-driven enrichment analysis from usage context

The system SHALL call LLM with a prompt that includes: the current skill body, the 5 most recent successful usage contexts (user message + agent reply summary), and the instruction to identify missing edge cases, unclear steps, or areas for improvement.

#### Scenario: LLM generates enrichment

- **WHEN** enrichment is triggered
- **THEN** LLM SHALL be called with the prompt: "以下是当前技能文档和最近5次成功使用的上下文。请分析技能文档可以在哪些方面改进：补充缺失的边界情况、澄清模糊步骤、增加更具体的示例。输出纯JSON：{\"suggestedContent\": \"改进后的完整技能正文(Markdown)\", \"changesSummary\": \"一句话描述改动\"}"
- **THEN** the result SHALL be parsed and passed to the quality gate

#### Scenario: LLM enrichment fails

- **WHEN** the enrichment LLM call throws or returns unparseable JSON
- **THEN** log the error, do NOT modify the skill, retry on the next successful match

### Requirement: Quality gate before auto-application

The system SHALL validate enrichment results through an internal quality gate before automatically applying them. All quality checks are internal with no user intervention.

#### Scenario: Enrichment passes quality gate

- **WHEN** enrichment LLM returns valid suggestedContent
- **THEN** the system SHALL verify: (1) suggestedContent contains valid YAML front matter with name/description/triggers, (2) core triggers count is ≥ 50% of original triggers, (3) body length is within ±80% of original, (4) content is not empty
- **THEN** if all checks pass, auto-apply via `SkillManager.updateSkill(name, suggestedContent, "enrichment", changesSummary)` and log "skill auto-enriched: {skillName} — {changesSummary}"

#### Scenario: Enrichment fails quality gate

- **WHEN** enrichment result fails any quality check
- **THEN** log the specific failure reason, do NOT modify the skill, retry on next threshold trigger

### Requirement: Auto-apply enrichment without human intervention

The system SHALL automatically apply validated enrichment results. No human approval API or pending-review state exists.

#### Scenario: Enrichment auto-applied

- **WHEN** quality gate passes for skill "spring-controller-pattern"
- **THEN** `SkillManager.updateSkill()` SHALL be called immediately, which: (1) backs up current version to `versions/v<N>.md`, (2) writes new content to SKILL.md with incremented version, (3) records `changeType: "enrichment"` and `changesSummary` in version metadata, (4) records pre-enrichment effectiveness score for regression monitoring

#### Scenario: Enrichment skipped due to quality gate failure

- **WHEN** quality gate rejects the enrichment
- **THEN** the system SHALL log the rejection reason and continue normal operation; the skill remains unchanged; enrichment will be retried when threshold is met again in the future
