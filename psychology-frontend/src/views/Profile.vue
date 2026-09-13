<template>
  <div class="profile-wrapper">
    <div class="back-btn" @click="goBack">← 返回主页</div>
    <div class="header-section">
      <h1>个人中心</h1>
      <p class="subtitle">管理您的个人信息</p>
    </div>

    <div class="profile-tabs">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'info' }"
        @click="setTab('info')"
      >
        我的信息
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'password' }"
        @click="setTab('password')"
      >
        修改密码
      </button>
      <button
        v-if="isDoctorOrAdmin"
        class="tab-btn"
        :class="{ active: activeTab === 'admin' }"
        @click="setTab('admin')"
      >
        用户管理
      </button>
    </div>

    <div class="profile-grid">
      <!-- 我的信息卡片 -->
      <div class="profile-card info-card" v-show="activeTab === 'info'">
        <div class="card-icon">👤</div>
        <h3>我的信息</h3>
        <div v-if="me" class="card-content">
          <div class="info-item">
            <span class="label">用户名</span>
            <span class="value">{{ me.username }}</span>
          </div>
          <div class="info-item">
            <span class="label">角色</span>
            <span class="value role" :class="`role-${me.role}`">{{ roleText(me.role) }}</span>
          </div>
          <div class="info-actions">
            <button @click="loadMe" class="btn-refresh">刷新</button>
            <button @click="logout" class="btn-logout">退出登录</button>
          </div>
        </div>
        <div v-else class="card-loading">
          <div class="spinner"></div>
        </div>
      </div>

      <!-- 修改密码卡片 -->
      <div class="profile-card password-card" v-show="activeTab === 'password'">
        <div class="card-icon">🔐</div>
        <h3>修改密码</h3>
        <form @submit.prevent="updateMyPassword" class="card-form">
          <input v-model="oldPassword" type="password" placeholder="原密码" required class="form-input" />
          <input v-model="newPassword" type="password" placeholder="新密码" required class="form-input" />
          <button type="submit" class="btn-save">保存</button>
          <div class="msg" :class="msgMy ? 'msg-show' : ''">{{ msgMy }}</div>
        </form>
      </div>

      <!-- 用户管理卡片（仅医生/管理员） -->
      <div v-if="isDoctorOrAdmin" class="profile-card admin-card" v-show="activeTab === 'admin'">
        <div class="card-icon">👥</div>
        <h3>用户管理</h3>
        <div class="card-content">
          <button @click="loadUsers" class="btn-load">加载用户列表</button>
          <div class="msg" :class="msgAdmin ? 'msg-show' : ''">{{ msgAdmin }}</div>
          <div v-if="users.length" class="user-list">
            <div v-for="u in users" :key="u.id" class="user-item">
              <div class="user-info">
                <span class="user-id">#{{ u.id }}</span>
                <input v-if="isAdmin" v-model="u._editUsername" class="edit-input" placeholder="用户名" />
                <span v-else class="username">{{ u.username }}</span>
              </div>
              <select v-if="isAdmin" v-model="u._editRole" class="role-select">
                <option value="student">学生</option>
                <option value="doctor">专家</option>
              </select>
              <span v-else class="role-badge" :class="`role-${u.role}`">{{ roleText(u.role) }}</span>
              <input v-if="isAdmin" v-model="u._editPassword" type="password" class="edit-input" placeholder="新密码(可选)" />
              <button v-if="isAdmin" @click="adminUpdateUser(u)" class="btn-update">保存</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getLoginInfo, clearLoginSession } from '../session'

const router = useRouter()
const me = ref(null)
const users = ref([])
const oldPassword = ref('')
const newPassword = ref('')
const msgMy = ref('')
const msgAdmin = ref('')

const loginInfo = getLoginInfo()
const username = loginInfo ? loginInfo.username : ''
const role = loginInfo ? loginInfo.role : ''

const isDoctorOrAdmin = role === 'doctor' || role === 'admin'
const isAdmin = role === 'admin'

const activeTab = ref('info')

function goBack() {
  router.push('/main')
}

function logout() {
  clearLoginSession()
  router.push('/login')
}

onMounted(() => {
  if (!username) {
    router.push('/login')
  } else {
    loadMe()
  }
})

function setTab(tab) {
  activeTab.value = tab
  if (tab === 'info') {
    loadMe()
  } else if (tab === 'admin') {
    loadUsers()
  }
}

function roleText(r) {
  if (r === 'student') return '学生'
  if (r === 'doctor') return '专家'
  if (r === 'admin') return '管理员'
  return r
}

async function loadMe() {
  if (!username) return
  const res = await fetch('/api/profile/me')
  const data = await res.json()
  if (data.success) {
    me.value = data.user
    msgMy.value = ''
  } else {
    msgMy.value = data.message || '加载个人信息失败'
  }
}

async function updateMyPassword() {
  msgMy.value = ''
  const res = await fetch('/api/profile/updateMe', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, oldPassword: oldPassword.value, newPassword: newPassword.value })
  })
  const data = await res.json()
  if (data.success) {
    msgMy.value = '密码修改成功'
    oldPassword.value = ''
    newPassword.value = ''
  } else {
    msgMy.value = data.message || '密码修改失败'
  }
}

