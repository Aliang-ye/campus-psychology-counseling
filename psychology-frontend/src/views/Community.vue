<template>
  <div class="community-wrapper">
    <div class="back-btn" @click="goBack">← 返回</div>
    <h2>社区讨论</h2>

    <div class="community-tabs">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'publish' }"
        @click="setTab('publish')"
      >
        发布新帖
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'list' }"
        @click="setTab('list')"
      >
        帖子列表
      </button>
      <button
        v-if="role === 'admin'"
        class="tab-btn"
        :class="{ active: activeTab === 'manage' }"
        @click="setTab('manage')"
      >
        置顶/锁帖管理
      </button>
      <button
        v-if="role === 'admin'"
        class="tab-btn"
        :class="{ active: activeTab === 'audit' }"
        @click="setTab('audit')"
      >
        帖子审核
      </button>
    </div>

    <!-- 发布帖子区域 -->
    <section class="card publish-section" v-show="activeTab === 'publish'">
      <h3>发布新帖</h3>
      <form @submit.prevent="publishPost">
        <input 
          v-model="newPost.title" 
          placeholder="帖子标题"
          maxlength="100"
          required 
        />
        <textarea 
          v-model="newPost.content" 
          placeholder="帖子内容"
          required
          rows="5"
        ></textarea>
        <label class="anonymous-label">
          <input type="checkbox" v-model="newPost.isAnonymous" />
          匿名发帖（其他用户看不到您的名字）
        </label>
        <button type="submit">发布帖子</button>
      </form>
      <div class="msg" v-if="msgPublish">{{ msgPublish }}</div>
    </section>

    <!-- 帖子列表区域 -->
    <section class="card posts-section" v-show="activeTab === 'list'">
      <h3>帖子列表</h3>
      <button @click="loadPosts" class="refresh-btn">刷新</button>
      
      <div v-if="posts.length === 0" class="empty">暂无帖子</div>
      
      <div 
        v-for="post in posts" 
        :key="post.id" 
        class="post-item"
        :class="{ pinned: post.isPinned, locked: post.isLocked }"
      >
        <div class="post-header">
          <div class="post-title-section">
            <span v-if="post.isPinned" class="badge pinned-badge">置顶</span>
            <span v-if="post.isLocked" class="badge locked-badge">已锁定</span>
            <h4 @click="openPost(post)" class="clickable">{{ post.title }}</h4>
          </div>
          <div class="post-actions" v-if="canManagePost(post)">
            <button 
              v-if="role === 'admin'" 
              class="link-btn" 
              @click.stop="togglePin(post)"
            >
              {{ post.isPinned ? '取消置顶' : '置顶' }}
            </button>
            <button 
              v-if="role === 'admin'" 
              class="link-btn" 
              @click.stop="toggleLock(post)"
            >
              {{ post.isLocked ? '解锁' : '锁帖' }}
            </button>
            <button 
              v-if="canDeletePost(post)" 
              class="link-btn danger" 
              @click.stop="deletePost(post)"
            >
              删除
            </button>
          </div>
        </div>
        <p class="post-meta">
          <span>作者：{{ post.isAnonymous ? '匿名用户' : post.username + '（' + roleText(post.role) + '）' }}<span v-if="role === 'admin' && post.isAnonymous" class="admin-real-name"> [真实：{{ post.username }}（{{ roleText(post.role) }}）]</span></span>
          <span>回复数：{{ post.replyCount }}</span>
          <span>{{ formatDate(post.createdAt) }}</span>
        </p>
      </div>
      
      <div class="msg" v-if="msgPosts">{{ msgPosts }}</div>
    </section>

    <!-- 管理区域（管理员） -->
    <section v-if="role === 'admin'" class="card manage-section" v-show="activeTab === 'manage'">
      <h3>管理员管理区</h3>
      <button @click="loadPosts" class="refresh-btn">刷新</button>

      <div v-if="posts.length === 0" class="empty">暂无帖子</div>

      <div
        v-for="post in posts"
        :key="post.id"
        class="post-item manage-item"
        :class="{ pinned: post.isPinned, locked: post.isLocked }"
      >
        <div class="post-header">
          <div class="post-title-section">
            <span v-if="post.isPinned" class="badge pinned-badge">置顶</span>
            <span v-if="post.isLocked" class="badge locked-badge">已锁定</span>
            <h4 @click="openPost(post)" class="clickable">{{ post.title }}</h4>
          </div>
          <div class="post-actions">
            <button class="link-btn" @click.stop="togglePin(post)">
              {{ post.isPinned ? '取消置顶' : '置顶' }}
            </button>
            <button class="link-btn" @click.stop="toggleLock(post)">
              {{ post.isLocked ? '解锁' : '锁帖' }}
            </button>
            <button class="link-btn danger" @click.stop="deletePost(post)">删除</button>
          </div>
        </div>
        <p class="post-meta">
          <span>作者：{{ post.isAnonymous ? '匿名用户' : post.username + '（' + roleText(post.role) + '）' }}<span v-if="role === 'admin' && post.isAnonymous" class="admin-real-name"> [真实：{{ post.username }}（{{ roleText(post.role) }}）]</span></span>
          <span>回复数：{{ post.replyCount }}</span>
          <span>{{ formatDate(post.createdAt) }}</span>
        </p>
      </div>

      <div class="msg" v-if="msgPosts">{{ msgPosts }}</div>
    </section>

    <!-- 审核区域（管理员） -->
    <section v-if="role === 'admin'" class="card audit-section" v-show="activeTab === 'audit'">
      <h3>待审核帖子</h3>
      <button @click="loadPendingPosts" class="refresh-btn">刷新待审核</button>

      <div v-if="pendingPosts.length === 0" class="empty">暂无待审核帖子</div>

      <div v-for="post in pendingPosts" :key="post.id" class="post-item pending">
        <div class="post-header">
          <div class="post-title-section">
            <span class="badge pending-badge">待审核</span>
            <h4>{{ post.title }}</h4>
          </div>
        </div>
        <p class="post-content-preview">{{ post.content.substring(0, 100) }}{{ post.content.length > 100 ? '...' : '' }}</p>
        <p class="post-meta">
          <span>作者：{{ post.isAnonymous ? '匿名用户' : post.username + '（' + roleText(post.role) + '）' }}<span v-if="role === 'admin' && post.isAnonymous" class="admin-real-name"> [真实：{{ post.username }}（{{ roleText(post.role) }}）]</span></span>
          <span>{{ formatDate(post.createdAt) }}</span>
        </p>
        <textarea v-model="post._comment" placeholder="审核备注（可选）" class="audit-textarea"></textarea>
        <div class="audit-actions">
          <button class="approve" @click="auditPost(post, 'approve')">通过并发布</button>
          <button class="reject" @click="auditPost(post, 'reject')">拒绝</button>
        </div>
      </div>

      <div class="msg" v-if="msgAudit">{{ msgAudit }}</div>
    </section>

    <!-- 帖子详情弹窗 -->
    <div v-if="detailPost" class="modal-mask" @click.self="detailPost = null">
      <div class="modal-wrapper">
        <div class="modal-header">
          <h3>{{ detailPost.title }}</h3>
          <button class="close-btn" @click="detailPost = null">×</button>
        </div>
        <div class="modal-body">
          <div class="post-content-section">
            <p class="post-meta-detail">
              <strong>作者：</strong>{{ detailPost.isAnonymous ? '匿名用户' : detailPost.username + '（' + roleText(detailPost.role) + '）' }}<span v-if="role === 'admin' && detailPost.isAnonymous" class="admin-real-name"> [真实：{{ detailPost.username }}（{{ roleText(detailPost.role) }}）]</span>
              <br/>
              <strong>发布时间：</strong>{{ formatDate(detailPost.createdAt) }}
              <br/>
              <span v-if="detailPost.isLocked" class="locked-warning">此帖已被锁定，无法回复</span>
            </p>
            <p class="post-content">{{ detailPost.content }}</p>
          </div>

          <!-- 回复列表 -->
          <div class="replies-section">
            <h4>回复（{{ replies.length }}）</h4>
            <div v-if="replies.length === 0" class="empty-replies">暂无回复</div>
            <div 
              v-for="reply in replies" 
              :key="reply.id" 
              class="reply-item"
            >
              <div class="reply-header">
                <span class="reply-author">{{ reply.username }} ({{ roleText(reply.role) }})</span>
                <button 
                  v-if="canDeleteReply(reply)" 
                  class="link-btn danger" 
                  @click="deleteReply(reply)"
                >
                  删除
                </button>
              </div>
              <p class="reply-time">{{ formatDate(reply.createdAt) }}</p>
              <p class="reply-content">{{ reply.content }}</p>
            </div>
          </div>

          <!-- 发布回复 -->
          <div v-if="!detailPost.isLocked" class="reply-form-section">
            <h4>发布回复</h4>
            <form @submit.prevent="publishReply">
              <textarea 
                v-model="newReply" 
                placeholder="请输入回复内容"
                required
                rows="4"
              ></textarea>
              <button type="submit">发布回复</button>
            </form>
            <div class="msg" v-if="msgReply">{{ msgReply }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// 动态获取登录信息，避免在模块加载时数据还没保存的问题
let username = null
let role = null
let userId = null

const activeTab = ref('list')

onMounted(() => {
  // 在组件挂载时获取最新的值
  // use sessionStorage for tab-isolated identity
  username = sessionStorage.getItem('username')
  role = sessionStorage.getItem('role')
  userId = sessionStorage.getItem('userId')
  
  console.log('=== Community.vue 组件挂载 ===')
  console.log('sessionStorage 中的数据:')
  console.log('  username:', username)
  console.log('  role:', role)
  console.log('  userId:', userId)
  console.log('================')
  
  // 确保userId是有效的数字
  if (!userId || userId === 'undefined' || userId === 'null') {
    userId = null
  } else {
    userId = parseInt(userId)
  }
  
  if (!username) {
    router.push('/login')
  } else {
    loadPosts()
  }
})

function setTab(tab) {
  activeTab.value = tab
  if (tab === 'list' || tab === 'manage') {
    loadPosts()
  } else if (tab === 'audit') {
    loadPendingPosts()
  }
}

// 状态数据
const posts = ref([])
const pendingPosts = ref([])
const replies = ref([])
const detailPost = ref(null)
const newPost = reactive({ title: '', content: '', isAnonymous: false })
const newReply = ref('')

// 消息提示
const msgPublish = ref('')
const msgPosts = ref('')
const msgReply = ref('')
const msgAudit = ref('')

const API_BASE = '/api/community'

function goBack() {
  router.push('/main')
}

function roleText(r) {
  if (r === 'student') return '学生'
  if (r === 'teacher' || r === 'doctor') return '专家'
  if (r === 'admin') return '管理员'
  return r
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  try {
    return new Date(dateStr).toLocaleString('zh-CN')
  } catch (e) {
    return dateStr
  }
}

async function loadPosts() {
  msgPosts.value = ''
  try {
    const currentRole = sessionStorage.getItem('role') || ''
    const url = `${API_BASE}/posts?viewerRole=${currentRole}`
    console.log('加载帖子URL:', url)
    const res = await fetch(url)
    const data = await res.json()
    console.log('加载帖子响应:', data)
    if (data.code === 200) {
      posts.value = data.data
    } else {
      msgPosts.value = data.message || '加载帖子失败'
    }
  } catch (e) {
    msgPosts.value = '加载帖子失败：' + e.message
    console.error('加载帖子错误:', e)
  }
}

// 获取最新的登录信息
function getLoginInfo() {
  return {
    username: sessionStorage.getItem('username'),
    role: sessionStorage.getItem('role'),
    userId: sessionStorage.getItem('userId')
  }
}

async function publishPost() {
  msgPublish.value = ''
  if (!newPost.title.trim() || !newPost.content.trim()) {
    msgPublish.value = '标题和内容不能为空'
    return
  }

  const loginInfo = getLoginInfo()
  const { userId: uid, username: uname, role: urole } = loginInfo

  if (!uid || uid === 'undefined' || uid === 'null') {
    msgPublish.value = '请先登录（userId丢失，请重新登录）'
    console.error('userId为空或无效:', uid)
    return
  }

  if (!uname) {
    msgPublish.value = '用户名丢失，请重新登录'
    return
  }

  if (!urole) {
    msgPublish.value = '用户角色丢失，请重新登录'
    return
  }

  try {
    const url = `${API_BASE}/posts`
    console.log('发布帖子URL:', url)
    console.log('发布内容:', { title: newPost.title, content: newPost.content })
    
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: newPost.title,
        content: newPost.content,
        isAnonymous: newPost.isAnonymous
      })
    })
    
    console.log('响应状态:', res.status)
    const data = await res.json()
    console.log('发布帖子响应:', data)
    
    if (data.code === 201) {
      msgPublish.value = '发布成功'
      newPost.title = ''
      newPost.content = ''
      newPost.isAnonymous = false
      loadPosts()
    } else {
      msgPublish.value = data.message || '发布失败'
    }
  } catch (e) {
    msgPublish.value = '发布失败：' + e.message
    console.error('发布帖子错误:', e)
  }
}

