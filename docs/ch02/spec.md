# FirstCode 阶段一 Spec:交互式对话 REPL

## 背景

FirstCode 定位为终端 AI 编程助手(类似 Claude Code),长远目标是支持工具调用、文件操作、代码编辑等 agent 能力。本阶段只做**纯对话能力**,作为后续 agent 功能的承载底座。

当前没有代码,需要从零起步:
- 没有项目骨架(无构建脚本、无源码目录)
- 没有配置文件约定
- 没有 LLM 接入层
- 没有 TUI 入口

后续每个阶段都会在第一阶段铺设的底座上叠加能力,所以第一阶段必须把 Provider 抽象、会话管理、配置加载这些"地基"做对,避免后期返工。

## 目标

- 终端执行 FirstCode,进入交互式对话 REPL
- 用户输入一行文本,程序调用 LLM API,把回复**流式逐字**打印到终端
- 支持多轮对话,程序记住之前所有用户输入和助手回复,作为后续请求的上下文
- 支持 Anthropic Claude 与 OpenAI 兼容后端(OpenAI 官方、DeepSeek、Qwen、智谱等),通过配置文件切换
- 支持 Claude 的 extended thinking(开关可配置,默认关闭)
- 配置文件放在用户家目录 `~/.firstcode/config.yaml`,YAML 格式,核心字段:protocol、model、base_url、api_key
- 会话历史持久化到本地文件,重启后可续接
- Provider 层封装为统一接口,新增后端只需实现该接口

## 功能需求

### F1:启动入口
程序可执行(例如 `firstcode` 命令或 `java -jar` 启动),启动后:
- 读取并校验 `~/.firstcode/config.yaml`;若文件不存在、格式不合法或关键字段缺失,打印明确错误信息并退出(非零退出码)
- 校验通过后,进入交互式 REPL,显示欢迎信息(包含当前后端名称与模型名)

### F2:REPL 交互循环
REPL 持续运行,行为为:
- 显示输入提示符(例如 `> `)
- 读取用户输入的一行文本
- 若输入为空行,提示重新输入,不发起请求
- 若输入为内置命令(至少支持 `exit` 退出、`/clear` 清空屏幕、`/history` 查看当前会话历史、`/reset` 开启新会话),执行对应行为
- 其它输入视为对话消息,触发 F3-F5

### F3:流式回复
对话消息触发后,程序调用 LLM 并**实时逐字/逐 token** 把回复打印到终端:
- 不能等回复完整生成后再一次性输出
- 同一行内连续打印(无多余换行),回复结束后换行
- 流式过程中若发生网络中断或 API 错误,打印可读的错误信息并保留已收到的部分,返回 REPL 等待下一条输入(不退出)

### F4:多轮上下文
每次发起请求时,把当前会话的全部历史(user 与 assistant 消息)作为上下文发送给 LLM,使 LLM 能引用前文。

### F5:会话持久化
- 每次用户输入和助手完整回复(流式结束后)写入本地会话文件
- 默认路径为 `~/.firstcode/session.json`(具体格式在 plan.md 定义)
- 会话文件采用追加/原子写入,避免半写状态导致损坏

### F6:重启续接
启动时若 `~/.firstcode/session.json` 存在:
- 自动加载历史消息到内存
- 提示用户「已加载 N 条历史消息,继续对话或输入 /reset 开始新会话」
- 不强制清空,允许用户在已有上下文上继续提问

### F7:Provider 抽象
提供统一的 Provider 接口,定义至少以下能力:
- 接收消息列表 + 当前请求,返回**流式事件**(逐 token 数据 + 结束事件 + 错误事件)
- 不在 Provider 实现里直接处理 I/O、终端打印、配置解析
- 新增一个后端只需实现该接口,业务层无改动

### F8:Anthropic 后端
实现 F7 接口,基于 Anthropic Messages API + SSE:
- 正确处理 Anthropic 风格的 SSE 事件(`message_start` / `content_block_delta` / `message_stop` 等)
- 支持 system prompt(由调用方注入,本阶段可不暴露配置)
- 支持 `thinking` 字段(extended thinking)按 F10 处理

### F9:OpenAI 兼容后端
实现 F7 接口,基于 OpenAI Chat Completions 流式协议 + SSE:
- 正确处理 OpenAI 风格的 SSE 事件(`data: [DONE]` 终止、`choices[].delta.content` 增量等)
- 同时支持以下厂商(它们都对外暴露 OpenAI 兼容接口,通过 `base_url` + `model` 区分):
  - OpenAI 官方
  - DeepSeek(`https://api.deepseek.com/v1`)
  - 阿里通义千问 Qwen/DashScope(OpenAI 兼容 endpoint)
  - 智谱 GLM(OpenAI 兼容 endpoint)
  - MiniMax(OpenAI 兼容 endpoint,如有)
- 与 F8 行为对齐(同样的输入产生语义一致的输出,流式行为一致)

### F10:Extended Thinking
- 配置文件中可设置 `thinking: enabled | disabled`(可选,默认 disabled)
- 当 `protocol: anthropic` 且 thinking 启用时,请求里开启 extended thinking,流式事件中包含 thinking 内容(打印时与正文视觉可区分,例如行首标记 `[thinking]`)
- OpenAI 兼容后端忽略此字段(本阶段不模拟)

