<template>
  <div>
    <div class="article-wrapper">
      <div class="back-btn" @click="goBack">← 返回主页</div>
      <h2>文章库</h2>

      <div class="section-tabs">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'published' }"
          @click="setTab('published')"
        >
          已发布
        </button>
        <button
          v-if="isAdmin"
          class="tab-btn"
          :class="{ active: activeTab === 'overview' }"
          @click="setTab('overview')"
        >
          文章总览
        </button>
        <button
          v-if="role === 'admin'"
          class="tab-btn"
          :class="{ active: activeTab === 'audit' }"
          @click="setTab('audit')"
        >
          文章审核
        </button>
      </div>

      <section class="card" v-show="activeTab === 'published'">
        <h3>已发布文章</h3>
        <button @click="loadArticles">刷新列表</button>
        <div v-if="articles.length === 0" class="empty">暂无文章</div>
        <div v-for="article in articles" :key="article.id" class="article-item">
          <div class="title-line">
            <h4 class="clickable" @click="openDetail(article)">{{ article.title }}</h4>
            <div class="right-actions" v-if="isAdmin">
              <button class="link-btn danger" @click="unpublishArticle(article)">下架</button>
              <button class="link-btn danger" @click="deleteArticle(article)">删除</button>
            </div>
          </div>
          <p class="meta">
            作者：{{ article.authorUsername }}（{{ roleText(article.authorRole) }}）
            <span>发布时间：{{ formatDate(article.createdAt) }}</span>
          </p>
          <p class="content excerpt">{{ excerpt(article.content) }}</p>
          <div class="actions-row">
            <button @click="likeArticle(article)" v-if="!isAdmin">👍 点赞（{{ article.likeCount || 0 }}）</button>
          </div>
          <span class="audit-comment" v-if="article.auditComment">
            备注：{{ article.auditComment }}
          </span>
        </div>
        <div class="msg">{{ msgPublic }}</div>
      </section>

      <section v-if="role === 'doctor'" class="card" v-show="activeTab === 'published'">
        <h3>教师投稿</h3>
        <form @submit.prevent="submitArticle">
          <input v-model="submitForm.title" placeholder="文章标题" required />
          <textarea v-model="submitForm.content" placeholder="文章内容" required></textarea>
          <button type="submit">提交审核</button>
        </form>
        <div class="msg">{{ msgSubmit }}</div>
      </section>

      <section v-if="isAdmin" class="card" v-show="activeTab === 'overview'">
        <h3>管理员文章总览（含已发布/下架/打回等）</h3>
        <div class="filters">
          <label>状态过滤：
            <select v-model="adminFilterStatus" @change="loadAdminAll">
              <option value="all">全部</option>
              <option value="published">已发布</option>
              <option value="unpublished">已下架</option>
              <option value="pending">待审核</option>
              <option value="rejected">已打回</option>
            </select>
          </label>
          <button @click="loadAdminAll">刷新</button>
        </div>
        <div v-if="adminAll.length === 0" class="empty">暂无记录</div>
        <div v-for="article in adminAll" :key="article.id" class="article-item admin-manage">
          <div class="title-line">
            <h4 class="clickable" @click="openDetail(article)">{{ article.title }}</h4>
            <div class="right-actions">
              <button class="link-btn danger" @click="unpublishArticleAdminList(article)">下架</button>
              <button class="link-btn danger" @click="deleteArticleAdminList(article)">删除</button>
            </div>
          </div>
          <p class="content excerpt">{{ excerpt(article.content) }}</p>
          <p class="meta">
            作者：{{ article.authorUsername }}（{{ roleText(article.authorRole) }}）
            <span>时间：{{ formatDate(article.createdAt) }}</span>
          </p>
        </div>
        <div class="msg">{{ msgAdminAll }}</div>
      </section>

      <section v-if="role === 'admin'" class="card" v-show="activeTab === 'audit'">
        <h3>待审核文章</h3>
        <button @click="loadPending">刷新待审核</button>
        <div v-if="pendingArticles.length === 0" class="empty">暂无待审核文章</div>
        <div v-for="article in pendingArticles" :key="article.id" class="article-item pending">
          <h4>{{ article.title }}</h4>
          <p class="meta">
            作者：{{ article.authorUsername }}（{{ roleText(article.authorRole) }}）
            <span>提交时间：{{ formatDate(article.createdAt) }}</span>
          </p>
          <p class="content">{{ article.content }}</p>
          <textarea v-model="article._comment" placeholder="审核备注（可选）"></textarea>
          <div class="actions">
            <button class="approve" @click="auditArticle(article, 'approve')">发布</button>
            <button class="reject" @click="auditArticle(article, 'reject')">打回</button>
          </div>
        </div>
        <div class="msg">{{ msgAudit }}</div>
      </section>
    </div>

    <!-- 详情弹窗 -->
    <div v-if="detailArticle" class="modal-mask" @click.self="detailArticle = null">
      <div class="modal-wrapper">
        <div class="modal-header">
          <h3>{{ detailArticle.title }}</h3>
          <button class="close-btn" @click="detailArticle = null">×</button>
        </div>
        <div class="modal-body">
          <p class="meta">
            作者：{{ detailArticle.authorUsername }}（{{ roleText(detailArticle.authorRole) }}）
            <span>时间：{{ formatDate(detailArticle.createdAt) }}</span>
          </p>
          <p class="content">{{ detailArticle.content }}</p>
          <p v-if="detailArticle.auditComment" class="audit-comment">
            备注：{{ detailArticle.auditComment }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const username = sessionStorage.getItem('username')
const role = sessionStorage.getItem('role')

const articles = ref([])
const pendingArticles = ref([])
const msgPublic = ref('')
const msgSubmit = ref('')
const msgAudit = ref('')
const msgAdminAll = ref('')
const submitForm = ref({ title: '', content: '' })

const activeTab = ref('published')

const isAdmin = role === 'admin'
const adminFilterStatus = ref('all')
const adminAll = ref([])
const detailArticle = ref(null)        // 普通查看弹窗

function goBack() {
  router.push('/main')
}

onMounted(() => {
  if (!username) {
    router.push('/login')
  } else {
    loadArticles()
    if (role === 'admin') {
      loadPending()
      loadAdminAll()
    }
  }
})

function setTab(tab) {
  activeTab.value = tab
  if (tab === 'published') {
    loadArticles()
  } else if (tab === 'overview') {
    loadAdminAll()
  } else if (tab === 'audit') {
    loadPending()
  }
}

function roleText(r) {
  if (r === 'student') return '学生'
  if (r === 'doctor') return '专家'
  if (r === 'admin') return '管理员'
  return r
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  try {
    return new Date(dateStr).toLocaleString()
  } catch (e) {
    return dateStr
  }
}

async function loadArticles() {
  msgPublic.value = ''
  const res = await fetch('/api/articles/public')
  const data = await res.json()
  if (data.success) {
    articles.value = (data.articles || []).map(a => ({
      ...a,
      _title: a.title,
      _content: a.content,
      _status: a.status || 'published',
      _comment: a.auditComment || ''
    }))
  } else {
    msgPublic.value = data.message || '加载文章失败'
  }
}

async function likeArticle(article) {
  const res = await fetch('/api/articles/like', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ articleId: article.id, username })
  })
  const data = await res.json()
  if (data.success) {
    article.likeCount = (article.likeCount || 0) + 1
  } else {
    msgPublic.value = data.message || '已点赞'
  }
}

