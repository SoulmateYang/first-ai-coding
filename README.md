# FirstCode

终端 AI 编程助手(类似 Claude Code)的 Java 实现。阶段一目标:交互式对话 REPL,支持流式回复、多轮上下文、本地会话持久化,可在 Anthropic Claude 与 OpenAI 兼容后端之间切换。

## 当前阶段

**ch02 — 交互式对话 REPL**(`docs/ch02/`)

已包含:配置加载、Provider 抽象、Anthropic / OpenAI 兼容实现、SSE 解析、Repl 主循环、命令解析、会话持久化、扩展思考(Anthropic)。

未包含(后续章节):工具调用、文件操作、代码编辑、Agent 行为等。

## 环境要求

- JDK 17(本项目用 Temurin 验证)
- Maven 3.6.3+(`mvn -v` 确认)

## 构建

Windows(PowerShell / Git Bash / cmd):

```bash
mvn clean package
```

macOS / Linux:

```bash
mvn clean package
```

仅编译不打包:

```bash
mvn compile
```

## 运行

### 准备配置

首次运行需创建配置文件 `~/.firstcode/config.yaml`(Windows 上为 `C:\Users\<user>\.firstcode\config.yaml`)。

启动程序,会打印 YAML 示例:

```bash
mvn package
java -jar target/firstcode-0.1.0-all.jar
```

按示例创建配置文件,字段含义见下节。

### 启动

Windows(打包后):

```bat
firstcode.bat
```

或直接:

```bat
java -Dfile.encoding=UTF-8 -jar target\firstcode-0.1.0-all.jar
```

macOS / Linux(打包后):

```bash
./firstcode.sh
```

或直接:

```bash
java -Dfile.encoding=UTF-8 -jar target/firstcode-0.1.0-all.jar
```

## 配置文件

位置:`~/.firstcode/config.yaml`

```yaml
# LLM 后端协议: anthropic 或 openai
protocol: anthropic

# 模型名
model: claude-3-5-sonnet-20241022

# API base URL
base_url: https://api.anthropic.com

# API 密钥(Anthropic: sk-ant-xxx / OpenAI: sk-xxx)
api_key: YOUR_API_KEY_HERE

# 可选:启用 extended thinking(仅 protocol: anthropic 生效)
# thinking: enabled
```

### 字段说明

| 字段 | 必填 | 取值 | 说明 |
|------|------|------|------|
| `protocol` | 是 | `anthropic` / `openai` | 后端协议 |
| `model` | 是 | 字符串 | 模型名 |
| `base_url` | 是 | URL | API 入口 |
| `api_key` | 是 | 字符串 | 密钥 |
| `thinking` | 否 | `enabled` / `disabled`(默认 `disabled`) | Anthropic 扩展思考开关 |

### 切换到不同后端

**OpenAI 官方:**

```yaml
protocol: openai
model: gpt-4o-mini
base_url: https://api.openai.com/v1
api_key: sk-xxx
```

**DeepSeek(OpenAI 兼容):**

```yaml
protocol: openai
model: deepseek-chat
base_url: https://api.deepseek.com/v1
api_key: sk-xxx
```

**通义千问 Qwen/DashScope(OpenAI 兼容):**

```yaml
protocol: openai
model: qwen-turbo
base_url: https://dashscope.aliyuncs.com/compatible-mode/v1
api_key: sk-xxx
```

**智谱 GLM(OpenAI 兼容):**

```yaml
protocol: openai
model: glm-4-flash
base_url: https://open.bigmodel.cn/api/paas/v4
api_key: xxx
```

### 启用 Extended Thinking

仅 `protocol: anthropic` 生效:

```yaml
protocol: anthropic
model: claude-3-5-sonnet-20241022
base_url: https://api.anthropic.com
api_key: sk-ant-xxx
thinking: enabled
```

启用后,思考过程会以 `[thinking]...` 行首标记的形式打印在正式回答之前。

## REPL 命令

| 命令 | 行为 |
|------|------|
| `<消息>` | 发送给 LLM,流式输出回复 |
| `/clear` | 清屏 |
| `/history` | 打印当前会话所有 user/assistant 消息 |
| `/reset` | 清空当前会话,开始新对话(历史写入 session 后重置) |
| `exit` / `quit` | 退出程序(退出码 0) |
| `Ctrl-D` | 退出(EOF) |
| `Ctrl-C` | 中断(退出码 130) |

## 会话持久化

所有对话自动写入 `~/.firstcode/session.json`,采用原子写(写临时文件后 `Files.move(ATOMIC_MOVE)`),即使进程被 kill -9 也不会留下半截 JSON。

重启程序会自动加载历史,可继续对话。输入 `/reset` 开始新会话。

## 测试

```bash
mvn test              # 全部测试
mvn verify            # 测试 + 覆盖率门禁(行覆盖率 < 80% 失败)
```

覆盖率报告:`target/site/jacoco/index.html`

## Gradle 备份

项目根目录还保留 `build.gradle.kts` / `settings.gradle.kts` / `gradlew` / `gradle/`,作为历史构建方式的备份。当前推荐使用 Maven。

## 常见错误

| 现象 | 原因 | 解决 |
|------|------|------|
| 启动报「配置文件不存在」 | `~/.firstcode/config.yaml` 缺失 | 按 stderr 中的示例创建 |
| 启动报「protocol 非法」 | 字段值非 `anthropic`/`openai` | 修正 `protocol` |
| 提问后「ERROR: network ...」 | 网络不通或 base_url 错 | 检查 `base_url`、代理、防火墙 |
| 提问后「HTTP 401」 | api_key 无效或过期 | 更新 `api_key` |
| Windows 终端中文乱码 | 控制台非 UTF-8 | 启动前 `chcp 65001`,或检查启动脚本的 `-Dfile.encoding=UTF-8` |
| 流式输出整批才出 | 罕见的 IDE/终端缓冲问题 | 用 Windows Terminal 替代 cmd.exe |

## 项目结构

```
src/main/java/com/firstcode/
├── app/         — Main 入口
├── config/      — Config + 校验
├── repl/        — Repl 主循环 + 命令解析
├── session/     — 内存 + 持久化
├── provider/    — LLM 抽象 + Anthropic / OpenAI 实现
├── net/         — HTTP 客户端 + SSE 解析
└── io/          — Output + 脱敏
```

详细规范见 `docs/ch02/`:

- `spec.md` — 做什么
- `plan.md` — 怎么做
- `task.md` — 按什么顺序做
- `checklist.md` — 做对了没

## 许可

内部项目。
