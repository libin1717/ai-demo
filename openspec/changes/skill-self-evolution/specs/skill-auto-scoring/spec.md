## ADDED Requirements

### Requirement: Compute automatic effectiveness score from usage data

The system SHALL compute an effectiveness score (0-100) for each skill based solely on usage data: usage frequency (30%), execution success rate (40%), and recency (30%). No user feedback is used. Scoring runs automatically within the Agent execution cycle, invisible to users.

#### Scenario: Frequently used skill with high success rate

- **WHEN** skill "spring-controller-pattern" has been matched 20 times, 18 with successful agent cycles (no LLM exception), and last used 2 days ago
- **THEN** effectiveness SHALL be >= 78 (usageCount=min(20/50,1)×100=40 ×0.3=12, successRate=18/20=90 ×0.4=36, recency=100 ×0.3=30, total=78)

#### Scenario: Rarely used skill

- **WHEN** skill "db-migration-helper" has been matched 2 times, both success, last used 45 days ago
- **THEN** effectiveness SHALL be approximately 44 (usageCount=min(2/50,1)×100=4 ×0.3=1.2, successRate=100 ×0.4=40, recency=10 ×0.3=3)

#### Scenario: Skill with high failure rate

- **WHEN** skill "complex-regex" has been matched 10 times, only 3 success
- **THEN** effectiveness SHALL be < 50 — successRate dominates the calculation with 30% × 0.4 = 12

### Requirement: Score recalculated automatically on each usage event

The system SHALL recalculate a skill's effectiveness score every time a new usage record (match or outcome) is written for that skill. No user action required.

#### Scenario: New match triggers recalculation

- **WHEN** skill "spring-controller-pattern" is matched during an agent cycle
- **THEN** after the match record and outcome record are written, `SkillEvolutionService.recalculateScore(skillName)` SHALL be called and the result SHALL be written to the skill's frontmatter `effectiveness` field

### Requirement: Score stored in skill frontmatter

The system SHALL persist the computed effectiveness score and supporting metrics in the skill's YAML frontmatter. The score is visible in existing `GET /agent/skills` and `GET /agent/skills/{name}` responses as part of normal SkillInfo fields, but no dedicated scoring API exists.

#### Scenario: Skill file frontmatter after scoring

- **WHEN** a scoring cycle completes for skill "spring-controller-pattern"
- **THEN** the SKILL.md frontmatter SHALL include: `effectiveness: 85`, `usageCount: 20`, `lastUsed: "2026-06-17T10:30:00"`, `successRate: 90`

### Requirement: Plug the scoring into the existing incrementScore mechanism

The system SHALL replace the simple `incrementScore()` counter with the full effectiveness scoring. Calls to `incrementScore()` during skill matching SHALL be redirected to trigger `recalculateScore()` instead.

#### Scenario: Skill matched — scoring updated

- **WHEN** `AgentService.executeCycle()` calls `skillManager.incrementScore(skillName)` (existing code)
- **THEN** the system SHALL instead trigger the full effectiveness recalculation via `SkillEvolutionService`, updating frontmatter accordingly

### Requirement: Score included in existing skill list response

The system SHALL include evolution fields (effectiveness, usageCount, lastUsed) in the existing `SkillInfo` DTO, making them visible through the existing `GET /agent/skills` endpoint without creating any new API.

#### Scenario: List all skills with scores

- **WHEN** `GET /agent/skills` is called
- **THEN** each `SkillInfo` in the response SHALL include `effectiveness`, `usageCount`, `lastUsed` fields as regular DTO properties
