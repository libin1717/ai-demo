## MODIFIED Requirements

### Requirement: Skill creation with deduplication check

The system SHALL check for semantic duplicates before creating a new skill. When `SkillManager.createSkill()` is called, it SHALL first concatenate the candidate's description and triggers with all existing skills' descriptions and triggers, send to LLM with the question "这两个技能是否高度重复（覆盖相同任务场景）？仅回复 YES 或 NO", and skip creation if LLM returns YES.

#### Scenario: Duplicate skill detected

- **WHEN** `createSkill()` is called for "java-web-controller" and an existing skill "spring-controller-pattern" already covers controller creation
- **THEN** LLM SHALL return YES, creation SHALL be skipped, and a log SHALL record: "Duplicate skill skipped: java-web-controller (matches spring-controller-pattern)"

#### Scenario: No duplicate — skill created

- **WHEN** `createSkill()` is called for "db-migration-helper" and no existing skill covers database migration
- **THEN** LLM SHALL return NO, the skill SHALL be created normally with new frontmatter fields `effectiveness: 0`, `usageCount: 0`, `lastUsed: null`, `successRate: 0`

#### Scenario: LLM dedup call fails

- **WHEN** the dedup LLM call throws an exception or returns neither YES nor NO
- **THEN** the system SHALL default to creating the skill (fail-open: avoid losing potentially useful skills due to dedup errors)

### Requirement: Skill creation triggers usage tracking event

The system SHALL record a tracking event when a new skill is created.

#### Scenario: Skill creation event logged

- **WHEN** `SkillManager.createSkill()` successfully writes a new SKILL.md
- **THEN** `SkillUsageTracker` SHALL append a JSONL line to `.memory/skill-usage/<skillName>/<YYYY-MM-DD>.jsonl` with `type: "created"`, `skillName`, `description`, `triggers`, `timestamp`

### Requirement: Skill frontmatter includes evolution fields

The system SHALL initialize new skills with evolution-related frontmatter fields.

#### Scenario: New skill frontmatter

- **WHEN** a new skill is created
- **THEN** its YAML frontmatter SHALL include `version: 1`, `score: 0`, `effectiveness: 0`, `usageCount: 0`, `lastUsed: null`, `successRate: 0` alongside existing `name`, `description`, `triggers`, `created` fields
