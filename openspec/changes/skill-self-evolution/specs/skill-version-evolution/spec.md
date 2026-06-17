## ADDED Requirements

### Requirement: Version history on skill update

The system SHALL preserve the previous version of a skill whenever its content is modified via `updateSkill()`, storing it as a versioned file. All version management is internal with no API exposure.

#### Scenario: Skill update creates version backup

- **WHEN** `SkillManager.updateSkill("spring-controller-pattern", newContent)` is called
- **THEN** the system SHALL: (1) read the current SKILL.md, (2) copy it to `.memory/skills/spring-controller-pattern/versions/v<N>.md` where N is the current version, (3) write the new content to SKILL.md with `version: <N+1>` in frontmatter, (4) update `updatedAt` timestamp

#### Scenario: First update after creation

- **WHEN** a skill created with version 1 is updated for the first time
- **THEN** `versions/v1.md` SHALL be created and the main file's version SHALL become 2

### Requirement: Version limit enforcement

The system SHALL keep at most 10 historical versions per skill. When the 11th version would be created, the oldest version SHALL be deleted.

#### Scenario: Version limit reached

- **WHEN** skill has 10 existing versions and a new update is applied
- **THEN** `versions/v1.md` SHALL be deleted, remaining versions SHALL be retained (v2-v10), and the new version SHALL be saved as v11

### Requirement: Regression detection and automatic rollback

The system SHALL compare a skill's effectiveness score before and after an update. If the score drops by 15 or more points within 7 days after an update, the system SHALL automatically revert to the previous version. No user intervention required.

#### Scenario: Regression detected — auto rollback

- **WHEN** skill "spring-controller-pattern" effectiveness drops from 85 (pre-enrichment) to 60 (post-enrichment, measured 3 days later)
- **THEN** the system SHALL: (1) log "regression detected for skill: spring-controller-pattern (85 → 60, -25 points)", (2) automatically call `SkillManager.revertSkill(name, previousVersion)` to restore the pre-enrichment version, (3) log "skill auto-reverted: spring-controller-pattern to v{N-1}", (4) mark the enrichment version as `regression: true` in its metadata

#### Scenario: Normal fluctuation — no rollback

- **WHEN** effectiveness drops from 85 to 78
- **THEN** no regression SHALL be detected (below 15-point threshold) and no rollback SHALL occur

### Requirement: Update method with change tracking

The system SHALL require a `changeType` and `changeSummary` parameter when updating a skill, ensuring every version change has a documented reason. This is purely for internal logging and version metadata.

#### Scenario: Update with change metadata

- **WHEN** `SkillManager.updateSkill(name, newContent, "enrichment", "Added Java validation examples from successful usage patterns")` is called
- **THEN** the version log SHALL record `changeType: "enrichment"` and `changeSummary: "Added Java validation examples from successful usage patterns"`

### Requirement: Skill creation also creates version record

The system SHALL initialize evolution metadata when a new skill is first created.

#### Scenario: New skill creation

- **WHEN** `SkillManager.createSkill()` successfully writes a new SKILL.md
- **THEN** a `versions/v1.md` file SHALL NOT be created (the main file IS v1); instead, a creation event SHALL be recorded in the usage tracking JSONL with `type: "created"` and `version: 1`, and the pre-enrichment effectiveness SHALL be initialized in tracking metadata
