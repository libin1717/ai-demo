## Why

当前记忆匹配采用简单的子串匹配（`String.contains`），用户输入的自然语言查询（如"帮我写一个带参数校验的 Controller"）无法命中技术事实类的记忆条目（如"使用 @Valid 注解做参数校验"），导致记忆系统形同虚设。同时，Hermes Agent 仅支持阻塞式 HTTP 调用，缺少 SSE 流式交互方式，用户体验差且与项目中已有的 SSE 模式不一致。

## What Changes

- **优化记忆匹配方式**：用 LLM 关键词提取 + 增强评分替换简单子串匹配，让自然语言查询能命中相关记忆
- **新增 SSE 流式 Agent 对话**：为 Hermes Agent 增加 SSE 端点和流式对话能力，遵循项目已有 SSE 模式（SseEmitter + ConcurrentHashMap）
- **记忆搜索 API 增强**：保留并增强 `/agent/chat` 接口的记忆匹配逻辑，增加匹配质量日志

## Capabilities

### New Capabilities
- `agent-sse-streaming`: Hermes Agent 的 SSE 流式对话能力，支持客户端注册连接、Agent 循环流式推送、思考内容和正文分开发送

### Modified Capabilities
- `agent-memory`: 记忆检索需求变更——从简单子串匹配改为 LLM 辅助的关键词提取 + 多维度评分匹配，显著提升召回率

## Impact

- **Affected code**: `MemoryStore.java` (核心搜索逻辑重写)、`AgentService.java` (搜索调用方式调整)、新增 `AgentSseController.java`、`HermesAgentController.java` (可选新增 SSE 端点)
- **Dependencies**: 无新增外部依赖，LLM 关键词提取复用现有 `DeepSeekChatModel`
- **Breaking changes**: 无——`MemoryStore.search()` 签名不变，仅内部实现优化；新增端点不影响现有 API
