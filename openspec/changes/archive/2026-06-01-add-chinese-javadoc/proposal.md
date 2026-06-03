## Why

Hermes Agent 演示模块（9 个 Java 源文件）在上次实现中缺少规范的 Javadoc 注解，现有少量注解为英文且不完整。为了让团队成员能够快速理解每个方法的功能、参数、返回值和异常情况，需要为所有公共方法、内部类方法补充严格的中文 Javadoc 文档。

## What Changes

- 为 `agent/` 包下所有 9 个源文件中的方法添加严格的 Javadoc 注释（`@param`、`@return`、`@throws`）
- 将现有的英文注释（如 `AgentTools.resolveSafe`、`MemoryStore.search`、`SkillManager` 中的方法）替换为标准中文 Javadoc
- 为 Lombok DTO 类的字段添加 `@param` / `@return` 文档注释（虽然 getter/setter 由 Lombok 生成，但字段级 Javadoc 会在 IDE 中为生成的 getter/setter 提供文档）
- 所有 Javadoc 使用中文撰写

## Capabilities

### New Capabilities

<!-- 本次为纯文档变更，无新增功能能力 -->

### Modified Capabilities

<!-- 本次不修改任何现有功能规格 -->

## Impact

- 影响文件：`ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/` 下全部 9 个 Java 源文件
- 无 API 变更、无依赖变更、无破坏性变更
- 纯注释级别修改，不影响运行时行为
