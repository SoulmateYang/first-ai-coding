# FirstCode 阶段一 Tasks:交互式对话 REPL

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `build.gradle.kts` | Gradle 构建配置(Java 17, 依赖, application + shadowJar + jacoco) |
| 新建 | `settings.gradle.kts` | 项目名 firstcode |
| 新建 | `gradle.properties` | JVM 参数、编码 |
| 新建 | `gradle/wrapper/*` | Gradle wrapper(本地 `gradle wrapper` 生成) |
| 新建 | `.gitignore` | Java/Gradle 忽略 |
| 新建 | `.gitattributes` | 强制 LF |
| 新建 | `.editorconfig` | 缩进/编码 |
| 新建 | `README.md` | 项目说明、构建步骤、Win/Mac/Linux 启动方式 |
| 新建 | `firstcode.bat` | Win 启动脚本 |
| 新建 | `firstcode.sh` | macOS/Linux 启动脚本 |
| 新建 | `src/main/resources/logback.xml` | stderr appender, INFO 级别 |
| 新建 | `src/main/resources/config.example.yaml` | 错误信息中嵌入的示例 |
| 新建 | `src/main/java/com/firstcode/FirstCodeException.java` | 错误基类 |
| 新建 | `src/main/java/com/firstcode/io/Sanitizer.java` | 敏感串脱敏 |
| 新建 | `src/main/java/com/firstcode/io/Output.java` | 输出接口 |
| 新建 | `src/main/java/com/firstcode/io/ConsoleOutput.java` | stdout+stderr + flush |
| 新建 | `src/main/java/com/firstcode/io/BufferingOutput.java` | 测试用 |
| 新建 | `src/main/java/com/firstcode/session/Role.java` | USER/ASSISTANT |
| 新建 | `src/main/java/com/firstcode/session/Message.java` | record(role, content, thinking?) |
| 新建 | `src/main/java/com/firstcode/session/Session.java` | 内存列表 API |
| 新建 | `src/main/java/com/firstcode/session/SessionIO.java` | 原子写 |
| 新建 | `src/main/java/com/firstcode/session/SessionException.java` | 错误 |
| 新建 | `src/main/java/com/firstcode/config/Protocol.java` | 枚举 |
| 新建 | `src/main/java/com/firstcode/config/ThinkingMode.java` | 枚举 |
| 新建 | `src/main/java/com/firstcode/config/Config.java` | record + 静态 loadFromUserHome |
| 新建 | `src/main/java/com/firstcode/config/ConfigException.java` | 错误 |
| 新建 | `src/main/java/com/firstcode/net/SseChunk.java` | record(event, data) |
| 新建 | `src/main/java/com/firstcode/net/SseParser.java` | 行式状态机 |
| 新建 | `src/main/java/com/firstcode/net/HttpClientFactory.java` | 共享 HttpClient 实例 |
| 新建 | `src/main/java/com/firstcode/provider/StreamEvent.java` | sealed 风格 + 4 个 final 子类 |
| 新建 | `src/main/java/com/firstcode/provider/StreamRequest.java` | record(model, thinkingEnabled, systemPrompt?) |
| 新建 | `src/main/java/com/firstcode/provider/ProviderException.java` | 错误 |
| 新建 | `src/main/java/com/firstcode/provider/Provider.java` | 接口 |
| 新建 | `src/main/java/com/firstcode/provider/AnthropicProvider.java` | Anthropic 实现 |
| 新建 | `src/main/java/com/firstcode/provider/OpenAICompatProvider.java` | OpenAI 兼容实现 |
| 新建 | `src/main/java/com/firstcode/provider/ProviderFactory.java` | 根据 protocol 返回 |
| 新建 | `src/main/java/com/firstcode/repl/Command.java` | abstract + 4 个 final 子类 |
| 新建 | `src/main/java/com/firstcode/repl/CommandParser.java` | 解析输入 |
| 新建 | `src/main/java/com/firstcode/repl/ReplException.java` | 错误 |
| 新建 | `src/main/java/com/firstcode/repl/Repl.java` | 主循环 |
| 新建 | `src/main/java/com/firstcode/app/Main.java` | 启动入口 + 错误映射 |
| 新建 | `src/test/java/com/firstcode/repl/ReplTest.java` | 集成测试 |
| 新建 | `src/test/java/com/firstcode/io/SanitizerTest.java` | 脱敏单测 |
| 新建 | `src/test/java/com/firstcode/net/SseParserTest.java` | SSE 解析单测 |
| 新建 | `src/test/java/com/firstcode/session/SessionTest.java` | 会话内存单测 |
| 新建 | `src/test/java/com/firstcode/session/SessionIOTest.java` | 原子写单测 |
| 新建 | `src/test/java/com/firstcode/config/ConfigTest.java` | 配置加载单测 |
| 新建 | `src/test/java/com/firstcode/provider/AnthropicProviderTest.java` | 本地 mock HTTP server |
| 新建 | `src/test/java/com/firstcode/provider/OpenAICompatProviderTest.java` | 本地 mock HTTP server |
| 新建 | `src/test/java/com/firstcode/repl/CommandParserTest.java` | 命令解析单测 |
| 新建 | `src/test/resources/fixtures/` | SSE 事件样本 |

