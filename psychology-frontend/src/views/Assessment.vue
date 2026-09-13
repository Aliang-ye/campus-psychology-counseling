<template>
  <div class="assessment-container">
    <div class="back-btn" @click="goBack">← 返回主页</div>
    <!-- 测评开始页面 -->
    <div v-if="!started && !showResult" class="start-page">
      <h1>心理健康测评</h1>
      <div class="intro">
        <p>本测评共30题，涵盖5个维度：</p>
        <ul>
          <li>学业压力</li>
          <li>宿舍关系</li>
          <li>考试焦虑</li>
          <li>就业压力</li>
          <li>恋爱问题</li>
        </ul>
        <p>请根据您的实际情况选择最符合的选项，预计用时10-15分钟。</p>
      </div>
      <div class="start-actions">
        <button @click="startAssessment" class="btn-start">开始测评</button>
        <button @click="viewDefaultHistory" class="btn-action">查看常规测评历史</button>
        <button @click="viewScl90History" class="btn-action">查看 SCL-90 历史</button>
        <button @click="goScl90" class="btn-action">SCL-90 专项测评</button>
      </div>
    </div>

    <!-- 答题页面 -->
    <div v-if="started && !showResult" class="question-page">
      <div class="progress-bar">
        <div class="progress" :style="{ width: progressPercent + '%' }"></div>
      </div>
      <div class="progress-text">{{ currentIndex + 1 }} / {{ questions.length }}</div>

      <div v-if="currentQuestion" class="question-card">
        <h3>{{ currentQuestion.content }}</h3>
        <div class="options">
          <div
            v-for="(option, index) in options"
            :key="index"
            class="option-item"
            :class="{ selected: answers[currentQuestion.id] === index + 1 }"
            @click="selectOption(index + 1)"
          >
            <span class="option-label">{{ option }}</span>
          </div>
        </div>
      </div>

      <div class="navigation">
        <button @click="prevQuestion" :disabled="currentIndex === 0" class="btn-nav">上一题</button>
        <button v-if="currentIndex < questions.length - 1" @click="nextQuestion" :disabled="!answers[currentQuestion.id]" class="btn-nav">下一题</button>
        <button v-else @click="submitAssessment" :disabled="!isAllAnswered" class="btn-submit">提交测评</button>
      </div>
    </div>

    <!-- 结果页面 -->
    <div v-if="showResult" class="result-page">
      <h2>测评完成</h2>
      <div class="total-score">
        <h3>总分：{{ result.totalScore }} / 150</h3>
        <p class="score-level" :class="getLevelClass(result.totalScore)">{{ getScoreLevel(result.totalScore) }}</p>
      </div>

      <div class="dimension-scores">
        <h3>各维度得分</h3>
        <div class="dimension-item" v-for="(score, name) in result.dimensionScores" :key="name">
          <span class="dimension-name">{{ name }}</span>
          <div class="score-bar">
            <div class="score-fill" :style="{ width: (score / 30 * 100) + '%', backgroundColor: getDimensionColor(name) }"></div>
          </div>
          <span class="dimension-score">{{ score }} / 30</span>
        </div>
      </div>

      <div class="actions">
        <button @click="viewDefaultHistory" class="btn-action">查看常规测评历史</button>
        <button @click="viewScl90History" class="btn-action">查看 SCL-90 历史</button>
        <button @click="restartAssessment" class="btn-action">重新测评</button>
      </div>

      <div class="advice-section">
        <h3>AI 建议</h3>
        <div class="advice-loading" v-if="loadingAdvice">
          <div class="spinner"></div>
          <p>AI 正在分析您的测评结果，请稍候...</p>
        </div>
        <div class="advice-card" v-else-if="adviceText">
          <p class="advice-text">{{ adviceText }}</p>
        </div>
        <div class="advice-card" v-else-if="result.adviceOverall">
          <p>{{ result.adviceOverall }}</p>
        </div>
        <div class="advice-empty" v-else>
          <p>暂无建议</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Assessment',
  data() {
    return {
      started: false,
      showResult: false,
      questions: [],
      currentIndex: 0,
      answers: {},
      startTime: null,
      result: null,
      options: ['没有', '很轻', '中等', '偏重', '严重'],
      submitting: false,  // 防重复提交标志
      adviceText: '',  // 累积 AI 建议文本
      loadingAdvice: false  // 正在加载建议
    };
  },
  computed: {
    currentQuestion() {
      return this.questions[this.currentIndex];
    },
    progressPercent() {
      return (this.currentIndex / this.questions.length) * 100;
    },
    isAllAnswered() {
      return this.questions.every(q => this.answers[q.id] !== undefined);
    }
  },
  methods: {
    goBack() {
      this.$router.push('/main');
    },
    async startAssessment() {
      try {
        const response = await fetch('/api/assessment/questions');
        const data = await response.json();
        
        if (data.success) {
          this.questions = data.questions;
          this.started = true;
          this.startTime = Date.now();
        } else {
          alert('获取题目失败：' + data.message);
        }
      } catch (error) {
        console.error('获取题目失败:', error);
        alert('获取题目失败，请检查网络连接');
      }
    },
    selectOption(option) {
      this.answers[this.currentQuestion.id] = option;
    },
    nextQuestion() {
      if (this.currentIndex < this.questions.length - 1) {
        this.currentIndex++;
      }
    },
    prevQuestion() {
      if (this.currentIndex > 0) {
        this.currentIndex--;
      }
    },
    async submitAssessment() {
      if (!this.isAllAnswered) {
        alert('请完成所有题目');
        return;
      }

      if (this.submitting) {
        alert('测评正在提交中，请稍候...');
        return;
      }

      this.submitting = true;
      const duration = Math.floor((Date.now() - this.startTime) / 1000);
      const userId = parseInt(sessionStorage.getItem('userId'));
      const username = sessionStorage.getItem('username');

      try {
        const response = await fetch('/api/assessment/submit', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            userId,
            username,
            answers: this.answers,
            testDuration: duration
          })
        });

        const data = await response.json();
        
        if (data.success) {
          console.log('提交成功，返回数据:', data);
          // 立即显示分数页，不等 AI
          this.result = data;
          this.adviceText = '';
          this.loadingAdvice = true;
          this.showResult = true;
          
          console.log('this.result:', this.result);
          
          // 后台异步拉取 AI 建议
          this.loadAdviceStream(data.recordId);
        } else {
          alert('提交失败：' + data.message);
        }
      } catch (error) {
        console.error('提交失败:', error);
        alert('提交失败，请检查网络连接');
      } finally {
        this.submitting = false;
      }
    },

    /**
     * 流式拉取 AI 建议
     */
    loadAdviceStream(recordId) {
      const eventSource = new EventSource(`/api/assessment/advice/stream?recordId=${recordId}`);
      
      eventSource.onmessage = (event) => {
        const chunk = event.data;
        if (chunk === '[完成]') {
          this.loadingAdvice = false;
          eventSource.close();
        } else if (chunk.startsWith('[错误]')) {
          this.adviceText += '\n' + chunk;
          this.loadingAdvice = false;
          eventSource.close();
        } else {
          this.adviceText += chunk;
        }
      };
      
      eventSource.onerror = (error) => {
        console.error('加载建议失败:', error);
        this.adviceText += '\n[加载建议失败，请稍后重试]';
        this.loadingAdvice = false;
        eventSource.close();
      };
    },
    getScoreLevel(score) {
      if (score <= 45) return '正常';
      if (score <= 75) return '轻度';
      if (score <= 105) return '中度';
      return '重度';
    },
    getLevelClass(score) {
      if (score <= 45) return 'level-normal';
      if (score <= 75) return 'level-mild';
      if (score <= 105) return 'level-moderate';
      return 'level-severe';
    },
    getDimensionColor(name) {
      const colors = {
        '学业压力': '#4CAF50',
        '宿舍关系': '#2196F3',
        '考试焦虑': '#FFC107',
        '就业压力': '#FF9800',
        '恋爱问题': '#E91E63'
      };
      return colors[name] || '#9E9E9E';
    },
    viewDefaultHistory() {
      this.$router.push('/assessment-history');
    },
    viewScl90History() {
      this.$router.push({ path: '/assessment-history', query: { type: 'scl90' } });
    },
    goScl90() {
      this.$router.push('/assessment/scl90');
    },
    restartAssessment() {
      this.started = false;
      this.showResult = false;
      this.questions = [];
      this.currentIndex = 0;
      this.answers = {};
      this.startTime = null;
      this.result = null;
    }
  }
};
</script>