async function loadUsers() {
  const res = await fetch('/api/profile/users')
  const data = await res.json()
  if (data.success) {
    users.value = data.users.map(u => ({ ...u, _editUsername: u.username, _editRole: u.role, _editPassword: '' }))
  } else {
    msgAdmin.value = data.message || '加载用户列表失败'
  }
}

async function adminUpdateUser(u) {
  const res = await fetch('/api/profile/admin/updateUser', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      operatorRole: role,
      targetId: u.id,
      username: u._editUsername,
      password: u._editPassword || undefined,
      role: u._editRole
    })
  })
  const data = await res.json()
  if (data.success) {
    msgAdmin.value = '用户修改成功'
    loadUsers()
  } else {
    msgAdmin.value = data.message || '修改失败'
  }
}
</script>

<style scoped>
.profile-wrapper {
  min-height: 100vh;
  background: linear-gradient(135deg, #fff3e8 0%, #ffe6d4 45%, #fff9f2 100%);
  padding: 40px 20px;
}

.header-section {
  text-align: center;
  margin-bottom: 50px;
  animation: slideDown 0.6s ease-out;
}

.header-section h1 {
  font-size: 2.5rem;
  color: #333;
  margin: 0;
  font-weight: 900;
  background: linear-gradient(135deg, #f59f6f 0%, #f08c5b 50%, #e07a4f 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.subtitle {
  color: #666;
  font-size: 1.1rem;
  margin-top: 10px;
}

.back-btn {
  display: inline-block;
  padding: 10px 16px;
  background: #fffaf6;
  border-radius: 8px;
  color: #e07a4f;
  cursor: pointer;
  font-weight: 600;
  margin-bottom: 20px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(240, 140, 91, 0.2);
  border: 1px solid rgba(240, 140, 91, 0.15);
}

.back-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(240, 140, 91, 0.3);
  background: #fff7f1;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 30px;
  max-width: 1200px;
  margin: 0 auto;
}

.profile-tabs {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.tab-btn {
  padding: 10px 20px;
  border-radius: 999px;
  border: 1px solid #f3c4a6;
  background: #fff7f1;
  color: #b65c33;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 6px 16px rgba(240, 140, 91, 0.15);
}

.tab-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 20px rgba(240, 140, 91, 0.2);
}

.tab-btn.active {
  background: linear-gradient(135deg, #f59f6f 0%, #f08c5b 100%);
  color: #fff;
  border-color: transparent;
}

.profile-card {
  background: #fffaf6;
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(240, 140, 91, 0.15);
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  animation: fadeIn 0.6s ease-out;
}

.profile-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.12);
  border-color: rgba(240, 140, 91, 0.3);
}

.card-icon {
  font-size: 3rem;
  margin-bottom: 16px;
}

.profile-card h3 {
  font-size: 1.5rem;
  color: #333;
  margin: 0 0 20px 0;
  font-weight: 700;
}

.info-card .card-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: #f8f9ff;
  border-radius: 8px;
}

.info-item .label {
  color: #666;
  font-weight: 600;
}

.info-item .value {
  color: #333;
  font-weight: 700;
}

.role {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 0.9rem;
}

.role-student {
  background: #e3f2fd;
  color: #1976d2;
}

.role-doctor {
  background: #f3e5f5;
  color: #7b1fa2;
}

.role-admin {
  background: #fff3e0;
  color: #e65100;
}

.info-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.btn-refresh,
.btn-logout {
  flex: 1;
  padding: 10px;
  color: white;
  border: none;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-refresh {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.btn-logout {
  background: linear-gradient(135deg, #ff8a80 0%, #ff5252 100%);
}

.btn-refresh:hover,
.btn-logout:hover {
  transform: scale(1.02);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.card-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-input {
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 1rem;
  transition: all 0.3s ease;
}

.form-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.btn-save {
  padding: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-save:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.msg {
  font-size: 0.9rem;
  color: #d32f2f;
  opacity: 0;
  transition: all 0.3s ease;
  margin-top: 8px;
}

.msg-show {
  opacity: 1;
}

.btn-load {
  width: 100%;
  padding: 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-load:hover {
  transform: scale(1.02);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.user-list {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 400px;
  overflow-y: auto;
}

.user-item {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 8px;
  align-items: center;
  padding: 12px;
  background: #f8f9ff;
  border-radius: 8px;
  border-left: 3px solid #667eea;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.user-id {
  font-size: 0.8rem;
  color: #999;
}

.edit-input,
.role-select {
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 0.9rem;
}

.role-badge {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 0.85rem;
  font-weight: 600;
}

.btn-update {
  padding: 6px 12px;
  background: #4caf50;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-update:hover {
  background: #45a049;
  transform: scale(1.05);
}

.card-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
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

@media (max-width: 768px) {
  .header-section h1 {
    font-size: 2rem;
  }

  .profile-grid {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .profile-card {
    padding: 24px;
  }

  .user-item {
    grid-template-columns: 1fr;
  }
}
</style>
