<template>
  <div class="consult-wrapper">
    <div class="back-btn" @click="goBack">← 返回主页</div>
    <div class="header">
      <h1>咨询服务中心</h1>
      <p class="subtitle">选择您需要的服务</p>
    </div>
    
    <div class="circle-menu-container">
      <!-- 中心圆 -->
      <div class="center-circle">
        <div class="center-icon">💼</div>
        <div class="center-text">服务</div>
      </div>
      
      <!-- 三个扇形按钮 -->
      <!-- 上方扇形 -->
      <div class="sector sector-top" @click="goto('/articles')">
        <svg viewBox="0 0 200 200" class="sector-svg">
          <path d="M 100 100 L 100 20 A 80 80 0 0 0 30.73 56.70 Z" 
                class="sector-path" fill="#FFE5E5"/>
        </svg>
        <div class="sector-content">
          <div class="sector-icon">📚</div>
          <div class="sector-label">文章库</div>
          <div class="sector-desc">浏览心理文章</div>
        </div>
      </div>
      
      <!-- 左下扇形 -->
      <div class="sector sector-left" @click="goto('/community')">
        <svg viewBox="0 0 200 200" class="sector-svg">
          <path d="M 100 100 L 30.73 56.70 A 80 80 0 0 0 30.73 143.30 Z" 
                class="sector-path" fill="#E5F5FF"/>
        </svg>
        <div class="sector-content">
          <div class="sector-icon">👥</div>
          <div class="sector-label">社区</div>
          <div class="sector-desc">社区交流</div>
        </div>
      </div>
      
      <!-- 右下扇形 -->
      <div class="sector sector-right" @click="goto('/consult/hall')">
        <svg viewBox="0 0 200 200" class="sector-svg">
          <path d="M 100 100 L 169.27 143.30 A 80 80 0 0 0 100 20 Z" 
                class="sector-path" fill="#F0FFE5"/>
        </svg>
        <div class="sector-content">
          <div class="sector-icon">💬</div>
          <div class="sector-label">咨询</div>
          <div class="sector-desc">一对一咨询</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
function goto(path) { router.push(path) }
function goBack() { router.push('/main') }
</script>

<style scoped>
.consult-wrapper {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f5ff 0%, #fff5f0 100%);
  padding: 40px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.header {
  text-align: center;
  margin-bottom: 60px;
  animation: slideDown 0.6s ease-out;
}

.header h1 {
  font-size: 2.5rem;
  color: #333;
  margin: 0;
  font-weight: 900;
  background: linear-gradient(135deg, #ff6b9d 0%, #c44569 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.subtitle {
  color: #666;
  font-size: 1.1rem;
  margin-top: 10px;
  font-weight: 500;
}

.back-btn {
  display: inline-block;
  padding: 10px 16px;
  background: white;
  border-radius: 8px;
  color: #ff6b9d;
  cursor: pointer;
  font-weight: 600;
  position: absolute;
  top: 20px;
  left: 20px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(255, 107, 157, 0.2);
  border: 1px solid rgba(255, 107, 157, 0.15);
  z-index: 10;
}

.back-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(255, 107, 157, 0.3);
  background: #fff5f8;
}

.circle-menu-container {
  position: relative;
  width: 420px;
  height: 420px;
  margin: 0 auto;
}

/* 中心圆 */
.center-circle {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 120px;
  height: 120px;
  background: white;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  z-index: 20;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 3px solid #fff;
  cursor: default;
  transition: all 0.3s ease;
}

.center-circle:hover {
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.15);
}

.center-icon {
  font-size: 3rem;
}

.center-text {
  font-size: 1rem;
  font-weight: 700;
  color: #333;
}

/* 扇形通用样式 */
.sector {
  position: absolute;
  width: 240px;
  height: 240px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.sector-svg {
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.1));
}

.sector-path {
  transition: all 0.3s ease;
}

.sector:hover .sector-path {
  filter: brightness(0.9);
  box-shadow: inset 0 0 10px rgba(0, 0, 0, 0.1);
}

.sector-content {
  position: absolute;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  z-index: 10;
}

.sector-icon {
  font-size: 2.8rem;
  transition: transform 0.3s ease;
}

.sector-label {
  font-size: 1.1rem;
  font-weight: 700;
  color: #333;
  transition: color 0.3s ease;
}

.sector-desc {
  font-size: 0.8rem;
  color: #666;
  text-align: center;
  max-width: 80px;
  transition: color 0.3s ease;
}

.sector:hover .sector-icon {
  transform: scale(1.2);
}

.sector:hover .sector-label {
  color: #ff6b9d;
}

.sector:hover .sector-desc {
  color: #ff6b9d;
}

/* 扇形位置定位 */
.sector-top {
  top: -40px;
  left: 50%;
  transform: translateX(-50%);
}

.sector-left {
  top: 90px;
  left: -80px;
  transform: rotate(-60deg);
}

.sector-left .sector-content {
  transform: rotate(60deg);
}

.sector-right {
  top: 90px;
  right: -80px;
  transform: rotate(60deg);
}

.sector-right .sector-content {
  transform: rotate(-60deg);
}

/* 悬停时的位置调整 */
.sector:hover {
  filter: drop-shadow(0 8px 24px rgba(0, 0, 0, 0.15));
}

.sector-top:hover {
  transform: translateX(-50%) translateY(-15px);
}

.sector-left:hover {
  transform: rotate(-60deg) translateY(-15px);
}

.sector-right:hover {
  transform: rotate(60deg) translateY(-15px);
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
  .header h1 {
    font-size: 2rem;
  }

  .circle-menu-container {
    width: 340px;
    height: 340px;
  }

  .center-circle {
    width: 100px;
    height: 100px;
  }

  .center-icon {
    font-size: 2.4rem;
  }

  .center-text {
    font-size: 0.9rem;
  }

  .sector {
    width: 200px;
    height: 200px;
  }

  .sector-icon {
    font-size: 2.4rem;
  }

  .sector-label {
    font-size: 1rem;
  }

  .sector-desc {
    font-size: 0.75rem;
  }

  .sector-top {
    top: -30px;
  }

  .sector-left {
    top: 70px;
    left: -60px;
  }

  .sector-right {
    top: 70px;
    right: -60px;
  }
}

@media (max-width: 480px) {
  .header h1 {
    font-size: 1.5rem;
  }

  .header {
    margin-bottom: 40px;
  }

  .circle-menu-container {
    width: 300px;
    height: 300px;
  }

  .center-circle {
    width: 80px;
    height: 80px;
  }

  .center-icon {
    font-size: 2rem;
  }

  .center-text {
    font-size: 0.8rem;
  }

  .sector {
    width: 160px;
    height: 160px;
  }

  .sector-icon {
    font-size: 2rem;
  }

  .sector-label {
    font-size: 0.9rem;
  }

  .sector-desc {
    font-size: 0.7rem;
  }

  .sector-top {
    top: -20px;
  }

  .sector-left {
    top: 50px;
    left: -40px;
  }

  .sector-right {
    top: 50px;
    right: -40px;
  }

  .sector-top:hover {
    transform: translateX(-50%) translateY(-10px);
  }

  .sector-left:hover {
    transform: rotate(-60deg) translateY(-10px);
  }

  .sector-right:hover {
    transform: rotate(60deg) translateY(-10px);
  }
}
</style>
