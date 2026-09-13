<template>
  <div class="main-wrapper">
    <div class="header-section">
      <h1>心理咨询系统</h1>
      <p class="subtitle">欢迎来到心理健康助手中心</p>
    </div>
    
    <!-- 爱心形状的模块容器 -->
    <div class="heart-container">
      <!-- 上左 -->
      <div class="module-item top-left" @click="goto('/profile')" :style="getItemStyle('top-left')">
        <div class="icon">👤</div>
        <div class="label">个人中心</div>
      </div>
      
      <!-- 上右 -->
      <div class="module-item top-right" @click="goto('/assessment')" :style="getItemStyle('top-right')">
        <div class="icon">📋</div>
        <div class="label">心理测评</div>
      </div>
      
      <!-- 下左 -->
      <div class="module-item bottom-left" @click="goto('/consult')" :style="getItemStyle('bottom-left')">
        <div class="icon">💬</div>
        <div class="label">咨询服务</div>
      </div>
      
      <!-- 下右（底尖） -->
      <div class="module-item bottom-center" @click="goto('/ai-assistant')" :style="getItemStyle('bottom-center')">
        <div class="icon">🤖</div>
        <div class="label">AI小助手</div>
      </div>
      
      <!-- 爱心SVG背景 -->
      <svg class="heart-bg" viewBox="0 0 400 420" preserveAspectRatio="none">
        <path d="M200,380 C200,380 50,280 50,160 C50,100 85,60 120,60 C145,60 170,75 200,100 C230,75 255,60 280,60 C315,60 350,100 350,160 C350,280 200,380 200,380 Z" 
              fill="none" stroke="#FFB6D9" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()

function goto(path) { router.push(path) }

function getItemStyle(position) {
  const styles = {
    'top-left': { top: '30px', left: '40px' },
    'top-right': { top: '30px', right: '40px' },
    'bottom-left': { bottom: '110px', left: '60px' },
    'bottom-center': { bottom: '20px', left: '50%', transform: 'translateX(-50%)' }
  }
  return styles[position] || {}
}
</script>

<style scoped>
.main-wrapper {
  min-height: 100vh;
  background: linear-gradient(135deg, #fff5f8 0%, #fffbf0 100%);
  padding: 40px 20px;
  position: relative;
}

.header-section {
  text-align: center;
  margin-bottom: 50px;
  animation: slideDown 0.6s ease-out;
}

.header-section h1 {
  font-size: 2.5rem;
  color: #ff4d7d;
  margin: 0;
  font-weight: 900;
  letter-spacing: 1px;
}

.subtitle {
  color: #666;
  font-size: 1rem;
  margin-top: 8px;
  font-weight: 500;
}

.heart-container {
  position: relative;
  width: 400px;
  height: 420px;
  margin: 40px auto;
  perspective: 1000px;
}

.heart-bg {
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  z-index: 1;
}

.module-item {
  position: absolute;
  width: 100px;
  height: 100px;
  background: white;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  z-index: 10;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: 0 8px 24px rgba(255, 77, 125, 0.15);
  border: 2px solid rgba(255, 77, 125, 0.2);
}

.module-item:hover {
  transform: scale(1.15) translateY(-8px);
  box-shadow: 0 16px 40px rgba(255, 77, 125, 0.3);
  border-color: #ff4d7d;
  background: linear-gradient(135deg, #fff5f8, #ffe0ec);
}

.module-item:active {
  transform: scale(0.95);
}

.icon {
  font-size: 2.5rem;
  line-height: 1;
}

.label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #333;
  text-align: center;
  line-height: 1.2;
}

.module-item:hover .label {
  color: #ff4d7d;
  font-weight: 700;
}

/* 动画 */
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

/* 响应式设计 */
@media (max-width: 768px) {
  .header-section h1 {
    font-size: 2rem;
  }
  
  .heart-container {
    width: 320px;
    height: 340px;
  }
  
  .module-item {
    width: 80px;
    height: 80px;
  }
  
  .icon {
    font-size: 2rem;
  }
  
  .label {
    font-size: 0.7rem;
  }
}

@media (max-width: 480px) {
  .header-section h1 {
    font-size: 1.5rem;
  }
  
  .heart-container {
    width: 280px;
    height: 300px;
  }
  
  .module-item {
    width: 70px;
    height: 70px;
  }
  
  .icon {
    font-size: 1.8rem;
  }
  
  .label {
    font-size: 0.65rem;
  }
}
</style>
