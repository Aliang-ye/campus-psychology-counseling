<template>
  <div class="history-container">
    <div class="header">
      <h1>测评历史记录</h1>
      <div class="controls">
        <!-- 教师/管理员搜索功能 -->
        <div v-if="isTeacherOrAdmin" class="search-box">
          <input 
            v-model="searchUsername" 
            placeholder="搜索学生用户名..."
            @keyup.enter="searchStudent"
          />
          <button @click="searchStudent" class="btn-search">搜索</button>
        </div>

        <!-- 管理员的批量删除功能 -->
        <div v-if="isAdmin" class="admin-controls">
          <button 
            v-if="selectedRecords.length > 0" 
            @click="batchDelete" 
            class="btn-delete-batch"
          >
            删除选中 ({{ selectedRecords.length }})
          </button>
        </div>

        <!-- 返回按钮 -->
        <button @click="goBack" class="btn-back">返回</button>
      </div>
    </div>

    <div class="history-tabs">
      <button
        class="tab-btn"
        :class="{ active: activeHistoryType === 'default' }"
        @click="switchHistoryType('default')"
      >
        常规测评历史
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeHistoryType === 'scl90' }"
        @click="switchHistoryType('scl90')"
      >
        SCL-90 历史
      </button>
    </div>

    <!-- 记录列表 -->
    <div class="records-table">
      <div v-if="loading" class="loading">加载中...</div>
      
      <div v-else-if="displayedRecords.length === 0" class="empty-state">
        <p>暂无测评记录</p>
      </div>

      <div v-else-if="activeHistoryType === 'default'" class="table-wrapper">
        <table>
          <thead>
            <tr>
              <!-- 管理员显示复选框 -->
              <th v-if="isAdmin" class="checkbox-col">
                <input 
                  type="checkbox" 
                  :checked="allSelected"
                  @change="toggleSelectAll"
                />
              </th>
              <th>测评时间</th>
              <th>用户名</th>
              <th>总分 / 150</th>
              <th>结果等级</th>
              <th>学业压力</th>
              <th>宿舍关系</th>
              <th>考试焦虑</th>
              <th>就业压力</th>
              <th>恋爱问题</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records" :key="record.id" class="record-row">
              <!-- 管理员显示复选框 -->
              <td v-if="isAdmin" class="checkbox-col">
                <input 
                  type="checkbox" 
                  :checked="selectedRecords.includes(record.id)"
                  @change="toggleSelect(record.id)"
                />
              </td>
              <td>{{ formatDate(record.createdAt) }}</td>
              <td>{{ record.username }}</td>
              <td class="score-cell" :class="'score-' + getLevel(record.totalScore)">
                {{ record.totalScore }}
              </td>
              <td class="level-cell" :class="'level-' + getLevel(record.totalScore)">
                {{ getScoreLevel(record.totalScore) }}
              </td>
              <td>{{ record.dimensionAScore }}</td>
              <td>{{ record.dimensionBScore }}</td>
              <td>{{ record.dimensionCScore }}</td>
              <td>{{ record.dimensionDScore }}</td>
              <td>{{ record.dimensionEScore }}</td>
              <td class="action-cell">
                <button @click="viewDetail(record.id)" class="btn-detail">详情</button>
                <button 
                  v-if="isAdmin" 
                  @click="deleteRecord(record.id)" 
                  class="btn-delete-single"
                >
                  删除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="table-wrapper">
        <table>
          <thead>
            <tr>
              <th v-if="isAdmin" class="checkbox-col">
                <input
                  type="checkbox"
                  :checked="allSelected"
                  @change="toggleSelectAll"
                />
              </th>
              <th>测评时间</th>
              <th>用户名</th>
              <th>总分 / 450</th>
              <th>总均分</th>
              <th>阳性项目数</th>
              <th>阳性症状均分</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in scl90Records" :key="record.id" class="record-row">
              <td v-if="isAdmin" class="checkbox-col">
                <input
                  type="checkbox"
                  :checked="selectedRecords.includes(record.id)"
                  @change="toggleSelect(record.id)"
                />
              </td>
              <td>{{ formatDate(record.createdAt) }}</td>
              <td>{{ record.username }}</td>
              <td class="score-cell" :class="'score-' + getScl90Level(record.totalAvg)">
                {{ record.totalScore }}
              </td>
              <td>{{ record.totalAvg }}</td>
              <td>{{ record.positiveCount }}</td>
              <td>{{ record.positiveAvg }}</td>
              <td class="action-cell">
                <button @click="viewDetail(record.id)" class="btn-detail">详情</button>
                <button
                  v-if="isAdmin"
                  @click="deleteRecord(record.id)"
                  class="btn-delete-single"
                >
                  删除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 详情模态框 - 使用更直观的结果页面风格 -->
    <div v-if="showDetailModal" class="modal-overlay" @click="closeDetail">
      <div class="modal-content detail-modal" @click.stop>
        <button @click="closeDetail" class="btn-close-modal">×</button>
        
        <div v-if="detailType === 'default' && currentDetail && currentDetail.record" class="detail-result-page">
          <h2>测评详情</h2>
          
          <div class="total-score">
            <h3>总分：{{ currentDetail.record.totalScore }} / 150</h3>
            <p class="score-level" :class="getDetailLevelClass(currentDetail.record.totalScore)">
              {{ getDetailScoreLevel(currentDetail.record.totalScore) }}
            </p>
          </div>

          <div class="dimension-scores">
            <h3>各维度得分</h3>
            <div class="dimension-item" v-for="(name, key) in dimensionNames" :key="key">
              <span class="dimension-name">{{ name }}</span>
              <div class="score-bar">
                <div 
                  class="score-fill" 
                  :style="{ width: (getDimensionScore(key) / 30 * 100) + '%', backgroundColor: getDimensionColor(name) }"
                ></div>
              </div>
              <span class="dimension-score">{{ getDimensionScore(key) }} / 30</span>
            </div>
          </div>

          <div class="actions">
            <button @click="closeDetail" class="btn-action">关闭</button>
          </div>

          <div class="advice-section">
            <h3>AI 建议</h3>
            <div class="advice-card" v-if="currentDetail.adviceOverall">
              <p class="advice-text">{{ currentDetail.adviceOverall }}</p>
            </div>
            <div class="advice-empty" v-else>
              <p>暂无建议</p>
            </div>
          </div>
        </div>

        <div v-else-if="detailType === 'scl90' && currentDetail && currentDetail.record" class="detail-result-page">
          <h2>SCL-90 详情</h2>

          <div class="total-score">
            <h3>总分：{{ currentDetail.record.totalScore }} / 450</h3>
            <p class="score-level" :class="getScl90LevelClass(currentDetail.record.totalAvg)">
              {{ getScl90LevelText(currentDetail.record.totalAvg) }}
            </p>
            <p style="margin-top: 12px; color: #666;">总均分：{{ currentDetail.record.totalAvg }}</p>
            <p style="color: #666;">阳性项目数：{{ currentDetail.record.positiveCount }}，阳性症状均分：{{ currentDetail.record.positiveAvg }}</p>
          </div>

          <div class="dimension-scores">
            <h3>九因子均分</h3>
            <div class="dimension-item" v-for="(name, key) in scl90FactorNames" :key="key">
              <span class="dimension-name">{{ name }}</span>
              <div class="score-bar">
                <div
                  class="score-fill"
                  :style="{ width: (getScl90FactorScore(key) / 5 * 100) + '%', backgroundColor: '#5c6bc0' }"
                ></div>
              </div>
              <span class="dimension-score">{{ getScl90FactorScore(key) }}</span>
            </div>
          </div>

          <div class="actions">
            <button @click="closeDetail" class="btn-action">关闭</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AssessmentHistory',
  data() {
    return {
      activeHistoryType: 'default',
      records: [],
      scl90Records: [],
      currentDetail: null,
      detailType: 'default',
      showDetailModal: false,
      loading: false,
      historyRequestSeq: 0,
      searchUsername: '',
      selectedRecords: [],
      questionsMap: {},
      options: ['没有', '很轻', '中等', '偏重', '严重'],
      dimensionNames: {
        'dimensionAScore': '学业压力',
        'dimensionBScore': '宿舍关系',
        'dimensionCScore': '考试焦虑',
        'dimensionDScore': '就业压力',
        'dimensionEScore': '恋爱问题'
      },
      scl90FactorNames: {
        f1Somatization: '躯体化',
        f2Obsession: '强迫症状',
        f3Interpersonal: '人际关系敏感',
        f4Depression: '抑郁',
        f5Anxiety: '焦虑',
        f6Hostility: '敌对',
        f7Phobia: '恐怖',
        f8Paranoia: '偏执',
        f9Psychosis: '精神病性'
      }
    };
  },
  computed: {
    userRole() {
      return sessionStorage.getItem('role') || '';
    },
    userId() {
      return parseInt(sessionStorage.getItem('userId') || 0);
    },
    isStudent() {
      return this.userRole === 'student';
    },
    isTeacher() {
      return this.userRole === 'teacher' || this.userRole === 'doctor';
    },
    isAdmin() {
      return this.userRole === 'admin';
    },
    isTeacherOrAdmin() {
      return this.isTeacher || this.isAdmin;
    },
    displayedRecords() {
      return this.activeHistoryType === 'default' ? this.records : this.scl90Records;
    },
    allSelected() {
      return this.displayedRecords.length > 0 && this.selectedRecords.length === this.displayedRecords.length;
    }
  },
  methods: {
    switchHistoryType(type) {
      if (this.activeHistoryType === type) return;
      this.activeHistoryType = type;
      this.selectedRecords = [];
      this.searchUsername = '';
      this.loadHistory();
    },

    async loadHistory() {
      const requestType = this.activeHistoryType;
      const requestSeq = ++this.historyRequestSeq;
      this.loading = true;
      try {
        let url = '';

        if (requestType === 'default') {
          if (this.isStudent) {
            url = `/api/assessment/history/${this.userId}`;
          } else if (this.isTeacher) {
            url = '/api/assessment/history/all?page=0&size=50';
          } else if (this.isAdmin) {
            url = '/api/assessment/history/all?page=0&size=100';
          }
        } else {
          if (this.isStudent) {
            url = `/api/scl90/history/${this.userId}`;
          } else if (this.isTeacher) {
            url = '/api/scl90/history/all?page=0&size=50';
          } else if (this.isAdmin) {
            url = '/api/scl90/history/all?page=0&size=100';
          }
        }

        if (!url) {
          alert('当前角色无法查看该类型的测评历史');
          return;
        }

        const response = await fetch(url);
        const data = await response.json();
        if (requestSeq !== this.historyRequestSeq) return;

        if (data.success) {
          if (requestType === 'default') {
            this.records = data.records || [];
          } else {
            this.scl90Records = data.records || [];
          }
        } else {
          alert('加载失败: ' + data.message);
        }
      } catch (error) {
        if (requestSeq !== this.historyRequestSeq) return;
        console.error('加载失败:', error);
        alert('加载失败，请检查网络连接');
      } finally {
        if (requestSeq === this.historyRequestSeq) {
          this.loading = false;
        }
      }
    },

    async searchStudent() {
      if (!this.searchUsername.trim()) {
        alert('请输入学生用户名');
        return;
      }

      this.loading = true;
      try {
        const endpoint = this.activeHistoryType === 'default'
          ? `/api/assessment/history/search?username=${encodeURIComponent(this.searchUsername)}`
          : `/api/scl90/history/search?username=${encodeURIComponent(this.searchUsername)}`;
        const response = await fetch(endpoint);
        const data = await response.json();

        if (data.success) {
          if (this.activeHistoryType === 'default') {
            this.records = data.records || [];
          } else {
            this.scl90Records = data.records || [];
          }
        } else {
          alert('搜索失败: ' + data.message);
        }
      } catch (error) {
        console.error('搜索失败:', error);
        alert('搜索失败，请检查网络连接');
      } finally {
        this.loading = false;
      }
    },

    async viewDetail(recordId) {
      try {
        const endpoint = this.activeHistoryType === 'default'
          ? `/api/assessment/detail/${recordId}`
          : `/api/scl90/detail/${recordId}`;
        const response = await fetch(endpoint);
        const data = await response.json();

        if (data.success) {
          this.currentDetail = data;
          this.detailType = this.activeHistoryType;
          this.showDetailModal = true;
        } else {
          alert('加载详情失败: ' + data.message);
        }
      } catch (error) {
        console.error('加载详情失败:', error);
        alert('加载详情失败');
      }
    },

    closeDetail() {
      this.showDetailModal = false;
      this.currentDetail = null;
      this.detailType = 'default';
    },

    async deleteRecord(recordId) {
      if (!confirm('确定要删除这条记录吗？')) return;

      try {
        const endpoint = this.activeHistoryType === 'default'
          ? `/api/assessment/delete/${recordId}`
          : `/api/scl90/delete/${recordId}`;
        const response = await fetch(endpoint, { method: 'DELETE' });
        const data = await response.json();

        if (data.success) {
          alert('删除成功');
          this.loadHistory();
        } else {
          alert('删除失败: ' + data.message);
        }
      } catch (error) {
        console.error('删除失败:', error);
        alert('删除失败');
      }
    },

    toggleSelect(recordId) {
      const index = this.selectedRecords.indexOf(recordId);
      if (index > -1) {
        this.selectedRecords.splice(index, 1);
      } else {
        this.selectedRecords.push(recordId);
      }
    },

    toggleSelectAll() {
      if (this.allSelected) {
        this.selectedRecords = [];
      } else {
        this.selectedRecords = this.displayedRecords.map(r => r.id);
      }
    },

    async batchDelete() {
      if (this.selectedRecords.length === 0) {
        alert('请先选择要删除的记录');
        return;
      }

      if (!confirm(`确定要删除选中的 ${this.selectedRecords.length} 条记录吗？`)) {
        return;
      }

      try {
        const endpoint = this.activeHistoryType === 'default'
          ? '/api/assessment/delete/batch'
          : '/api/scl90/delete/batch';
        const response = await fetch(
          endpoint,
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ recordIds: this.selectedRecords })
          }
        );
        const data = await response.json();

        if (data.success) {
          alert(`删除成功，共删除 ${data.deletedCount} 条记录`);
          this.selectedRecords = [];
          this.loadHistory();
        } else {
          alert('删除失败: ' + data.message);
        }
      } catch (error) {
        console.error('批量删除失败:', error);
        alert('批量删除失败');
      }
    },

    formatDate(timestamp) {
      if (!timestamp) return '-';
      const date = new Date(timestamp);
      return date.toLocaleString('zh-CN');
    },

    getScoreLevel(score) {
      if (score <= 45) return '正常';
      if (score <= 75) return '轻度';
      if (score <= 105) return '中度';
      return '重度';
    },

    getLevel(score) {
      if (score <= 45) return 'normal';
      if (score <= 75) return 'mild';
      if (score <= 105) return 'moderate';
      return 'severe';
    },

    getDetailScoreLevel(score) {
      if (score <= 45) return '正常';
      if (score <= 75) return '轻度';
      if (score <= 105) return '中度';
      return '重度';
    },

    getDetailLevelClass(score) {
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

    getDimensionScore(key) {
      if (!this.currentDetail) return 0;
      return this.currentDetail.record[key] || 0;
    },

    getScl90FactorScore(key) {
      if (!this.currentDetail || !this.currentDetail.record) return 0;
      const val = this.currentDetail.record[key];
      return typeof val === 'number' ? val : Number(val || 0);
    },

    getScl90Level(avg) {
      const v = Number(avg || 0);
      if (v < 1.5) return 'normal';
      if (v < 2) return 'mild';
      if (v < 3) return 'moderate';
      return 'severe';
    },

    getScl90LevelText(avg) {
      const v = Number(avg || 0);
      if (v < 1.5) return '低风险';
      if (v < 2) return '轻度风险';
      if (v < 3) return '中度风险';
      return '高风险';
    },

    getScl90LevelClass(avg) {
      return `level-${this.getScl90Level(avg)}`;
    },

    getQuestionContent(questionId) {
      return this.questionsMap[questionId] || questionId;
    },

    goBack() {
      this.$router.push('/main');
    }
  },

  mounted() {
    if (this.$route.query.type === 'scl90') {
      this.activeHistoryType = 'scl90';
    }
    this.loadHistory();
  }
};
</script>

