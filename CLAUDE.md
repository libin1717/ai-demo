# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build all modules (tests are skipped globally)
./mvnw clean compile

# Run a specific module (each listens on its own port)
./mvnw -pl ai-deepseek spring-boot:run    # port 8091
./mvnw -pl ai-openai spring-boot:run      # port 8082
./mvnw -pl ai-qwen spring-boot:run        # port 8083
./mvnw -pl ai-common spring-boot:run      # port 8080

# Run a single test class
./mvnw -pl <module> -Dtest=<TestClass> test
# Example:
./mvnw -pl ai-deepseek -Dtest=AiDeepseekApplicationTests test -DskipTests=false

# All tests in a module
./mvnw -pl ai-deepseek test -DskipTests=false
```

Tests are skipped by default in the root POM (`maven-surefire-plugin` `<skip>true</skip>`). Override with `-DskipTests=false` when you want to run them.

## Architecture

This is a **Spring Boot 3.5.5 / Spring AI 1.0.1** multi-module Maven project (Java 17) that demonstrates integration with three LLM providers: DeepSeek, OpenAI (redirected to DeepSeek), and Alibaba DashScope (Qwen/Tongyi).

### Module Dependency Graph

```
ai-demo (root pom, packaging=pom)
├── ai-common          shared DTO library (no external deps)
├── ai-deepseek        → depends on ai-common + spring-ai-starter-model-deepseek
├── ai-openai          → depends on ai-common + spring-ai-starter-model-openai
└── ai-qwen            → depends on ai-common + spring-ai-alibaba-starter-dashscope
```

All three AI modules are independent of each other; they only share `ai-common`.

### What Each Module Does

**ai-common** — `ResultDto<T>` generic API response wrapper (code, message, data). Used by all other modules for consistent response formatting.

**ai-deepseek** (port 8091) — The most feature-rich module. Three chat approaches demonstrated:
1. Simple blocking chat via `DeepSeekChatModel.call()` in `DeepSeekChatController`
2. SSE streaming with client management via `SseEmitter` in `StreamChatSimpleController`
3. SSE streaming with in-memory conversation history per client in `StreamChatCompletionsController` (includes prompt template support for roles like "故事大王")

Also contains `ChatService` — a raw WebClient-based SSE parser that bypasses Spring AI abstractions entirely (uses a placeholder API key, non-functional as-is).

**ai-openai** (port 8082) — Uses the OpenAI Spring AI starter but points `base-url` at DeepSeek's API (`https://api.deepseek.com`). Demonstrates the `ChatClient` fluent API (`.prompt().call().content()`) rather than direct model injection. Single controller, single endpoint. Embeddings are disabled.

**ai-qwen** (port 8083) — Alibaba DashScope integration via third-party starter `spring-ai-alibaba-starter-dashscope:1.0.0.3`. Has **no controllers**; all AI interaction code lives in test methods. Covers chat, streaming chat, and async text-to-image generation (WANX 2.1) with manual polling.

### Two Spring AI Programming Models Used

1. **Direct model injection** (ai-deepseek): `@Autowired DeepSeekChatModel` → `.call()` / `.stream()`
2. **ChatClient fluent API** (ai-openai): `@Autowired ChatClient.Builder` → `.prompt().call().content()` — this is the recommended Spring AI pattern for new code

### SSE Streaming Pattern

Both SSE controllers in ai-deepseek follow the same pattern:
- `ConcurrentHashMap<String, SseEmitter>` for connected clients
- `GET /connect?clientId=` registers a client (1-hour timeout)
- `POST /send-to-client` sends a streaming chat response to a specific client
- `POST /broadcast` sends to all connected clients
- Timer endpoints for periodic broadcasting

The streaming controllers use the `deepseek-reasoner` model which returns both chain-of-thought (`getReasoningContent()`) and final answer (`getText()`) — these are sent as separate labeled SSE events.

### Conversation Memory

`StreamChatCompletionsController` maintains an in-memory `Map<String, List<Message>>` keyed by clientId. Each turn appends the user's `UserMessage` and the model's `AssistantMessage` to the history. A prompt template (e.g., "故事大王" storyteller) can be prepended to the first message. This is session-local with no persistence.

## Configuration

- **Two-layer YAML**: `application.yml` (base + profile activation) + `application-dev.yml` (secrets). Active profile is `dev`.
- **API keys**: `application.yml` references env vars (`${DEEPSEEK-API-KEY}`, `${QWEN-API-KEY}`). Actual keys for dev are hardcoded in `application-dev.yml` files.
- **Distinct ports**: each module can run simultaneously without conflicts.
- The `ai-deepseek` module's `application-dev.yml` is git-tracked with a live API key — treat with caution.
