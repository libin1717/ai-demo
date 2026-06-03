## ADDED Requirements

### Requirement: 所有公共方法必须有中文 Javadoc
`agent/` 包下所有 `public` 或 `protected` 方法 SHALL 包含完整的 Javadoc 注释，使用中文撰写。

#### Scenario: 有参数和返回值的方法
- **WHEN** 方法包含一个或多个参数且有返回值
- **THEN** Javadoc 必须包含功能描述、`@param` 标注每个参数、`@return` 标注返回值

#### Scenario: 无参数或 void 返回值的方法
- **WHEN** 方法无参数或返回类型为 void
- **THEN** Javadoc 必须包含功能描述；可省略 `@param`（无参数时）或 `@return`（void 时）

### Requirement: 方法可能抛出异常时必须标注
方法 SHALL 使用 `@throws` 标注所有可能抛出的受检异常（checked exceptions）。

#### Scenario: 方法包含 try-catch 或 throws 声明
- **WHEN** 方法声明了 `throws` 子句或在方法体内捕获并重新抛出异常
- **THEN** Javadoc 必须包含 `@throws ExceptionType 异常说明`

### Requirement: DTO 类字段必须有文档注释
所有 DTO 类的字段 SHALL 包含文档注释，描述字段含义和用途。

#### Scenario: Lombok 注解的 DTO 类字段
- **WHEN** DTO 类使用了 Lombok `@Data` 注解
- **THEN** 每个字段上方应有 Javadoc 注释，说明字段的业务含义

### Requirement: 内部类方法必须有 Javadoc
`private static` 内部类（如 `AgentService.SkillGeneration`）的公共字段和 getter 方法 SHALL 包含 Javadoc。

#### Scenario: 内部类作为 JSON 反序列化目标
- **WHEN** 内部类用于 JSON 反序列化（如 FastJSON 的 `parseObject`）
- **THEN** 每个字段应有 Javadoc 说明其含义