async function deletePost(post) {
  if (!confirm('确认删除此帖子吗？')) return

  const { userId: uid, role: urole } = getLoginInfo()
  if (!uid || !urole) {
    msgPosts.value = '请先登录'
    return
  }

  try {
    const res = await fetch(`${API_BASE}/posts/${post.id}`, {
      method: 'DELETE'
    })
    const data = await res.json()
    if (data.code === 200) {
      msgPosts.value = '删除成功'
      loadPosts()
    } else {
      msgPosts.value = data.message || '删除失败'
    }
  } catch (e) {
    msgPosts.value = '删除失败：' + e.message
  }
}

async function loadPendingPosts() {
  msgAudit.value = ''
  const { role: urole } = getLoginInfo()
  if (!urole || urole !== 'admin') {
    msgAudit.value = '无权限'
    return
  }

  try {
    const url = `${API_BASE}/posts/pending`
    console.log('加载待审核帖子URL:', url)
    const res = await fetch(url)
    const data = await res.json()
    console.log('加载待审核帖子响应:', data)
    if (data.code === 200) {
      pendingPosts.value = data.data.map(p => ({ ...p, _comment: '' }))
    } else {
      msgAudit.value = data.message || '加载待审核帖子失败'
    }
  } catch (e) {
    msgAudit.value = '加载待审核帖子失败：' + e.message
    console.error('加载待审核帖子错误:', e)
  }
}

