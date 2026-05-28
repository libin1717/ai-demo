## ADDED Requirements

### Requirement: 技能文档格式

系统 SHALL 以 Markdown + YAML front matter 格式存储技能文档，遵循 AgentSkills.io 标准：

```markdown
---
name: <kebab-case-name>
description: <一句话描述>
triggers: [触发关键词列表]
version: <版本号>
created: <ISO 日期>
score: <使用评分>
---

## <技能标题>

<技能具体内容>
```

#### Scenario: 技能文档格式验证

- **WHEN** 系统创建新技能文档
- **THEN** 文档 SHALL 包含必填的 front matter 字段：name、description、triggers
- **THEN** 正文 SHALL 为合法的 Markdown

### Requirement: 技能自动沉淀

系统 SHALL 在单次对话中 LLM 调用工具次数 ≥ 5 时，自动触发技能文档生成。

#### Scenario: 达到工具调用阈值，触发沉淀

- **WHEN** 一次完整对话中，Agent 的工具调用次数达到或超过 5 次
- **THEN** 系统 SHALL 在对话结束后，将本次对话的核心模式和技巧总结为技能文档
- **THEN** 生成的技能文档 SHALL 保存到 `.memory/skills/` 目录

#### Scenario: 未达阈值，不触发沉淀

- **WHEN** 一次完整对话中，Agent 的工具调用次数少于 5 次
- **THEN** 系统 SHALL NOT 触发技能文档生成
- **THEN** 仅正常写入对话事实/偏好/决策记忆

### Requirement: 技能检索与匹配

系统 SHALL 在每次对话开始前，根据用户输入的 query 匹配相关技能文档，并注入 System Prompt。

#### Scenario: 关键词匹配技能

- **WHEN** 用户输入的 query 包含某个技能 `triggers` 字段中的关键词
- **THEN** 该技能文档的正文 SHALL 被注入到 System Prompt 的 "相关技能" 区间

#### Scenario: 无匹配技能

- **WHEN** 用户输入的 query 不匹配任何已有技能
- **THEN** System Prompt 的 "相关技能" 区间 SHALL 为空

### Requirement: 技能评分

系统 SHALL 维护每个技能的使用评分，技能被成功匹配并使用后，评分上升。

#### Scenario: 技能被命中后评分上升

- **WHEN** 某个技能在当前对话的 System Prompt 中被加载
- **THEN** 该技能的 `score` 字段 SHALL 增加 1

### Requirement: 技能列表查询

系统 SHALL 提供 API 查询当前所有技能文档的列表和详情。

#### Scenario: 查询所有技能

- **WHEN** 调用技能列表查询接口
- **THEN** 系统 SHALL 返回所有技能的名称、描述、触发关键词、评分、创建时间
