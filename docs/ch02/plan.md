# FirstCode 阶段一 Plan:交互式对话 REPL

## 架构概览

FirstCode 采用**单进程、单模块**的 Gradle 项目骨架(后续可拆多模块),内部按职责划分为 7 个核心包:

```
┌──────────────────────────────────────────────────────────────┐
│                          Main 入口                          │
└─────────────────────┬────────────────────────────────────────┘
                      ▼
┌──────────────────────────────────────────────────────────────┐
│  Repl(REPL 控制器)                                          │
│  - 读行 → 解析命令 vs 对话消息 → 派发                       │
│  - 调用 Session 拿历史,调用 Provider 拿流式事件             │
│  - 把事件 sink 到 Output/Printer                            │
└─────┬─────────────┬──────────────┬─────────────┬─────────────┘
      ▼             ▼              ▼             ▼
┌──────────┐ ┌──────────────┐ ┌──────────┐ ┌────────────────┐
│ Command  │ │   Session    │ │ Provider │ │ Output/Printer │
│(命令解析)│ │(内存+持久化) │ │(LLM 抽象)│ │  (stdout/stderr)│
└──────────┘ └──────┬───────┘ └────┬─────┘ └────────────────┘
                     │              │
                     ▼              ▼
              ┌──────────┐   ┌────────────────────┐
              │SessionIO │   │ Provider 实现:      │
              │(文件原子写)│   │  - AnthropicProvider│
              └──────────┘   │  - OpenAICompatProvider│
                              └──────────┬─────────┘
                                         ▼
                                  ┌─────────────┐
                                  │  HttpClient │
                                  │  (SSE 解析) │
                                  └─────────────┘

横向支撑:
  Config ──→ 启动时加载 ~/.firstcode/config.yaml
  Logging ──→ SLF4J + Logback,日志走 stderr
```

数据流(一次对话):
1. Repl 读入用户输入行
2. Session.appendUserMessage → 内存列表新增 + 暂存待写入
3. Repl 调 Provider.stream(messages, request)
4. Provider 实现通过 HttpClient 发请求,逐 chunk 解析 SSE
5. 每个 delta 通过 sink(回调)传回 Repl → Output.print → stdout 立即打印
6. 流结束,Session.appendAssistantMessage → 内存更新 + 原子写文件

## 技术决策

| # | 决策点 | 选择 | 理由 |
|---|--------|------|------|
| 1 | 构建工具 | Gradle 8.x(Kotlin DSL) | 主流、构建快、生态丰富;Kotlin DSL 类型安全 |
| 2 | JDK | 17(LTS) | 满足 N7(≥11);LTS 长期支持;Records/Sealed/Text Blocks 可用 |
| 3 | HTTP 客户端 | Java 11+ `java.net.http.HttpClient` | 标准库原生、零额外依赖、支持 `BodyPublishers.ofInputStream` 流式 |
| 4 | SSE 解析 | 手写(`SseParser` 工具类) | 库不通用且容易出 bug,输入可控(自家 Provider)时手写更清晰 |
| 5 | JSON | Jackson(`jackson-databind`) | 业界标准,功能完整 |
| 6 | YAML | Jackson YAML(`jackson-dataformat-yaml`) | 与 JSON 共用 ObjectMapper,避免引入 SnakeYAML |
| 7 | 日志 | SLF4J + Logback | 业界标准,Logback 配置文件可控制级别/输出 |
| 8 | 测试 | JUnit 5 + Mockito + AssertJ | 主流组合,JUnit 5 与 JDK 17 兼容好 |
| 9 | 覆盖率 | JaCoCo | Gradle 原生集成,XML 报告便于 CI |
| 10 | 入口方式 | `application` 插件 + `shadowJar` 打包 fat-jar | `./gradlew shadowJar` 产出可执行 jar,`java -jar` 直接跑 |
| 11 | 模块组织 | 单模块多包(`com.firstcode.*`) | 阶段一规模小,单模块足够;后续多模块可平移 |
| 12 | 跨平台路径 | `java.nio.file.Path` + `System.getProperty("user.home")` | 标准库,无平台特定代码 |
| 13 | Unix 文件权限 | `Files.setPosixFilePermissions` + try-catch(Windows 跳过) | 标准库,平台能力检测 |
| 14 | 流式 sink | 函数式回调 `Consumer<StreamEvent>` | 简单直接,业务层只关心事件流 |
| 15 | 消息模型 | 内部统一为 `Message{role, content, thinking?}`(归一化),Provider 实现内做协议映射 | F12 要求跨后端消息兼容;归一化集中在边界层 |
| 16 | 错误处理 | 自定义 `FirstCodeException` 层级(子类:`ConfigException`、`ProviderException`、`SessionException`),启动期 throw → 退出码映射;运行期 throw → REPL 捕获并打印可读信息 | 统一处理,避免堆栈外泄 |
| 17 | 命令解析 | 简单前缀匹配(`/clear`、`/history`、`/reset`、`exit`/`quit`/`Ctrl-D`) | 阶段一命令极少,不引入 Picocli |
| 18 | 配置示例 | 启动时若文件不存在,打印**示例 YAML 内容**到 stderr(包含空 api_key 占位) | N4 要求"修复建议示例",降低用户上手成本 |

