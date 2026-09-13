<template>
  <div class="consult-ai-container">
    <div class="back-btn" @click="goBack">← 返回主页</div>
    <div class="chat-header">
      <h2>AI心理小助手</h2>
      <p class="header-subtitle">与AI进行心理咨询对话</p>
      <p class="header-disclaimer">AI仅供参考，详细请咨询专家</p>
    </div>

    <div v-if="loadingHistory" class="chat-loading">
      <div class="spinner"></div>
      <p>正在加载会话历史...</p>
    </div>

    <div v-else class="chat-messages" ref="chatMessages">
      <div
        v-for="msg in messages"
        :key="msg.id"
        :class="['message', msg.role]"
      >
        <div class="message-avatar">
          {{ msg.role === 'user' ? '你' : 'AI' }}
        </div>
        <div class="message-content">
          {{ msg.text }}
          <span v-if="msg.loading" class="typing-indicator">●●●</span>
        </div>
      </div>
      <div ref="messagesEnd"></div>
    </div>

    <div class="input-area">
      <div v-if="crisisDetected" class="crisis-banner">
        <div class="crisis-text">
          检测到高风险情绪表达，建议立即申请心理专家支持。
        </div>
        <button
          class="btn-crisis"
          @click="requestExpertConsult"
          :disabled="requestingExpert || loadingHistory"
        >
          {{ requestingExpert ? '提交中...' : '申请与心理专家对话' }}
        </button>
      </div>
      <div v-if="expertRequestMsg" class="crisis-msg">{{ expertRequestMsg }}</div>

      <textarea
        v-model="userInput"
        placeholder="输入你的想法或问题..."
        @keydown.enter.ctrl="sendMessage"
        :disabled="loading || loadingHistory"
        rows="3"
      ></textarea>
      <div class="button-group">
        <button @click="sendMessage" :disabled="loading || loadingHistory || !userInput.trim()">
          {{ loading ? '等待回复中...' : '发送 (Ctrl+Enter)' }}
        </button>
        <button @click="clearChat" :disabled="loading || loadingHistory" class="btn-secondary">
          清空对话
        </button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AiAssistant',
  data() {
    return {
      messages: [],
      userInput: '',
      loading: false,
      sessionId: null,
      userId: null,
      messageIdCounter: 0,
      loadingHistory: true,  // 正在加载历史记录
      crisisDetected: false,
      requestingExpert: false,
      expertRequestMsg: '',
      scrollRafId: null,
      streamAbort: null,
      streamIdleTimer: null,
    };
  },
  methods: {
    goBack() {
      this.$router.push('/main');
    },
    /**
     * 初始化：创建或获取会话，加载历史记录
     */
    async initSession() {
      try {
        const userIdStr = sessionStorage.getItem('userId');
        // 处理 'undefined' 或 'null' 字符串值
        if (!userIdStr || userIdStr === 'undefined' || userIdStr === 'null') {
          alert('请先登录');
          this.$router.push('/login');
          return;
        }
        this.userId = parseInt(userIdStr);
        if (!this.userId || isNaN(this.userId)) {
          alert('请先登录');
          this.$router.push('/login');
          return;
        }

        // 创建/获取会话
        const response = await fetch('/api/consult/ai/session', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ userId: this.userId })
        });

        const data = await response.json();
        if (data.success) {
          this.sessionId = data.sessionId;
          await this.loadHistory();
          await this.loadExpertRequestStatus();
        } else {
          alert('创建会话失败：' + data.message);
        }
      } catch (error) {
        console.error('初始化会话失败:', error);
        alert('初始化会话失败，请检查网络连接');
      } finally {
        this.loadingHistory = false;
      }
    },

    /**
     * 从数据库加载聊天历史
     */
    async loadHistory() {
      try {
        const response = await fetch(`/api/consult/ai/history?sessionId=${this.sessionId}`);
        const data = await response.json();
        if (data.success) {
          this.messages = (data.messages || []).map((msg, idx) => ({
            id: idx + 1,
            role: msg.sender_id === 0 ? 'ai' : 'user',
            text: msg.content,
            loading: false,
          }));
          this.messageIdCounter = this.messages.length;
          this.scrollToBottom('instant');
        }
      } catch (error) {
        console.error('加载历史记录失败:', error);
      }
    },

    /**
     * 发送消息：添加本地消息 → 数据库存储 + AI 响应流式输出
     */
    sendMessage() {
      const text = this.userInput.trim();
      if (!text || this.loading) return;

      // 立即添加用户消息到 UI
      const userMsg = {
        id: ++this.messageIdCounter,
        role: 'user',
        text,
        loading: false,
      };
      this.messages.push(userMsg);
      this.userInput = '';
      this.scrollToBottom('smooth');

      // 添加 AI 回复占位符
      const aiMessageId = ++this.messageIdCounter;
      const aiMsg = {
        id: aiMessageId,
        role: 'ai',
        text: '',
        loading: true,
      };
      this.messages.push(aiMsg);

      this.loading = true;
      this.streamChat(text, aiMessageId);
    },

    /**
     * 流式聊天：调用后端 /chat 接口（自动保存消息到数据库）
     */
    async streamChat(userMessage, aiMessageId) {
      const aiMsg = this.messages.find(m => m.id === aiMessageId);
      if (!aiMsg) return;

      this.stopStream();
      const controller = new AbortController();
      this.streamAbort = controller;
      let finished = false;

      const finish = (text, isError = false) => {
        if (finished) return;
        finished = true;
        if (this.streamIdleTimer) {
          clearTimeout(this.streamIdleTimer);
          this.streamIdleTimer = null;
        }
        if (text) aiMsg.text += text;
        aiMsg.loading = false;
        this.loading = false;
        controller.abort();
        this.streamAbort = null;
        if (isError) this.scrollToBottom('smooth');
      };

      const resetIdleTimer = () => {
        if (this.streamIdleTimer) clearTimeout(this.streamIdleTimer);
        this.streamIdleTimer = setTimeout(() => {
          finish('\n[请求超时，请重试]', true);
        }, 45000);
      };

      try {
        resetIdleTimer();
        const response = await fetch('/api/consult/ai/chat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            sessionId: this.sessionId,
            userId: this.userId,
            message: userMessage
          }),
          signal: controller.signal
        });

        if (!response.ok || !response.body) {
          finish('\n[连接失败，请重试]', true);
          return;
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const events = buffer.split('\n\n');
          buffer = events.pop() || '';
          for (const event of events) {
            const dataLines = event
              .split('\n')
              .filter(line => line.startsWith('data:'))
              .map(line => line.slice(5).trimStart());
            if (!dataLines.length) continue;
            const chunk = dataLines.join('\n');
            resetIdleTimer();
            if (chunk === '[CRISIS_SIGNAL]') {
              this.crisisDetected = true;
            } else if (chunk === '[完成]') {
              finish();
              this.scrollToBottom('smooth');
              return;
            } else if (chunk.startsWith('[错误]')) {
              finish(chunk, true);
              return;
            } else {
              aiMsg.text += chunk;
              this.scheduleStreamScroll();
            }
          }
        }
        finish();
      } catch (error) {
        if (error.name !== 'AbortError') {
          console.error('SSE 连接错误:', error);
          finish('\n[连接中断，请重试]', true);
        }
      }
    },

    stopStream() {
      if (this.streamIdleTimer) {
        clearTimeout(this.streamIdleTimer);
        this.streamIdleTimer = null;
      }
      if (this.streamAbort) {
        this.streamAbort.abort();
        this.streamAbort = null;
      }
    },

    async loadExpertRequestStatus() {
      if (!this.userId) return;
      try {
        const response = await fetch('/api/consult/ai/request-status');
        const data = await response.json();
        if (!data.success) return;

        if (data.hasRequest) {
          if (data.status === 'PENDING') {
            this.expertRequestMsg = '您已提交专家申请，当前状态：待处理';
          } else if (data.status === 'APPROVED') {
            this.expertRequestMsg = `您的专家申请已通过，咨询师：${data.consultantName || '待分配'}`;
          } else if (data.status === 'REJECTED') {
            this.expertRequestMsg = '您的上一次专家申请未通过，如仍需要可再次申请';
          }
        }
      } catch (error) {
        console.error('加载专家申请状态失败:', error);
      }
    },

    async requestExpertConsult() {
      if (!this.userId || this.requestingExpert) return;
      this.requestingExpert = true;
      this.expertRequestMsg = '';
      try {
        const username = sessionStorage.getItem('username') || '未知用户';
        const latestUserMsg = [...this.messages].reverse().find(m => m.role === 'user');
        const reason = latestUserMsg
          ? `危机对话触发申请。最近用户表达：${latestUserMsg.text.slice(0, 120)}`
          : '危机对话触发申请，请尽快介入';

        const response = await fetch('/api/consult/ai/request-expert', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ userId: this.userId, username, reason })
        });
        const data = await response.json();
        if (data.success) {
          this.expertRequestMsg = data.message || '申请已提交，请等待心理专家回复';
        } else {
          this.expertRequestMsg = data.message || '申请失败，请稍后重试';
        }
      } catch (error) {
        console.error('申请专家失败:', error);
        this.expertRequestMsg = '申请失败，请检查网络连接';
      } finally {
        this.requestingExpert = false;
      }
    },

    /**
     * 清空对话：从数据库删除该会话的所有消息
     */
    async clearChat() {
      if (!confirm('确定要清空所有对话吗？')) return;

      try {
        const response = await fetch(`/api/consult/ai/history?sessionId=${this.sessionId}`, {
          method: 'DELETE'
        });

        const data = await response.json();
        if (data.success) {
          this.messages = [];
          this.messageIdCounter = 0;
          this.userInput = '';
        } else {
          alert('清空对话失败：' + data.message);
        }
      } catch (error) {
        console.error('清空对话失败:', error);
        alert('清空对话失败，请检查网络连接');
      }
    },

    scheduleStreamScroll() {
      if (this.scrollRafId) return;
      this.scrollRafId = requestAnimationFrame(() => {
        this.scrollRafId = null;
        this.scrollToBottom('instant');
      });
    },

    scrollToBottom(mode = 'smooth') {
      this.$nextTick(() => {
        const container = this.$refs.chatMessages;
        if (!container) return;

        if (mode === 'instant') {
          container.scrollTop = container.scrollHeight;
          return;
        }

        if (this.$refs.messagesEnd) {
          this.$refs.messagesEnd.scrollIntoView({ behavior: 'smooth', block: 'end' });
        }
      });
    },
  },
  mounted() {
    this.initSession();
  },
  beforeUnmount() {
    this.stopStream();
  },

};
</script>

