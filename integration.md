针对你已经用 Kotlin + Android Studio 搭建好框架的情况，uni-ai-x 的集成需要走一个**将 uni-app-x 模块作为子项目引入**的路径。下面给你两种集成方案，你可以根据 UI 定制需求来选择。

---

## 🎯 方案一：独立页面集成（推荐）

将 uni-ai-x 作为一个独立的聊天页面直接使用，保留你现有框架的导航、底部 Tab 等全局结构。

### 集成步骤：

**1. 在现有 Android 工程中创建 uni-app-x 模块**

```gradle
// settings.gradle
include ':uniappx'
project(':uniappx').projectDir = new File('path/to/uni-ai-x') // uni-ai-x 下载后放置的目录
```

**2. 配置模块依赖**

```gradle
// app/build.gradle
dependencies {
    implementation project(':uniappx')
}
```

**3. 在你的 Activity 中打开 AI 聊天页面**

```kotlin
// 在你现有的 Activity 或 Fragment 中
val intent = Intent(this, UniAIXActivity::class.java)
startActivity(intent)

// 或者如果 uni-ai-x 导出的是 Fragment：
supportFragmentManager.beginTransaction()
    .replace(R.id.ai_container, UniAIXFragment())
    .commit()
```

**4. 通过 Intent 传递必要参数（如用户信息）**

```kotlin
intent.putExtra("currentUser", userJsonString)
intent.putExtra("apiKey", "your-api-key") // 或通过服务端获取 token
```

### 优点：
- ✅ 集成最快，代码侵入最小
- ✅ 保留 uni-ai-x 完整的 AI 聊天能力
- ✅ 你现有的 Figma 框架（导航栏、TabBar）完全不受影响

### 缺点：
- ⚠️ AI 聊天页面的 UI 是 uni-ai-x 原生的，与你 Figma 设计可能风格不一致
- ⚠️ 如果需要深度定制 UI（如消息气泡样式、输入框布局），需要修改 uni-ai-x 内部的组件

---

## 🎨 方案二：组件级集成（深度定制）

如果你希望 AI 聊天的 UI 完全符合你的 Figma 设计，可以只集成 uni-ai-x 的核心 SDK，自己实现 UI 层。

### 集成步骤：

**1. 只引入 uni-ai-x 的 SDK 模块**

```gradle
// settings.gradle
include ':uniappx:sdk'  // 只引入 sdk 目录，不引入 pages 和 components
```

**2. 在你的 UI 中使用 SDK 提供的 AI 能力**

```kotlin
import uni.ai.x.sdk.AIChatSDK
import uni.ai.x.sdk.models.*

// 初始化 SDK
val aiSDK = AIChatSDK(context)
aiSDK.setApiKey("your-api-key")

// 发送消息并接收流式回调
aiSDK.sendMessage("你好", object : MessageCallback {
    override fun onStreamChunk(chunk: String) {
        // 将流式返回的文本更新到你的 UI 上
        runOnUiThread {
            appendToMessageBubble(chunk)
        }
    }
    
    override fun onComplete(fullMessage: String) {
        // 消息完成
    }
    
    override fun onError(error: String) {
        // 处理错误
    }
})
```

**3. 手动实现 Markdown 渲染（复用 uni-ai-x 的渲染器）**

uni-ai-x 提供了 `uni-marked-el` 组件用于 Markdown 解析和高亮，你可以将其渲染逻辑抽取出来：

```kotlin
// 引入 uni-ai-x 的 Markdown 解析器
import uni.ai.x.markdown.MarkdownParser

val markdownParser = MarkdownParser()
markdownParser.setCodeHighlight(true)  // 开启代码高亮

// 在 RecyclerView 的 ViewHolder 中渲染
binding.markdownTextView.text = markdownParser.parse(aiMessage)
```

**4. 调用获取临时 token 的接口**

```kotlin
// 调用你自己的后端接口获取临时 token
apiService.getTempToken().enqueue(object : Callback<TokenResponse> {
    override fun onResponse(response: TokenResponse) {
        aiSDK.setToken(response.token)
    }
})
```

### 优点：
- ✅ UI 完全可控，可与你的 Figma 设计 100% 匹配
- ✅ 只复用核心能力（流式请求、Markdown 解析），保持代码纯净

### 缺点：
- ⚠️ 开发工作量较大，需要自己实现消息列表、输入框、会话管理等 UI
- ⚠️ 需要理解 uni-ai-x SDK 的 API 设计

---

## 📋 集成前的准备工作（两种方案都需要）

### 1. 下载 uni-ai-x

从 DCloud 插件市场下载：[https://ext.dcloud.net.cn/plugin?name=uni-ai-x](https://ext.dcloud.net.cn/plugin?name=uni-ai-x) 

点击 **"使用 HBuilderX 导入示例项目"**，然后找到 Android 工程目录（编译后的 Kotlin 代码在 `uni-app-x/native-android` 下）。

### 2. 配置 API Key（二选一）

**方式一：使用阿里百炼（免费额度较高）**

注册阿里百炼账户并创建 API-Key：[https://bailian.console.aliyun.com/](https://bailian.console.aliyun.com/) 

创建配置文件 `uniCloud/cloudfunctions/common/uni-config-center/uni-ai-x/config.json`：

```json
{
    "apiKey": {
        "bailian": "sk-你的API-Key"
    }
}
```

**方式二：使用七牛云 uni-ai 网关（新版默认）**

uni-ai-x 1.1.0 及以上版本默认使用七牛云，开通 uni-ai 网关即可使用，无需手动配置 API Key 。

### 3. 打自定义基座（运行到 Android 真机时）

uni-ai-x 运行到 Android 真机需要打自定义基座，否则会报错 ：

```
error: app-service.js ReferenceError:Can't find variable: TextDecoder
```

在 HBuilderX 中：**运行 → 运行到手机或模拟器 → 制作自定义调试基座**

---

## 💡 我的建议

| 你的情况 | 推荐方案 |
| :--- | :--- |
| Figma 设计的聊天页面风格与 DeepSeek/标准 AI 对话差异不大 | **方案一**，节省开发时间 |
| Figma 设计的聊天页面有独特布局（特殊气泡、自定义工具栏等） | **方案二**，UI 自由度更高 |
| 你是独立开发者/小团队，追求快速上线 MVP | **方案一** |
| 你有充足时间打磨 UI，追求极致体验 | **方案二** |

根据你之前描述的 "已经有 Figma 框架并转成 Kotlin 代码"，我推测你对自己的 UI 设计比较在意，如果聊天页面的设计有特色，**方案二**会更适合你。

方便分享一下你的 Figma 聊天页面设计大概长什么样吗？比如是偏向标准对话风格还是有特殊布局？我可以帮你评估具体需要复刻哪些组件。