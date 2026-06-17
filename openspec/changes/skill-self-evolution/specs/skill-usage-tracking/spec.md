## ADDED Requirements

### Requirement: Record skill match events

The system SHALL record a usage event every time a skill is matched during the Agent execution cycle, including timestamp, skill name, matched triggers, user message digest (first 200 chars), and the AgentResponse's toolCallCount and newMemoryCount. All recording is internal with no API exposure.

#### Scenario: Skill matched during agent cycle

- **WHEN** `SkillManager.match()` returns a non-empty result during `AgentService.executeCycle()` or `executeCycleWithEvents()`
- **THEN** for each matched skill, `SkillUsageTracker.recordMatch()` SHALL be called and a JSONL line SHALL be appended to `.memory/skill-usage/<skillName>/<YYYY-MM-DD>.jsonl` with fields: `timestamp`, `type: "match"`, `triggers`, `userMessageDigest`, `toolCallCount`, `newMemoryCount`

#### Scenario: No skill matched

- **WHEN** `SkillManager.match()` returns an empty map
- **THEN** no usage record SHALL be written

#### Scenario: Skill generation tracked

- **WHEN** `SkillManager.createSkill()` is called (new skill generated)
- **THEN** a JSONL line SHALL be written with `type: "created"`, `skillName`, `description`, `triggers`, `timestamp`

### Requirement: Track skill outcome correlation

The system SHALL correlate each skill match with the outcome of the agent cycle (success/failure indicated by whether the LLM call completed without exception and whether user sent corrections). Recording is fully automatic, no user input required.

#### Scenario: Successful agent cycle after skill match

- **WHEN** an agent cycle completes without LLM exception AND the AgentResponse is returned
- **THEN** `SkillUsageTracker.recordOutcome()` SHALL append a JSONL line with `type: "outcome"`, `result: "success"`, `toolCallCount`, and the matched skill names

#### Scenario: Failed agent cycle

- **WHEN** an agent cycle throws an exception during LLM call
- **THEN** `SkillUsageTracker.recordOutcome()` SHALL append `type: "outcome"`, `result: "failure"`, `errorMessage` (truncated to 200 chars)

### Requirement: Internal usage query capability

The system SHALL support internal query of usage records for scoring and enrichment logic, but SHALL NOT expose this via any REST API.

#### Scenario: Internal query for scoring

- **WHEN** `SkillEvolutionService.recalculateScore(skillName)` is called
- **THEN** the system SHALL internally read JSONL records to compute usageCount and successCount without requiring any external input

#### Scenario: Internal query for enrichment context

- **WHEN** `SkillEvolutionService.generateEnrichment(skillName)` is called
- **THEN** the system SHALL internally fetch the N most recent successful usage contexts for the enrichment prompt
