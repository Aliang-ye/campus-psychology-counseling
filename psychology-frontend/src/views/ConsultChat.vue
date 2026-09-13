<template>
  <div class="consult-service-wrapper">
    <div class="back-btn" @click="goBack">← 返回</div>
    <h2>一对一咨询</h2>
    <div class="top-actions">
      <button class="hall-btn" @click="gotoConnect">去用户大厅（发起新沟通）</button>
    </div>

    <section v-if="myRole === 'admin'" class="reviews-area">
      <h3>🧾 学生对教师评价</h3>
      <div v-if="reviews.length === 0" class="empty">暂无评价记录</div>
      <ul v-else>
        <li v-for="item in reviews" :key="item.id">
          <div class="review-main">
            <div><strong>评价学生：</strong>{{ item.reviewer_name }}（ID {{ item.reviewer_id }}）</div>
            <div><strong>被评教师：</strong>{{ item.teacher_name }}（ID {{ item.teacher_id }}）</div>
            <div><strong>评价内容：</strong>{{ item.content }}</div>
          </div>
          <div class="review-time">{{ formatTime(item.created_at) }}</div>
        </li>
      </ul>
    </section>

    <section class="pending-requests" v-if="pendingRequests.length">
      <h3>🔔 待处理邀请</h3>
      <ul>
        <li v-for="req in pendingRequests" :key="req.id">
          <div class="request-info">
            <div class="user-avatar">👤</div>
            <div class="request-details">
              <span class="req-from">来自：<strong>用户 ID {{ req.from_user_id }}</strong></span>
              <span class="req-time">{{ formatTime(req.created_at) }}</span>
            </div>
          </div>
          <div class="request-actions">
            <button @click="acceptRequest(req)" class="accept-btn">✓ 同意</button>
            <button @click="rejectRequest(req)" class="reject-btn">✕ 拒绝</button>
          </div>
        </li>
      </ul>
    </section>

    <section class="sessions-area" v-if="sessions.length">
      <h3>💬 活跃会话</h3>
      <ul>
        <li v-for="sess in sessions" :key="sess.id">
          <div class="session-info">
            <div class="user-avatar">👥</div>
            <div class="session-details">
              <span class="session-user">与 <strong>{{ getOtherUsername(sess) }}</strong> 的对话</span>
              <span class="session-id">会话 #{{ sess.id }}</span>
            </div>
          </div>
          <button @click="openSession(sess)" class="action-btn">📝 打开</button>
        </li>
      </ul>
    </section>

    <section class="chat-area" v-if="activeSession">
      <div class="chat-header">
        <div class="chat-partner-info">
          <div class="partner-avatar">👤</div>
          <div class="partner-details">
            <h3>正在与 <strong>{{ getOtherUsername(activeSession) }}</strong> 对话</h3>
            <span class="session-id">会话 #{{ activeSession.id }}</span>
          </div>
        </div>
        <button @click="closeSession" class="close-btn">✕ 关闭会话</button>
      </div>
      <div class="messages">
        <div v-for="(m, idx) in messages" :key="idx" :class="['msg', m.sender_id === me ? 'me' : 'them']">
          <div class="meta">{{ getSenderName(m.sender_id) }} · {{ formatTime(m.created_at) }}</div>
          <div class="content">{{ m.content }}</div>
        </div>
      </div>
      <div class="send">
        <input id="chat-message" name="message" v-model="outgoing" @keyup.enter="sendMessage" placeholder="输入消息并回车发送" autocomplete="off" />
        <button @click="sendMessage">发送</button>
      </div>

      <div v-if="canReviewActiveTeacher()" class="review-box">
        <h4>对本次咨询教师进行评价</h4>
        <textarea
          v-model="reviewContent"
          rows="3"
          maxlength="500"
          placeholder="请输入评价内容（仅管理员可见）"
        ></textarea>
        <div class="review-actions">
          <button class="action-btn" @click="submitReview" :disabled="!reviewContent.trim()">提交评价</button>
          <span class="review-msg" v-if="reviewMsg">{{ reviewMsg }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { wsUrl } from '../apiClient'

const router = useRouter()
const pendingRequests = ref([])
const sessions = ref([])
const activeSession = ref(null)
const ws = ref(null)
const me = ref(null)
const messages = ref([])
const outgoing = ref('')
const myRole = ref('')
const myUsername = ref('')
const reviewContent = ref('')
const reviewMsg = ref('')
const reviews = ref([])
const userRoleMap = ref({})

function goBack() { router.push('/main') }
function gotoConnect() { router.push('/consult/hall') }

function getLoginInfo() {
  try {
    const userId = sessionStorage.getItem('userId')
    const username = sessionStorage.getItem('username')
    const role = sessionStorage.getItem('role')
    const token = sessionStorage.getItem('token')
    const tabId = sessionStorage.getItem('tabId')
    
    if (!userId || userId === 'undefined' || userId === 'null') return null
    
    return { 
      userId: parseInt(userId), 
      username: username || '', 
      role: role || '', 
      token: token || '', 
      tabId: tabId || '' 
    }
  } catch (e) { 
    console.error('getLoginInfo error:', e)
    return null 
  }
}

function formatTime(dateStr) {
  return new Date(dateStr).toLocaleTimeString()
}

function roleText(role) {
  if (role === 'student') return '学生'
  if (role === 'doctor') return '专家'
  if (role === 'teacher') return '教师'
  if (role === 'admin') return '管理员'
  return role
}

function isTeacherLikeRole(role) {
  return role === 'teacher' || role === 'doctor'
}

function getOtherUsername(session) {
  if (!session) return '未知用户'
  const info = getLoginInfo()
  if (!info) return '未知用户'
  
  // 如果session有username_a和username_b字段
  if (session.username_a && session.username_b) {
    const otherUsername = session.user_a === info.userId ? session.username_b : session.username_a
    return otherUsername || '未知用户'
  }
  
  // 否则显示对方的ID
  const otherId = session.user_a === info.userId ? session.user_b : session.user_a
  return otherId ? `用户${otherId}` : '未知用户'
}

function getSenderName(senderId) {
  const info = getLoginInfo()
  if (!info) return senderId ? `用户${senderId}` : '未知'
  
  if (senderId === info.userId) {
    return '我'
  }
  
  // 如果activeSession有用户名信息
  if (activeSession.value) {
    if (activeSession.value.username_a && activeSession.value.user_a === senderId) {
      return activeSession.value.username_a
    }
    if (activeSession.value.username_b && activeSession.value.user_b === senderId) {
      return activeSession.value.username_b
    }
  }
  
  return senderId ? `用户${senderId}` : '未知'
}

async function acceptRequest(req) {
  const info = getLoginInfo()
  if (!info) { alert('请先登录'); return }
  if (!req || !req.id) { alert('请求信息无效'); return }
  try {
    const res = await fetch('/api/chat/request/accept', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ requestId: req.id, acceptorId: info.userId })
    })
    if (!res.ok) {
      console.warn('acceptRequest failed:', res.status)
      throw new Error('接受申请失败')
    }
    const data = await res.json()
    await loadPendingRequests()
    await loadSessions()
    if (data && data.sessionId) {
      // 重新加载sessions后找到对应的session对象
      const session = sessions.value.find(s => s.id === data.sessionId)
      if (session) {
        await openSession(session)
      } else {
        // 如果没找到，创建一个基本的session对象
        activeSession.value = { id: data.sessionId }
        await loadMessages(data.sessionId)
      }
    }
  } catch (e) { 
    console.error('acceptRequest error:', e)
    alert('接受失败：' + e.message) 
  }
}

