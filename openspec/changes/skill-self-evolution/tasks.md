## 1. DTO 层扩展

- [ ] 1.1 扩展 `SkillInfo.java`：新增 `effectiveness`(int)、`usageCount`(int)、`lastUsed`(String)、`successRate`(int) 字段
- [ ] 1.2 新建 `SkillUsageRecord.java`：JSONL 反序列化 DTO，包含 `timestamp`、`type`(match/outcome/created)、`skillName`、`triggers`、`userMessageDigest`、`toolCallCount`、`result`(success/failure)、`errorMessage` 字段
- [ ] 1.3 扩展 `AgentResponse.java`：新增 `skillsMatched`(List<String>) 字段，记录本轮对话匹配了哪些 skill

## 2. Skill 使用追踪器 (SkillUsageTracker)

- [ ] 2.1 新建 `SkillUsageTracker.java` 服务：构造时创建 `.memory/skill-usage/` 目录
- [ ] 2.2 实现 `recordMatch(skillName, triggers, userMessageDigest, toolCallCount)`：追加 JSONL 行到 `.memory/skill-usage/<skillName>/<YYYY-MM-DD>.jsonl`
- [ ] 2.3 实现 `recordOutcome(skillNames, result, toolCallCount, errorMessage)`：为每个匹配的 skill 追加 outcome 记录
- [ ] 2.4 实现 `recordCreation(skillName, description, triggers)`：追加 created 事件
- [ ] 2.5 实现内部 `queryUsage(skillName, fromDate, toDate)`：读取 JSONL 文件解析为 `List<SkillUsageRecord>`，支持日期范围过滤（内部方法，不暴露 API）
- [ ] 2.6 实现 `countSuccessfulMatches(skillName)`：统计某 skill 的 outcome=success 记录数
- [ ] 2.7 实现 `getRecentSuccessContexts(skillName, limit)`：获取最近 N 次成功使用的上下文，用于富化分析

## 3. Skill 自动评分与进化的核心服务 (SkillEvolutionService)

- [ ] 3.1 新建 `SkillEvolutionService.java`：注入 SkillManager、SkillUsageTracker、DeepSeekChatModel
- [ ] 3.2 实现 `recalculateScore(skillName)`：读取 usageCount/successCount/lastUsed → 计算 effectiveness 三因子加权 → 调用 SkillManager 更新 frontmatter
- [ ] 3.3 实现 `checkEnrichmentThreshold(skillName)`：判断匹配成功次数 ≥ 5 且成功率 ≥ 80%（内部判断，不暴露）
- [ ] 3.4 实现 `generateEnrichment(skillName)`：收集最近 5 次成功上下文 → 调用 LLM 生成改进建议 → 通过质量门禁 → 自动应用
- [ ] 3.5 实现内部 `qualityGate(skillName, suggestedContent)`：验证 (1) 有效 YAML front matter (2) 核心触发词保留 ≥ 50% (3) 正文长度变化 ≤ ±80% (4) 内容非空
- [ ] 3.6 实现 `autoApplyEnrichment(skillName, suggestedContent, changesSummary)`：记录富化前 effectiveness → 调用 SkillManager.updateSkill() → 记录供回归监控
- [ ] 3.7 实现 `detectRegression(skillName)`：对比当前 effectiveness 与最近一次变更前的记录，下降 ≥ 15 分且在变更后 7 天内 → 自动调用 `SkillManager.revertSkill()` 回滚
- [ ] 3.8 实现内部 `logEvolutionEvent(skillName, eventType, detail)`：统一日志输出，所有进化事件通过 log.info 记录（用户可通过应用日志查看进化过程）

## 4. SkillManager 扩展

