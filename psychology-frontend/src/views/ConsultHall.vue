<template>
  <div class="consult-connect-wrapper">
    <div class="back-btn" @click="goBack">← 返回</div>
    <h2>咨询用户大厅</h2>

    <section class="status-panel">
      <div class="status-left">
        <h3>我的沟通状态</h3>
        <p>
          当前状态：
          <span :class="['status-tag', myStatus === 'busy' ? 'busy' : 'available']">
            {{ myStatus === 'busy' ? '正忙（拒绝沟通）' : '可沟通' }}
          </span>
        </p>
      </div>
      <button class="toggle-btn" @click="toggleMyStatus" :disabled="updatingStatus">
        {{ myStatus === 'busy' ? '切换为可沟通' : '切换为正忙' }}
      </button>
    </section>

    <section class="toolbar">
      <input
        v-model="keyword"
        @input="applyFilter"
        placeholder="按用户名筛选..."
      />
      <div class="tool-actions">
        <button @click="loadAllUsers">刷新全部用户</button>
        <button class="open-chat" @click="gotoChat">进入对话界面</button>
      </div>
    </section>

    <section class="users-panel">
      <h3>当前所有用户</h3>
      <div v-if="filteredUsers.length === 0" class="empty">暂无可显示用户</div>
      <ul v-else>
        <li v-for="user in filteredUsers" :key="user.id">
          <div class="user-main">
            <div class="avatar">👤</div>
            <div class="user-meta">
              <div class="name-row">
                <strong>{{ user.username }}</strong>
                <span class="role">{{ roleText(user.role) }}</span>
              </div>
              <div>
                状态：
                <span :class="['status-tag', user.status === 'busy' ? 'busy' : 'available']">
                  {{ user.status === 'busy' ? '正忙' : '可沟通' }}
                </span>
              </div>
            </div>
          </div>
          <button
            class="invite-btn"
            :disabled="isSelf(user) || sendingToId === user.id || user.status === 'busy'"
            @click="sendRequest(user)"
          >
            {{ isSelf(user) ? '自己' : user.status === 'busy' ? '对方正忙' : '发起沟通' }}
          </button>
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getLoginInfo } from '../session'

const router = useRouter()
const allUsers = ref([])
const filteredUsers = ref([])
const keyword = ref('')
const myStatus = ref('available')
const updatingStatus = ref(false)
const sendingToId = ref(null)

function goBack() {
  router.push('/consult')
}

function gotoChat() {
  router.push('/consult/chat')
}

function roleText(role) {
  if (role === 'student') return '学生'
  if (role === 'teacher' || role === 'doctor') return '专家'
  if (role === 'admin') return '管理员'
  return role
}

function isSelf(user) {
  const info = getLoginInfo()
  return info && user && user.id === info.userId
}

function applyFilter() {
  const key = keyword.value.trim().toLowerCase()
  if (!key) {
    filteredUsers.value = [...allUsers.value]
    return
  }
  filteredUsers.value = allUsers.value.filter(user =>
    String(user.username || '').toLowerCase().includes(key)
  )
}

async function loadAllUsers() {
  try {
    const res = await fetch('/api/chat/users/all')
    const data = await res.json()
    allUsers.value = Array.isArray(data) ? data : []
    applyFilter()
    const info = getLoginInfo()
    if (info) {
      const me = allUsers.value.find(item => item.id === info.userId)
      myStatus.value = (me && me.status) ? me.status : 'available'
    }
  } catch (e) {
    console.error('loadAllUsers error:', e)
    allUsers.value = []
    filteredUsers.value = []
  }
}

async function toggleMyStatus() {
  const info = getLoginInfo()
  if (!info) {
    alert('请先登录')
    return
  }
  const nextStatus = myStatus.value === 'busy' ? 'available' : 'busy'
  updatingStatus.value = true
  try {
    const res = await fetch('/api/chat/user/status', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: info.userId, status: nextStatus })
    })
    const data = await res.json()
    if (data && data.success) {
      myStatus.value = nextStatus
      await loadAllUsers()
    } else {
      alert((data && data.message) || '状态更新失败')
    }
  } catch (e) {
    console.error('toggleMyStatus error:', e)
    alert('状态更新失败：' + e.message)
  } finally {
    updatingStatus.value = false
  }
}

async function sendRequest(user) {
  const info = getLoginInfo()
  if (!info) {
    alert('请先登录')
    return
  }
  if (!user || !user.id || user.id === info.userId) return
  sendingToId.value = user.id
  try {
    const res = await fetch('/api/chat/request/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fromUserId: info.userId, toUserId: user.id })
    })
    const data = await res.json()
    if (!res.ok || !data || data.success === false) {
      alert((data && data.message) || '发起沟通失败')
      return
    }
    alert('沟通请求已发送')
  } catch (e) {
    console.error('sendRequest error:', e)
    alert('发起沟通失败：' + e.message)
  } finally {
    sendingToId.value = null
  }
}

onMounted(async () => {
  const info = getLoginInfo()
  if (!info) {
    router.push('/login')
    return
  }
  await loadAllUsers()
})
</script>

<style scoped>
.consult-connect-wrapper {
  min-height: 100vh;
  background: linear-gradient(135deg, #f0f5ff 0%, #fff5f0 100%);
  padding: 36px 20px;
}

h2 {
  text-align: center;
  margin: 0 0 24px;
  font-size: 2rem;
  color: #333;
}

.back-btn {
  display: inline-block;
  padding: 10px 16px;
  background: #fff;
  border-radius: 8px;
  color: #667eea;
  cursor: pointer;
  font-weight: 600;
  margin-bottom: 20px;
}

section {
  max-width: 940px;
  margin: 0 auto 18px;
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.07);
}

.status-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.status-left h3 {
  margin: 0 0 8px;
}

.status-left p {
  margin: 0;
  color: #555;
}

.status-tag {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-tag.available {
  background: #e8fff1;
  color: #1f8a4c;
}

.status-tag.busy {
  background: #fff0f0;
  color: #bf2f2f;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.toolbar input {
  flex: 1;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #ddd;
}

.tool-actions {
  display: flex;
  gap: 10px;
}

button {
  border: none;
  border-radius: 8px;
  padding: 10px 14px;
  cursor: pointer;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.open-chat {
  background: linear-gradient(135deg, #ff8a65 0%, #ff7043 100%);
}

ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border: 1px solid #efefef;
  border-radius: 10px;
  margin-bottom: 10px;
}

.user-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: #eef2ff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.role {
  font-size: 12px;
  color: #666;
}

.invite-btn:disabled,
.toggle-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.empty {
  color: #777;
}
</style>