async function submitArticle() {
  msgSubmit.value = ''
  const res = await fetch('/api/articles/submit', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      title: submitForm.value.title,
      content: submitForm.value.content,
      authorUsername: username,
      authorRole: role
    })
  })
  const data = await res.json()
  if (data.success) {
    msgSubmit.value = '提交成功，请等待管理员审核'
    submitForm.value = { title: '', content: '' }
  } else {
    msgSubmit.value = data.message || '提交失败'
  }
}

async function loadPending() {
  msgAudit.value = ''
  const res = await fetch('/api/articles/pending')
  const data = await res.json()
  if (data.success) {
    pendingArticles.value = (data.articles || []).map(a => ({ ...a, _comment: '' }))
  } else {
    msgAudit.value = data.message || '加载待审核列表失败'
  }
}

async function updateArticle(article) {
  msgPublic.value = ''
  const res = await fetch('/api/articles/admin/update', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      operatorRole: role,
      articleId: article.id,
      title: article._title,
      content: article._content,
      status: article._status,
      comment: article._comment
    })
  })
  const data = await res.json()
  if (data.success) {
    msgPublic.value = '保存成功'
    article.title = article._title
    article.content = article._content
    article.status = article._status
    article.auditComment = article._comment
    loadArticles()
  } else {
    msgPublic.value = data.message || '保存失败'
  }
}

