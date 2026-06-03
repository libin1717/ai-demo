## Context

Hermes Agent 演示模块（`ai-deepseek/agent/`）在上次实现中完成了核心功能编码，但方法的 Javadoc 文档不完整。现有注释为英文片段，不成体系。当前状态：

- `HermesAgentController` — 6 个方法，0 个有 Javadoc
- `AgentService` — 6 个方法 + 1 个内部类，0 个有 Javadoc
- `AgentTools` — 6 个方法，仅 `resolveSafe` 有英文注释
- `AgentConfig` — 1 个 Bean 方法，无 Javadoc
- `AgentResponse`、`MemoryExtraction`、`SkillInfo` — Lombok DTO，字段无文档
- `MemoryStore` — 8 个方法，仅 `search` 有英文 Javadoc
- `SkillManager` — 10 个方法，4 个有简短英文 Javadoc

目标是让所有方法获得规范的中文 Javadoc，使团队成员能借助 IDE 快速理解每个方法。

## Goals / Non-Goals

**Goals:**
- 为 9 个源文件中所有 public/protected/package-private 方法添加严格中文 Javadoc
- 每个方法必须包含功能描述，参数用 `@param`，返回值用 `@return`，可能异常用 `@throws`
- 将现有英文注释替换为标准中文 Javadoc
- Lombok DTO 类字段添加文档注释
- 注释风格统一，符合 Java 标准 Javadoc 规范

**Non-Goals:**
- 不修改任何业务逻辑代码
- 不修改任何注释之外的代码
- 不添加单元测试
- 不变更 API 接口或配置

## Decisions

1. **中文撰写** — 项目团队为中文环境，中文 Javadoc 降低理解门槛
2. **严格格式** — 每个方法必须有 `@param`（有参数时）、`@return`（非 void 时）、`@throws`（有受检异常时），不允许省略
3. **字段级文档** — 对 Lombok DTO 的字段添加 Javadoc，IDE 会自动将这些文档关联到生成的 getter/setter
4. **覆盖范围** — 包括 private 辅助方法（如 `buildSystemPrompt`、`captureMemoryState`），因为内部方法也需要维护文档

## Risks / Trade-offs

- [Javadoc 与代码不同步] → Javadoc 是纯文本注释，不会自动与代码同步；后续代码变更时需要人工维护
- [Lombok 字段文档可见性] → 某些 IDE 版本可能不将字段 Javadoc 传播到 Lombok 生成的 getter/setter；作为缓解，关键 DTO 字段在类级别添加说明