<style scoped>
.consult-ai-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial,
    sans-serif;
}

.chat-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 20px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.chat-header h2 {
  margin: 0 0 5px 0;
  font-size: 24px;
}

.back-btn {
  display: inline-block;
  padding: 10px 16px;
  background: white;
  border-radius: 8px;
  color: #667eea;
  cursor: pointer;
  font-weight: 600;
  margin: 20px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
  border: 1px solid rgba(102, 126, 234, 0.15);
  position: absolute;
  top: 0;
  left: 0;
  z-index: 10;
}

.back-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.3);
  background: #f8f9ff;
}

.header-subtitle {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

.header-disclaimer {
  margin: 8px 0 0;
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.95);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  scroll-behavior: auto;
  overflow-anchor: none;
}

.chat-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  color: #666;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.message {
  display: flex;
  gap: 10px;
  animation: slideIn 0.3s ease-out;
}

.message.user {
  justify-content: flex-end;
}

.message.ai {
  justify-content: flex-start;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  color: white;
  font-size: 12px;
  flex-shrink: 0;
}

.message.user .message-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  order: 2;
}

.message.ai .message-avatar {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
  font-size: 14px;
}

.message.user .message-content {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-bottom-right-radius: 4px;
}

.message.ai .message-content {
  background: white;
  color: #333;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.typing-indicator {
  animation: typingBlink 1.4s infinite;
  font-size: 12px;
  margin-left: 4px;
}

