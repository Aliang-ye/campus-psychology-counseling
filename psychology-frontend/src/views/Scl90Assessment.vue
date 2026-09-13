<template>
  <div class="scl90-container">
    <div class="back-btn" @click="goBack">← 返回心理测评</div>

    <div v-if="!started && !showResult" class="start-page">
      <h1>SCL-90 症状自评量表</h1>
      <div class="intro">
        <p>本量表共 90 题，按最近一周的实际感受作答。</p>
        <p>每题 1-5 分，分别对应：没有、很轻、中等、偏重、严重。</p>
        <p>系统将按 SCL-90 标准计算：总分、总均分、阳性项目数、阳性症状均分及 9 因子均分。</p>
      </div>
      <button class="btn-start" @click="startAssessment">开始 SCL-90 测评</button>
      <button class="btn-action" @click="goHistory">查看 SCL-90 历史</button>
      <div class="msg" v-if="msg">{{ msg }}</div>
    </div>

    <div v-if="started && !showResult" class="question-page">
      <div class="progress-bar">
        <div class="progress" :style="{ width: progressPercent + '%' }"></div>
      </div>
      <div class="progress-text">{{ currentIndex + 1 }} / {{ questions.length }}</div>

      <div v-if="currentQuestion" class="question-card">
        <h3>{{ currentQuestion.id }}. {{ currentQuestion.text }}</h3>
        <div class="options">
          <div
            v-for="(option, idx) in options"
            :key="idx"
            class="option-item"
            :class="{ selected: answers[currentQuestion.id] === idx + 1 }"
            @click="selectOption(idx + 1)"
          >
            {{ idx + 1 }} - {{ option }}
          </div>
        </div>
      </div>

      <div class="navigation">
        <button class="btn-nav" @click="prevQuestion" :disabled="currentIndex === 0">上一题</button>
        <button
          v-if="currentIndex < questions.length - 1"
          class="btn-nav"
          @click="nextQuestion"
          :disabled="!answers[currentQuestion.id]"
        >
          下一题
        </button>
        <button
          v-else
          class="btn-submit"
          @click="submitAssessment"
          :disabled="!isAllAnswered || submitting"
        >
          {{ submitting ? '提交中...' : '提交测评' }}
        </button>
      </div>
    </div>

    <div v-if="showResult" class="result-page">
      <h2>SCL-90 测评结果</h2>
      <div class="result-grid">
        <div class="result-card">
          <h4>总分</h4>
          <p>{{ result.totalScore }} / 450</p>
        </div>
        <div class="result-card">
          <h4>总均分</h4>
          <p>{{ result.totalAvg }}</p>
        </div>
        <div class="result-card">
          <h4>阳性项目数</h4>
          <p>{{ result.positiveCount }}</p>
        </div>
        <div class="result-card">
          <h4>阳性症状均分</h4>
          <p>{{ result.positiveAvg }}</p>
        </div>
      </div>

      <div class="summary" :class="{ warning: hasPositiveSignal }">
        {{ summaryText }}
      </div>

      <div class="factor-section">
        <h3>九因子均分</h3>
        <div class="factor-item" v-for="(score, name) in result.factorScores" :key="name">
          <span>{{ name }}</span>
          <div class="factor-bar">
            <div class="factor-fill" :style="{ width: (Math.min(score, 5) / 5 * 100) + '%' }"></div>
          </div>
          <strong>{{ score }}</strong>
        </div>
      </div>

      <div class="actions">
        <button class="btn-action" @click="restartAssessment">重新测评</button>
        <button class="btn-action" @click="goHistory">查看历史</button>
        <button class="btn-action" @click="goBack">返回原测评页</button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Scl90Assessment',
  data() {
    return {
      started: false,
      showResult: false,
      submitting: false,
      questions: [],
      options: ['没有', '很轻', '中等', '偏重', '严重'],
      currentIndex: 0,
      answers: {},
      startTime: null,
      result: null,
      msg: ''
    }
  },
  computed: {
    currentQuestion() {
      return this.questions[this.currentIndex]
    },
    progressPercent() {
      if (!this.questions.length) return 0
      return ((this.currentIndex + 1) / this.questions.length) * 100
    },
    isAllAnswered() {
      return this.questions.length > 0 && this.questions.every(q => this.answers[q.id] !== undefined)
    },
    hasPositiveSignal() {
      if (!this.result) return false
      const factorScores = Object.values(this.result.factorScores || {})
      return this.result.totalAvg >= 2 || factorScores.some(v => Number(v) >= 2)
    },
    summaryText() {
      if (!this.result) return ''
      if (this.hasPositiveSignal) {
        return '筛查提示：结果存在阳性信号（总均分>=2 或 因子均分>=2），建议结合专业人员进一步评估。'
      }
      return '筛查提示：本次结果整体处于较低风险区间，请持续关注自身状态。'
    }
  },
  methods: {
    goBack() {
      this.$router.push('/assessment')
    },
    goHistory() {
      this.$router.push({ path: '/assessment-history', query: { type: 'scl90' } })
    },
    async startAssessment() {
      this.msg = ''
      try {
        const res = await fetch('/api/scl90/questions')
        const data = await res.json()
        if (!data.success) {
          this.msg = data.message || '获取题目失败'
          return
        }
        this.questions = data.questions || []
        this.options = data.options || this.options
        if (this.questions.length !== 90) {
          this.msg = `SCL-90 题库数量异常，当前 ${this.questions.length} 题`
          return
        }
        this.started = true
        this.startTime = Date.now()
      } catch (e) {
        this.msg = '获取题目失败：' + e.message
      }
    },
    selectOption(value) {
      this.answers[this.currentQuestion.id] = value
    },
    nextQuestion() {
      if (this.currentIndex < this.questions.length - 1) {
        this.currentIndex += 1
      }
    },
    prevQuestion() {
      if (this.currentIndex > 0) {
        this.currentIndex -= 1
      }
    },
    async submitAssessment() {
      if (!this.isAllAnswered || this.submitting) return
      this.submitting = true
      this.msg = ''
      try {
        const userId = parseInt(sessionStorage.getItem('userId'))
        const username = sessionStorage.getItem('username')
        const testDuration = Math.floor((Date.now() - this.startTime) / 1000)
        const res = await fetch('/api/scl90/submit', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            userId,
            username,
            answers: this.answers,
            testDuration
          })
        })
        const data = await res.json()
        if (!data.success) {
          this.msg = data.message || '提交失败'
          return
        }
        this.result = data
        this.showResult = true
      } catch (e) {
        this.msg = '提交失败：' + e.message
      } finally {
        this.submitting = false
      }
    },
    restartAssessment() {
      this.started = false
      this.showResult = false
      this.submitting = false
      this.questions = []
      this.currentIndex = 0
      this.answers = {}
      this.startTime = null
      this.result = null
      this.msg = ''
    }
  }
}
</script>