## Windows 11 兼容性专项

研发环境为 Windows 11,以下方面需特别处理并纳入验收。

### 路径与文件
- 家目录通过 `System.getProperty("user.home")` 获取,不硬编码 `~` 或 `/home/`
- 配置文件与会话文件使用 `java.nio.file.Path` API,跨平台分隔符
- Unix 文件权限 0600:Windows 上 `Files.setPosixFilePermissions` 抛 `UnsupportedOperationException`,需 try-catch 跳过(技术决策 13 已覆盖)
- 文件名与会话文件名避开 Windows 保留字符(`< > : " / \ | ? *`)

### 终端与控制台
- 强制 UTF-8:启动时检测控制台编码,非 UTF-8 时打印提示(让用户用 `chcp 65001` 或在启动脚本中加 `-Dfile.encoding=UTF-8`)
- 流式输出必须显式 `flush()`:Windows 控制台缓冲策略与 Unix 不同,逐 token 打印依赖显式 flush
- 本阶段不引入 ANSI 颜色,兼容 Win11 Windows Terminal 与旧版 cmd.exe

### 换行符
- 源代码统一 LF(`.editorconfig` / `.gitattributes` 强制)
- 会话文件 JSON 写出以 LF 结尾,Jackson 默认行为即 LF
- 终端打印统一 `\n`(`println`),Win11 Windows Terminal 正确处理

### 信号与进程
- `exit` / `quit` 命令统一处理退出
- `Ctrl-D`(EOF)通过 `Scanner.hasNextLine()` 检测
- `Ctrl-C` 捕获 `InterruptedException`,退出码 130
- `session.json` 原子写(先写 `*.tmp` 再 `Files.move(ATOMIC_MOVE)`)已在 Win 上由 `Files.move` 原子性保证

### 启动脚本
- 提供 `firstcode.bat`(Windows)与 `firstcode.sh`(macOS/Linux)双脚本
- `bat` 中:路径含空格时正确加引号、设置 `-Dfile.encoding=UTF-8`
- `sh` 中:用 `exec java` 替换进程,信号传递更干净

### Win11 专项测试点(纳入 checklist)
- 家目录解析正确(`System.getProperty("user.home")` 在 Win11 上返回 `C:\Users\<user>`)
- 配置文件与会话文件可正常创建、读取、原子写
- 流式输出在 Windows Terminal 中逐 token 显示,无明显缓冲
- 跨平台 CI 视角:代码无 `# Win only` / `# Unix only` 分支(除 `setPosixFilePermissions` 的 try-catch 跳过)
- `.gitattributes` 设置 `* text=auto eol=lf`,避免 CRLF 污染源码

### 已知差异不修复(写明避免范围蔓延)
- 旧版 cmd.exe 的 ANSI 不支持(本阶段不做颜色)
- Win 文件权限模型差异(本阶段跳过 0600 设置,无安全影响)
- Win 长路径(>260)限制:若用户家目录极深导致失败,README 说明需启用 Win 长路径策略

## 核心数据结构

### Config(配置,启动期加载)
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `protocol` | `Protocol` 枚举(`ANTHROPIC`/`OPENAI`) | 是 | 决定使用哪个 Provider |
| `model` | `String` | 是 | 模型名 |
| `baseUrl` | `String` | 是 | API base URL |
| `apiKey` | `String` | 是 | 永不打印 |
| `thinking` | `ThinkingMode` 枚举(`ENABLED`/`DISABLED`) | 否,默认 `DISABLED` | Anthropic 专属 |

