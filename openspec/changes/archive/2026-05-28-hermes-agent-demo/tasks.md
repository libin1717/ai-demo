## 1. 目录与配置准备

- [x] 1.1 创建 agent 包结构 `agent/controller`, `agent/service`, `agent/tool`
- [x] 1.2 创建 `.memory/` 目录初始骨架（空的 MEMORY.md, profile.md, facts.md, decisions.md, skills/ 子目录）
- [x] 1.3 更新 `ai-deepseek/.gitignore`，排除 `.memory/`（或按需保留特定文件用于演示）

## 2. MemoryStore 记忆存储

- [x] 2.1 实现 `MemoryStore` 服务类：基于 `java.nio.file` 读写 `.memory/` 目录下 Markdown 文件
- [x] 2.2 实现三类记忆的写入：`saveFact()`, `savePreference()`, `saveDecision()`，各自追加到对应的 md 文件
- [x] 2.3 实现 `search(query)` 全文检索：遍历所有记忆文件，按关键词匹配度返回相关片段（完全匹配优先于部分匹配）
- [x] 2.4 实现 `MEMORY.md` 索引文件的追加更新

## 3. SkillManager 技能管理

- [x] 3.1 实现 `SkillManager` 服务类：负责 `.memory/skills/` 目录下技能文档的管理
- [x] 3.2 实现技能文档生成 `createSkill(name, description, triggers, content)`：生成带 YAML front matter 的 Markdown 文件
- [x] 3.3 实现 `match(query)` 检索：基于 `triggers` 字段关键词匹配，返回匹配的技能正文列表
- [x] 3.4 实现 `incrementScore(skillName)` 评分递增
- [x] 3.5 实现 `listAll()` 和 `getByName(name)` 查询方法

## 4. Agent Tools 工具方法

- [x] 4.1 创建 `AgentTools` 类，使用 `@Tool` 注解定义 4 个工具方法
- [x] 4.2 实现 `readFile(path)` — 读取项目文件内容（限制在项目目录内）
- [x] 4.3 实现 `writeFile(path, content)` — 写入文件（带路径安全校验）
- [x] 4.4 实现 `searchCode(keyword)` — 搜索项目代码中匹配关键字的文件和行
- [x] 4.5 实现 `listDirectory(path)` — 列出目录结构

## 5. AgentService 核心循环

- [x] 5.1 实现 `AgentService`：注入 `DeepSeekChatModel`、`MemoryStore`、`SkillManager`
- [x] 5.2 实现三段式 System Prompt 拼装逻辑（角色定义 + 记忆片段 + 技能文档）
- [x] 5.3 实现 `executeCycle(userMessage)` 核心循环方法：
  - 检索记忆和技能
  - 拼装 System Prompt
  - 调用 LLM（支持 Tool Calling）
  - 处理 LLM 回复（区分工具调用 vs 最终文本）
  - 执行工具并回传结果给 LLM
  - 统计工具调用次数
- [x] 5.4 实现对话后自动沉淀 `autoSediment(userMessage, assistantReply, toolCallCount)`：
  - 从对话中提取事实/偏好/决策 → 调用 MemoryStore 写入
  - 若 toolCallCount ≥ 5 → 调用 SkillManager 生成技能文档

## 6. Controller REST API

- [x] 6.1 实现 `POST /agent/chat` — 对话接口，返回 `ResultDto<AgentResponse>`
- [x] 6.2 实现 `GET /agent/memory` — 记忆概要查询
- [x] 6.3 实现 `GET /agent/memory/{fileName}` — 记忆文件内容查询
- [x] 6.4 实现 `GET /agent/skills` — 技能列表查询
- [x] 6.5 实现 `GET /agent/skills/{skillName}` — 技能详情查询

## 7. 演示路径

- [x] 7.1 实现 `POST /agent/demo/run` — 预设 4 轮演示对话
- [x] 7.2 第 1 轮：请求写一个 Controller → 首次执行，无记忆
- [x] 7.3 第 2 轮：请求加参数校验 → 回忆上文，沉淀偏好
- [x] 7.4 第 3 轮：请求写同类 Controller → 自动应用风格
- [x] 7.5 第 4 轮：再次写 Controller → 技能已沉淀，直接使用
- [x] 7.6 汇聚演示结果：返回每轮 AI 回复 + 对话前后 `.memory/` 差异

## 8. 验证

- [x] 8.1 启动模块，手动请求 `/agent/chat` 验证单轮对话正常
- [x] 8.2 运行 `/agent/demo/run` 验证 4 轮演示完整执行
- [x] 8.3 查看 `.memory/` 目录确认记忆和技能文件已生成
- [x] 8.4 再次运行演示，确认历史记忆和技能在后续对话中被加载
