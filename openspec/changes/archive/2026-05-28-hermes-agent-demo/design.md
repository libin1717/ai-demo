## Context

当前项目是一个 Spring AI 多模块 Demo，已有 Chat 和 SSE 流式对话功能，但都是无状态单次调用。本次在 `ai-deepseek` 模块中构建一个 Hermes Agent 理念复刻 Demo，核心是让 Agent "越用越懂你"。

Hermes Agent 的四项核心能力中，本 Demo 聚焦前两项（持久记忆 + 自动技能沉淀），后两项（多平台网关 + GEPA 进化）不在本次范围。

## Goals / Non-Goals

**Goals:**
- 在 Spring AI 生态内展示 Agent 的持久记忆和技能沉淀能力
- 轻量化：不引入外部数据库，用本地 Markdown 文件存储
- 可演示性强：通过预设对话序列，直观对比 "有记忆/技能" vs "无状态首次执行"

**Non-Goals:**
- 不部署 Python 版 Hermes Agent，不做跨语言对接
- 不做真实的多平台消息网关
- 不做 GEPA 自我进化引擎
- 不做多用户/权限管理
- 不修改现有 ai-deepseek 的任何已有功能

## Decisions

### D1: 记忆存储用 Markdown 文件而非 SQLite

**选择**: 在 `.memory/` 目录下用多个 `.md` 文件分类存储，如 `profile.md`、`facts.md`、`decisions.md`、`MEMORY.md`（索引）。

**原因**:
- 与 Hermes Agent 自身的 `MEMORY.md` / `USER.md` 模式一致
- 对人类可读/可编辑，演示效果好，可直接打开看 Agent "学到了什么"
- 零依赖，不需要额外数据库驱动
- 全文检索用简单的 `String.contains()` 实现，Demo 级别数据量完全够用

**备选**: SQLite (被否掉的原因：增加依赖、不可直接查看、对 Demo 过度工程化)

### D2: Skills 存储格式

**选择**: 每个 Skill 一个 Markdown 文件，YAML front matter 存元信息，正文为技能描述。

```markdown
---
name: spring-controller-pattern
description: 项目中 Spring Controller 的编写规范和模式
triggers: [controller, REST, API]
version: 1
created: 2026-05-28T10:00:00
score: 5
---

## 规范
...
```

**原因**: 符合 AgentSkills.io 开放标准（Hermes 也遵循此标准），front matter 可程序化解析，正文人类可读。

### D3: 技能沉淀触发条件

**选择**: 单次对话中 LLM 调用工具次数 ≥ 5 时，在对话结束后异步触发技能总结。

**原因**: 直接复用 Hermes 的阈值设计（5 次工具调用意味着任务有一定复杂度，值得总结）。

### D4: 工具设计

**选择**: 4 个工具方法，用 `@Tool` 注解标注：

| 工具 | 用途 |
|------|------|
| `readFile` | 读取指定文件内容 |
| `writeFile` | 写入文件（带路径校验，仅限项目目录） |
| `searchCode` | 在项目中搜索关键字 |
| `listDirectory` | 列出目录结构 |

**原因**: 覆盖 Agent 在执行软件工程任务时需要的基本操作。所有工具附带回传 `.memory/` 相关记忆，实现"上下文回注"。

### D5: System Prompt 拼装策略

**选择**: 三段式拼装：

```
[角色定义 + Hermes Agent 理念简述]
[相关记忆片段] ← MemoryStore.search(query)
[相关技能文档] ← SkillManager.match(query)
[当前可用工具列表] ← Spring AI 自动注入
```

**原因**: 记忆和技能按需加载（基于当前 query 关键词匹配），而非全量注入，避免超出 context window。

### D6: 演示路径设计

**选择**: 硬编码预设对话序列，而非随机对话。

4 轮对话预设主题：
1. "帮我写一个用户注册的 Controller" → 首次执行，无记忆
2. "给刚才的 Controller 加参数校验" → 回忆上文，沉淀偏好
3. "帮我写个订单 Controller" → 自动应用之前的风格
4. 再次 "写个 xxx Controller" → 直接加载技能，一次到位

**原因**: 可控、可复现、对比效果明显。真实的 open-ended 对话也可支持，但预设路径确保演示质量。

## Risks / Trade-offs

- **记忆检索精度低**: 简单的 `String.contains()` 匹配无法做语义搜索 → Demo 数据量小（几十条记忆），够用。未来可接向量检索。
- **技能沉淀质量依赖 LLM**: 自动生成的技能文档可能质量不稳定 → 预设演示对话时对 LLM 输出做后处理（截断/格式化），不做全自动。
- **文件并发写入**: 多用户场景下有并发写文件风险 → 当前单用户 Demo，用 `synchronized` 方法保护即可。
- **`.memory/` 目录膨胀**: 长期使用文件增多 → 添加 `.gitignore` 排除，仅 Demo 演完后手动保留一份展示用。