@keyframes typingBlink {
  0%,
  60%,
  100% {
    opacity: 0.7;
  }
  30% {
    opacity: 1;
  }
}

.input-area {
  padding: 20px;
  background: white;
  border-top: 1px solid #e0e0e0;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
}

.crisis-banner {
  margin-bottom: 12px;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid #ffcc80;
  background: #fff8e1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.crisis-text {
  color: #8d4f00;
  font-size: 13px;
  font-weight: 600;
}

.btn-crisis {
  flex: 0 0 auto;
  background: linear-gradient(135deg, #ef5350 0%, #c62828 100%);
  color: #fff;
  padding: 10px 14px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 700;
}

.btn-crisis:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.crisis-msg {
  margin-bottom: 10px;
  color: #2e7d32;
  font-size: 13px;
  font-weight: 600;
}

.input-area textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-family: inherit;
  font-size: 14px;
  resize: none;
  transition: border-color 0.3s;
}

.input-area textarea:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.input-area textarea:disabled {
  background: #f5f5f5;
  color: #999;
  cursor: not-allowed;
}

.button-group {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

button {
  flex: 1;
  padding: 12px 20px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: #e0e0e0;
  color: #666;
}

.btn-secondary:hover:not(:disabled) {
  background: #d0d0d0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* 移动设备适配 */
@media (max-width: 768px) {
  .message-content {
    max-width: 85%;
    font-size: 13px;
  }

  .button-group {
    flex-direction: column;
  }

  .crisis-banner {
    flex-direction: column;
    align-items: stretch;
  }

  button {
    width: 100%;
  }
}
</style>