<style scoped>
.scl90-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #eef3ff 0%, #f5fbff 50%, #fff4f1 100%);
  padding: 32px 16px;
}

.back-btn {
  display: inline-block;
  padding: 10px 14px;
  background: #fff;
  border-radius: 8px;
  color: #475bb5;
  cursor: pointer;
  font-weight: 600;
  margin-bottom: 20px;
  border: 1px solid #d6defe;
}

.start-page,
.question-page,
.result-page {
  max-width: 860px;
  margin: 0 auto;
}

.start-page h1,
.result-page h2 {
  text-align: center;
}

.intro {
  background: #fff;
  border: 1px solid #e3e8ff;
  border-radius: 12px;
  padding: 20px;
  margin: 20px 0;
  line-height: 1.8;
}

.msg {
  margin-top: 12px;
  color: #c62828;
}

.btn-start,
.btn-nav,
.btn-submit,
.btn-action {
  border: none;
  border-radius: 8px;
  padding: 10px 16px;
  cursor: pointer;
  font-weight: 600;
}

.btn-start,
.btn-submit,
.btn-action {
  background: linear-gradient(135deg, #4d63d3 0%, #6c4fb8 100%);
  color: #fff;
}

.progress-bar {
  width: 100%;
  height: 8px;
  background: #dfe5f7;
  border-radius: 5px;
  margin-bottom: 8px;
  overflow: hidden;
}

.progress {
  height: 100%;
  background: linear-gradient(90deg, #4d63d3 0%, #6c4fb8 100%);
}

.progress-text {
  text-align: center;
  color: #667;
  margin-bottom: 20px;
}

.question-card {
  background: #fff;
  border: 1px solid #e2e8ff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
}

.options {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  margin-top: 12px;
}

.option-item {
  padding: 12px;
  border: 1px solid #d7dff8;
  border-radius: 8px;
  background: #f9fbff;
  cursor: pointer;
}

.option-item.selected {
  border-color: #4d63d3;
  background: #eef2ff;
  color: #3349b0;
}

.navigation {
  display: flex;
  gap: 12px;
}

.btn-nav {
  background: #eef1f7;
  color: #333;
}

.btn-nav:disabled,
.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin: 18px 0;
}

.result-card {
  background: #fff;
  border: 1px solid #e2e8ff;
  border-radius: 10px;
  padding: 14px;
  text-align: center;
}

.summary {
  background: #e8f5e9;
  border: 1px solid #b7dfbe;
  border-radius: 10px;
  padding: 12px;
  color: #1b5e20;
  margin-bottom: 20px;
}

.summary.warning {
  background: #fff3e0;
  border-color: #ffcc80;
  color: #e65100;
}

.factor-section {
  background: #fff;
  border: 1px solid #e2e8ff;
  border-radius: 12px;
  padding: 14px;
}

.factor-item {
  display: grid;
  grid-template-columns: 130px 1fr 50px;
  gap: 10px;
  align-items: center;
  margin: 10px 0;
}

.factor-bar {
  height: 8px;
  background: #e4e9f9;
  border-radius: 8px;
  overflow: hidden;
}

.factor-fill {
  height: 100%;
  background: linear-gradient(90deg, #4d63d3 0%, #6c4fb8 100%);
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

@media (max-width: 640px) {
  .factor-item {
    grid-template-columns: 110px 1fr 44px;
  }

  .actions,
  .navigation {
    flex-direction: column;
  }
}
</style>
