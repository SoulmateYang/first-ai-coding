# FirstCode 阶段一 Checklist:交互式对话 REPL

> 每一项通过运行代码或观察行为来验证,聚焦系统行为。验证方式写在括号内。

## 实现完整性

### 启动与配置 (AC1-AC5)
- [ ] 启动入口已实现:`com.firstcode.app.Main.main` 可被 JVM 直接执行(验证:`./gradlew shadowJar` 产出 `firstcode-all.jar`,`java -jar` 可启动)
- [ ] 配置文件缺失时,打印明确错误并退出码非 0(验证:删 `~/.firstcode/config.yaml`,运行 `firstcode`,观察 stderr 含示例 YAML 且 `echo $?` 非 0)
- [ ] 合法配置下,启动首行包含 `protocol` 与 `model` 字段(验证:配置后启动,捕获第一行 stdout/stderr 输出)
- [ ] 启动行不含 `api_key` 与 `base_url` 字符串(验证:启动输出 `grep -E "api_key|base_url"` 应无匹配)
- [ ] 必填字段缺失报错信息含字段名(验证:删除 `model`,启动,看错误文本)
- [ ] `protocol` 取非法值报错并退出(验证:`protocol: foo`,启动,看错误)
- [ ] 错误信息不暴露字段值(验证:配 `api_key: sk-supersecret123456`,故意触发解析错误,grep 错误信息不含 `supersecret`)

### REPL 与命令 (AC6-AC11)
- [ ] REPL 主循环持续运行(验证:连续输入两条,均得到回复)
- [ ] 空行不发起网络请求(验证:mock Provider 计数器,空行后计数不增)
- [ ] `exit` 退出码 0(验证:输入 exit,`echo $?` = 0)
- [ ] `quit` 同 `exit`(验证:同上)
- [ ] `/clear` 清屏(验证:终端观察屏幕清空)
- [ ] `/history` 打印当前会话全部 user/assistant 消息(验证:输入几条,运行 /history,观察输出)
- [ ] `/reset` 后会话清空,后续提问不引用前文(验证:/reset 后问一个前文名字,回答「不知道」)

### 流式回复 (AC12-AC15)
- [ ] 回复逐 token 打印,非整批(验证:Provider mock 每次 sink 之间 sleep 100ms,观察终端确实是分批出现)
- [ ] 流式过程无多余换行(验证:观察同一行内连续打印,仅回复结束换行)
- [ ] 模拟网络中断(改坏 base_url)REPL 不退出,打印一行可读错误(验证:配错 URL,提问,看错误信息,继续 `> ` 提示)
- [ ] 错误信息不含 Java 堆栈、绝对路径(验证:错误输出不含 `Exception`、`at com.firstcode`、盘符路径)

### 多轮与持久化 (AC16-AC20)
- [ ] 第二轮提问 LLM 能引用前文(验证:第一轮「我叫小明」,第二轮「我叫什么」,得到「小明」)
- [ ] 流式结束后消息写入 `~/.firstcode/session.json`(验证:对话后 cat 文件,看到 user/assistant JSON)
- [ ] 进程被 kill -9 时不出现半写 session.json(验证:对话中 kill,重启,文件要么完整、要么不存在,无半截)
- [ ] 重启且 session.json 存在时自动加载并提示「已加载 N 条历史消息」(验证:对话 → exit → 启动,观察启动行 N 与提示)
- [ ] 加载历史后新提问可引用历史(验证:两轮对话 → kill → 重启 → 问前文,正确)

### Provider 抽象 (AC21-AC26)
- [ ] 业务层代码不出现 `anthropic` / `openai` / `deepseek` / `qwen` / `zhipu` / `MiniMax` 字面量(验证:`grep -REn "anthropic|openai|deepseek|qwen|zhipu|MiniMax" src/main/java/com/firstcode/repl src/main/java/com/firstcode/session src/main/java/com/firstcode/app`,应无匹配)
- [ ] Anthropic 端到端打通(验证:配 protocol=anthropic,提问,得回复)
- [ ] OpenAI 兼容端到端打通(验证:配 protocol=openai + 任意 base_url,提问,得回复)
- [ ] OpenAI 兼容后端可对接至少 4 家厂商(验证:用 OpenAI / DeepSeek / Qwen / 智谱 4 套 base_url+model 各跑通一次)
- [ ] 切换 `protocol` 重启后生效,会话历史不丢失(验证:anthropic 对话 → 改 openai → 重启 → 提问引用前文)
- [ ] Provider 收到 chunk 后立刻 sink,无中间聚合(验证:代码走查 AnthropicProvider / OpenAICompatProvider,确认循环内直接 `sink.accept`,无 `StringBuilder` 缓存整批)

