## Why

当前 Hermes Agent 的 skill 系统仅实现了"生成"环节——当工具调用次数 ≥ 5 时，LLM 从对话中提取并写入一个新的 skill 文件。但 skill 一旦创建便进入静态状态：没有使用追踪（无法知道哪些 skill 常用、哪些无用），没有自动优化（skill 不会根据后续使用数据自我改进），也没有版本管理（更新即覆盖、无法追溯）。需要基于使用数据构建 **全自动、用户无感** 的 skill 自我进化闭环：系统在后台自动追踪每次 skill 的使用效果，根据成功率和频次自动评分，对高频成功的 skill 自动触发 LLM 富化分析并静默应用，对持续退化的 skill 自动回滚到稳定版本。整个过程完全内置在 AgentService 执行周期中，不暴露任何新的用户接口，用户唯一感知到的是 Agent 越用越聪明。

## What Changes

- **新增 Skill 使用追踪**：在 AgentService 执行周期中自动注入追踪逻辑，记录每次 skill 匹配事件（时间戳、user message 摘要、toolCallCount、成功/失败），持久化为 JSONL 文件，纯内部使用无对外 API
- **新增 Skill 自动评分**：根据使用频次（30%）、执行成功率（40%）、时效性衰减（30%）自动计算 0-100 effectiveness score，每次使用后自动重算并写入 skill frontmatter，替换现有 `incrementScore()`
- **新增 Skill 自动富化**：当 skill 匹配成功 ≥ 5 次且成功率 ≥ 80% 时，自动收集最近 N 次成功上下文，通过 LLM 生成 skill 内容改进建议，经内部质量门禁后**自动应用**（无需人工审批）
- **新增 Skill 版本管理与自动回滚**：每次 skill 内容变更保留旧版本（最多 10 个），富化后持续监控 effectiveness，若 7 天内下降 ≥ 15 分则**自动回滚**到变更前版本
- **修改 Skill 创建流程**：增加与已有 skill 的语义去重检查，创建时初始化 usageCount/effectiveness/lastUsed 等 evolution 字段

## Capabilities

### New Capabilities

- `skill-usage-tracking`: 在 AgentService 执行周期中自动注入追踪逻辑，记录每次 skill 匹配和对话结果，持久化为 JSONL，纯内部使用不暴露 API
- `skill-auto-scoring`: 纯自动评分引擎——usageCount×0.3 + successRate×0.4 + recency×0.3 → 0-100 effectiveness，每次使用后自动重算并写入 skill frontmatter
- `skill-auto-enrichment`: 当 skill 匹配成功 ≥ 5 次且成功率 ≥ 80% 时，自动收集成功上下文送 LLM 分析改进点，经内部质量门禁后自动应用到 skill 文件
- `skill-version-evolution`: 每次变更保留旧版本，维护版本日志；effectiveness 下降 ≥ 15 分时自动回滚到上一个稳定版本

### Modified Capabilities

- `skill-generation`: 创建前 LLM 语义去重检查，创建时初始化 effectiveness/usageCount/lastUsed 字段，创建后写入追踪事件

## Impact

- **SkillManager.java**: 新增 `updateSkill()`、`checkDuplicate()`、`updateEffectiveness()`、`revertSkill()`、`getVersionHistory()`（内部方法）
- **AgentService.java**: 执行周期注入 `skillUsageTracker` 调用；skill 生成前调用去重检查；outcome 记录后触发自动评分和自动富化
- **HermesAgentController.java**: 无变更（不新增任何端点）
- **SkillInfo.java**: 新增 `effectiveness`、`usageCount`、`lastUsed`、`successRate` 字段
- **AgentResponse.java**: 新增 `skillsMatched` 字段用于内部追踪传递
- **新增文件**: `SkillUsageTracker.java`、`SkillEvolutionService.java`、`SkillUsageRecord.java`
- **无破坏性变更**：所有新增字段和设备均为增量，现有 API 保持原样
- **无对外新接口**：所有进化能力完全内置于 Agent 执行循环中，用户无感