### F11:配置加载
读取 `~/.firstcode/config.yaml`,支持字段:
- `protocol`:字符串,取值 `anthropic` 或 `openai`
- `model`:字符串,模型名
- `base_url`:字符串,API base URL
- `api_key`:字符串,API 密钥
- 可选 `thinking`:字符串,`enabled` / `disabled`
- 字段类型错误或值非法时,打印明确错误并退出

### F12:Provider 切换
- `protocol` 切换时(例如从 anthropic 改为 openai)无需改代码,重启后自动用新后端
- 切换后端时,会话历史**保留**(不重置),由 Provider 自行处理跨后端消息兼容(必要时做归一化)

## 非功能需求

### N1:跨平台
- 在 macOS、Linux、Windows 三个平台均能正常启动与运行
- 文件路径使用跨平台方式访问家目录(`~/.firstcode/`),不硬编码分隔符
- 终端编码按 UTF-8 处理,支持中文输入与输出

### N2:安全
- `api_key` 不得出现在任何日志、错误信息、屏幕回显中(包含配置文件本身被打印的情况)
- 配置文件与会话文件的权限在 Unix 系统上设为 `0600`(仅当前用户可读写)
- 任何外部输入(用户消息、配置文件内容)不直接拼接到 shell 命令或代码执行路径中(本阶段不做代码执行,作为底线约束)

### N3:可测试性
- Provider 接口必须能脱离真实网络进行 mock 测试
- 会话序列化/反序列化、配置解析、消息归一化等纯逻辑模块必须可独立单测
- 核心业务逻辑(消息流转、上下文拼接、命令解析)的单元测试覆盖率 ≥ 80%

### N4:错误处理
- 启动期错误(配置缺失/非法):打印明确原因 + 修复建议示例,非零退出码
- 运行期错误(网络/API 异常):打印可读错误,REPL 不退出,用户可继续输入
- 运行期错误不向终端输出 Java 堆栈、绝对路径、源码位置等内部细节

### N5:可观测性(轻量)
- 启动时打印一行:后端协议、模型、是否启用 thinking(不打印 base_url 与 api_key)
- 流式过程中不刷日志,避免干扰输出
- 运行期错误发生时打印一行结构化错误(级别 + 原因)
- 日志默认写到 stderr,不影响 stdout 的流式输出

### N6:性能基线
- 启动到显示首个 prompt 的冷启动时间 ≤ 3 秒(不含首次网络请求)
- 流式首 token 延迟由 Provider 决定,本阶段不做硬性指标,但实现上不能引入额外整批缓冲(必须边收边打)
- 长会话(数百条历史)下,请求构造时间 ≤ 500ms

### N7:依赖与构建
- 使用主流 Java 构建工具(Gradle 或 Maven,在 plan.md 选定)
- 第三方依赖须为可信赖来源,无已知高危漏洞
- 锁定依赖版本,提交时附带 lockfile/equivalent
- JDK 版本锁定(在 plan.md 选定,需 ≥ 11)

### N8:可维护性
- 模块职责清晰,Provider、配置、会话、REPL、命令解析相互独立
- 公共类与方法有 Javadoc
- 关键设计决策在 plan.md / README 中有记录
- 遵循项目编码规范(命名、注释、函数长度、嵌套深度等)

## 不做的事

### 范围外(本期明确不做)
- **工具调用 (tool use)**:不支持 function calling、工具注册、工具执行
- **文件系统操作**:不读取/写入项目文件(只读/写 `~/.firstcode/` 下的自身配置与会话)
- **代码编辑**:不做 patch、diff、应用修改等能力
- **Agent 行为**:不做任务规划、子任务派发、循环自检等
- **多行输入**:REPL 只接受单行输入,需要换行的内容用户自行处理
- **Markdown / 代码高亮**:回复文本按原文输出,不做 ANSI 着色或语法高亮
- **快捷键 / 状态栏 / 加载动画**:不引入富 TUI 框架
- **会话历史的可视化查看 / 编辑 / 搜索**:用户需要查看可读 `~/.firstcode/session.json` 原文
- **多会话并存 / 会话切换 / 命名空间**:同一时刻只维护一个会话,`/reset` 即重开
- **多 Provider 并行 / 模型对比 / 投票**:一次只用一个后端
- **账号系统 / 登录 / 配额管理**:不做
- **配置远程拉取 / 同步 / 热更新**:不联网拉配置,改完文件需重启
- **插件系统 / 扩展点 / 钩子**:不暴露
- **服务端模式 / HTTP API / Web UI**:纯本地 CLI
- **国际化 / 多语言界面**:界面文案只用中文(用户向 LLM 提问的内容不受此限)
- **彩色输出**:本阶段不引入 ANSI 颜色
- **Prompt 缓存 / token 计数 / 成本估算**:不做

### 留待后续阶段
- 上述任何一项如未来需要,均作为新阶段独立评估(spec 流程重走)
- 与本阶段产生的会话文件格式、Provider 接口保持向后兼容(或显式提供迁移方案)

