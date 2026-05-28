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

#### Scenario: 关键词命中记忆

- **WHEN** 用户输入包含与已有记忆相关的关键词
- **THEN** 系统 SHALL 返回匹配的记忆片段，按相关度排序（完全匹配 > 部分匹配）
- **THEN** 匹配的记忆 SHALL 被注入到当前对话的 System Prompt 中

#### Scenario: 无匹配记忆

- **WHEN** 用户输入不匹配任何已有记忆
- **THEN** 系统 SHALL 返回空结果，System Prompt 中不包含记忆片段

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