## T1:初始化 Gradle 项目骨架
**文件:** 根目录多个文件
**依赖:** 无
**步骤:**
1. `gradle wrapper --gradle-version 8.5`(本机已装 Gradle 时;否则手工放置 wrapper jar)
2. 写 `build.gradle.kts`:plugins `application` + `jacoco`;Java 17 toolchain;主类 `com.firstcode.app.Main`;依赖 `com.fasterxml.jackson.core:jackson-databind`、`jackson-dataformat-yaml`、`ch.qos.logback:logback-classic`;testImpl `org.junit.jupiter:junit-jupiter`、`org.mockito:mockito-core`、`org.assertj:assertj-core`;tasks.test { useJUnitPlatform() }
3. 写 `settings.gradle.kts`:`rootProject.name = "firstcode"`
4. 写 `gradle.properties`:`org.gradle.jvmargs=-Xmx1g`、`file.encoding=UTF-8`
5. 写 `.gitignore`(Java/Gradle/IDE 标准)
6. 写 `.gitattributes`:`* text=auto eol=lf`
7. 写 `.editorconfig`(UTF-8, LF, 4 空格缩进)
8. 写 `README.md`(项目简介 + 构建 + 启动 + Win/Mac/Linux 说明)

**验证:** `./gradlew tasks` 成功列出任务;`./gradlew build` 通过(空项目)

## T2:创建源码目录
**文件:** 目录树
**依赖:** T1
**步骤:**
1. `mkdir -p src/main/java/com/firstcode/{app,config,repl,session,provider,net,io}`
2. `mkdir -p src/main/resources src/test/java/com/firstcode src/test/resources/fixtures`

**验证:** 目录存在,`./gradlew build` 仍通过(空源)

## T3:配置资源
**文件:** `src/main/resources/logback.xml`、`src/main/resources/config.example.yaml`
**依赖:** T2
**步骤:**
1. 写 `logback.xml`:根 logger INFO,`APPENDER` 指向 `System.err`,pattern 不带颜色
2. 写 `config.example.yaml`:注释 + 四个字段 + thinking 可选行(api_key 留空占位)

**验证:** `./gradlew build` 通过;运行时能加载 logback 配置

## T4:Sanitizer
**文件:** `src/main/java/com/firstcode/io/Sanitizer.java`
**依赖:** 无
**步骤:**
1. 三个正则:API key 形 `sk-[A-Za-z0-9_-]{8,}`、`sk-ant-[A-Za-z0-9_-]{8,}`、`api_key\s*[:=]\s*["']?[A-Za-z0-9]{8,}`
2. 静态方法 `String scrub(String)` 全部替换为 `***`
3. 静态方法 `String scrub(Throwable)`,递归处理 message + cause chain