### Extended Thinking (AC27-AC29)
- [ ] `thinking: enabled` + `protocol: anthropic` 时输出含 `[thinking]` 段(验证:用真/ mock Anthropic 启用 thinking,观察输出)
- [ ] `thinking` 缺省或 `disabled` 时无 `[thinking]` 段(验证:同上,关 thinking)
- [ ] `protocol: openai` 配 `thinking: enabled` 不出现 `[thinking]` 段(验证:OpenAI 配 enabled,观察无 thinking 段)

## 集成

- [ ] Main → Config → Repl → Provider 装配链通(验证:ReplTest 中注入 mock Provider + 真实 Session/SessionIO,跑通两轮对话并断言 Session 落盘)
- [ ] SessionIO 原子写在 Win11 与 Unix 都生效(验证:SessionIOTest `@EnabledOnOs({LINUX, MAC, WINDOWS})` 全绿)
- [ ] SseParser 喂入 HTTP 输入流的 chunk 边界与一次喂入整段等价(验证:SseParserTest 中同一事件分块喂入 vs 一次性喂入,产出的 SseChunk 列表一致)
- [ ] 业务层所有公开 API 至少被一处调用(验证:`./gradlew build` 编译通过 + `ReplTest` 覆盖 Repl、`AnthropicProviderTest`/`OpenAICompatProviderTest` 覆盖两个 Provider)

## 编译与测试

- [ ] `./gradlew clean build` 在 Win11 上无错误通过(验证:运行命令,exit 0)
- [ ] `./gradlew test` 全部测试通过(验证:运行命令,无 failure)
- [ ] 核心模块单元测试覆盖率 ≥ 80%(验证:`./gradlew jacocoTestReport`,检查 `build/reports/jacoco/test/jacocoTestReport.xml` 中 `com.firstcode.config.*`、`session.*`、`provider.*`、`net.*`、`repl.CommandParser`、`io.Sanitizer` 覆盖率)
- [ ] `./gradlew shadowJar` 产出 `firstcode-all.jar`(验证:`ls build/libs/`,存在 `firstcode-all.jar`)
- [ ] `firstcode.bat` 在 Win11 上可启动并进入 REPL(验证:双击或在 cmd 运行,出现 `> ` 提示符)
- [ ] `firstcode.sh` 在 macOS/Linux 上可启动并进入 REPL(验证:同上的跨平台观察)
- [ ] 源码以 LF 结尾(验证:`git ls-files | xargs -I {} sh -c 'file "{}" | grep -q CRLF && echo CR LF: {}'` 无输出,或 `cat -A file.java` 行尾无 `^M`)
- [ ] `grep -R "api_key" build/` 全文不出现真实 api_key 值(验证:用真实 key 配置后跑一次,grep 不应泄漏)
- [ ] 错误信息不出现 api_key(AC30 细化):对所有单元测试与一次 E2E 失败注入,断言捕获的错误信息不含 key 值
- [ ] Unix 上配置文件与会话文件权限为 `0600`(验证:对话后 `ls -l ~/.firstcode/`,权限 `rw-------`)
- [ ] Win11 上文件权限设置不抛异常(验证:SessionIOTest 在 Win 上跑通,无 UnsupportedOperationException 上抛)

## Win11 端到端场景

### 场景 E1:首次启动与配置
- [ ] Win11 用户家目录 `C:\Users\<user>` 下 `~/.firstcode/` 目录可被 `Path API` 正确解析(验证:代码中 `System.getProperty("user.home")` 返回的路径拼接 `.firstcode/config.yaml` 后,`Files.exists` / `Files.createDirectories` 在 Win 上正常工作)
- [ ] 首次启动(无 config.yaml)stderr 打印 YAML 示例,退出码 2(验证:启动 → 观察 stderr + exit code)
- [ ] 按示例创建 `~/.firstcode/config.yaml` 后再次启动,REPL 进入(验证:看到 `> ` 提示符)