<style scoped>
.history-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f5ff 0%, #fff5f0 100%);
  padding: 40px 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 40px;
  flex-wrap: wrap;
  gap: 20px;
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
}

.header h1 {
  font-size: 2.2rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  font-weight: 900;
  margin: 0;
}

.controls {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.history-tabs {
  max-width: 1400px;
  margin: 0 auto 20px auto;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.tab-btn {
  padding: 10px 18px;
  border: 1px solid #cfd8ff;
  border-radius: 999px;
  background: #f5f7ff;
  color: #5466c7;
  cursor: pointer;
  font-weight: 700;
  transition: all 0.25s ease;
}

.tab-btn.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-color: transparent;
}

.search-box {
  display: flex;
  gap: 10px;
}

.search-box input {
  padding: 10px 16px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 0.95rem;
  transition: all 0.3s ease;
}

.search-box input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.btn-search,
.btn-back,
.btn-delete-batch {
  padding: 10px 20px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 0.95rem;
  font-weight: 600;
}

.btn-search {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-search:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-back {
  background: white;
  color: #667eea;
  border: 1px solid #ddd;
}

.btn-back:hover {
  background: #f8f9ff;
  border-color: #667eea;
}

.btn-delete-batch {
  background: linear-gradient(135deg, #f44336 0%, #da190b 100%);
  color: white;
}

.btn-delete-batch:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(244, 67, 54, 0.4);
}

.admin-controls {
  display: flex;
  gap: 10px;
}

.loading,
.empty-state {
  text-align: center;
  padding: 60px 40px;
  color: #999;
  font-size: 1.1rem;
  background: white;
  border-radius: 16px;
  margin: 0 auto;
  max-width: 1400px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
}

.table-wrapper {
  background: white;
  border-radius: 16px;
  overflow: auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.05);
  max-width: 1400px;
  margin: 0 auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.95rem;
}

thead tr {
  background: linear-gradient(135deg, #f8f9ff 0%, #f0f5ff 100%);
  border-bottom: 2px solid #667eea;
}

th {
  padding: 16px;
  text-align: left;
  font-weight: 700;
  color: #333;
}

td {
  padding: 14px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.checkbox-col {
  width: 40px;
  text-align: center;
}

.checkbox-col input {
  cursor: pointer;
}

tbody tr {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

tbody tr:hover {
  background: #fafbff;
  transform: translateY(-2px);
}

.score-cell,
.level-cell {
  font-weight: 700;
}

.score-normal,
.level-normal {
  color: #4caf50;
}

.score-mild,
.level-mild {
  color: #ffc107;
}

.score-moderate,
.level-moderate {
  color: #ff9800;
}

.score-severe,
.level-severe {
  color: #f44336;
}

.action-cell {
  display: flex;
  gap: 8px;
}

.btn-detail,
.btn-delete-single {
  padding: 8px 14px;
  font-size: 0.85rem;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-weight: 600;
}

.btn-detail {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-detail:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-delete-single {
  background: #f44336;
  color: white;
}

.btn-delete-single:hover {
  background: #d32f2f;
  transform: translateY(-2px);
}

/* 模态框 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  padding: 20px;
  animation: fadeIn 0.3s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.modal-content.detail-modal {
  background: white;
  border-radius: 16px;
  max-width: 900px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 16px 64px rgba(0, 0, 0, 0.2);
  width: 100%;
  position: relative;
}

.btn-close-modal {
  position: absolute;
  top: 20px;
  right: 20px;
  background: none;
  border: none;
  font-size: 28px;
  color: #999;
  cursor: pointer;
  z-index: 10;
  transition: all 0.3s ease;
}

.btn-close-modal:hover {
  color: #333;
  transform: rotate(90deg);
}

.detail-result-page {
  padding: 40px;
  max-width: 800px;
  margin: 0 auto;
}

.detail-result-page h2 {
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
  background: linear-gradient(135deg, #f8f9ff 0%, #fff5f0 100%);
  padding: 40px;
  border-radius: 16px;
  margin-bottom: 40px;
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.1);
  border: 1px solid rgba(102, 126, 234, 0.1);
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
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
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

.btn-action {
  padding: 12px 32px;
  font-size: 1rem;
  border: 2px solid #667eea;
  background: white;
  color: #667eea;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-weight: 600;
}

.btn-action:hover {
  background: #667eea;
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.35);
}

.advice-section {
  background: white;
  padding: 40px;
  border-radius: 16px;
  margin-top: 0;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
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
  padding: 20px;
  border-radius: 10px;
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

.advice-empty {
  text-align: center;
  padding: 60px 40px;
  color: #999;
}

.modal-body {
  padding: 20px;
}

.detail-section {
  margin-bottom: 30px;
}

.detail-section h3 {
  font-size: 1.1rem;
  margin-bottom: 16px;
  color: #333;
  font-weight: 700;
  border-bottom: 2px solid #667eea;
  padding-bottom: 10px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: #f8f9ff;
  padding: 12px;
  border-radius: 8px;
}

.info-item .label {
  font-weight: 600;
  color: #999;
  font-size: 0.85rem;
}

.info-item .value {
  font-size: 1rem;
  color: #333;
  font-weight: 600;
}

.score-badge {
  display: inline-block;
  padding: 8px 16px;
  border-radius: 20px;
  color: white;
  font-weight: 600;
}

.score-badge.level-normal {
  background: #4caf50;
}

.score-badge.level-mild {
  background: #ffc107;
}

.score-badge.level-moderate {
  background: #ff9800;
}

.score-badge.level-severe {
  background: #f44336;
}

.dimension-chart {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dimension-bar {
  display: flex;
  align-items: center;
  gap: 16px;
}

.dim-name {
  min-width: 120px;
  font-weight: 700;
  color: #333;
}

.bar-container {
  flex: 1;
  height: 32px;
  background: #f0f0f0;
  border-radius: 16px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 16px;
  transition: width 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.dim-a {
  background: linear-gradient(90deg, #4caf50, #45a049);
}

.dim-b {
  background: linear-gradient(90deg, #2196f3, #1976d2);
}

.dim-c {
  background: linear-gradient(90deg, #ffc107, #ffb300);
}

.dim-d {
  background: linear-gradient(90deg, #ff9800, #f57c00);
}

.dim-e {
  background: linear-gradient(90deg, #e91e63, #c2185b);
}

.dim-score {
  min-width: 60px;
  text-align: right;
  font-weight: 700;
  color: #667eea;
}

.answers-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.answer-item {
  padding: 14px;
  background: #f8f9ff;
  border-left: 4px solid #667eea;
  border-radius: 8px;
}

.question-info {
  margin-bottom: 8px;
  color: #333;
  font-weight: 600;
}

.answer-info {
  display: flex;
  gap: 20px;
  font-size: 0.9rem;
  color: #666;
}

.selected-option {
  color: #667eea;
  font-weight: 700;
}

.score {
  color: #4caf50;
  font-weight: 700;
}

@media (max-width: 768px) {
  .history-container {
    padding: 20px 16px;
  }

  .header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header h1 {
    font-size: 1.8rem;
  }

  .controls {
    width: 100%;
    flex-direction: column;
  }

  .search-box {
    width: 100%;
  }

  .search-box input {
    flex: 1;
  }

  table {
    font-size: 0.85rem;
  }

  th,
  td {
    padding: 10px;
  }

  .action-cell {
    flex-direction: column;
  }

  .detail-result-page {
    padding: 20px;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .dimension-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