async function rejectRequest(req) {
  const info = getLoginInfo()
  if (!info) { alert('请先登录'); return }
  if (!req || !req.id) { alert('请求信息无效'); return }
  try {
    const res = await fetch('/api/chat/request/reject', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ requestId: req.id, rejectorId: info.userId })
    })
    if (!res.ok) {
      console.warn('rejectRequest failed:', res.status)
      throw new Error('拒绝申请失败')
    }
    await loadPendingRequests()
  } catch (e) { 
    console.error('rejectRequest error:', e)
    alert('拒绝失败：' + e.message) 
  }
}

function setupWebSocket() {
  const info = getLoginInfo()
  if (!info) { console.warn('未登录，WS 不会连接'); return }
  
  if (!info.userId || isNaN(info.userId)) {
    console.warn('userId 无效，跳过 WS 连接')
    return
  }
  
  me.value = info.userId
  myRole.value = info.role || ''
  myUsername.value = info.username || ''
  try {
    const params = new URLSearchParams()
    if (info.token) params.set('token', info.token)
    if (info.tabId) params.set('tabId', info.tabId)
    ws.value = new WebSocket(`${wsUrl('/ws/chat')}?${params.toString()}`)
    ws.value.onopen = () => {
      console.log('[FE] ws open')
      loadPendingRequests()
    }
    ws.value.onmessage = evt => {
      try {
        if (!evt || !evt.data) {
          console.warn('[FE] ws message empty')
          return
        }
        const data = JSON.parse(evt.data)
        if (!data) return
        
        console.log('[FE] ws msg:', data)
        
        if (data.type === 'INVITE') {
          loadPendingRequests()
        } else if (data.type === 'ACCEPT') {
          loadSessions()
        } else if (data.type === 'REJECT') {
          loadPendingRequests()
        } else if (data.sessionId && data.content) {
          if (activeSession.value && Number(data.sessionId) === Number(activeSession.value.id)) {
            messages.value.push(data)
          }
        }
      } catch (e) { 
        console.error('[FE] ws msg parse error:', e)
      }
    }
    ws.value.onclose = () => console.log('[FE] ws closed')
    ws.value.onerror = e => console.error('[FE] ws error:', e)
  } catch (e) { console.error('[FE] ws setup error:', e) }
}