## 验收标准

> 每条均通过运行/观察来验证,聚焦系统行为。验证方式写在括号内。

### 启动与配置 (F1, F11)
- AC1: 首次启动(`~/.firstcode/config.yaml` 不存在)打印明确错误提示并退出码非 0(运行 `firstcode`,观察到错误信息与非 0 退出码)
- AC2: 配置合法时,启动后打印欢迎行,包含 `protocol` 与 `model` 字段值(运行命令,观察首行输出)
- AC3: 启动行不出现 `api_key` 与 `base_url`(运行命令,检查输出文本)
- AC4: 必填字段缺失或类型错误时,打印包含字段名的错误并退出(删除/改坏配置,运行验证)
- AC5: 协议字段取非法值(非 `anthropic`/非 `openai`)时,明确报错并退出(改坏配置,运行验证)

### REPL 与命令 (F2)
- AC6: 启动后持续显示输入提示符,可循环输入(连续输入两条,均得到回复)
- AC7: 输入空行时 REPL 重新提示,不发起网络请求(网络隔离环境验证:不出现新请求)
- AC8: 输入 `exit`(或等价退出命令)正常退出,退出码 0(运行命令,观察进程退出)
- AC9: `/clear` 清空屏幕(运行命令,观察屏幕状态)
- AC10: `/history` 打印当前会话所有 user/assistant 消息(输入几条后运行,观察输出)
- AC11: `/reset` 后会话历史被清空,后续提问不再引用之前内容(运行命令 + 提问验证)

### 流式回复 (F3)
- AC12: 回复逐 token 打印(可观察到:打印过程中字符是分批出现的,而不是一次刷出)(在网络受限/人为延迟下观察)
- AC13: 流式过程中无多余换行(同一行内连续输出,回复结束才换行)
- AC14: 模拟网络中断或返回 5xx,REPL 不退出,打印一行可读错误,继续等待输入(拔网/配置错误 endpoint 验证)
- AC15: 错误信息不包含 Java 堆栈、源码绝对路径(观察错误输出文本)

### 多轮与持久化 (F4, F5, F6)
- AC16: 第二轮提问时,LLM 能引用前序内容(在第一轮告诉它一个名字,第二轮问该名字,得到正确回答)
- AC17: 用户消息与助手回复在流式结束后被写入 `~/.firstcode/session.json`(查看文件内容)
- AC18: 进程被 kill -9 时,`session.json` 不出现半写状态(重复操作 + 查看文件结构)
- AC19: 重新启动且 `session.json` 存在时,自动加载历史并提示「已加载 N 条历史消息」(运行命令,观察启动输出)
- AC20: 加载历史后,新提问可引用历史内容(在两轮对话后重启,再问前文内容,LLM 正确回答)

### Provider 抽象 (F7, F8, F9, F12)
- AC21: 业务层(REPL/会话/命令)代码不出现 `anthropic` / `openai` / `deepseek` / `qwen` / `zhipu` / `MiniMax` 等任一具体厂商名(代码检索验证)
- AC22: Anthropic 后端能完成一次端到端对话(配置 protocol=anthropic,输入消息,得到回复)
- AC23: OpenAI 兼容后端能完成一次端到端对话(配置 protocol=openai + 任意 base_url,得到回复)
- AC24: OpenAI 兼容后端可分别对接至少 OpenAI / DeepSeek / Qwen / 智谱(用 4 套不同的 base_url + model 各跑通一次)
- AC25: 切换 `protocol` 字段(anthropic ↔ openai)重启后生效,会话历史不丢失(切换前后提问引用历史)
- AC26: 流式首 token 不会被缓冲成整批(代码走查:Provider 收到 chunk 后立刻转发到 sink,无中间聚合)

### Extended Thinking (F10)
- AC27: `thinking: enabled` + `protocol: anthropic` 时,回复中包含 `[thinking]` 段(运行验证,观察输出)
- AC28: `thinking` 缺省或 `disabled` 时,无 `[thinking]` 段(运行验证)
- AC29: `protocol: openai` 时,即使配 `thinking: enabled` 也不出现 `[thinking]` 段(运行验证)

### 非功能 (N2, N3, N5, N6, N7, N8)
- AC30: 任意错误信息与启动日志全文不出现 `api_key`(grep 验证)
- AC31: 配置文件与会话文件权限在 Unix 上为 `0600`(`ls -l` 验证)
- AC32: 单元测试套件通过,核心模块(Provider 抽象、配置解析、会话序列化、消息归一化)覆盖率 ≥ 80%(运行测试 + 覆盖率工具验证)
- AC33: Provider 可被 mock 后独立测试 REPL 行为(不发起真实网络,REPL 主流程通过 mock Provider 跑通)
- AC34: 项目可通过单一命令完成构建(例如 `./gradlew build` 或 `mvn package`),产出可执行产物(运行命令,获得 jar/可执行入口)
- AC35: 冷启动到首个 prompt 时间 ≤ 3 秒(用 `time` 计时)
- AC36: 日志走 stderr,stdout 仅含流式回复与用户输入(运行命令,分别检查两路输出)
