<template>
  <div class="login-container">
    <h2>{{ isLogin ? '登录' : '注册' }}</h2>
    <form @submit.prevent="handleSubmit">
      <input v-model="form.username" placeholder="用户名" required />
      <input type="password" v-model="form.password" placeholder="密码" required />
      <select v-model="form.role" :disabled="!isLogin">
        <option value="student">学生</option>
        <option v-if="isLogin" value="doctor">专家</option>
        <option v-if="isLogin" value="admin">管理员</option>
      </select>
      <button type="submit">{{ isLogin ? '登录' : '注册' }}</button>
      <span class="toggle" @click="isLogin = !isLogin">
        {{ isLogin ? '没有账号？去注册' : '已有账号？去登录' }}
      </span>
    </form>
    <div>{{ message }}</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiUrl } from '../apiClient'

const router = useRouter()
const isLogin = ref(true)
const message = ref('')
const form = ref({
  username: '',
  password: '',
  role: 'student'
})

async function handleSubmit() {
  if (!isLogin.value) {
    form.value.role = 'student'
  }
  const url = isLogin.value ? apiUrl('/auth/login') : apiUrl('/auth/register')
  message.value = ''
  
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form.value),
      cache: 'no-cache'  // 禁用缓存
    })
    
    if (!res.ok) {
      message.value = `网络错误: ${res.status} ${res.statusText}`
      console.error('HTTP错误:', res.status, res.statusText)
      return
    }
    
    const data = await res.json()
    console.log('API返回的数据:', data)
    
    if (data.success) {
      message.value = (isLogin.value ? '登录成功，用户类型：' : '注册成功，用户类型：') + data.role
      console.log('=== 登录/注册成功 ===')
      
      // 使用 sessionStorage 存储标签页级别的身份信息，避免多标签覆盖
      let tabId = sessionStorage.getItem('tabId')
      if (!tabId) {
        try { 
          tabId = self.crypto ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2)
        } catch (e) { 
          tabId = Date.now().toString(36) + Math.random().toString(36).slice(2) 
        }
        sessionStorage.setItem('tabId', tabId)
      }
      
      sessionStorage.setItem('username', form.value.username)
      sessionStorage.setItem('role', data.role)
      sessionStorage.setItem('userId', data.userId || '')
      if (data.token) sessionStorage.setItem('token', data.token)
      
      console.log('sessionStorage 中现在的值:')
      console.log('  username:', sessionStorage.getItem('username'))
      console.log('  role:', sessionStorage.getItem('role'))
      console.log('  userId:', sessionStorage.getItem('userId'))
      console.log('==================')
      
      setTimeout(() => { router.push('/main') }, 600)
    } else {
      message.value = data.message || '操作失败'
      console.error('操作失败:', data.message)
    }
  } catch (error) {
    message.value = '请求失败：' + error.message
    console.error('请求异常:', error)
  }
}
</script>

<style scoped>
.login-container { max-width: 320px; margin: 80px auto; box-shadow:0 0 8px #888; padding: 32px; border-radius: 10px;}
form { display: flex; flex-direction: column }
input,select,button { margin: 10px 0; padding: 8px }
.toggle { color: #1976d2; cursor: pointer; margin-top: 12px}
</style>