async function unpublishArticle(article) {
  if (!confirm('确认下架此文章吗？')) return
  msgPublic.value = ''
  const res = await fetch('/api/articles/admin/unpublish', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      operatorRole: role,
      articleId: article.id,
      comment: article._comment
    })
  })
  const data = await res.json()
  if (data.success) {
    msgPublic.value = '已下架'
    loadArticles()
  } else {
    msgPublic.value = data.message || '下架失败'
  }
}

async function deleteArticle(article) {
  if (!confirm('确认删除此文章吗？删除后无法恢复！')) return
  msgPublic.value = ''
  console.log('=== 删除文章请求 ===')
  console.log('文章ID:', article.id)
  console.log('操作角色:', role)
  
  try {
    const requestBody = {
      operatorRole: role,
      articleId: String(article.id)
    }
    console.log('请求体:', requestBody)
    
    const res = await fetch('/api/articles/admin/delete', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestBody)
    })
    
    console.log('响应状态:', res.status)
    const data = await res.json()
    console.log('响应数据:', data)
    
    if (data.success) {
      msgPublic.value = '删除成功'
      loadArticles()
    } else {
      msgPublic.value = data.message || '删除失败'
    }
  } catch (e) {
    console.error('删除文章错误:', e)
    msgPublic.value = '删除失败：' + e.message
  }
}

async function auditArticle(article, action) {
  msgAudit.value = ''
  const res = await fetch('/api/articles/audit', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      operatorRole: role,
      articleId: article.id,
      action,
      comment: article._comment
    })
  })
  const data = await res.json()
  if (data.success) {
    msgAudit.value = action === 'approve' ? '发布成功' : '已打回'
    pendingArticles.value = pendingArticles.value.filter(a => a.id !== article.id)
    if (action === 'approve') {
      loadArticles()
    }
  } else {
    msgAudit.value = data.message || '操作失败'
  }
}

function excerpt(content, len = 80) {
  if (!content) return ''
  return content.length > len ? content.slice(0, len) + '...' : content
}

function openDetail(article) {
  detailArticle.value = { ...article }
}

async function loadAdminAll() {
  msgAdminAll.value = ''
  const res = await fetch(`/api/articles/admin/list?status=${encodeURIComponent(adminFilterStatus.value)}`)
  const data = await res.json()
  if (data.success) {
    adminAll.value = (data.articles || []).map(a => ({
      ...a,
      _title: a.title,
      _content: a.content,
      _status: a.status || 'published',
      _comment: a.auditComment || ''
    }))
  } else {
    msgAdminAll.value = data.message || '加载失败'
  }
}

async function unpublishArticleAdminList(article) {
  if (!confirm('确认下架此文章吗？')) return
  msgAdminAll.value = ''
  const res = await fetch('/api/articles/admin/unpublish', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      operatorRole: role,
      articleId: article.id,
      comment: article._comment
    })
  })
  const data = await res.json()
  if (data.success) {
    msgAdminAll.value = '已下架'
    loadAdminAll()
    loadArticles()
  } else {
    msgAdminAll.value = data.message || '下架失败'
  }
}

async function deleteArticleAdminList(article) {
  if (!confirm('确认删除此文章吗？删除后无法恢复！')) return
  msgAdminAll.value = ''
  console.log('=== 删除文章请求（管理列表） ===')
  console.log('文章ID:', article.id)
  console.log('操作角色:', role)
  
  try {
    const requestBody = {
      operatorRole: role,
      articleId: String(article.id)
    }
    console.log('请求体:', requestBody)
    
    const res = await fetch('/api/articles/admin/delete', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestBody)
    })
    
    console.log('响应状态:', res.status)
    const data = await res.json()
    console.log('响应数据:', data)
    
    if (data.success) {
      msgAdminAll.value = '删除成功'
      loadAdminAll()
      loadArticles()
    } else {
      msgAdminAll.value = data.message || '删除失败'
    }
  } catch (e) {
    console.error('删除文章错误:', e)
    msgAdminAll.value = '删除失败：' + e.message
  }
}
</script>

