## 1. 优化记忆匹配

- [x] 1.1 在 `AgentService` 中新增 `extractKeywords(String userMessage)` 方法，使用 `DeepSeekChatModel` 从用户消息中提取 3-5 个搜索关键词（零 temperature，轻量 prompt）
- [x] 1.2 修改 `AgentService.executeCycle()` 的记忆搜索逻辑：先用关键词分别搜索，合并去重结果并按命中次数降序排列
- [x] 1.3 在 `MemoryStore` 中新增 `searchByKeywords(List<String> keywords)` 方法，对多个关键词 OR 搜索并合并排序
- [x] 1.4 添加兜底机制：关键词搜索无结果时回退到原始消息全量搜索；用户消息 < 10 字符时跳过关键词提取
- [x] 1.5 添加调试日志：记录提取的关键词和匹配结果数量，便于验证匹配质量
- [x] 1.6 编写 `MemoryStoreTest` 单元测试，覆盖关键词匹配、无匹配回退、短消息跳过提取等场景

## 2. 新增 SSE 流式交互

- [x] 2.1 创建 `AgentSseController`，复用 `SseEmitter` + `ConcurrentHashMap` 模式，提供 `/agent/sse/connect`、`/agent/sse/send`、`/agent/sse/clients` 三个端点
- [x] 2.2 在 `AgentService` 中新增 `executeCycleWithEvents(String userMessage, Consumer<SseEvent> callback)` 方法，在各阶段通过回调推送 SSE 事件（thinking → memory → tool_call → tool_result → reply_chunk → complete）
- [x] 2.3 实现 SSE 事件格式化方法，统一 `type`、`content`、`timestamp` 字段的 JSON 结构
- [x] 2.4 添加连接生命周期管理：超时回调、错误回调、完成回调中清理 emitter
- [x] 2.5 编写 `AgentSseController` 集成测试，验证 SSE 事件流完整性和连接管理

## 3. 验证与清理

- [x] 3.1 运行完整测试套件确保无回归：`./mvnw -pl ai-deepseek test -DskipTests=false`
- [x] 3.2 端到端验证：启动 ai-deepseek 模块，用 curl 测试记忆匹配改进前后的召回率对比
- [x] 3.3 端到端验证：用 curl 测试 SSE 流式 Agent 对话的完整事件流