async function auditPost(post, action) {
  msgAudit.value = ''
  const { role: urole } = getLoginInfo()
  if (!urole || urole !== 'admin') {
    msgAudit.value = '无权限'
    return
  }

  const comment = post._comment || ''
  const actionText = action === 'approve' ? '通过' : '拒绝'
  
  if (!confirm(`确认${actionText}此帖子吗？`)) return

  try {
    const url = `${API_BASE}/posts/${post.id}/audit?action=${action}&comment=${encodeURIComponent(comment)}`
    console.log('审核帖子URL:', url)
    const res = await fetch(url, { method: 'PUT' })
    const data = await res.json()
    console.log('审核帖子响应:', data)
    
    if (data.code === 200) {
      msgAudit.value = data.message || `${actionText}成功`
      loadPendingPosts()
    } else {
      msgAudit.value = data.message || `${actionText}失败`
    }
  } catch (e) {
    msgAudit.value = `${actionText}失败：` + e.message
    console.error('审核帖子错误:', e)
  }
}

async function togglePin(post) {
  const { role: urole } = getLoginInfo()
  if (!urole) {
    msgPosts.value = '请先登录'
    return
  }

  try {
    const url = post.isPinned 
      ? `${API_BASE}/posts/${post.id}/unpin`
      : `${API_BASE}/posts/${post.id}/pin`
    const res = await fetch(url, { method: 'PUT' })
    const data = await res.json()
    if (data.code === 200) {
      loadPosts()
    } else {
      msgPosts.value = data.message || '操作失败'
    }
  } catch (e) {
    msgPosts.value = '操作失败：' + e.message
  }
}