<style scoped>
.article-wrapper {
  min-height: 100vh;
  background: linear-gradient(135deg, #fff4e6 0%, #ffe9d6 45%, #fff8f2 100%);
  padding: 40px 20px;
}

.article-wrapper h2 {
  text-align: center;
  font-size: 2.2rem;
  color: #333;
  margin-bottom: 40px;
  background: linear-gradient(135deg, #f59f6f 0%, #f08c5b 50%, #e07a4f 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
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

.section-tabs {
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
  margin-bottom: 30px;
  padding: 32px;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  background: #fffaf6;
  max-width: 900px;
  margin-left: auto;
  margin-right: auto;
  border: 1px solid rgba(240, 140, 91, 0.15);
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

.card h3 {
  font-size: 1.5rem;
  color: #333;
  margin-top: 0;
  margin-bottom: 24px;
  font-weight: 700;
  border-bottom: 2px solid #f08c5b;
  padding-bottom: 12px;
}

.card > button[class*="refresh"],
.filters button {
  background: white;
  border: 1px solid #ddd;
  padding: 10px 20px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 16px;
  font-weight: 600;
  color: #667eea;
  transition: all 0.3s ease;
}

.card > button[class*="refresh"]:hover,
.filters button:hover {
  background: #f8f9ff;
  border-color: #667eea;
}

.article-item {
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  background: #fafbff;
}

.article-item:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.15);
  background: white;
}

.article-item.pending {
  border-left: 4px solid #ff9800;
  background: #fff8f0;
}

.article-item h4 {
  margin: 0 0 12px;
  font-size: 1.1rem;
  color: #333;
}

.meta {
  font-size: 0.85rem;
  color: #999;
  display: flex;
  justify-content: space-between;
  margin: 8px 0 12px;
}

.meta span {
  display: block;
}

.content {
  margin: 12px 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  line-height: 1.6;
  color: #333;
}

.excerpt {
  color: #666;
  font-size: 0.95rem;
}

.actions-row {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

textarea {
  width: 100%;
  min-height: 120px;
  padding: 12px;
  margin: 12px 0;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-family: inherit;
  font-size: 1rem;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

textarea:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

input,
select {
  padding: 10px 12px;
  margin: 8px 0;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-family: inherit;
  font-size: 1rem;
  transition: all 0.3s ease;
}

input:focus,
select:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

button {
  padding: 10px 20px;
  margin-right: 8px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.3s ease;
}

.empty {
  margin: 24px 0;
  text-align: center;
  color: #999;
  font-size: 1rem;
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

.audit-comment {
  display: block;
  margin-top: 12px;
  padding: 10px;
  background: #fffbf0;
  border-left: 3px solid #ff9800;
  color: #ff9800;
  border-radius: 4px;
  font-size: 0.9rem;
}

.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}

.approve {
  background: linear-gradient(135deg, #4caf50 0%, #45a049 100%);
  color: white;
}

.approve:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(76, 175, 80, 0.4);
}

.reject {
  background: linear-gradient(135deg, #f44336 0%, #da190b 100%);
  color: white;
}

.reject:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(244, 67, 54, 0.4);
}

.filters {
  margin-bottom: 20px;
  display: flex;
  gap: 12px;
  align-items: center;
}

.filters label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.filters select {
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px solid #ddd;
}

.title-line {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
}

.clickable {
  cursor: pointer;
  color: #667eea;
  transition: all 0.3s ease;
}

.clickable:hover {
  color: #764ba2;
  text-decoration: underline;
}

.right-actions {
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
  max-width: 900px;
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
  padding: 0;
}

.close-btn:hover {
  color: #333;
  transform: rotate(90deg);
}

.modal-body {
  margin-bottom: 20px;
}

.modal-body input,
.modal-body textarea,
.modal-body select {
  width: 100%;
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-family: inherit;
  font-size: 1rem;
  box-sizing: border-box;
  transition: all 0.3s ease;
}

.modal-body input:focus,
.modal-body textarea:focus,
.modal-body select:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.modal-body .meta {
  background: #f8f9ff;
  padding: 12px;
  border-radius: 8px;
  margin-top: 12px;
}

.modal-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  border-top: 1px solid #f0f0f0;
  padding-top: 20px;
}

.modal-actions button {
  flex: 1;
  padding: 12px 20px;
}

@media (max-width: 768px) {
  .article-wrapper {
    padding: 20px 16px;
  }

  .article-wrapper h2 {
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

  .title-line {
    flex-direction: column;
  }

  .right-actions {
    width: 100%;
  }

  .meta {
    flex-direction: column;
  }

  .filters {
    flex-direction: column;
    align-items: stretch;
  }

  .filters label {
    flex-direction: column;
  }

  .actions {
    flex-wrap: wrap;
  }
}
</style>
