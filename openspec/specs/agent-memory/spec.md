## ADDED Requirements

### Requirement: 记忆存储

系统 SHALL 在 `.memory/` 目录下以 Markdown 文件存储记忆，目录结构如下：

```
.memory/
├── MEMORY.md       # 索引文件，每条记忆一行
├── profile.md      # 用户画像（角色、偏好命名风格、常用框架）
├── facts.md        # 对话中抽取的事实信息
└── decisions.md    # 用户做过的技术决策
```

#### Scenario: 对话结束后自动写入记忆

- **WHEN** 一次对话完成后
- **THEN** 系统 SHALL 从对话中抽取事实、偏好和决策
- **THEN** 将抽取结果追加写入对应的 Markdown 文件
- **THEN** 在 `MEMORY.md` 索引文件中新增一行指针

### Requirement: 记忆检索

系统 SHALL 支持基于关键词的记忆检索，在每次新对话开始前根据用户输入检索相关记忆。

检索流程：
1. 使用 LLM 从用户自然语言消息中提取 3-5 个搜索关键词（名词、术语、框架名、注解名）
2. 对每个关键词执行子串匹配搜索
3. 合并去重结果，按命中次数降序排列（命中更多关键词的记忆排前面）
4. 若所有关键词均无匹配，回退到原始消息全量子串搜索

#### Scenario: 自然语言查询命中记忆

- **WHEN** 用户输入自然语言消息（如"帮我写一个带参数校验的注册接口"）
- **THEN** 系统 SHALL 先通过 LLM 提取关键词（如 @Valid、参数校验、注册接口、Controller）
- **THEN** 使用关键词在记忆文件中搜索
- **THEN** 返回按相关度排序的匹配记忆（多关键词命中优先）
- **THEN** 匹配的记忆 SHALL 被注入到当前对话的 System Prompt 中

#### Scenario: 关键词无匹配时回退

- **WHEN** LLM 提取的关键词均未命中任何记忆条目
- **THEN** 系统 SHALL 回退使用原始用户消息进行全量子串匹配
- **THEN** 若仍无匹配，System Prompt 中不包含记忆片段

#### Scenario: 极短消息跳过关键词提取

- **WHEN** 用户消息长度少于 10 个字符
- **THEN** 系统 SHALL 跳过 LLM 关键词提取步骤
- **THEN** 直接使用原始消息进行子串匹配搜索

### Requirement: 记忆分类

系统 SHALL 将记忆分为三类，各自使用独立文件：

| 类型 | 文件 | 内容示例 |
|------|------|---------|
| 事实 | `facts.md` | "项目的 base package 是 com.libin.springai" |
| 偏好 | `profile.md` | "用户喜欢使用 @Valid 注解做参数校验" |
| 决策 | `decisions.md` | "决定使用 JUnit 5 而非 TestNG" |

#### Scenario: 事实类记忆存储

- **WHEN** Agent 从对话中识别到关于项目的客观事实（如包名、端口、使用的框架）
- **THEN** SHALL 将事实写入 `facts.md`，格式为 `- <事实内容>`

#### Scenario: 偏好类记忆存储

- **WHEN** Agent 识别到用户明确的编码风格或工具偏好
- **THEN** SHALL 将偏好写入 `profile.md` 的用户画像区段

#### Scenario: 决策类记忆存储

- **WHEN** 用户在对话中做出明确的技术选型决策
- **THEN** SHALL 将决策写入 `decisions.md`
