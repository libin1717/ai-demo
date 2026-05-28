## Why

在当前 Spring AI 多模块 Demo 项目中，已有的 Chat/SSE 功能都是无状态的单次对话。本变更引入 Hermes Agent 的核心理念——持久记忆 + 自动技能沉淀——让用户直观感受 Agent "越用越懂你" 的价值，而非停留在一个普通的 Chatbot。

## What Changes

- 在 `ai-deepseek` 模块中新增 `HermesAgentController`，提供对话入口和记忆/技能的管理接口
- 新增 `MemoryStore` 服务，基于本地文件（`.memory/` 目录，Markdown 格式）实现持久记忆的读写和全文检索
- 新增 `SkillManager` 服务，实现技能文档的自动沉淀、索引和按需加载
- 新增 `AgentService`，编排完整的 Agent 循环：检索记忆/技能 → 拼装 System Prompt → LLM 决策（带 Spring AI Tool Calling）→ 自动沉淀产出
- 定义 `@Tool` 注解的工具方法：`readFile`、`writeFile`、`searchCode`、`listDirectory`，供 LLM 在决策过程中调用
- 演示路径：通过有序的多轮对话，展现 Agent 从无状态执行到具备项目上下文和技能积累的成长过程

## Capabilities

### New Capabilities

- `agent-memory`: 基于本地 Markdown 文件的持久记忆系统，支持事实抽取、偏好记录、决策存储和全文检索。每次对话后自动提取关键信息写入 `.memory/` 目录，后续对话自动注入相关记忆到 System Prompt。
- `agent-skill`: 技能自动沉淀系统。当单次任务工具调用超过阈值（默认 5 次）时，自动总结生成可复用的技能文档（Markdown + YAML 元信息）。后续对话匹配到相关技能时自动加载。
- `agent-loop`: Agent 核心执行循环，串联记忆检索 → 技能匹配 → System Prompt 拼装 → LLM + Tool Calling → 结果处理 → 自动沉淀的完整链路。

### Modified Capabilities

_无（纯新增功能，不修改现有能力）_

## Impact

- **新增文件**: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/` 包下的 Controller、Service、Tool 类
- **新增运行时目录**: `ai-deepseek/.memory/` （可通过 `.gitignore` 选择性提交用于演示）
- **已有代码无修改**: 现有 Controller、配置、DTO 均不受影响
- **依赖无变化**: 复用已有 `spring-ai-starter-model-deepseek` + Spring AI Tool Calling 能力