<style scoped>
.assessment-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #f0f5ff 0%, #fff5f0 100%);
  padding: 40px 20px;
}

/* 开始页面 */
.start-page {
  text-align: center;
  padding: 60px 40px;
  max-width: 800px;
  margin: 0 auto;
  animation: slideDown 0.6s ease-out;
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

.start-page h1 {
  font-size: 2.5rem;
  margin-bottom: 40px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  font-weight: 900;
}

.intro {
  background: white;
  padding: 40px;
  border-radius: 16px;
  margin-bottom: 40px;
  text-align: left;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.intro p {
  font-size: 1.1rem;
  color: #666;
  line-height: 1.8;
  margin-bottom: 20px;
}

.intro ul {
  margin: 20px 0;
  padding-left: 40px;
}

.intro li {
  margin: 12px 0;
  font-size: 1.05rem;
  color: #555;
}

.start-actions {
  display: flex;
  gap: 20px;
  justify-content: center;
  flex-wrap: wrap;
}

.btn-start,
.btn-action {
  padding: 14px 40px;
  font-size: 1rem;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.btn-start {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  flex: 0 1 auto;
}

.btn-start:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(102, 126, 234, 0.4);
}

.btn-action {
  border: 2px solid #667eea;
  background: white;
  color: #667eea;
}

.btn-action:hover {
  background: #667eea;
  color: white;
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(102, 126, 234, 0.4);
}

.back-btn {
  display: inline-block;
  padding: 10px 16px;
  background: white;
  border-radius: 8px;
  color: #667eea;
  cursor: pointer;
  font-weight: 600;
  margin-bottom: 20px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
  border: 1px solid rgba(102, 126, 234, 0.15);
}

.back-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.3);
  background: #f8f9ff;
}

