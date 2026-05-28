## ADDED Requirements

### Requirement: Agent 执行循环

系统 SHALL 实现以下固定执行流程，每次对话都经过完整循环：

```
用户输入
  → MemoryStore.search(query)     # 检索相关记忆
  → SkillManager.match(query)     # 匹配相关技能
  → 拼装 System Prompt（角色 + 记忆 + 技能 + 工具）
  → LLM 决策（支持 Tool Calling）
  → 执行工具（如有）
  → LLM 综合工具结果生成最终回复
  → 自动沉淀（写入记忆，若达到阈值则生成技能）
  → 返回结果给用户
```

#### Scenario: 完整执行循环

- **WHEN** 用户通过 API 发送对话请求
- **THEN** 系统 SHALL 依次执行记忆检索、技能匹配、System Prompt 拼装、LLM 调用
- **THEN** LLM 在有需要时 SHALL 调用工具，系统 SHALL 执行工具并回传结果给 LLM
- **THEN** 最终回复 SHALL 返回给用户
- **THEN** 最后 SHALL 执行自动沉淀流程

#### Scenario: 带工具调用的循环

- **WHEN** LLM 决定需要调用一个或多个工具
- **THEN** 系统 SHALL 执行工具调用
- **THEN** 将工具执行结果回传给 LLM
- **THEN** LLM SHALL 基于工具结果生成最终回复
- **THEN** 工具调用次数 SHALL 被记录以用于技能沉淀判断

### Requirement: System Prompt 拼装

系统 SHALL 在每次对话时将记忆和技能按三段式拼装为 System Prompt。

#### Scenario: System Prompt 包含所有部分

- **WHEN** 记忆检索和技能匹配均返回结果
- **THEN** System Prompt SHALL 包含：角色定义、相关记忆片段、相关技能文档、可用工具列表
- **THEN** 各部分之间 SHALL 有明确的分隔标记

#### Scenario: System Prompt 仅含角色定义

- **WHEN** 记忆检索和技能匹配均无结果
- **THEN** System Prompt SHALL 仅包含角色定义和可用工具列表

### Requirement: 对话 API

系统 SHALL 提供 REST API 供用户发起与 Agent 的对话。

#### Scenario: 发送对话请求

- **WHEN** 用户 POST 消息到对话接口 `/agent/chat`，携带 `message` 参数
- **THEN** 系统 SHALL 执行完整的 Agent 循环
- **THEN** 返回 `ResultDto<AgentResponse>`，包含 AI 回复、本次使用了哪些记忆、本次是否触发了技能沉淀

### Requirement: 记忆查看 API

系统 SHALL 提供 REST API 查询当前 Agent 的所有记忆。

#### Scenario: 查看所有记忆

- **WHEN** 用户 GET `/agent/memory`
- **THEN** 系统 SHALL 返回 `.memory/` 目录下所有记忆文件的概要（文件列表 + 各文件条目数）

#### Scenario: 查看特定记忆文件内容

- **WHEN** 用户 GET `/agent/memory/{fileName}`
- **THEN** 系统 SHALL 返回该文件的完整内容

### Requirement: 技能查看 API

系统 SHALL 提供 REST API 查询当前 Agent 的所有技能文档。

#### Scenario: 查看技能列表

- **WHEN** 用户 GET `/agent/skills`
- **THEN** 系统 SHALL 返回所有技能的概览（名称、描述、触发词、评分）

#### Scenario: 查看特定技能详情

- **WHEN** 用户 GET `/agent/skills/{skillName}`
- **THEN** 系统 SHALL 返回该技能文档的完整 Markdown 内容

### Requirement: 预设演示 API

系统 SHALL 提供一个便捷接口，按预设顺序执行演示对话，并汇总对比结果。

#### Scenario: 运行演示

- **WHEN** 用户 POST `/agent/demo/run`
- **THEN** 系统 SHALL 按预设顺序执行 4 轮对话
- **THEN** 每轮返回 AI 回复 + 本轮沉淀的记忆/技能
- **THEN** 最终返回汇总：对话前后 `.memory/` 目录的差异对比 (diff)