加载入口:`Config.loadFromUserHome() throws ConfigException`,失败抛异常由 Main 映射为退出码 + 友好错误 + YAML 示例。

### Message(消息,内部统一模型)
| 字段 | 类型 | 说明 |
|------|------|------|
| `role` | `Role` 枚举(`USER`/`ASSISTANT`) | 消息角色 |
| `content` | `String` | 文本内容 |
| `thinking` | `String?` | 仅 assistant,可空;extended thinking 内容 |

`Message` 是归一化模型,Provider 内部负责与各协议的消息体互转(F15 决策)。

### StreamEvent(流式事件,Provider → Repl 回调)
`sealed interface`,三种实现:
- `StreamEvent.Delta(String text)` — 增量文本(可流式打印)
- `StreamEvent.Thinking(String text)` — extended thinking 增量(Repl 决定如何呈现,默认行首加 `[thinking]` 标记)
- `StreamEvent.Done()` — 流正常结束
- `StreamEvent.Error(FirstCodeException cause)` — 流异常结束(Repl 转为可读错误,继续 REPL)

> 注:sealed interface 在 Java 17 可用,但 Java 17 无原生 sealed 语法。可改用 `abstract class StreamEvent` + 4 个 final 子类,行为等价。

### Provider(接口,LLM 抽象)
```java
public interface Provider {
    String name();   // 显示名,如 "anthropic" / "openai"

    void stream(
        List<Message> messages,
        StreamRequest request,
        Consumer<StreamEvent> sink
    ) throws ProviderException;
}
```

`StreamRequest` 字段:
- `String model`(冗余,允许覆盖)
- `boolean thinkingEnabled`
- `String systemPrompt`(可选,本阶段不暴露)

`Provider` 实现:
- `AnthropicProvider implements Provider` — 调 Messages API + SSE,处理 `content_block_delta` / `message_stop`
- `OpenAICompatProvider implements Provider` — 调 Chat Completions + SSE,处理 `choices[].delta.content` / `data: [DONE]`
- `ProviderFactory.fromConfig(Config)` — 根据 `protocol` 字段返回对应实例

### Session(会话,内存 + 持久化)
| 字段/方法 | 说明 |
|------|------|
| `List<Message> messages` | 内存中的全部历史 |
| `appendUserMessage(String)` | 追加用户消息 |
| `appendAssistantMessage(String, String? thinking)` | 流式结束后追加助手消息 |
| `clear()` | /reset 调用,清空内存 |
| `size()` | 消息数(给 AC6/AC19 用) |
| `saveTo(Path file)` | 原子写入(写 tmp → `Files.move(ATOMIC_MOVE)`) |
| `static Session loadFrom(Path file)` | 反序列化,文件不存在返回空 Session |
| `static Session empty()` | 全新空会话 |

### SessionIO(文件读写)
- `SessionIO` 工具类,封装 `ObjectMapper` 与原子写
- 写入流程:序列化 → 写 `session.json.tmp` → `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`
- 读:反序列化 `session.json`,失败抛 `SessionException`
- 权限:首次创建时设 `PosixFilePermission.OWNER_READ|OWNER_WRITE`(Win 跳过)

### Repl(主控制器)
```java
public class Repl {
    Repl(Config config, Provider provider, Session session, Output output);

    void run();   // 阻塞主循环,直到 exit / EOF / SIGINT
}
```

主循环:
1. `output.printPrompt()` — 打印 `> `
2. 读一行(`Scanner.nextLine()`,处理 EOF / Ctrl-C)
3. 命令分派:CommandParser 解析 → 若是命令执行;若是消息,转 4
4. `session.appendUserMessage(line)`
5. `provider.stream(messages, request, sink)` — sink 回调中:`output.print(text)` 立即打印
6. 流结束:`session.appendAssistantMessage(fullText, fullThinking?)` → `session.saveTo(file)`
7. 回到 1

### Output(终端输出抽象)
- `void print(String text)` — stdout 立即 flush
- `void printPrompt()` — 打印 `> ` 并 flush
- `void println(String line)` — 打印一行 + flush
- `void printError(FirstCodeException e)` — stderr 打印 `ERROR: <reason>` + 友好提示
- `void printInfo(String line)` — stderr 打印 `INFO: <line>`

实现:`ConsoleOutput`(生产)/ `BufferingOutput`(测试用,记录到内存不真实打印)