/* 答题页面 */
.question-page {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.progress-bar {
  width: 100%;
  height: 8px;
  background: #e0e0e0;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 20px;
}

.progress {
  height: 100%;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  transition: width 0.3s ease;
}

.progress-text {
  text-align: center;
  font-size: 0.95rem;
  color: #999;
  margin-bottom: 30px;
  font-weight: 600;
}

.question-card {
  background: white;
  padding: 40px;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  margin-bottom: 40px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.question-card h3 {
  font-size: 1.3rem;
  margin-bottom: 32px;
  line-height: 1.6;
  color: #333;
  font-weight: 700;
}

.options {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.option-item {
  padding: 16px 20px;
  border: 2px solid #e0e0e0;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  background: #fafbff;
  font-weight: 500;
}

.option-item:hover {
  border-color: #667eea;
  background: white;
  transform: translateX(4px);
}

.option-item.selected {
  border-color: #667eea;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.05) 100%);
  color: #667eea;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.15);
}

.option-label {
  font-size: 1rem;
}

.navigation {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.btn-nav,
.btn-submit {
  padding: 12px 28px;
  font-size: 1rem;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-weight: 600;
}

.btn-nav {
  background: #f0f0f0;
  color: #666;
  flex: 1;
}

.btn-nav:hover:not(:disabled) {
  background: #e0e0e0;
  transform: translateY(-2px);
}

.btn-nav:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-submit {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  flex: 1;
}

.btn-submit:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.35);
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 结果页面 */
.result-page {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
  animation: slideDown 0.6s ease-out;
}

.result-page h2 {
  text-align: center;
  font-size: 2rem;
  margin-bottom: 40px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  font-weight: 900;
}

.total-score {
  text-align: center;
  background: white;
  padding: 40px;
  border-radius: 16px;
  margin-bottom: 40px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.total-score h3 {
  font-size: 2.5rem;
  margin-bottom: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  font-weight: 900;
}

.score-level {
  font-size: 1.2rem;
  font-weight: 700;
  padding: 12px 40px;
  border-radius: 24px;
  display: inline-block;
  margin-top: 12px;
}

.level-normal {
  background: linear-gradient(135deg, #4caf50 0%, #45a049 100%);
  color: white;
}

.level-mild {
  background: linear-gradient(135deg, #ffc107 0%, #ffb300 100%);
  color: white;
}

.level-moderate {
  background: linear-gradient(135deg, #ff9800 0%, #f57c00 100%);
  color: white;
}

.level-severe {
  background: linear-gradient(135deg, #f44336 0%, #da190b 100%);
  color: white;
}

.dimension-scores {
  background: white;
  padding: 40px;
  border-radius: 16px;
  margin-bottom: 40px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.dimension-scores h3 {
  font-size: 1.3rem;
  margin-bottom: 28px;
  color: #333;
  font-weight: 700;
  border-bottom: 2px solid #667eea;
  padding-bottom: 12px;
}

.dimension-item {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.dimension-name {
  min-width: 120px;
  font-weight: 700;
  color: #333;
}

.score-bar {
  flex: 1;
  height: 28px;
  background: #f0f0f0;
  border-radius: 14px;
  overflow: hidden;
}

.score-fill {
  height: 100%;
  transition: width 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
  border-radius: 14px;
}

.dimension-score {
  min-width: 60px;
  text-align: right;
  font-weight: 700;
  color: #667eea;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-bottom: 40px;
  flex-wrap: wrap;
}

.advice-section {
  background: white;
  padding: 40px;
  border-radius: 16px;
  margin-top: 0;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.advice-section h3 {
  font-size: 1.3rem;
  margin-bottom: 24px;
  color: #333;
  font-weight: 700;
  border-bottom: 2px solid #667eea;
  padding-bottom: 12px;
}

.advice-card {
  background: linear-gradient(135deg, #f8f9ff 0%, #fff5f0 100%);
  padding: 24px;
  border-radius: 12px;
  border-left: 4px solid #667eea;
  line-height: 1.8;
  color: #555;
  font-size: 1rem;
}

.advice-text {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}

.advice-loading {
  text-align: center;
  padding: 60px 40px;
}

.spinner {
  width: 48px;
  height: 48px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

.advice-loading p {
  color: #666;
  font-size: 1rem;
  margin: 0;
}

.advice-empty {
  text-align: center;
  padding: 60px 40px;
  color: #999;
}

@media (max-width: 768px) {
  .assessment-container {
    padding: 20px 12px;
  }

  .start-page {
    padding: 40px 20px;
  }

  .start-page h1 {
    font-size: 2rem;
  }

  .intro {
    padding: 24px;
  }

  .question-card {
    padding: 24px;
  }

  .navigation {
    flex-direction: column;
  }

  .btn-nav,
  .btn-submit {
    width: 100%;
  }

  .dimension-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .score-bar {
    width: 100%;
  }

  .actions {
    flex-direction: column;
  }

  .btn-action {
    width: 100%;
  }
}
</style>