**验证:** 单元测试覆盖(T19)

## T5:Output 接口 + 两个实现
**文件:** `src/main/java/com/firstcode/io/{Output,ConsoleOutput,BufferingOutput}.java`
**依赖:** T4
**步骤:**
1. `Output` 接口:`print/printPrompt/println/printError/printInfo`
2. `ConsoleOutput`:`System.out` 包 `PrintWriter(autoflush=true)`;`System.err` 同;`printError` 内部调 `Sanitizer.scrub`;每次写完 flush
3. `BufferingOutput`:四个 StringBuilder,公开 `text()` / `errors()` / `infos()` 读出

**验证:** ReplTest 间接覆盖;`./gradlew build` 编译通过

## T6:Role + Message
**文件:** `src/main/java/com/firstcode/session/{Role,Message}.java`
**依赖:** 无
**步骤:**
1. `Role` 枚举 `USER, ASSISTANT`
2. `Message` 用 Java 17 record:`record Message(Role role, String content, String thinking)`,提供静态工厂 `user(String)`、`assistant(String)`、`assistant(String, String thinking)`

**验证:** 编译通过;`./gradlew build` 通过

## T7:Session
**文件:** `src/main/java/com/firstcode/session/Session.java`
**依赖:** T6
**步骤:**
1. 不可变 List<Message> 内部存储(`List.copyOf` 包装)
2. `appendUserMessage(String)` 返回新 Session
3. `appendAssistantMessage(String, String?)` 同上
4. `clear()` 返回空 Session
5. `size()` / `messages()` 只读访问
6. `static empty()` / `static from(List<Message>)`

**验证:** 编译通过;SessionTest 覆盖(T20)