- [ ] 4.1 修改 `createSkill()`：创建前调用 LLM 语义去重检查（`checkDuplicate()`）；创建时写入 evolution 字段（effectiveness/usageCount/lastUsed/successRate）；创建后调用 `skillUsageTracker.recordCreation()`
- [ ] 4.2 新增 `updateSkill(name, newContent, changeType, changeSummary)`：备份当前版本到 `versions/v<N>.md` → 覆盖主文件 → version+1 → 更新 updatedAt — 超过 10 个版本时删除最旧
- [ ] 4.3 新增 `updateEffectiveness(name, effectiveness, usageCount, successRate, lastUsed)`：更新 frontmatter 中的 evolution 字段（仅写文件，不改版本）
- [ ] 4.4 新增内部 `getVersionHistory(name)`：列出 `versions/` 下所有文件和元数据（内部方法）
- [ ] 4.5 新增内部 `getVersionContent(name, version)`：读取 `versions/v<N>.md`（内部方法）
- [ ] 4.6 新增 `revertSkill(name, targetVersion)`：读取目标版本内容 → `updateSkill()` → 标记 `changeType: "revert"` 和回归原因
- [ ] 4.7 扩展 `parseFrontMatter()` 解析新增字段：effectiveness、usageCount、lastUsed、successRate、regression
- [ ] 4.8 扩展 `listAll()` 返回的 SkillInfo 包含 evolution 字段
- [ ] 4.9 修改 `incrementScore()`：内部委托到 `SkillEvolutionService.recalculateScore()`，保留方法签名向后兼容

## 5. AgentService 集成追踪与进化调用

- [ ] 5.1 注入 `SkillUsageTracker` 和 `SkillEvolutionService`
- [ ] 5.2 在 `executeCycleWithEvents()` 中：skill match 后调用 `tracker.recordMatch()` + `evolutionService.recalculateScore()`
- [ ] 5.3 在 agent cycle 成功完成后（LLM 调用无异常）：调用 `tracker.recordOutcome(result="success")`
- [ ] 5.4 在 agent cycle 异常时（catch 块中）：调用 `tracker.recordOutcome(result="failure", errorMessage)`
- [ ] 5.5 在 outcome 记录后：自动调用 `evolutionService.recalculateScore()` → 自动检查 `evolutionService.checkEnrichmentThreshold()` → 达到阈值自动 `generateEnrichment()`（含质量门禁 + 自动应用）
- [ ] 5.6 在评分更新后：自动调用 `evolutionService.detectRegression()` → 检测到回归自动回滚
- [ ] 5.7 将现有的 `incrementScore()` 调用替换为完整的追踪+评分流程
- [ ] 5.8 填充 `AgentResponse.skillsMatched` 字段

## 6. 测试

- [ ] 6.1 编写 `SkillUsageTrackerTest`：测试 recordMatch/recordOutcome/recordCreation 写入 + 内部 query 读取 + 日期范围过滤
- [ ] 6.2 编写 `SkillEvolutionServiceTest`：测试 recalculateScore 计算正确性 + checkEnrichmentThreshold 边界 + qualityGate 各检查项 + autoApplyEnrichment + detectRegression 阈值边界 + auto-revert 触发
- [ ] 6.3 编写 `SkillManagerTest` 扩展：测试 updateSkill 版本备份/恢复 + checkDuplicate + updateEffectiveness + parseFrontMatter evolution 字段 + revertSkill + 版本上限（10个）
- [ ] 6.4 编写 `AgentServiceTest` 集成验证：模拟 agent cycle → 验证 tracker 记录已写入、score 已更新、富化和回归检测在后台自动运行
- [ ] 6.5 运行现有测试确保向后兼容：`./mvnw -pl ai-deepseek test -DskipTests=false`

## 7. 集成验证

- [ ] 7.1 启动 ai-deepseek 服务，运行 4 轮 demo：验证新 skill 生成后 frontmatter 包含 evolution 字段
- [ ] 7.2 多次调用同一 skill 场景：验证 usageCount 递增、effectiveness 自动更新（通过日志和 skill 文件确认）
- [ ] 7.3 验证自动富化流程：模拟足够成功的匹配 → 通过日志检查 enrichment 触发 → 验证 skill 文件内容已自动更新 → 验证版本历史已创建
- [ ] 7.4 验证质量门禁：构造异常 LLM 输出 → 验证门禁拦截并记录日志 → skill 未被修改
- [ ] 7.5 验证自动回滚：手动降低 effectiveness → 验证 regression 检测触发 → skill 自动回滚到上一版本