### 场景 E2:单轮对话(Anthropic + 流式)
- [ ] 输入 `你好`,在 Windows Terminal 中观察回复逐 token 出现(验证:视觉确认无整批闪出,行内连续打印)
- [ ] 流式结束自动换行,光标回到 `> `(验证:可继续输入)
- [ ] 关闭 cmd 后,`~/.firstcode/session.json` 出现一条 user + 一条 assistant(验证:`cat` 文件,JSON 数组长度 2)

### 场景 E3:多轮上下文
- [ ] 第一轮「我今天买了 3 个苹果」,得到回答
- [ ] 第二轮「我买了几个?」,LLM 答「3 个」(验证:引用前文)
- [ ] 第三轮「为什么是苹果不是香蕉?」,LLM 自由发挥且语境连贯
- [ ] `/history` 打印 6 条消息(3 user + 3 assistant)(验证:行数与序号)

### 场景 E4:Provider 切换
- [ ] 把 `protocol` 从 `anthropic` 改为 `openai`,`base_url` 改为 OpenAI 官方,重启 — **需 Anthropic key(本次未验证)**
- [ ] 启动行展示 `protocol: openai`、`model: ...`(验证:视觉)
- [ ] 历史被加载(验证:启动行有「已加载 6 条历史消息」)
- [ ] 输入「我买了几个?」,OpenAI 后端答「3 个」(验证:跨后端历史兼容)

### 场景 E5:Extended Thinking
- [ ] 恢复 `protocol: anthropic`,加 `thinking: enabled`,重启 — **需 Anthropic key(本次未验证)**
- [ ] 输入一个需要推理的问题(例如「9.11 和 9.9 哪个大?」)
- [ ] 观察到输出含 `[thinking]` 行(thinking 内容),后接正式回答(验证:thinking 与 text 视觉可区分)
- [ ] 改 `thinking: disabled` 重启,同样问题,无 `[thinking]` 行(验证:开关生效)

### 场景 E6:异常恢复
- [ ] 故意把 `base_url` 改成 `https://invalid.example.com`,输入消息
- [ ] 终端打印一行 `ERROR: ...`(验证:无 Java 堆栈)
- [ ] REPL 不退出,继续显示 `> `(验证:可继续输入命令或退到正常配置)
- [ ] 把 `base_url` 改回正确值,再次输入消息,得到正常回复(验证:恢复后能继续用,无需重启)

### 场景 E7:会话持久化与原子写
- [ ] 对话 5 轮后,kill -9 任务管理器结束进程(Win:任务管理器结束任务;或 `taskkill /F /PID <pid>`)
- [ ] 检查 `session.json` 不是半截 JSON(验证:`cat` 文件,要么不存在,要么 JSON 完整可解析;无半截)
- [ ] 重启程序,若 session.json 完整 → 加载 10 条历史;若不存在 → 空 session 启动(验证:行为分支正确)

### 场景 E8:内置命令
- [ ] `/clear` 后屏幕清空(Win Terminal / cmd 行为可能略不同,但本阶段不依赖清屏,只需不报错)
- [ ] `/reset` 后 `/history` 显示 0 条
- [ ] 输入 `exit`,退出码 0
- [ ] 输入 `Ctrl-C`,REPL 干净退出(不死锁、不留半截状态);Win 上退出码可能为 0/130/255,均表示用户主动中断,符合平台习惯

### 场景 E9:OpenAI 兼容多厂商
依次在配置中切换,每次重启后跑通一次端到端对话,记录证据:
- [ ] OpenAI 官方(`https://api.openai.com/v1`,模型 `gpt-4o-mini` 等)— **需 OpenAI key(本次未验证)**
- [ ] DeepSeek(`https://api.deepseek.com/v1`,模型 `deepseek-chat`)✅
- [ ] 通义千问 Qwen/DashScope OpenAI 兼容 endpoint(模型 `qwen-turbo` 等)— **需 Qwen key(本次未验证)**
- [ ] 智谱 GLM OpenAI 兼容 endpoint(模型 `glm-4-flash` 等)— **需 智谱 key(本次未验证)**

## 跨平台验证