async function toggleLock(post) {
  const { role: urole } = getLoginInfo()
  if (!urole) {
    msgPosts.value = '请先登录'
    return
  }

  try {
    const url = post.isLocked 
      ? `${API_BASE}/posts/${post.id}/unlock`
      : `${API_BASE}/posts/${post.id}/lock`
    const res = await fetch(url, { method: 'PUT' })
    const data = await res.json()
    if (data.code === 200) {
      loadPosts()
      if (detailPost.value && detailPost.value.id === post.id) {
        detailPost.value.isLocked = !post.isLocked
      }
    } else {
      msgPosts.value = data.message || '操作失败'
    }
  } catch (e) {
    msgPosts.value = '操作失败：' + e.message
  }
}

function canManagePost(post) {
  return userId && (userId == post.userId || role === 'admin')
}

function canDeletePost(post) {
  return userId && (userId == post.userId || role === 'admin')
}

async function openPost(post) {
  detailPost.value = post
  replies.value = []
  msgReply.value = ''
  newReply.value = ''
  
  try {
    const currentRole = sessionStorage.getItem('role') || ''
    const res = await fetch(`${API_BASE}/posts/${post.id}?viewerRole=${currentRole}`)
    const data = await res.json()
    if (data.code === 200) {
      detailPost.value = data.data.post
      replies.value = data.data.replies
    }
  } catch (e) {
    msgReply.value = '加载帖子详情失败'
  }
}

