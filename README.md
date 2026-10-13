# AI 膳食咨询 Demo · LangChain4j + SSE 流式

> 配套投递作品 · 展示 LangChain4j Memory + RAG + 多模型路由 + 幻觉兜底 + SSE 流式 UI 全链路。
> 雷火游戏宣发官网岗位(项目 02)的 AI 工程化能力,直接看这里运行。

---

## 5 分钟跑起来

### 0. 前置
- Java 25+
- Maven 3.6+
- 一个**硅基流动** API key([注册送 2000 万 token 免费额度](https://siliconflow.cn))

### 1. 启动后端
```bash
cd backend
export AI_PRIMARY_API_KEY=sk-your-siliconflow-key
export AI_SECONDARY_API_KEY=sk-your-siliconflow-key
mvn spring-boot:run
```
> 后端跑在 `http://localhost:8081`
> 默认模型:`DeepSeek-V3.2` + `Qwen2.5-72B-Instruct`,同走硅基流动

### 2. 启动前端
```bash
cd frontend
npm install
npm run dev
```
> 前端跑在 `http://localhost:5173`,访问即可

### 3. 演示路径
1. 打开 `http://localhost:5173`
2. 点顶部的「糖尿病」「花生过敏」「高血压」「痛风」任意预设
3. 观察顶部 tag 实时变化:
   - **模型**:每条消息自动选 DeepSeek-V3.2(长) / Qwen(短)
   - **RAG N 块**:从 `knowledge.txt` 召回 N 块相关知识
   - **短期 N 条 / 长期摘要 ✓**:会话记忆状态
   - **幻觉检查**:通过 ✓ / 触发 retry
   - **skip-retry ×N**:触发了几次
4. 输入「我对花生过敏,能推荐些高蛋白食物吗?」,观察回答中**不出现花生**的兜底过程
5. 输入**超过 10 条**消息,观察**长期摘要**自动生成

---

## 项目结构

```
ai-demo/
├─ backend/                  Spring Boot 3.4 + LangChain4j 1.0
│  ├─ pom.xml
│  ├─ src/main/java/com/demo/
│  │  ├─ AiDemoApplication.java        入口
│  │  ├─ config/
│  │  │  ├─ AiConfig.java              统一配置(prefix="ai")
│  │  │  ├─ ChatModelConfig.java       primary / secondary Bean
│  │  │  └─ CorsConfig.java
│  │  ├─ dto/
│  │  │  ├─ ChatRequest.java
│  │  │  └─ MessageSnapshot.java
│  │  ├─ memory/
│  │  │  └─ ChatMemoryStore.java       ★ 短期窗口 + 长期摘要
│  │  ├─ rag/
│  │  │  └─ KnowledgeBase.java         ★ 内存 BM25 检索 + knowledge.txt
│  │  ├─ service/
│  │  │  ├─ MultiModelRouter.java      ★ 短/长消息路由 + 故障降级
│  │  │  ├─ HallucinationGuard.java    ★ 过敏原后置校验 + skip-retry
│  │  │  └─ ChatService.java           ★ 整合编排 + SSE 流式
│  │  └─ controller/
│  │     └─ ChatController.java        POST /api/chat/stream(SSE)
│  └─ src/main/resources/
│     ├─ application.yml
│     └─ prompts/knowledge.txt          ★ 营养科权威知识文档
└─ frontend/                  Vue 3 + Vite + 自研 mobile UI
   ├─ package.json
   ├─ vite.config.js
   ├─ index.html
   └─ src/App.vue                       Vant 风格(自研,不引依赖)
```

---

## 核心代码对照(简历 ↔ 这里)

| 简历描述 | 真实实现 |
|----------|----------|
| LangChain4j Memory(短期 + 长期摘要) | `ChatMemoryStore.java` |
| RAG(Embedding / 向量召回 / 重排) | `KnowledgeBase.java` + `knowledge.txt`(BM25,生产换 Qdrant 即可) |
| JSON 结构化 + skip-retry | `HallucinationGuard.check()` + `ChatService.generateWithRetry()` |
| 多模型按场景路由 + 故障自动降级 | `MultiModelRouter.java` + `ChatService.generateWithRetry()` |
| SSE 流式 + 心跳 + 断线重连 | Spring `SseEmitter` + 前端 `fetch + reader.read()` |
| 流式 UI(打字机) | 前端逐 token 累加 + 闪烁光标 |

---

## 演示要点(面试用)

1. **Memory** —— 连续问 5 轮后看 `短期 N 条`,继续问到第 10 轮触发 `长期摘要 ✓`
2. **RAG** —— 打开 devtools network,看 `/api/chat/stream` 的 `event:rag` 块
3. **多模型路由** —— 短消息(预设「高血压」)走 secondary,长消息走 primary,看 `event:slot`
5. **幻觉兜底** —— 过敏原输入「花生」,问「推荐高蛋白食物」,触发 `skip-retry`,最终输出不含花生
6. **故障降级** —— 临时改 `application.yml` 把 primary key 改错,看 secondary 自动接管

---

## 雷火岗位对应

| 雷火 JD 要求 | 在这里 |
|--------------|--------|
| AI 工具(Claude Code / Codex / Cursor) | 整个项目用 Codex 协作产出 |
| OpenSpec / SDD 范式 | `application.yml` Spec-first 配置 → `service` 实现 → `controller` 暴露 |
| Prompt / Spec 结构化设计 | `systemPrompt` 模板 + Few-shot 留口子 |
| Skill 能力复用 | LangChain4j 生态组件复用 |
| Workflow / Agent 雏形 | router + skip-retry + fallback 三件套,构成简易 Agent 决策流 |

---

## 录屏建议(MP4 demo)

录屏工具:
- Windows 自带:`Win + G`(Xbox Game Bar)
- 推荐:[ScreenToGif](https://www.screentogif.com/)(免费,可生成 gif + mp4)

录屏脚本(60 秒):
1. (0-10s) 打开页面,介绍 demo
2. (10-30s) 问预设问题「糖尿病」,看打字机效果 + 顶部 tag 实时变化
3. (30-50s) 输入「我对花生过敏,推荐高蛋白食物」,展示 skip-retry + 兜底输出
4. (50-60s) 切换「强制 Qwen」开关,展示多模型路由

---

## License

MIT · 配套作品集投递用途