function getOtherUserId(session) {
  if (!session) return null
  const info = getLoginInfo()
  if (!info) return null
  return session.user_a === info.userId ? session.user_b : session.user_a
}

function getOtherUserRole(session) {
  if (!session) return null
  const info = getLoginInfo()
  if (!info) return null
  const roleFromSession = session.user_a === info.userId ? session.role_b : session.role_a
  if (roleFromSession) return roleFromSession

  const otherId = getOtherUserId(session)
  if (!otherId) return null
  return userRoleMap.value[otherId] || null
}

function canReviewActiveTeacher() {
  const info = getLoginInfo()
  if (!info || !activeSession.value) return false
  if (info.role !== 'student') return false
  return isTeacherLikeRole(getOtherUserRole(activeSession.value))
}

async function loadUserRoleMap() {
  try {
    const res = await fetch('/api/chat/users/all')
    if (!res.ok) return
    const data = await res.json()
    const map = {}
    if (Array.isArray(data)) {
      data.forEach(u => {
        if (u && u.id != null) {
          map[u.id] = u.role || ''
        }
      })
    }
    userRoleMap.value = map
  } catch (e) {
    console.error('loadUserRoleMap error:', e)
  }
}

async function submitReview() {
  const info = getLoginInfo()
  if (!info || !activeSession.value) { reviewMsg.value = '请先登录并打开会话'; return }
  const teacherId = getOtherUserId(activeSession.value)
  if (!teacherId) { reviewMsg.value = '未识别到教师信息'; return }
  const content = reviewContent.value.trim()
  if (!content) { reviewMsg.value = '评价内容不能为空'; return }
  try {
    const res = await fetch('/api/chat/review', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        reviewerId: info.userId,
        teacherId,
        content
      })
    })
    const data = await res.json()
    if (data.success) {
      reviewMsg.value = '评价提交成功'
      reviewContent.value = ''
    } else {
      reviewMsg.value = data.message || '评价提交失败'
    }
  } catch (e) {
    console.error('submitReview error:', e)
    reviewMsg.value = '评价提交失败'
  }
}

async function loadReviews() {
  const info = getLoginInfo()
  if (!info || info.role !== 'admin') {
    reviews.value = []
    return
  }
  try {
    const res = await fetch('/api/chat/reviews')
    const data = await res.json()
    if (data.success) {
      reviews.value = Array.isArray(data.reviews) ? data.reviews : []
    } else {
      reviews.value = []
    }
  } catch (e) {
    console.error('loadReviews error:', e)
    reviews.value = []
  }
}

async function loadPendingRequests() {
  const info = getLoginInfo()
  if (!info || !info.userId) return
  try {
    const res = await fetch('/api/chat/request/pending')
    if (!res.ok) {
      console.warn('loadPendingRequests failed:', res.status)
      return
    }
    const data = await res.json()
    pendingRequests.value = Array.isArray(data) ? data : []
    console.log('[FE] loaded pending requests:', pendingRequests.value)
  } catch (e) { 
    console.error('[FE] loadPendingRequests error:', e)
    pendingRequests.value = []
  }
}

async function loadSessions() {
  const info = getLoginInfo()
  if (!info || !info.userId) return
  try {
    const res = await fetch('/api/chat/sessions')
    if (!res.ok) {
      console.warn('loadSessions failed:', res.status)
      return
    }
    const data = await res.json()
    sessions.value = Array.isArray(data) ? data : []
    console.log('[FE] loaded sessions:', sessions.value)
  } catch (e) { 
    console.error('[FE] loadSessions error:', e)
    sessions.value = []
  }
}

async function openSession(sess) {
  if (!sess || !sess.id) {
    console.warn('openSession: invalid session')
    return
  }
  // 保存完整的session对象，包括用户名信息
  activeSession.value = { 
    id: sess.id,
    user_a: sess.user_a,
    user_b: sess.user_b,
    username_a: sess.username_a,
    username_b: sess.username_b,
    role_a: sess.role_a,
    role_b: sess.role_b,
    status: sess.status
  }
  console.log('打开会话:', activeSession.value)
  await loadMessages(sess.id)
}

