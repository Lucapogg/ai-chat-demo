<template>
  <el-container class="layout-container" style="height: 100vh">
    <!-- 侧边栏:Logo + 对话历史 -->
    <el-aside width="240px" class="sidebar">
      <div class="sidebar-brand">
        <el-icon :size="22" color="#d8ac4f"><ChatLineRound /></el-icon>
        <h1>Healthy<span>Diet</span> AI</h1>
      </div>
      <div class="sidebar-brand" style="padding: 4px 18px 14px;">
        <span class="tag">LangChain4j Demo</span>
      </div>

      <el-menu class="sidebar-menu" :default-active="'current'">
        <el-menu-item index="current" @click="onNewSession">
          <el-icon><EditPen /></el-icon>
          <span>新建对话</span>
        </el-menu-item>
      </el-menu>

      <div style="padding: 0 14px; color: rgba(255,255,255,0.4); font-size: 11px; margin-top: 16px; text-transform: uppercase; letter-spacing: 1px;">
        历史会话
      </div>
      <div class="session-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          :class="['session-item', { active: s.id === sessionId }]"
          @click="onSwitchSession(s.id)"
        >
          <el-icon :size="13" class="icon"><ChatLineRound /></el-icon>
          <span style="flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">{{ s.title }}</span>
        </div>
        <div v-if="sessions.length === 1" class="session-item" style="cursor:default; opacity:0.5;">
          <el-icon :size="13" class="icon"><InfoFilled /></el-icon>
          <span>暂无历史对话</span>
        </div>
      </div>

      <div class="sidebar-meta" style="margin-top: auto;">
        <div class="label">本机后端</div>
        <div class="value">localhost:8081</div>
      </div>
    </el-aside>

    <!-- 主区 -->
    <el-container>
      <el-header class="layout-header" height="56px">
        <div class="status-tags">
          <el-tag v-if="modelSession" :type="modelSession === 'PRIMARY' ? 'success' : 'info'" size="small" effect="dark">
            模型:{{ modelSession }}
          </el-tag>
          <el-tag v-if="ragInfo" type="warning" size="small">RAG {{ ragInfo.topK }} 块 · {{ ragInfo.chars }} 字</el-tag>
          <el-tag v-if="memoryInfo" type="info" size="small">短期 {{ memoryInfo.shortTermSize }} 条</el-tag>
          <el-tag v-if="memoryInfo && memoryInfo.hasLongTermSummary" type="warning" size="small" effect="dark">长期摘要已生成</el-tag>
          <el-tag :type="hallucinationPassed === null ? 'info' : (hallucinationPassed ? 'success' : 'danger')" size="small">
            幻觉:{{ hallucinationPassed === null ? 'N/A' : (hallucinationPassed ? '通过' : '触发 retry') }}
          </el-tag>
          <el-tag v-if="attempts > 0" type="danger" size="small">skip-retry ×{{ attempts }}</el-tag>
        </div>
        <div>
          <el-tag type="success" size="small" effect="plain">Spring Boot + LangChain4j 1.22 + SSE</el-tag>
        </div>
      </el-header>

      <el-main class="chat-container" style="padding: 0;">
        <div ref="scrollRef" class="message-list">
          <div v-if="messages.length === 0 && !loading" class="empty-state">
            <el-icon :size="48" color="#94a3b8"><ChatLineRound /></el-icon>
            <h3>开始 AI 膳食咨询</h3>
            <p>点击下方预设快速体验,或在下方输入你的问题</p>
          </div>

          <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
            <el-avatar :size="36" :style="m.role === 'user' ? 'background: var(--primary)' : 'background: var(--accent)'">
              {{ m.role === 'user' ? '我' : 'AI' }}
            </el-avatar>
            <div class="bubble">
              <div>{{ m.content }}<span v-if="m.streaming" class="cursor">|</span></div>
              <div class="markdown-meta" v-if="m.role === 'assistant' && (m.model || m.attempts > 0)">
                <el-tag v-if="m.model" size="small" :type="m.model === 'PRIMARY' ? 'success' : 'info'" effect="plain">{{ m.model }}</el-tag>
                <el-tag v-if="m.attempts > 0" size="small" type="danger" effect="plain">skip-retry ×{{ m.attempts }}</el-tag>
              </div>
            </div>
          </div>

          <div v-if="loading && (messages.length === 0 || messages[messages.length - 1].role !== 'assistant')" class="msg assistant typing">
            <el-avatar :size="36" style="background: var(--accent)">AI</el-avatar>
            <div class="bubble">
              <span class="dot"></span><span class="dot"></span><span class="dot"></span>
            </div>
          </div>
        </div>

        <div class="chat-input-area">
          <div class="chat-presets">
            <el-button size="small" plain @click="preset('我有糖尿病,适合吃什么水果?')">
              <el-icon><Goblet /></el-icon>糖尿病
            </el-button>
            <el-button size="small" plain @click="preset('我对花生过敏,能推荐些高蛋白食物吗?')">
              <el-icon><Warning /></el-icon>花生过敏
            </el-button>
            <el-button size="small" plain @click="preset('高血压患者的早餐推荐')">
              <el-icon><FirstAidKit /></el-icon>高血压
            </el-button>
            <el-button size="small" plain @click="preset('痛风患者能喝啤酒吗?')">
              <el-icon><ColdDrink /></el-icon>痛风
            </el-button>
          </div>
          <div class="chat-input-row">
            <textarea
              v-model="input"
              placeholder="输入饮食问题,如:我有糖尿病,可以吃米饭吗?(Enter 发送,Shift+Enter 换行)"
              rows="2"
              :disabled="loading"
              @keydown.enter.exact.prevent="onSend"
            ></textarea>
            <el-button type="primary" :loading="loading" @click="onSend" style="height: 40px;">
              <el-icon><Promotion /></el-icon>
              发送
            </el-button>
          </div>
        </div>

        <div class="chat-controls">
          <span class="label">高级:</span>
          <el-checkbox v-model="useRag" size="small">启用 RAG</el-checkbox>
          <el-checkbox v-model="forceSecondary" size="small">强制 Qwen</el-checkbox>
          <span class="label">过敏原:</span>
          <el-input v-model="allergens" placeholder="如:花生,海鲜" size="small" class="allergens" />
          <el-button size="small" plain @click="onClear">
            <el-icon><Delete /></el-icon>清空会话
          </el-button>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, nextTick } from 'vue'