- [ ] `firstcode.sh` 在 macOS 或 Linux 容器内可启动(验证:同 Win11 E1-E8 主流程,任选一个平台)
- [ ] Unix 上 `ls -l ~/.firstcode/config.yaml` 权限为 `-rw-------`(AC31)
- [ ] Unix 上 `ls -l ~/.firstcode/session.json` 权限为 `-rw-------`(AC31)
- [ ] Win11 上 `setPosixFilePermissions` 路径不抛异常(走 try-catch),文件仍可读写(AC31 在 Win 上的退化行为正确)
- [ ] 源代码 `grep -R "Win" src/main/java` 唯一匹配点是 SessionIO 权限设置的 try-catch 与 Win 兼容性代码(不应有大量 `# Win only` 散落)
- [ ] 启动脚本 `firstcode.bat` 含 `-Dfile.encoding=UTF-8` 与 `chcp 65001` 或同等设置(验证:cat 文件,看到设置)
- [ ] 启动脚本 `firstcode.sh` 用 `exec java`(验证:cat 文件,看到 `exec`)

## 安全与脱敏

- [ ] 用包含 `sk-xxx...` 的真实 key 配置后,任意错误信息 grep `sk-xxx` 无输出(AC30)
- [ ] 用包含 `sk-ant-xxx...` 的真实 key 配置后,任意错误信息 grep `sk-ant-xxx` 无输出
- [ ] kill -9 留下的 tmp 文件 `session.json.tmp` 不含明文 key 之外的会话内容(本阶段 key 不在会话文件内,但确认 tmp 与正式文件内容一致)
- [ ] `SanitizerTest` 覆盖 cause chain(验证:构造一个 cause 链中含 key 的异常,scrub 后整链都脱敏)
- [ ] 启动行 / 启动日志全文 grep `api_key` 无匹配(AC30)

## 性能基线

- [ ] 冷启动到首个 `> ` 提示符时间 ≤ 3 秒(AC35)(验证:`time firstcode` 直到看到 `> `,秒表记录)
- [ ] 流式首 token 不被整批缓冲(已在 AC12 / Provider 走查覆盖)
- [ ] 长会话(本地构造 500 条历史)下,发起一次 stream 的请求构造时间 ≤ 500ms(AC36 拆分项)(验证:Session 包含 500 条时,Repl 主循环进入 stream 调用前的耗时 < 500ms;可写一个 micro-benchmark 测试)
- [ ] stdout 与 stderr 分离(AC36)(验证:启动后 `2>stderr.log`,主对话不污染 stderr;`1>stdout.log`,日志不污染流式输出)
- [ ] 100 轮连续对话不出现明显内存增长(验证:用 jconsole / VisualVM 或 `-Xmx` + Runtime.totalMemory 观察,稳定)

## 文档与可维护性

- [ ] `README.md` 包含:项目介绍、构建步骤、Win/Mac/Linux 启动方式、配置文件路径与示例、YAML 字段说明、常见错误(网络/配置)与解决思路
- [ ] `README.md` 含「如何切到 OpenAI / DeepSeek / Qwen / 智谱」的具体配置示例
- [ ] `README.md` 含「如何启用 extended thinking」的说明
- [ ] 公共类(`Config` / `Session` / `Provider` / `Repl` / `Output` / `SseParser`)有 Javadoc
- [ ] `git log` 自 T1 起每完成一组逻辑相关任务就有一次提交(粒度参考 T1-T26 段落边界)
- [ ] 提交信息含类型前缀(`build:` / `feat:` / `test:` / `fix:` / `docs:` / `chore:`)
- [ ] 代码风格符合项目编码规范:`grep -RE "TODO|FIXME|XXX" src/` 应无业务 TODO 残留(可有阶段性 TODO 但需在 commit message 解释)

## 自检

1. **spec 对齐**:spec.md 36 条 AC 全部映射为 checklist 条目(AC1→E1, AC2→配置启动, ..., AC36→性能与输出分离)✓
2. **可观测性**:每一条都是「做 X,看到 Y」,无需要逐行读代码才能验证的项 ✓
3. **耦合度**:不依赖具体实现细节(类名 `Repl`/`Session` 是抽象的);重命名类不会使 checklist 失效 ✓
4. **端到端**:Win11 端 9 个场景(E1-E9)+ 跨平台段 7 项 + 性能 5 项 + 安全 5 项 + 文档 7 项 ✓