async function loadMessages(sessionId) {
  try {
    if (!sessionId) {
      console.warn('sessionId 为空')
      messages.value = []
      return
    }
    const res = await fetch(`/api/chat/messages?sessionId=${sessionId}`)
    if (!res.ok) {
      console.warn('loadMessages failed:', res.status)
      messages.value = []
      return
    }
    const data = await res.json()
    messages.value = Array.isArray(data) ? data : []
    console.log('[FE] loaded messages:', messages.value)
  } catch (e) { 
    console.error('[FE] loadMessages error:', e)
    messages.value = []
  }
}

function sendMessage() {
  const info = getLoginInfo()
  if (!info) { alert('请先登录'); return }
  if (!activeSession.value || !activeSession.value.id) { alert('没有激活的会话'); return }
  if (!outgoing.value.trim()) return
  const payload = { sessionId: activeSession.value.id, content: outgoing.value }
  try {
    if (!ws.value) {
      console.warn('WebSocket 不存在')
      alert('WebSocket 连接不存在')
      return
    }
    if (ws.value.readyState !== WebSocket.OPEN) {
      console.warn('WebSocket 未打开，状态:', ws.value.readyState)
      alert('WebSocket 未打开，请稍后重试')
      return
    }
    // 立即添加到本地消息列表（作为用户发送的消息）
    if (Array.isArray(messages.value)) {
      messages.value.push({
        sender_id: me.value,
        content: outgoing.value,
        created_at: new Date().toISOString()
      })
    }
    ws.value.send(JSON.stringify(payload))
    console.log('[FE] sent message:', payload)
    outgoing.value = ''
  } catch (e) { 
    console.error('[FE] sendMessage error:', e)
    alert('发送消息失败：' + e.message)
  }
}

async function closeSession() {
  const info = getLoginInfo()
  if (!info) { alert('请先登录'); return }
  if (!activeSession.value || !activeSession.value.id) { alert('没有激活的会话'); return }
  
  try {
    const res = await fetch('/api/chat/session/close', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId: activeSession.value.id, userId: info.userId })
    })
    if (!res.ok) {
      console.warn('closeSession failed:', res.status)
      throw new Error('关闭会话失败')
    }
    alert('会话已关闭')
    activeSession.value = null
    messages.value = []
    await loadSessions()
  } catch (e) { 
    console.error('closeSession error:', e)
    alert('关闭会话失败：' + e.message) 
  }
}

onMounted(async () => {
  try {
    setupWebSocket()
    await loadUserRoleMap()
    await loadPendingRequests()
    await loadSessions()
    await loadReviews()
  } catch (e) {
    console.error('[ConsultChat] onMounted error:', e)
  }
})
onBeforeUnmount(() => { if (ws.value) ws.value.close() })
</script>

<style scoped>
.consult-service-wrapper {
  min-height: 100vh;
  background: linear-gradient(135deg, #f0f5ff 0%, #fff5f0 100%);
  padding: 40px 20px;
}

.consult-service-wrapper h2 {
  text-align: center;
  font-size: 2.2rem;
  color: #333;
  margin-bottom: 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.top-actions {
  max-width: 900px;
  margin: 0 auto 16px;
  display: flex;
  justify-content: flex-end;
}

.hall-btn {
  background: linear-gradient(135deg, #ff8a65 0%, #ff7043 100%);
}

.tips {
  max-width: 900px;
  margin: 0 auto 18px;
  color: #666;
  text-align: center;
}

.back-btn {
  display: inline-block;
  padding: 10px 16px;
  background: white;
  border-radius: 8px;
  color: #667eea;
  cursor: pointer;
  font-weight: 600;
  margin-bottom: 30px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
}

.back-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.3);
}

section {
  background: white;
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 30px;
  max-width: 900px;
  margin-left: auto;
  margin-right: auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.05);
  animation: fadeIn 0.6s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

section h3 {
  font-size: 1.5rem;
  color: #333;
  margin-top: 0;
  margin-bottom: 24px;
  font-weight: 700;
  border-bottom: 2px solid #667eea;
  padding-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-box {
  display: flex;
  gap: 12px;
  margin: 0;
}

.search-box input {
  flex: 1;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 1rem;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.search-box input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.search-box button {
  padding: 12px 32px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.3s ease;
}

.search-box button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.results ul,
.pending-requests ul,
.sessions-area ul,
.reviews-area ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.results li,
.pending-requests li,
.sessions-area li,
.reviews-area li {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  margin-bottom: 12px;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  background: #fafbff;
}

.results li:hover,
.pending-requests li:hover,
.sessions-area li:hover,
.reviews-area li:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.15);
  background: white;
  border-color: #667eea;
}

.review-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #333;
}

