## ADDED Requirements

### Requirement: SSE 连接管理

系统 SHALL 提供 Agent 的 SSE 流式连接端点，支持客户端注册、超时断开和连接计数。

#### Scenario: 客户端成功注册 SSE 连接

- **WHEN** 客户端调用 `GET /agent/sse/connect?clientId=<id>`
- **THEN** 系统 SHALL 创建 SseEmitter 实例（1 小时超时）
- **THEN** 系统 SHALL 将 emitter 存入 ConcurrentHashMap
- **THEN** 系统 SHALL 向客户端发送 `connected` 事件确认连接成功
- **THEN** 客户端断开时系统 SHALL 自动移除 emitter 并释放资源

#### Scenario: 查看已连接客户端

- **WHEN** 客户端调用 `GET /agent/sse/clients`
- **THEN** 系统 SHALL 返回当前连接客户端数量和客户端 ID 列表

### Requirement: SSE 流式 Agent 对话

系统 SHALL 支持通过 SSE 连接发送消息并流式接收 Agent 各阶段的执行进度。

#### Scenario: 客户端发送消息触发 Agent 循环

- **WHEN** 已注册客户端调用 `POST /agent/sse/send?clientId=<id>&message=<msg>`
- **THEN** 系统 SHALL 启动 Agent 执行循环并依次推送以下 SSE 事件：
  - `thinking` 事件：通知客户端 Agent 正在搜索相关记忆
  - `memory` 事件：推送匹配到的记忆数量和摘要
  - `reply_chunk` 事件：推送 AI 回复的文本块（伪流式，按句子拆分）
  - `complete` 事件：推送 Agent 循环完成汇总（含工具调用次数、新增记忆数、技能沉淀状态）
- **THEN** `tool_call` 和 `tool_result` 事件留待后续迭代实现（需迁移到 Streaming ChatClient）

#### Scenario: 向未注册客户端发送消息

- **WHEN** 客户端调用 `POST /agent/sse/send` 但 clientId 未注册
- **THEN** 系统 SHALL 返回 "客户端未连接" 错误

### Requirement: SSE 事件格式

系统 SHALL 使用统一的 JSON 事件格式推送 SSE 消息。

#### Scenario: SSE 事件结构

- **WHEN** 系统推送任意 SSE 事件
- **THEN** 每个事件 SHALL 包含 `type`（事件类型）、`content`（消息内容）、`timestamp`（时间戳）字段
- **THEN** 事件类型 SHALL 为以下之一：`connected`、`thinking`、`memory`、`reply_chunk`、`complete`
