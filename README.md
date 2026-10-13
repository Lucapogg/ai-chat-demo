# AI 膳食咨询 Demo · LangChain4j + SSE 流式

> 配套投递作品 · 展示 LangChain4j Memory + RAG + 多模型路由 + 幻觉兜底 + SSE 流式 UI 全链路。

---

## 5 分钟跑起来

### 0. 前置
- Java 25+
- Maven 3.6+
- 一个**硅基流动** API key([注册送 2000 万 token 免费额度](https://siliconflow.cn))

### 1. 启动后端
```bash
cd backend
export "your-key"
export "your-key"
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


## License

MIT · 配套作品集投递用途