async function publishReply() {
  msgReply.value = ''
  if (!newReply.value.trim()) {
    msgReply.value = '回复内容不能为空'
    return
  }

  const { userId: uid, username: uname, role: urole } = getLoginInfo()
  if (!uid || !uname || !urole) {
    msgReply.value = '请先登录'
    return
  }

  try {
    const res = await fetch(`${API_BASE}/replies?postId=${detailPost.value.id}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content: newReply.value })
    })
    const data = await res.json()
    if (data.code === 201) {
      msgReply.value = '回复成功'
      newReply.value = ''
      openPost(detailPost.value) // 重新加载
      loadPosts() // 更新回复计数
    } else {
      msgReply.value = data.message || '回复失败'
    }
  } catch (e) {
    msgReply.value = '回复失败：' + e.message
  }
}

async function deleteReply(reply) {
  if (!confirm('确认删除此回复吗？')) return

  const { userId: uid, role: urole } = getLoginInfo()
  if (!uid || !urole) {
    msgReply.value = '请先登录'
    return
  }

  try {
    const res = await fetch(`${API_BASE}/replies/${reply.id}`, {
      method: 'DELETE'
    })
    const data = await res.json()
    if (data.code === 200) {
      msgReply.value = '删除成功'
      openPost(detailPost.value) // 重新加载
      loadPosts() // 更新回复计数
    } else {
      msgReply.value = data.message || '删除失败'
    }
  } catch (e) {
    msgReply.value = '删除失败：' + e.message
  }
}

function canDeleteReply(reply) {
  return userId && (userId == reply.userId || role === 'admin')
}
</script>

<style scoped>
.community-wrapper {
  min-height: 100vh;
  background: linear-gradient(135deg, #fff3e8 0%, #ffe6d4 45%, #fff9f2 100%);
  padding: 40px 20px;
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

.community-wrapper > h2 {
  text-align: center;
  font-size: 2.2rem;
  color: #333;
  margin-bottom: 40px;
  background: linear-gradient(135deg, #f59f6f 0%, #f08c5b 50%, #e07a4f 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.community-tabs {
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

.card {
  background: #fffaf6;
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 30px;
  max-width: 900px;
  margin-left: auto;
  margin-right: auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(240, 140, 91, 0.15);
  animation: fadeIn 0.6s ease-out;
}

.card h3 {
  font-size: 1.5rem;
  color: #333;
  margin-top: 0;
  margin-bottom: 24px;
  font-weight: 700;
  border-bottom: 2px solid #f08c5b;
  padding-bottom: 12px;
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

.publish-section input,
.publish-section textarea {
  width: 100%;
  padding: 12px;
  margin: 12px 0;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-family: inherit;
  font-size: 1rem;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.publish-section input:focus,
.publish-section textarea:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.publish-section textarea {
  min-height: 120px;
  resize: vertical;
}

.publish-section button[type="submit"] {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 12px 32px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  margin-top: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.publish-section button[type="submit"]:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.refresh-btn {
  background: white;
  border: 1px solid #ddd;
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 20px;
  font-weight: 600;
  color: #667eea;
  transition: all 0.3s ease;
}

.refresh-btn:hover {
  background: #f8f9ff;
  border-color: #667eea;
}

.post-item {
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  background: #fafbff;
}

.post-item:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.15);
  background: white;
}

.post-item.pinned {
  border-left: 4px solid #ffa500;
  background: #fffbf0;
}

.post-item.locked {
  opacity: 0.6;
}

.post-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.post-title-section {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
}

.badge {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 0.8rem;
  font-weight: bold;
}

.pinned-badge {
  background: #ffe4b5;
  color: #ff8c00;
}

.locked-badge {
  background: #ffcccb;
  color: #dc143c;
}

.post-item h4 {
  margin: 0;
  cursor: pointer;
  color: #667eea;
  font-size: 1.1rem;
  transition: all 0.3s ease;
}

.post-item h4:hover {
  color: #764ba2;
  text-decoration: underline;
}

.clickable {
  cursor: pointer;
}

.post-actions {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}

.link-btn {
  background: none;
  border: none;
  color: #667eea;
  cursor: pointer;
  padding: 4px 8px;
  font-size: 0.9rem;
  font-weight: 600;
  transition: all 0.3s ease;
}

.link-btn:hover {
  color: #764ba2;
  text-decoration: underline;
}

.link-btn.danger {
  color: #d9534f;
}

.link-btn.danger:hover {
  color: #c9423f;
}

.post-meta {
  margin: 8px 0 0;
  font-size: 0.85rem;
  color: #999;
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.empty {
  margin: 40px 0;
  text-align: center;
  color: #999;
  font-size: 1.1rem;
}

.msg {
  margin-top: 16px;
  padding: 12px;
  color: #d9534f;
  font-size: 0.95rem;
  background: #ffe6e6;
  border-radius: 8px;
  border-left: 3px solid #d9534f;
}

/* 弹窗样式 */
.modal-mask {
  position: fixed;
  z-index: 9998;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fadeIn 0.3s ease-out;
}

.modal-wrapper {
  width: 90%;
  max-width: 800px;
  background: white;
  border-radius: 16px;
  padding: 32px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 16px 64px rgba(0, 0, 0, 0.2);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 2px solid #f0f0f0;
  margin-bottom: 24px;
  padding-bottom: 16px;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.4rem;
  color: #333;
}

.close-btn {
  background: none;
  border: none;
  font-size: 28px;
  cursor: pointer;
  color: #999;
  transition: all 0.3s ease;
}

.close-btn:hover {
  color: #333;
  transform: rotate(90deg);
}

.modal-body {
  padding: 0;
}

.post-content-section {
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid #f0f0f0;
}

.post-meta-detail {
  font-size: 0.95rem;
  color: #666;
  margin-bottom: 16px;
  line-height: 1.8;
  background: #f8f9ff;
  padding: 12px;
  border-radius: 8px;
}

.locked-warning {
  display: block;
  color: #d9534f;
  font-weight: bold;
  margin-top: 8px;
  padding: 8px;
  background: #ffe6e6;
  border-radius: 6px;
}

.post-content {
  white-space: pre-wrap;
  word-wrap: break-word;
  line-height: 1.8;
  color: #333;
  font-size: 1rem;
}

.replies-section {
  margin-bottom: 24px;
}

.replies-section h4 {
  margin: 0 0 16px;
  color: #333;
  font-size: 1.1rem;
  font-weight: 600;
}

.empty-replies {
  color: #999;
  font-size: 0.95rem;
  padding: 20px;
  text-align: center;
}

.reply-item {
  border-left: 3px solid #667eea;
  padding: 16px;
  margin-bottom: 12px;
  background: #f8f9ff;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.reply-item:hover {
  transform: translateX(4px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.1);
}

.reply-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.reply-author {
  font-weight: 700;
  color: #333;
}

.reply-time {
  font-size: 0.8rem;
  color: #999;
  margin: 4px 0;
}

.reply-content {
  white-space: pre-wrap;
  word-wrap: break-word;
  margin: 8px 0 0;
  color: #333;
  line-height: 1.6;
}

.reply-form-section {
  border-top: 2px solid #f0f0f0;
  padding-top: 24px;
  margin-top: 24px;
}

.reply-form-section h4 {
  margin: 0 0 16px;
  color: #333;
  font-size: 1.1rem;
  font-weight: 600;
}

.reply-form-section textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-family: inherit;
  resize: vertical;
  font-size: 1rem;
  transition: all 0.3s ease;
}

.reply-form-section textarea:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.reply-form-section button {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 12px 32px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  margin-top: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.reply-form-section button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

/* 审核区域样式 */
.audit-section .post-item.pending {
  border-left: 4px solid #f59f6f;
  background: #fffbf8;
}

.pending-badge {
  background: linear-gradient(135deg, #ffeaa7 0%, #fdcb6e 100%);
  color: #d63031;
}

.post-content-preview {
  color: #555;
  margin: 12px 0;
  line-height: 1.6;
  font-size: 0.95rem;
}

.anonymous-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.93rem;
  color: #7a5c3f;
  margin: 10px 0 14px;
  cursor: pointer;
  user-select: none;
}

.anonymous-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: #f08c5b;
  cursor: pointer;
}

.admin-real-name {
  font-size: 0.85em;
  color: #c0392b;
  font-weight: 600;
  background: #fff1f0;
  border-radius: 4px;
  padding: 1px 5px;
  margin-left: 4px;
}

.audit-textarea {
  width: 100%;
  padding: 10px;
  margin: 12px 0;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-family: inherit;
  resize: vertical;
  min-height: 60px;
  box-sizing: border-box;
}

.audit-textarea:focus {
  outline: none;
  border-color: #f08c5b;
  box-shadow: 0 0 0 3px rgba(240, 140, 91, 0.1);
}

.audit-actions {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}

.audit-actions button {
  padding: 10px 24px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.3s ease;
  flex: 1;
}

.audit-actions button.approve {
  background: linear-gradient(135deg, #00b894 0%, #00cec9 100%);
  color: white;
}

.audit-actions button.approve:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 184, 148, 0.4);
}

.audit-actions button.reject {
  background: linear-gradient(135deg, #ff7675 0%, #d63031 100%);
  color: white;
}

.audit-actions button.reject:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 118, 117, 0.4);
}

@media (max-width: 768px) {
  .community-wrapper {
    padding: 20px 16px;
  }

  .community-wrapper > h2 {
    font-size: 1.8rem;
    margin-bottom: 30px;
  }

  .card {
    padding: 20px;
    border-radius: 12px;
  }

  .modal-wrapper {
    width: 95%;
    padding: 20px;
  }

  .post-header {
    flex-direction: column;
  }

  .post-actions {
    flex-wrap: wrap;
  }
}
</style>