const sessionId = ref('demo-' + Math.random().toString(36).slice(2, 8))
const sessions = ref([{ id: sessionId.value, title: '当前对话' }])
const messages = ref([])
const input = ref('')
const loading = ref(false)
const useRag = ref(true)
const forceSecondary = ref(false)
const allergens = ref('')

const modelSession = ref(null)
const ragInfo = ref(null)
const memoryInfo = ref(null)
const hallucinationPassed = ref(null)
const attempts = ref(0)

const scrollRef = ref(null)

function preset(q) { input.value = q }

function onNewSession() {
  if (messages.value.length === 0) return
  // 把当前会话加入历史
  const firstUser = messages.value.find(m => m.role === 'user')
  const title = firstUser ? firstUser.content.slice(0, 12) + (firstUser.content.length > 12 ? '...' : '') : '对话 ' + (sessions.value.length)
  const oldId = sessionId.value
  const oldMsgs = messages.value
  sessions.value = [{ id: oldId, title }, ...sessions.value.filter(s => s.id !== oldId)]
  // 新建会话
  sessionId.value = 'demo-' + Math.random().toString(36).slice(2, 8)
  sessions.value.unshift({ id: sessionId.value, title: '新对话' })
  messages.value = []
  modelSession.value = null
  ragInfo.value = null
  memoryInfo.value = null
  hallucinationPassed.value = null
  attempts.value = 0
}

function onSwitchSession(id) {
  if (id === sessionId.value) return
  sessionId.value = id
  messages.value = []
  modelSession.value = null
  ragInfo.value = null
  memoryInfo.value = null
  hallucinationPassed.value = null
  attempts.value = 0
}

async function onSend() {
  const text = input.value.trim()
  if (!text || loading.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  scrollToBottom()
  loading.value = true
  modelSession.value = null
  ragInfo.value = null
  hallucinationPassed.value = null
  attempts.value = 0

  const assistantMsg = { role: 'assistant', content: '', streaming: true, model: null, attempts: 0 }
  messages.value.push(assistantMsg)
  scrollToBottom()

  try {
    const res = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        sessionId: sessionId.value,
        message: text,
        allergens: allergens.value,
        useRag: useRag.value,
        forceSecondary: forceSecondary.value
      })
    })

    if (!res.ok || !res.body) {
      assistantMsg.content = '连接后端失败,请确认服务在 8081 运行'
      assistantMsg.streaming = false
      loading.value = false
      return
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      const events = buf.split('\n\n')
      buf = events.pop() || ''
      for (const ev of events) {
        if (!ev.trim()) continue
        const lines = ev.split('\n')
        let name = 'message', data = ''
        for (const ln of lines) {
          if (ln.startsWith('event:')) name = ln.slice(6).trim()
          else if (ln.startsWith('data:')) data += ln.slice(5).trim()
        }
        if (!data) continue
        try { handleEvent(name, JSON.parse(data), assistantMsg) } catch (e) {}
      }
    }
    assistantMsg.streaming = false
  } catch (e) {
    assistantMsg.content = '出错了:' + e.message
    assistantMsg.streaming = false
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function handleEvent(name, payload, msg) {
  switch (name) {
    case 'model': modelSession.value = payload.slot; msg.model = payload.slot; break
    case 'rag': ragInfo.value = payload; break
    case 'token':
      msg.content += payload.text
      scrollToBottom()
      break
    case 'hallucination':
      hallucinationPassed.value = payload.passed
      break
    case 'attempt':
      attempts.value = Math.max(attempts.value, (payload.attempt || 0) + 1)
      msg.attempts = attempts.value
      break
    case 'retry':
      attempts.value = Math.max(attempts.value, payload.attempt || 0)
      msg.attempts = attempts.value
      break
    case 'done':
      memoryInfo.value = payload.memory
      break
    case 'error':
      msg.content = '错误:' + payload.message
      break
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  })
}

async function onClear() {
  await fetch('/api/chat/clear/' + sessionId.value, { method: 'POST' })
  messages.value = []
  modelSession.value = null
  ragInfo.value = null
  memoryInfo.value = null
  hallucinationPassed.value = null
  attempts.value = 0
}
</script>

<style scoped>
.layout-container { overflow: hidden; }
.sidebar { display: flex; flex-direction: column; }
.sidebar-menu { background: transparent !important; flex-shrink: 0; }
</style>