.review-time {
  font-size: 0.85rem;
  color: #666;
  white-space: nowrap;
}

.user-info,
.request-info,
.session-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.user-avatar,
.partner-avatar {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.user-details,
.request-details,
.session-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.uname,
.session-user {
  font-size: 1.05rem;
  font-weight: 600;
  color: #333;
}

.urole,
.session-id,
.req-time {
  font-size: 0.85rem;
  color: #666;
}

.req-from {
  font-size: 0.95rem;
  color: #333;
}

.request-actions {
  display: flex;
  gap: 8px;
}

.action-btn,
.accept-btn,
.reject-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
  font-size: 0.95rem;
  transition: all 0.3s ease;
}

.action-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.accept-btn {
  background: linear-gradient(135deg, #00b894 0%, #00cec9 100%);
  color: white;
}

.accept-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 184, 148, 0.4);
}

.reject-btn {
  background: linear-gradient(135deg, #ff7675 0%, #d63031 100%);
  color: white;
}

.reject-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 118, 117, 0.4);
}

.uname {
  font-weight: 700;
  color: #333;
  flex: 1;
}

.urole {
  color: #999;
  margin-left: 8px;
  font-size: 0.9rem;
}

.req-info {
  flex: 1;
  color: #666;
}

button {
  padding: 8px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  font-size: 0.9rem;
  transition: all 0.3s ease;
}

button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.chat-area {
  display: flex;
  flex-direction: column;
  height: 600px;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  border-bottom: 2px solid #f0f0f0;
  padding-bottom: 16px;
  background: linear-gradient(135deg, #f8f9ff 0%, #fff8f9 100%);
  padding: 20px;
  margin: -32px -32px 20px -32px;
  border-radius: 16px 16px 0 0;
  border-bottom: 3px solid #667eea;
}

.chat-partner-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.partner-avatar {
  width: 52px;
  height: 52px;
}

.partner-details h3 {
  margin: 0;
  padding: 0;
  border: none;
  font-size: 1.2rem;
  color: #333;
}

.partner-details .session-id {
  font-size: 0.85rem;
  color: #666;
}

.chat-header h3 {
  margin: 0;
  font-size: 1.3rem;
  border: none;
  padding: 0;
  color: #333;
}

.close-btn {
  background: linear-gradient(135deg, #f44336 0%, #da190b 100%);
  color: white;
  padding: 10px 20px;
}

.close-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(244, 67, 54, 0.4);
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fafbff;
  border-radius: 10px;
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.msg {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  word-wrap: break-word;
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.msg.me {
  margin-left: auto;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-bottom-right-radius: 4px;
}

.msg.them {
  background: white;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border: 1px solid #e8e8e8;
}

.meta {
  font-size: 0.75rem;
  opacity: 0.8;
  margin-bottom: 6px;
  font-weight: 600;
}

.msg.them .meta {
  color: #667eea;
}

.msg .content {
  line-height: 1.5;
  font-size: 0.95rem;
}

.send {
  display: flex;
  gap: 12px;
}

.send input {
  flex: 1;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-family: inherit;
  font-size: 1rem;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.send input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.send button {
  padding: 12px 32px;
}

.review-box {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #e7e7e7;
  border-radius: 10px;
  background: #fffdf7;
}

.review-box h4 {
  margin: 0 0 10px;
  color: #333;
}

.review-box textarea {
  width: 100%;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 10px;
  box-sizing: border-box;
  resize: vertical;
}

.review-actions {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.review-msg {
  color: #2e7d32;
  font-size: 0.9rem;
}

@media (max-width: 768px) {
  .consult-service-wrapper {
    padding: 20px 16px;
  }

  .consult-service-wrapper h2 {
    font-size: 1.8rem;
    margin-bottom: 30px;
  }

  section {
    padding: 20px;
    border-radius: 12px;
  }

  .search-box {
    flex-direction: column;
  }

  .results li,
  .pending-requests li,
  .sessions-area li,
  .reviews-area li {
    flex-direction: column;
    align-items: flex-start;
  }

  .msg {
    max-width: 100%;
  }

  .send {
    flex-direction: column;
  }

  .send button {
    width: 100%;
  }

  .chat-area {
    height: auto;
    max-height: 600px;
  }
}
</style>
