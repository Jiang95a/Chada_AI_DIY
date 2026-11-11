# 心屿同行视觉设计规范

## 1. 设计关键词

克制、可信、温暖、安静、有安全感。

页面以暖纸白为底，墨绿承担品牌识别与主要操作，暖棕用于温度与次级强调，鼠尾草绿用于信息和成功状态。公共端允许更舒展的留白，运营端在保留自然气质的同时提高信息密度。

## 2. 配色

### 品牌色

| Token | 色值 | 用途 |
| --- | --- | --- |
| `--brand-50` | `#edf4f0` | 浅选中背景 |
| `--brand-100` | `#dce9e1` | 标签、次级选中 |
| `--brand-200` | `#bfd5c8` | 浅边框、悬停 |
| `--brand-300` | `#94b6a1` | 辅助图形 |
| `--brand-400` | `#6d937f` | 图标与次级描边 |
| `--brand-500` | `#486f5e` | 浅色主按钮 |
| `--brand-600` | `#2d5046` | 品牌主色、主按钮 |
| `--brand-700` | `#213e36` | 悬停、深色标题 |
| `--brand-800` | `#193129` | 深色背景 |
| `--brand-900` | `#10241d` | 最强对比 |

### 暖棕与状态色

| Token | 色值 | 用途 |
| --- | --- | --- |
| `--accent-50` | `#f9f0eb` | 温暖提示背景 |
| `--accent-100` | `#f2dfd4` | 模板封面、标签 |
| `--accent-200` | `#e5c1ae` | 次级图形 |
| `--accent-500` | `#b88c72` | 辅助色、选中标记 |
| `--accent-700` | `#795645` | 暖棕文字 |
| `--mint-500` | `#5f8a73` | 成功、已发布 |
| `--sky-500` | `#6d8f91` | 信息状态 |
| `--rose-500` | `#b45e55` | 删除、错误 |

### 中性色

| Token | 色值 | 用途 |
| --- | --- | --- |
| `--bg` | `#faf8f5` | 页面背景 |
| `--surface` | `#fffdf9` | 主卡片背景 |
| `--surface-soft` | `#f4f0e9` | 次级区块 |
| `--ink-900` | `#1f3129` | 主文字 |
| `--ink-600` | `#556a60` | 正文 |
| `--muted` | `#78867f` | 辅助说明 |
| `--line` | `#e5ded5` | 默认边框 |
| `--line-strong` | `#d5cdc2` | 强边框 |

### 表面色

```css
--surface-paper: #fffdf9;
--surface-sage: #eef3ec;
--surface-sand: #f1e9da;
--surface-ink: #183a2e;
```

正式页面不使用渐变代替图片，也不使用高饱和紫色、蓝紫色或荧光绿。强调关系统一通过纯色块、字号、留白和边框表达。

## 3. 圆角、阴影与间距

```css
--radius-card: 16px;
--radius-button: 12px;
--radius-input: 10px;
--radius-sm: 10px;
--radius-xl: 24px;

--shadow-card: 0 4px 12px rgb(0 0 0 / 5%);
--shadow-md: 0 12px 30px -20px rgb(37 55 45 / 22%);
--shadow-lg: 0 28px 68px -38px rgb(37 55 45 / 30%);

--space-1: 4px;
--space-2: 8px;
--space-3: 12px;
--space-4: 16px;
--space-5: 20px;
--space-6: 24px;
--space-8: 32px;
--space-10: 40px;
--space-12: 48px;
```

使用规则：

- 同一卡片内部使用 `8 / 12 / 16px`。
- 卡片之间使用 `12 / 16px`。
- 页面模块之间使用 `28 / 36 / 48px`。
- 标题上方留白应大于标题下方留白。
- 不使用硬边阴影、彩色发光光晕和超过两层嵌套卡片。

## 4. 文字层级

| 层级 | 字号 | 字重 | 行高 | 字体 |
| --- | --- | --- | --- | --- |
| 页面主标题 | `34px` | `700` | `1.12` | 宋体标题 |
| 模块标题 | `22–24px` | `700` | `1.25` | 宋体标题 |
| 卡片标题 | `15–17px` | `650–700` | `1.4` | 无衬线 |
| 正文 | `13–15px` | `400–500` | `1.7` | 无衬线 |
| 辅助文字 | `11–12px` | `500–650` | `1.6` | 无衬线 |
| 大数字 | `34–48px` | `700–750` | `1` | 无衬线 |

正文行长控制在 `60–72` 个中文字符以内。正文不可使用过浅灰色，最低应保持 `4.5:1` 对比度。

## 5. 组件

### 主按钮

