## Context

ai-deepseek 模块的 agent 包已实现完整的 Agent 执行循环和基于文件的 skill 持久化，但 skill 生命周期仅限于"创建"和"匹配"——无使用追踪、无自动评分、无自动优化、无版本管理。需要在现有 `SkillManager` + `AgentService` 基础上构建基于使用数据的**全自动、用户无感**的 skill 进化闭环。

核心约束：
- **用户无感**：所有进化逻辑自动触发、自动执行，不新增任何对外 REST API
- **沿用现有架构**：在 `.memory/` 文件系统上增量构建，与现有 `SkillInfo`、`AgentResponse` DTO 向后兼容
- **纯 Java 17 + Spring Boot**：不引入外部数据库、消息队列或新的外部依赖

## Goals / Non-Goals

**Goals:**
- 所有进化逻辑基于使用数据自动触发，无需用户手动反馈或操作
- 在现有 agent 架构内完成全部变更，沿用 `.memory/` 文件系统
- 与现有 `SkillInfo`、`AgentResponse` DTO 向后兼容
- 富化结果自动应用（内部质量门禁：成功率不下降），用户无需感知
- 回归自动回滚，保障 skill 质量不退化

**Non-Goals:**
- 不新增任何对外 REST API 端点（无评分查询、无富化审批、无版本查看、无回滚接口）
- 不修改 ai-openai、ai-qwen、ai-common 模块
- 不引入外部数据库或消息队列
- 不进行代码自动提交（git commit）
- 不改变 DeepSeek Chat 和 SSE streaming 的对外行为

## Decisions

### Decision 1: 全自动评分 — 三因子加权

**选择**: `effectiveness = usageCount×0.3 + successRate×0.4 + recency×0.3` (0-100 归一化)

- `usageCount` — 使用次数越多越可靠（归一化：min(count/50, 1.0)×100）
- `successRate` — 追踪中记录每次 skill 匹配后对话是否成功完成（LLM 调用无异常 = success），权重最高
- `recency` — 7 天内使用过 = 100，30 天内 = 50，否则 = 10

**理由**: 全自动无人工介入——使用次数代表实用价值，成功率代表质量，时效性代表活跃度。在 AgentService 的 `executeCycleWithEvents()` 末尾自动触发重算，完全对用户透明。

**替代方案**: 仅用 `score++` 计数器（当前方案）→ 无法区分高频低质和低频高质 skill，放弃。

### Decision 2: 触发自动富化的条件与自动应用

**选择**: 同时满足两个条件触发富化：① skill 被成功匹配 ≥ 5 次（agent cycle 无异常完成）② 成功率 ≥ 80%。满足条件后自动调用 LLM 生成改进建议，经**内部质量门禁**（检查富化后的内容格式有效、核心触发词未丢失）后**自动应用**，不等待人工审批。

**自动应用的质量门禁**:
1. 富化结果必须是有效的 Markdown（包含 YAML front matter）
2. 核心触发词（triggers）不能减少超过 50%
3. 正文长度变化不超过 ±80%（防止 LLM 输出异常）

**理由**: 用户无感——如果每次富化都需要人工审批，就违背了"用户无感自我进化"的核心理念。5 次成功匹配 + 80% 成功率 + 质量门禁三重保障足以安全地自动应用。

**替代方案**: 人工审批（原方案）→ 违背用户无感理念，放弃。

### Decision 3: 自动回滚

**选择**: 富化自动应用后持续监控 effectiveness。若 7 天内新版本 effectiveness 相比富化前记录下降 ≥ 15 分，则**自动回滚**到上一个版本，并记录回滚原因到日志。

**理由**: 自动富化有风险，自动回滚是其安全网。15 分阈值提供缓冲空间，避免因正常波动误触发回滚。

### Decision 4: 存储 — JSONL 按 skill 按天分片

**选择**: 追踪记录存储在 `.memory/skill-usage/<skillName>/<YYYY-MM-DD>.jsonl`

**理由**: 与现有 `.memory/` 文件系统一致，追加写入支持，按天分片便于内部查询和定期清理。

### Decision 5: 去重 — LLM 语义判断

**选择**: 创建新 skill 前将候选 description+triggers 与已有 skill 拼接送 LLM 判断重复。

### Decision 6: 版本管理 — 文件复制 + 版本号递增

**选择**: 更新时将当前版本复制到 `<skillName>/versions/v<N>.md`，主文件覆盖并 inc version。最多保留 10 个历史版本。

### Decision 7: 无新 Controller 端点

**选择**: 不新增任何 REST API。所有进化状态通过日志输出（`log.info`），现有 `GET /agent/skills` 和 `GET /agent/skills/{name}` 响应中自然包含 evolution 字段（effectiveness、usageCount 等），用户可选择性查看，但不做专门的进化管理接口。

**理由**: 核心需求——用户无感。进化是 Agent 的内部行为，不是用户需要管理的功能。

## Risks / Trade-offs

- **[LLM 调用成本] 去重和富化需额外 LLM 调用** → 去重仅创建时 1 次（创建本身已有 LLM 调用）；富化有双重阈值门槛（≥5 次成功匹配 + ≥80% 成功率），触发频率低
- **[自动富化质量风险] 自动应用可能引入劣化内容** → 三重质量门禁 + 自动回滚机制（7 天内下降 ≥ 15 分自动回滚），形成安全闭环
- **[效果依赖 LLM 质量] 自动评分中的 success 判断依赖 LLM 调用无异常** → 虽然不是完美的质量指标，但可作为自动化场景下的合理代理；如果后续需要更精细的质量判断，可以在 AgentService 中扩展
- **[文件系统并发]** → SkillManager/SkillUsageTracker 关键写操作均用 `synchronized`，Spring 单例 Service 天然线程安全
- **[存储增长] JSONL 按天分片持续增长** → 提供内部 `cleanupOldRecords(retentionDays)` 方法，默认保留 90 天，后续可通过定时任务调用

## Internal Architecture

```
AgentService.executeCycleWithEvents()
  │
  ├─ 1. skillManager.match(query)
  │     └─ skillUsageTracker.recordMatch(skillName, ...)   ← 追踪匹配
  │
  ├─ 2. skillManager.incrementScore() → evolutionService.recalculateScore()  ← 自动评分
  │
  ├─ 3. LLM call + tool execution
  │
  ├─ 4. skillUsageTracker.recordOutcome(skillName, success/failure)  ← 追踪结果
  │
  ├─ 5. evolutionService.recalculateScore(skillName)  ← 结果后重算评分
  │     │
  │     ├─ checkEnrichmentThreshold(skillName)
  │     │   └─ if (matchSuccess >= 5 && successRate >= 80%)
  │     │       └─ generateEnrichment(skillName)  ← 自动富化
  │     │           └─ qualityGate(suggestedContent)  ← 质量门禁
  │     │               └─ skillManager.updateSkill()  ← 自动应用
  │     │                   └─ backup current → versions/v<N>.md
  │     │
  │     └─ detectRegression(skillName)
  │         └─ if (score drop >= 15 in 7 days)
  │             └─ skillManager.revertSkill()  ← 自动回滚
  │
  └─ 6. AgentResponse.skillsMatched = [...]  ← 填充匹配列表
```

所有进化事件仅通过 `log.info` 对外可见，用户可通过应用日志查看进化过程，但不需要任何 API 交互。
