import { createApp } from 'vue'
import App from './App.vue'
import { createRouter, createWebHistory } from 'vue-router'
import './apiClient'
import { isLoggedIn } from './session'
import Login from './views/Login.vue'
import Main from './views/Main.vue'
import Profile from './views/Profile.vue'
import Articles from './views/Articles.vue'
import Consult from './views/Consult.vue'
import Community from './views/Community.vue'
import ConsultChat from './views/ConsultChat.vue'
import ConsultHall from './views/ConsultHall.vue'
import Assessment from './views/Assessment.vue'
import AssessmentHistory from './views/AssessmentHistory.vue'
import AiAssistant from './views/AiAssistant.vue'
import Scl90Assessment from './views/Scl90Assessment.vue'
import './styles/global.css'

const routes = [
  { path: '/', component: Login },
  { path: '/login', component: Login },
  { path: '/main', component: Main },
  { path: '/profile', component: Profile },
  { path: '/assessment', component: Assessment },
  { path: '/assessment/scl90', component: Scl90Assessment },
  { path: '/assessment-history', component: AssessmentHistory },
  { path: '/consult', component: Consult },
  { path: '/articles', component: Articles },
  { path: '/community', component: Community },
  { path: '/consult/hall', component: ConsultHall },
  { path: '/consult/chat', component: ConsultChat },
  { path: '/ai-assistant', component: AiAssistant },
  { path: '/article-library', redirect: '/articles' },
  { path: '/consult-connect', redirect: '/consult/hall' },
  { path: '/consult-service', redirect: '/consult/chat' },
  { path: '/ai', redirect: '/ai-assistant' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const publicPaths = ['/', '/login']
  if (publicPaths.includes(to.path)) {
    next()
    return
  }
  if (!isLoggedIn()) {
    next('/login')
    return
  }
  next()
})

createApp(App).use(router).mount('#app')