```css
.app-button--primary {
  min-height: 44px;
  padding: 0 18px;
  border: 0;
  border-radius: var(--radius-button);
  color: #fff;
  background: var(--brand-600);
  box-shadow: 0 8px 18px -14px rgb(45 80 70 / 70%);
}

.app-button--primary:hover {
  background: var(--brand-700);
  transform: translateY(-1px);
}
```

### 次按钮

```css
.app-button--secondary {
  border: 1px solid var(--line);
  color: var(--ink-800);
  background: var(--surface-raised);
}

.app-button--secondary:hover {
  border-color: var(--brand-200);
  color: var(--brand-700);
  background: var(--brand-50);
}
```

### 卡片

```css
.app-card {
  border: 1px solid var(--line);
  border-radius: var(--radius-card);
  background: var(--surface);
  box-shadow: var(--shadow-card);
}
```

### 输入框

```css
.app-input {
  min-height: 44px;
  padding: 0 13px;
  border: 1px solid var(--line);
  border-radius: var(--radius-input);
  color: var(--ink-800);
  background: var(--surface-raised);
}

.app-input:focus {
  border-color: var(--brand-400);
  box-shadow: 0 0 0 3px rgb(45 80 70 / 9%);
}
```

### 侧边栏菜单项

- 高度 `43px`，圆角 `12px`。
- 默认使用低对比白绿色文字。
- 选中态使用米白底、墨绿字、暖棕左侧标记。
- 分组标题与菜单项之间保持 `8–12px` 间距。

### 数据统计卡片

- 核心指标使用纯墨绿背景与 `48px` 数字。
- 普通指标使用暖纸白或极浅鼠尾草背景。
- 四张卡片不得等权重；核心指标占更宽列。
- 数据与实际业务值绑定，不使用虚构趋势图。

## 6. 页面结构

### 用户前台

首页、心理活动、心晴工具、心理资源、关于心屿。使用舒展留白、自然曲线和较大标题，突出安全感。

### 运营后台

运营概览、参与数据、转介待办。运营概览同时承担活动列表与数据概览，不再设置功能重复的独立活动管理入口。使用顶部导航、左侧分组侧栏、核心数据卡和活动列表。功能密度更高，但沿用同一品牌色。

### 创作工具

页面工坊、模板库、AI 助手。AI 仅保留一个统一入口，模板库中的 AI 作为创建方式出现，不重复建设平行 AI 页面。

## 7. 动效与可访问性

- 常规交互时长 `150–220ms`。
- 卡片悬停上移不超过 `4px`。
- 动效只表达状态变化，不做持续闪烁。
- 所有按钮、链接提供可见键盘焦点。
- 尊重 `prefers-reduced-motion`。
- 移动端触控目标不小于 `44px`。
- 页面在 `1440 / 1024 / 768 / 390px` 宽度下不得横向溢出。

### 可选页面主题

界面提供四套保存在本机的配色，通过工作台顶栏或公共导航的换色按钮切换：

| 主题 | 页面底色 | 品牌色 | 气质 |
| --- | --- | --- | --- |
| 鼠尾草绿 | `#faf8f5` | `#2d5046` | 默认，克制安静 |
| 燕麦米黄 | `#f5efe2` | `#5f5a43` | 更温暖、接近社团手账 |
| 雾青 | `#eef3f2` | `#416a67` | 清爽、适合白天活动 |
| 雾蓝石板 | `#eef1f4` | `#4f6775` | 冷色、适合运营和数据处理 |

主题只改变表面色、品牌色和少量辅助色，不改变信息结构。选择结果写入浏览器本地存储，不影响其他成员。

## 8. 活动图片位

运营概览和模板库使用同一组活动封面。当前文件是可离线展示的平涂插画，不使用渐变：

| 场景 | 当前占位文件 | 建议替换内容 |
| --- | --- | --- |
| 心晴游园会 | `web/src/assets/activity-cover-garden.svg` | 游园会现场、摊位或同学参与活动的横图 |
| 自我关怀 | `web/src/assets/activity-cover-self-care.svg` | 安静阅读区、绿植、桌面、休息空间 |
| 心理社团招新 | `web/src/assets/activity-cover-club.svg` | 社团成员交流、团体活动、招新现场 |
| 安心表达 | `web/src/assets/activity-cover-quiet.svg` | 便利贴墙、匿名信箱、安静空间 |

正式照片建议比例为 `3:2`，最小分辨率 `1200 × 800`，使用 `.webp` 或压缩后的 `.jpg`。涉及人物时需取得照片内同学同意。

活动海报在页面工坊中提供两种来源：上传已有图片，或调用后端 AI 图像接口生成底图。AI 只负责视觉底图，活动时间、地点、隐私说明和报名字段必须由用户核对。
