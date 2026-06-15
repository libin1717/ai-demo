## Context

当前 Hermes Agent 系统有两个关键缺陷：

**记忆匹配问题**：`MemoryStore.search()` 使用 `String.toLowerCase().contains()` 做全量子串扫描。用户输入的自然语言（如"帮我写一个带校验的注册接口"）与记忆条目中存储的技术事实（如"使用 @Valid 注解做参数校验"、"接口为 POST /api/register"）之间缺乏直接字面关联。结果：`memoryStore.search(userMessage)` 几乎总是返回空 Map，记忆系统从未被实际使用。

**交互方式单一**：`HermesAgentController` 仅提供 `POST /agent/chat` 阻塞式端点。Agent 循环（记忆搜索 → LLM 调用 → 工具执行 → 记忆提取）全程同步，用户需等待完整响应（数秒到数十秒）。项目中 `StreamChatCompletionsController` 已有成熟的 SSE 模式（SseEmitter + ConcurrentHashMap + 思考/正文分离推送），但 Agent 未复用此模式。

## Goals / Non-Goals

**Goals:**
- 记忆搜索能从自然语言查询中识别出相关记忆，召回率从接近 0% 提升到 80%+
- 新增 Agent SSE 流式端点，用户可实时看到 Agent 思考过程和工具调用
- 重用现有 DeepSeekChatModel 做关键词提取，不引入额外 LLM 依赖
- 保持 `MemoryStore.search()` API 签名不变，内部优化对调用方透明
- SSE 实现遵循项目现有模式（`StreamChatCompletionsController`）

**Non-Goals:**
- 不引入向量数据库（Milvus/Pinecone）——过度工程化
- 不引入 Embedding API（额外成本）
- 不改变记忆存储格式
- 不重构 `StreamChatCompletionsController`（它不属于 Agent 体系）

## Decisions

### 1. 记忆匹配：LLM 关键词提取 + 多维度评分

**方案**：在 `AgentService.executeCycle()` 中，先用轻量级 LLM 调用从用户消息中提取 3-5 个搜索关键词，再用这些关键词对 `MemoryStore.search()` 做 OR 搜索并合并去重排序。

```
用户消息 → LLM 提取关键词 → MemoryStore.search(每个关键词) → 合并去重 → 按命中次数排序
```

关键词提取 prompt：
```
"从以下用户消息中提取3-5个用于搜索技术文档的关键词（名词、术语、框架名、注解名），用逗号分隔，只输出关键词：
用户消息：{userMessage}"
```

**替代方案对比**：
| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| A. Embedding 向量相似度 | 语义理解最强 | 引入 Embedding API 成本，架构复杂度 | ❌ 过度 |
| B. TF-IDF / 分词 | 无 LLM 调用开销 | 中文分词复杂，需引入分词库 | ❌ 新依赖 |
| C. LLM 关键词提取 + 子串匹配 | 轻量、复用现有模型、中文友好 | 多一次 LLM 调用（~0.5s） | ✅ 最佳平衡 |
| D. 正则/模糊匹配 | 零额外开销 | 无法解决语义鸿沟 | ❌ 解决不了根本问题 |

### 2. SSE 流式 Agent 架构

**方案**：新增 `AgentSseController`，遵循 `StreamChatCompletionsController` 的 SSE 模式：
- `GET /agent/sse/connect?clientId=` 注册客户端
- `POST /agent/sse/send` 发送消息并流式推送 Agent 循环各阶段

SSE 事件设计（统一 SSE event name="agent_event"，具体类型通过 JSON `type` 字段区分）：

| type         | SSE name     | 说明                          |
|-------------|--------------|-------------------------------|
| connected   | connected    | SSE 连接建立成功                 |
| thinking    | agent_event  | 正在分析用户消息并搜索相关记忆        |
| memory      | agent_event  | 匹配到的记忆数量和关键词摘要         |
| reply_chunk | agent_event  | AI 回复文本块（伪流式，按句子拆分）    |
| complete    | agent_event  | Agent 循环完成汇总（工具调用/记忆/技能）|

事件 JSON 统一结构：`{"type":"...", "content":"...", "timestamp":"..."}`
- `tool_call` 和 `tool_result` 留待后续迭代实现（需迁移到 Streaming ChatClient）

**替代方案对比**：
| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| A. Flux/Reactor SSE | Spring AI 原生流式 | Agent 循环是过程性的，不是单一流 | ❌ 不适合多阶段 |
| B. WebSocket | 双向通信 | 更重，项目无先例 | ❌ 过度 |
| C. SseEmitter 多事件 | 项目已有先例，轻量 | 需手动管理事件 | ✅ 最佳匹配 |

### 3. Agent 循环适配流式

将 `AgentService.executeCycle()` 拆分为可流式化的阶段方法：
- `searchMemories(userMessage)` → 返回匹配结果
- `executeWithStreaming(userMessage, memories, eventCallback)` → 在各阶段通过回调发送 SSE 事件

这样既保持原有阻塞 API 可用，又支持 SSE 流式推送。

## Risks / Trade-offs

- **[额外 LLM 调用开销]** 关键词提取增加 ~0.5-1s 延迟 → 使用 `deepseek-chat` 模型（轻量），设置低 temperature；对于极短消息（<10 字符）跳过提取直接用原消息
- **[关键词提取不稳定]** LLM 偶尔返回无关关键词 → 设置兜底：如果所有关键词均无匹配，回退到原始消息全量搜索
- **[SSE 连接泄漏]** 客户端异常断开 → 遵循现有模式：SseEmitter 1 小时超时 + onCompletion/onError 回调清理
- **[代码重复]** SseEmitter 管理与现有 Controller 相似 → 提取公共 SSE 管理逻辑到 `SseManager` 工具类

## Open Questions

- 无——所有技术决策已明确