### Command(命令解析)
`sealed interface Command`(用 `abstract class + final 子类`):
- `Exit`
- `ClearScreen`
- `ShowHistory`
- `ResetSession`

解析:输入以 `/` 开头 → 解析 `/clear` / `/history` / `/reset`;`exit` / `quit` 视为 Exit;空行返回 `null`(Repl 不发起请求);其它视为对话消息。

### FirstCodeException(错误层级)
```
FirstCodeException (abstract)
├── ConfigException       — 配置加载/校验失败,启动期
├── ProviderException      — Provider 调用失败(网络/解析/上游错误),运行期
├── SessionException       — 会话文件读写失败
└── ReplException          — 通用兜底
```

### 错误信息脱敏
`Output.printError` 在打印前,经 `Sanitizer.scrub(Throwable)` 处理:扫描 message 与 cause chain,把任何 `api_key` / `sk-...` / `sk-ant-...` / 长 hex 串替换为 `***`。N2 + AC30 强约束。

### 类间依赖图(单向无环)
```
Main → Config → Repl → Provider (via ProviderFactory)
                → Session → SessionIO
                → Output
                → CommandParser
```

模块全部位于包 `com.firstcode.*`:
- `com.firstcode.app` — Main
- `com.firstcode.config` — Config, ConfigException, Protocol, ThinkingMode
- `com.firstcode.repl` — Repl, CommandParser, Command*, ReplException
- `com.firstcode.session` — Session, SessionIO, Message, Role, SessionException
- `com.firstcode.provider` — Provider, StreamEvent, StreamRequest, ProviderException, ProviderFactory, AnthropicProvider, OpenAICompatProvider
- `com.firstcode.net` — HttpClientFactory, SseParser
- `com.firstcode.io` — Output, ConsoleOutput, BufferingOutput, Sanitizer

## 模块设计

### com.firstcode.app
**职责:** 启动入口与全局错误映射。
**对外接口:** `Main.main(String[])`
**依赖:** `Config`、`Repl`
**关键逻辑:**
- 加载 `Config`(try-catch `ConfigException`,打印 YAML 示例 + 退出码 2)
- 构造 `ProviderFactory.fromConfig(config)`(失败同样映射退出码)
- 加载或创建 `Session`(失败打 ERROR 但不退出,降级为空 Session)
- 构造 `ConsoleOutput`
- 实例化 `Repl` 并 `run()`
- 顶层 catch `Throwable` → 打印 `ERROR: unexpected` + 退出码 1(防止堆栈外泄)

### com.firstcode.config
**职责:** YAML 解析、字段校验、错误信息生成。
**对外接口:** `Config.loadFromUserHome()`
**依赖:** Jackson YAML
**关键逻辑:**
- `Files.exists` 检查 `~/.firstcode/config.yaml`,缺失抛 `ConfigException` 并附 YAML 示例
- `new ObjectMapper(new YAMLFactory()).readValue(file, Config.class)`
- 字段校验:`protocol` 必须 ∈ {anthropic, openai};`thinking` 若存在必须 ∈ {enabled, disabled};URL 用 `URI.create` 验证
- 错误信息:包含字段名 + 期望值示例,不打印字段值本身

### com.firstcode.repl
**职责:** REPL 主循环与命令解析。
**对外接口:** `Repl.run()`、`CommandParser.parse(String) → ParsedLine(command | message | null)`
**依赖:** `Session`、`Provider`、`Output`
**关键逻辑:**
- 主循环:`output.printPrompt()` → `Scanner.nextLine()` → 分派
- 命令执行:直接调用 Output 能力(`/clear` 走 ANSI 清屏序列或打印多空行;`/history` 走 `Session.messages.forEach` 打印;`/reset` 走 `Session.clear()` + 重新 save)
- 消息流:见 Repl 主循环伪代码(上一段)
- EOF(Ctrl-D / Win Ctrl-Z):输出感谢语 + 退出 0
- SIGINT:捕获 `InterruptedException`,退出码 130

