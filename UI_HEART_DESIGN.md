# 爱心形主页面设计说明

## 🎨 UI/UX 优化完成

### 设计特点

#### 1. **爱心形布局**
- 四个选项按爱心形状排列：
  - 👤 **个人中心** - 爱心左上
  - 📋 **心理测评** - 爱心右上
  - 💬 **咨询服务** - 爱心左下
  - 🤖 **AI小助手** - 爱心底尖

#### 2. **视觉设计**
- **颜色方案**：玫红色爱心主题 (#ff4d7d)
- **背景**：温暖的渐变色（粉白到淡黄）
- **SVG爱心轮廓**：柔和的粉红描边
- **圆形按钮**：白色圆形 + 柔和阴影

#### 3. **交互效果**
- ✨ **悬停效果**：按钮放大 1.15 倍 + 向上浮起
- ✨ **点击反馈**：按钮缩小至 0.95 倍（按压感）
- ✨ **阴影增强**：悬停时阴影更深（深度感）
- ✨ **标签变色**：悬停时文字变为玫红色

#### 4. **动画**
- **页面加载**：标题从上向下滑入 (0.6s)
- **平滑过渡**：所有状态变化 0.3s 缓动曲线
- **立体感**：使用 CSS3 transform + perspective

### 文件结构

```
psychology-frontend/src/views/Main.vue
├── template
│   ├── header-section (标题 + 副标题)
│   ├── heart-container (爱心容器)
│   │   ├── module-item × 4 (四个圆形按钮)
│   │   └── svg.heart-bg (爱心背景)
├── script setup
│   ├── router (路由导航)
│   └── getItemStyle() (动态位置计算)
└── style scoped
    ├── 响应式布局
    ├── 动画和过渡
    └── 媒体查询 (手机/平板适配)
```

### 响应式适配

| 设备 | 宽度 | heart-container | module-item | 图标大小 |
|------|------|-----------------|-------------|---------|
| 桌面 | >768px | 400×420px | 100×100px | 2.5rem |
| 平板 | 480-768px | 320×340px | 80×80px | 2rem |
| 手机 | <480px | 280×300px | 70×70px | 1.8rem |

### 核心代码解析

#### 爱心SVG路径
```svg
<path d="M200,380 C200,380 50,280 50,160 C50,100 85,60 120,60 
         C145,60 170,75 200,100 C230,75 255,60 280,60 C315,60 350,100 
         350,160 C350,280 200,380 200,380 Z"/>
```
- M200,380：底尖起点
- 两个贝塞尔曲线绘制爱心的上半部分
- Z 闭合路径

#### 位置计算
```javascript
function getItemStyle(position) {
  const styles = {
    'top-left': { top: '30px', left: '40px' },
    'top-right': { top: '30px', right: '40px' },
    'bottom-left': { bottom: '110px', left: '60px' },
    'bottom-center': { bottom: '20px', left: '50%', transform: 'translateX(-50%)' }
  }
  return styles[position] || {}
}
```

#### 悬停效果
```css
.module-item:hover {
  transform: scale(1.15) translateY(-8px);  /* 放大 + 浮起 */
  box-shadow: 0 16px 40px rgba(255, 77, 125, 0.3);  /* 深阴影 */
  border-color: #ff4d7d;  /* 爱心边框 */
  background: linear-gradient(135deg, #fff5f8, #ffe0ec);  /* 渐变背景 */
}
```

### 功能特性

✅ **保持原有功能**
- 四个按钮分别导航到对应页面
- 路由逻辑不变

✅ **增强用户体验**
- 爱心形设计体现了心理咨询的温暖主题
- 圆形按钮更加友好和现代
- 平滑的交互动画增加使用快感

✅ **可访问性**
- 按钮具有足够的点击区域 (100×100px)
- 清晰的图标和文字标签
- 悬停时有明显的视觉反馈

### 自定义建议

如需调整，可修改以下参数：

```css
/* 爱心颜色主题 */
--primary-color: #ff4d7d;  /* 当前：玫红色 */

/* 按钮大小 */
width: 100px;  /* 当前：100px */
height: 100px;

/* 爱心轮廓宽度 */
stroke-width: 3;  /* 当前：3px */

/* 悬停缩放倍数 */
transform: scale(1.15);  /* 当前：1.15倍 */
```

### 浏览器兼容性

- ✅ Chrome/Edge (最新)
- ✅ Firefox (最新)
- ✅ Safari (最新)
- ✅ 移动浏览器 (iOS Safari, Chrome Mobile)

### 性能优化

- 使用 CSS 动画代替 JavaScript（性能更优）
- SVG 使用矢量图形（缩放不失真）
- 响应式设计减少重排和重绘

---

**修改日期**: 2026-01-28  
**修改文件**: psychology-frontend/src/views/Main.vue  
**预览地址**: http://localhost:5174