## T8:SessionException + SessionIO
**文件:** `src/main/java/com/firstcode/session/{SessionException,SessionIO}.java`
**依赖:** T7, build.gradle.kts 含 jackson-databind
**步骤:**
1. `SessionException` 继承 `FirstCodeException`(若尚未建基类,本任务先建一个最简的 abstract class,后续 T22 完善)
2. `SessionIO`:`ObjectMapper`(注册 `JavaTimeModule` 不必要,不需要时间),`saveTo(Session, Path)` 序列化 + 原子写(写 `.tmp` + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`);首次创建时尝试 `Files.setPosixFilePermissions` 设 `OWNER_READ|OWNER_WRITE`,try-catch `UnsupportedOperationException` 跳过
3. `loadFrom(Path)`:文件不存在 → `Session.empty()`;存在但失败 → 抛 `SessionException`
4. `defaultPath()` 返回 `~/.firstcode/session.json`

**验证:** SessionIOTest 覆盖(T20)

## T9:Config 模块
**文件:** `src/main/java/com/firstcode/config/{Protocol,ThinkingMode,Config,ConfigException}.java`、`FirstCodeException` 基类补到 `com.firstcode` 根包
**依赖:** T6
**步骤:**
1. `FirstCodeException extends RuntimeException`,带 `(String message, Throwable cause)` 构造
2. `Protocol` 枚举 `ANTHROPIC, OPENAI`,带 `String wireName()` 返回小写串
3. `ThinkingMode` 枚举 `ENABLED, DISABLED`,默认 `DISABLED`
4. `Config` record:`(Protocol protocol, String model, String baseUrl, String apiKey, ThinkingMode thinking)`
5. `Config.loadFromUserHome()`:
   - 拼路径 `System.getProperty("user.home") + "/.firstcode/config.yaml"`
   - `Files.notExists` → 抛 `ConfigException` 并附示例(从 `getClass().getResource("/config.example.yaml")` 读)
   - Jackson `new ObjectMapper(new YAMLFactory()).readValue(file, Config.class)`
   - 字段校验:baseUrl 用 `URI.create` 验证;thinking 缺省 = DISABLED
   - 错误信息包含字段名 + 不打印字段值
6. `ConfigException` 继承 `FirstCodeException`

**验证:** ConfigTest 覆盖(T21);`./gradlew build` 通过

## T10:net 模块 — SseChunk + SseParser
**文件:** `src/main/java/com/firstcode/net/{SseChunk,SseParser}.java`
**依赖:** 无
**步骤:**
1. `SseChunk` record:`(String event, String data)`,`data` 允许多行已拼接
2. `SseParser`:内部 `StringBuilder currentData`、当前 event 状态
3. 方法 `List<SseChunk> feed(String chunk)`:
   - 按 `\n` 拆行,处理 `\r`(strip 末尾 `\r`)
   - 空行 → 完成一个 event,append currentData + 跳过
   - `event: foo` → 记 event
   - `data: ...` → append 到 currentData(以 `\n` 拼接)
   - `id:`,`retry:` 忽略
   - 注释行 `:` 开头忽略
4. 处理 UTF-8:输入是 String,无需转码
5. 完整 SSE 测试样本放进 `src/test/resources/fixtures/`

**验证:** SseParserTest 覆盖(T20)

## T11:HttpClientFactory
**文件:** `src/main/java/com/firstcode/net/HttpClientFactory.java`
**依赖:** 无
**步骤:**
1. 静态 `HttpClient create()`:返回 `HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(NEVER).build()`
2. 静态 `Duration requestTimeout()` 返回 60s,供调用方使用

**验证:** 编译通过

## T12:Provider 接口与事件
**文件:** `src/main/java/com/firstcode/provider/{StreamEvent,StreamRequest,ProviderException,Provider}.java`
**依赖:** T6
**步骤:**
1. `StreamRequest` record:`(String model, boolean thinkingEnabled, String systemPrompt)`
2. `StreamEvent`:`abstract class StreamEvent` 基类;4 个 final 静态内部类:`Delta(String text)`、`Thinking(String text)`、`Done()`(无参)、`Error(FirstCodeException cause)`
3. `ProviderException` 继承 `FirstCodeException`
4. `Provider` 接口:`String name()`、`void stream(List<Message>, StreamRequest, Consumer<StreamEvent>) throws ProviderException`

**验证:** 编译通过

## T13:AnthropicProvider
**文件:** `src/main/java/com/firstcode/provider/AnthropicProvider.java`
**依赖:** T10, T11, T12
**步骤:**
1. 构造器接 `Config` 或 `(String baseUrl, String apiKey, String model)`
2. `stream` 实现:
   - 构造 JSON:`{"model":..., "stream":true, "messages":[...], "max_tokens": 8192}`
   - 若 `thinkingEnabled`,加 `"thinking": {"type": "enabled", "budget_tokens": 4096}`(或 2048,小一点保险)
   - 用 `HttpRequest.newBuilder().uri(URI.create(baseUrl+"/v1/messages")).header("x-api-key", apiKey).header("anthropic-version","2023-06-01").POST(BodyPublishers.ofString(json))`
   - `HttpResponse.BodyHandlers.ofInputStream()`,从 input stream 读 UTF-8 行,逐行喂 `SseParser.feed`,得到 SseChunk 后:
     - `event: content_block_start` data 含 `type=thinking` → 标记 thinking 模式
     - `event: content_block_delta` data 含 `delta.type=text_delta` → sink Delta
     - `event: content_block_delta` data 含 `delta.type=thinking_delta` → sink Thinking
     - `event: message_stop` → sink Done
   - 用 Jackson 解析 data 字段
3. 错误:HTTP 非 2xx → 抛 `ProviderException`(读 error body 拼到 message,但经 Sanitizer)

**验证:** AnthropicProviderTest 覆盖(T23),用本地 mock HTTP server(Java `com.sun.net.httpserver.HttpServer`)

## T14:OpenAICompatProvider
**文件:** `src/main/java/com/firstcode/provider/OpenAICompatProvider.java`
**依赖:** T10, T11, T12
**步骤:**
1. 构造器同 T13
2. `stream` 实现:
   - JSON:`{"model":..., "stream":true, "messages":[...]}`
   - Header:`Authorization: Bearer <apiKey>`
   - POST `{baseUrl}/chat/completions`
   - SSE 行以 `data: ` 开头 → 喂 SseParser,只关心 data
   - `data: [DONE]` → sink Done
   - 其他 data → Jackson 解析 `choices[0].delta.content` → sink Delta(没有则忽略,如 role-only delta)
   - `data: {"error":{...}}` → sink Error
3. 错误处理同 T13

**验证:** OpenAICompatProviderTest 覆盖(T23)

## T15:ProviderFactory
**文件:** `src/main/java/com/firstcode/provider/ProviderFactory.java`
**依赖:** T13, T14
**步骤:**
1. 静态 `Provider fromConfig(Config c)`:
   - switch c.protocol():
     - ANTHROPIC → new AnthropicProvider(c.baseUrl, c.apiKey, c.model)
     - OPENAI → new OpenAICompatProvider(c.baseUrl, c.apiKey, c.model)
     - default → 抛 ConfigException("unsupported protocol: ...")(实际 enum 限制,主要是防御)
2. Provider.name() 返回 `c.protocol().wireName()`

**验证:** 编译通过;ConfigTest 间接覆盖

## T16:Command + CommandParser + ReplException
**文件:** `src/main/java/com/firstcode/repl/{Command,CommandParser,ReplException}.java`
**依赖:** T6
**步骤:**
1. `Command`:`abstract class Command`,4 个 final static 内部类 `Exit`、`ClearScreen`、`ShowHistory`、`ResetSession`
2. `CommandParser.parse(String line)`:
   - 去除首尾空白
   - 空 → `ParsedLine.empty()`
   - `exit` / `quit` → `ParsedLine.command(Exit)`
   - `/clear` → `ClearScreen`
   - `/history` → `ShowHistory`
   - `/reset` → `ResetSession`
   - 其他 → `ParsedLine.message(line)`
3. `ParsedLine`:`sealed-like`(abstract + 3 final 子类 Empty/Command/Message)暴露 `boolean isEmpty()`、`Optional<Command> command()`、`Optional<String> message()`
4. `ReplException` 继承 `FirstCodeException`

**验证:** CommandParserTest 覆盖(T24)

## T17:Repl
**文件:** `src/main/java/com/firstcode/repl/Repl.java`
**依赖:** T5, T7, T8, T12, T15, T16
**步骤:**
1. 构造器:`Repl(Config config, Provider provider, Session session, Output output)`
2. 主循环 `run()`:
   - 包 `Scanner` 读 `System.in`(`new Scanner(System.in).useDelimiter("\n")` 或按行)
   - 死循环:
     a. `output.printPrompt()`
     b. 读一行(try-catch `NoSuchElementException` = EOF → 退出 0)
     c. `CommandParser.parse` → 分派
     d. 命令分支:执行对应 Output 行为;Exit 退出;Reset 走 `session.clear()` + `sessionIO.saveTo`
     e. 消息分支:`session.appendUserMessage(text)` → 调 `provider.stream(session.messages(), request, sink)`,sink 内:
        - `Delta` → `output.print(d.text)`;累积到 `textBuilder`
        - `Thinking` → `output.print("[thinking]" + t.text)`;累积到 `thinkBuilder`
        - `Done` → 收尾:`session.appendAssistantMessage(textBuilder, thinkBuilder)`,`sessionIO.saveTo`,`output.println("")`
        - `Error` → `output.printError(e.cause)`,不退出
     f. 回到 a
3. 路径常量从 Main 注入(session 路径由构造参数决定,不在 Repl 内写硬)

**验证:** ReplTest 覆盖(T18)

## T18:ReplTest(集成测试,核心)
**文件:** `src/test/java/com/firstcode/repl/ReplTest.java`
**依赖:** T17
**步骤:**
1. 用 Mockito mock `Provider`,模拟给定消息流(Delta × N + Done)
2. 注入真实 `Session` + `SessionIO` + `BufferingOutput`
3. 通过 `System.setIn` 注入用户输入序列(多轮对话)
4. 跑 `repl.run()`,断言:
   - 每次 Delta 都被原样写入 buffer
   - 流结束后 session.messages() 包含 user + assistant
   - 第二次发起 stream 时,messages 含历史
5. 场景:EOF 退出(输入流结束)、exit 退出、/reset 后再发问不再带历史

**验证:** `./gradlew test --tests ReplTest` 全绿

## T19:SanitizerTest
**文件:** `src/test/java/com/firstcode/io/SanitizerTest.java`
**依赖:** T4
**步骤:**
1. 覆盖:`sk-xxx...`、`sk-ant-xxx...`、`api_key: "abcdef1234..."`、嵌套在异常 cause chain 中的 api_key
2. 断言:替换为 `***`;非敏感串不替换

**验证:** `./gradlew test --tests SanitizerTest` 全绿

## T20:SseParserTest + SessionTest + SessionIOTest
**文件:** `src/test/java/com/firstcode/{net/SseParserTest,session/SessionTest,session/SessionIOTest}.java`、`src/test/resources/fixtures/`
**依赖:** T7, T8, T10
**步骤:**
1. `SseParserTest`:覆盖完整事件、分块喂入(分多次 feed 同一事件)、含心跳注释、末尾 `[DONE]`、UTF-8 中文、跨多行 data
2. `SessionTest`:append / clear / size / from 不可变
3. `SessionIOTest`:保存 → 加载往返;tmp 文件不留垃圾;Unix 上权限 0600(`PosixFilePermissions` 检查,Win 上 `@EnabledOnOs(Windows)` 跳过);kill -9 模拟(直接写半截 JSON 后 loadFrom 抛 SessionException)

**验证:** 三套测试全绿

## T21:ConfigTest
**文件:** `src/test/java/com/firstcode/config/ConfigTest.java`
**依赖:** T9
**步骤:**
1. 用 JUnit 5 `@TempDir` 临时目录 + system property `user.home` 覆盖(用 `System.setProperty` + `System.clearProperty` 配 `@AfterEach` 还原)
2. 场景:
   - 配置文件不存在 → ConfigException,message 含示例
   - 缺字段 → ConfigException,message 含字段名
   - protocol 非法 → ConfigException
   - thinking 非法 → ConfigException
   - 合法配置(4 字段、5 字段带 thinking)→ 解析成功
3. 断言 error message 不含 api_key 实际值

**验证:** 全绿

## T22:FirstCodeException 完善
**文件:** `src/main/java/com/firstcode/FirstCodeException.java`(若 T8/T9 已建,本任务补充 Javadoc 与 cause chain 工具)
**依赖:** T8, T9
**步骤:**
1. 加 `Throwable rootCause()` 工具方法
2. 加 Javadoc 说明该层级的使用规约

**验证:** 编译通过

## T23:Provider 单元测试
**文件:** `src/test/java/com/firstcode/provider/{AnthropicProviderTest,OpenAICompatProviderTest}.java`
**依赖:** T13, T14
**步骤:**
1. 用 JDK 自带 `com.sun.net.httpserver.HttpServer` 起本地 mock server,绑定 `127.0.0.1:0` 随机端口
2. Anthropic 场景:
   - 完整流:message_start → content_block_start(thinking) → thinking_delta × N → content_block_start(text) → text_delta × N → message_stop
   - 验证 sink 收到的事件序列正确(text 与 thinking 分离)
   - HTTP 401:抛 ProviderException,message 经 Sanitizer
3. OpenAI 场景:
   - 完整流:delta × N + `[DONE]`
   - 错误:500 → ProviderException
   - 中途 error JSON → sink Error
4. 两个测试都用 `Consumer<StreamEvent>` sink 收集事件再断言

**验证:** 全绿

## T24:CommandParserTest
**文件:** `src/test/java/com/firstcode/repl/CommandParserTest.java`
**依赖:** T16
**步骤:**
1. 覆盖:空行、纯空白、`exit`/`quit`/`EXIT`、未知命令(视为 message)、`/clear`/`/history`/`/reset`、含前后空白的 `/clear`、消息含 `/`

**验证:** 全绿

## T25:Gradle JaCoCo 覆盖率 + 构建配置收口
**文件:** 修改 `build.gradle.kts`;新建 `firstcode.bat`、`firstcode.sh`
**依赖:** T1-T24 全部
**步骤:**
1. 在 `build.gradle.kts` 加 `jacocoTestReport` task,绑定到 `test`,配置 `com.firstcode.config.*`、`session.*`、`provider.*`、`net.*`、`repl.CommandParser`、`io.Sanitizer` 五个目标 ≥ 80%
2. `application` 插件已设主类;加 `com.github.johnrengelman.shadow` 插件 + 配置 `shadowJar`(Main-Class + Class-Path 合并)
3. `firstcode.bat`:`@echo off` + 检查 `JAVA_HOME` + `java -Dfile.encoding=UTF-8 -jar build\libs\firstcode-all.jar %*` + 路径加引号
4. `firstcode.sh`:`#!/bin/sh` + `exec java -Dfile.encoding=UTF-8 -jar "$(dirname "$0")/build/libs/firstcode-all.jar" "$@"`
5. 验证:`./gradlew shadowJar` 产出 `firstcode-all.jar`;`./gradlew jacocoTestReport` 产出报告

**验证:** `./gradlew clean build` 全绿;覆盖率 ≥ 80% 在报告 XML 中可验证

## T26:Win11 端到端冒烟(手动验收)
**文件:** 无
**依赖:** T25
**步骤:**
1. 在 Win11 上 `gradlew shadowJar` 打包
2. 准备 `~/.firstcode/config.yaml` 指向真实或 mock API
3. 启动 `firstcode.bat`,观察:
   - 启动行(无 api_key)
   - `> ` 提示符
   - 输入消息,逐 token 流式输出
   - `/history` 显示历史
   - 关闭终端后重启,自动加载历史
4. 同时切到 Linux 容器或 mac 跑一次(若本机有),验证跨平台
5. 记录视频/截图作为 AC 证据

**验证:** AC1-AC36 全部跑通并截图/日志为证;若 mock API 不可用,至少完成本地 mock 的 Anthropic 流式端到端

## 执行顺序

```
T1 ──▶ T2 ──▶ T3 ──▶ T4 ──▶ T5 ──▶ T6 ──▶ T7 ──▶ T8 ──▶ T9
                                                          │
       ┌──────────────────────────────────────────────────┤
       ▼                                                  ▼
      T10 ──▶ T11 ──▶ T12 ──▶ T13 ──▶ T14 ──▶ T15
                                       │
                                       ▼
                                      T16 ──▶ T17 ──▶ T18
                                              │       │
                                              ▼       ▼
                                  T19, T20, T21, T22, T23, T24
                                                  │
                                                  ▼
                                                 T25 ──▶ T26
```

测试任务(T18-T24)与实现任务并行思路:实现任务一完成对应测试即可跑;但 T18-T24 整体在 T17 之后做完整集成验证,避免在 Repl 未完成时 mock Provider 跑空。

## 自检

1. **plan 覆盖**:plan.md 中每个组件都有任务(Provider→T13/T14、Session→T7/T8、Repl→T17、Output→T5、Config→T9、net→T10/T11、Sanitizer→T4、Command→T16) ✓
2. **占位符扫描**:无 TBD/TODO 残留 ✓
3. **依赖链**:无循环,T26 依赖 T25 依赖全部 ✓
4. **验证完整性**:每个任务有具体验证命令或观察点 ✓
5. **类型一致性**:T12 定义的 `StreamEvent`/`StreamRequest` 在 T13/T14/T17 中一致使用 ✓