### com.firstcode.session
**职责:** 会话内存模型与文件持久化。
**对外接口:** `Session` API(append/clear/size/saveTo/loadFrom/empty)、`Message`、`Role`、`SessionIO`
**依赖:** Jackson JSON
**关键逻辑:**
- `Session` 内部 `List<Message>` 不可变包装(防御性复制)
- 序列化格式:JSON 数组 `[{role,content,thinking?}, ...]`,紧凑可读
- 写入原子性:tmp 文件 + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`
- 权限设置(Unix 优先,Win 跳过)
- 损坏文件处理:`loadFrom` 失败抛 `SessionException`,由 Main 降级为空 Session + 提示

### com.firstcode.provider
**职责:** LLM 协议适配层。
**对外接口:** `Provider`、`StreamEvent`、`StreamRequest`、`ProviderException`、`ProviderFactory`
**依赖:** `HttpClientFactory`、`SseParser`、`Message`
**关键逻辑:**
- `AnthropicProvider.stream`:构造 `messages` payload + `anthropic_version` + `thinking` 块(若启用)→ POST `/v1/messages` with `stream: true` → 解析 SSE → 映射事件:
  - `message_start` → 忽略
  - `content_block_start (type=thinking)` → 准备 thinking buffer
  - `content_block_delta (type=thinking_delta)` → `sink.accept(Thinking(delta))`
  - `content_block_start (type=text)` → 准备 text buffer
  - `content_block_delta (type=text_delta)` → `sink.accept(Delta(delta))`
  - `message_stop` → `sink.accept(Done())`
  - 解析失败 → `sink.accept(Error(...))`
- `OpenAICompatProvider.stream`:构造 `messages` 数组 + `stream: true` → POST `/chat/completions` → 解析 SSE:
  - `data: {"choices":[{"delta":{"content":"..."}}]}` → `sink.accept(Delta(...))`
  - `data: [DONE]` → `sink.accept(Done())`
  - `data: {"error":...}` → `sink.accept(Error(...))`
- `ProviderFactory.fromConfig(config)`:根据 `config.protocol()` 返回对应实例,未知值抛 `ConfigException`

### com.firstcode.net
**职责:** HTTP 与 SSE 基础能力。
**对外接口:** `HttpClientFactory.create()`、`SseParser.feed(byte[]) → List<SseChunk>`
**依赖:** Java 11+ `java.net.http`
**关键逻辑:**
- `HttpClientFactory`:配置超时(connect 10s / request 60s,Win 下同样生效),禁用重定向,禁用 cookie
- `SseParser`:行式状态机,识别 `event:` / `data:` / `id:` / 空行分界;UTF-8 解码;多行 data 用 `\n` 拼接
- `SseChunk { event: String?, data: String }`
- 测试:在 `SseParserTest` 中覆盖完整/分块/含心跳/末尾 DONE 场景

### com.firstcode.io
**职责:** 输出与脱敏。
**对外接口:** `Output`、`ConsoleOutput`、`BufferingOutput`、`Sanitizer.scrub(Throwable)`
**依赖:** 无
**关键逻辑:**
- `ConsoleOutput`:包装 `System.out`(写流式内容)+ `System.err`(写日志/错误),每次 `print` 后 `flush()`
- `BufferingOutput`:用于测试,把 `print/println/printError/printInfo` 全部写入 `StringBuilder`
- `Sanitizer.scrub`:正则 `sk-[A-Za-z0-9_-]{8,}` / `sk-ant-[A-Za-z0-9_-]{8,}` / `api_key\s*[:=]\s*["']?[A-Za-z0-9]{8,}` 替换为 `***`,扫描 message + cause chain

## 文件组织

```
D:/work/projectTest/first-code/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/...
├── .editorconfig
├── .gitattributes
├── .gitignore
├── README.md
├── firstcode.bat                  — Win 启动脚本
├── firstcode.sh                   — macOS/Linux 启动脚本
├── docs/
│   ├── ch01/                      — (后续章节预留)
│   └── ch02/
│       ├── spec.md                — 已批准
│       ├── plan.md                — 本文件
│       ├── task.md                — 阶段三产出
│       └── checklist.md           — 阶段四产出
└── src/
    ├── main/
    │   ├── java/com/firstcode/
    │   │   ├── app/Main.java
    │   │   ├── config/
    │   │   │   ├── Config.java
    │   │   │   ├── ConfigException.java
    │   │   │   ├── Protocol.java
    │   │   │   └── ThinkingMode.java
    │   │   ├── repl/
    │   │   │   ├── Repl.java
    │   │   │   ├── ReplException.java
    │   │   │   ├── CommandParser.java
    │   │   │   └── Command.java        — sealed 风格基类 + 4 个 final 子类
    │   │   ├── session/
    │   │   │   ├── Session.java
    │   │   │   ├── SessionException.java
    │   │   │   ├── SessionIO.java
    │   │   │   ├── Message.java
    │   │   │   └── Role.java
    │   │   ├── provider/
    │   │   │   ├── Provider.java
    │   │   │   ├── StreamEvent.java
    │   │   │   ├── StreamRequest.java
    │   │   │   ├── ProviderException.java
    │   │   │   ├── ProviderFactory.java
    │   │   │   ├── AnthropicProvider.java
    │   │   │   └── OpenAICompatProvider.java
    │   │   ├── net/
    │   │   │   ├── HttpClientFactory.java
    │   │   │   ├── SseParser.java
    │   │   │   └── SseChunk.java
    │   │   └── io/
    │   │       ├── Output.java
    │   │       ├── ConsoleOutput.java
    │   │       ├── BufferingOutput.java
    │   │       └── Sanitizer.java
    │   └── resources/
    │       ├── logback.xml         — stderr appender,INFO 级别
    │       └── config.example.yaml — 错误信息中打印的示例
    └── test/
        ├── java/com/firstcode/
        │   ├── config/ConfigTest.java
        │   ├── repl/CommandParserTest.java
        │   ├── repl/ReplTest.java          — 使用 BufferingOutput + MockProvider
        │   ├── session/SessionTest.java
        │   ├── session/SessionIOTest.java
        │   ├── provider/AnthropicProviderTest.java   — MockHttpClient
        │   ├── provider/OpenAICompatProviderTest.java — MockHttpClient
        │   ├── net/SseParserTest.java
        │   └── io/SanitizerTest.java
        └── resources/
            └── fixtures/  — SSE 事件样本
```

## 模块交互(典型时序:一次对话)

```
用户           Repl         CommandParser    ProviderFactory   Provider       HttpClient
 │  输入"hi"    │                  │                │              │              │
 │─────────────▶│                  │                │              │              │
 │              │ parse("hi")      │                │              │              │
 │              │─────────────────▶│                │              │              │
 │              │  返回 MessageCmd │                │              │              │
 │              │◀─────────────────│                │              │              │
 │              │ session.appendUserMessage("hi")                │              │
 │              │                                                     │              │
 │              │ provider.stream(messages, req, sink)               │              │
 │              │─────────────────────────────────────────────────────▶│              │
 │              │                                                     │  HTTP POST   │
 │              │                                                     │─────────────▶│
 │              │                                                     │  SSE chunks  │
 │              │                                                     │◀─────────────│
 │              │ sink(Delta("Hello"))      ◀─────────────────────────│              │
 │              │ output.print("Hello")                                │              │
 │  看到"Hello" │                                                     │              │
 │◀─────────────│                                                     │              │
 │              │ sink(Delta("!"))           ◀─────────────────────────│              │
 │              │ output.print("!")                                   │              │
 │              │ sink(Done())                ◀─────────────────────────│              │
 │              │ session.appendAssistantMessage("Hello!", null)      │              │
 │              │ session.saveTo(file)  (原子写)                      │              │
 │              │ printPrompt()                                       │              │
 │  看到"> "    │                                                     │              │
```

## spec 覆盖自检

| F 需求 | 归属 |
|--------|------|
| F1 启动入口 | `app.Main` + `config.Config` |
| F2 REPL | `repl.Repl` + `repl.CommandParser` |
| F3 流式回复 | `provider.Provider.stream` + `io.Output` + `net.SseParser` |
| F4 多轮上下文 | `session.Session` 在每次 stream 前提供完整 messages |
| F5 会话持久化 | `session.SessionIO` + 原子写 |
| F6 重启续接 | `session.Session.loadFrom` + `app.Main` 启动时调用 |
| F7 Provider 抽象 | `provider.Provider` 接口 |
| F8 Anthropic | `provider.AnthropicProvider` |
| F9 OpenAI 兼容 | `provider.OpenAICompatProvider` |
| F10 Extended Thinking | `AnthropicProvider` 内的 `thinking` 块 + `StreamEvent.Thinking` + Repl 行首标记 |
| F11 配置加载 | `config.Config.loadFromUserHome` |
| F12 Provider 切换 | `ProviderFactory.fromConfig` + `protocol` 字段 |

每条 F 需求均有归属,无缺口。
