# DSH 伴侣（Android）

一个能自己接 API 的手机桌宠：多个角色、悬浮在所有应用上、会说话、能听你说话。

![大肥鱼](app/src/main/assets/characters/dafeiyu/preview.png)

## 现在有什么

- **手机桌宠**：`SYSTEM_ALERT_WINDOW` 悬浮窗，拖到任意位置；点一下回聊天，长按摸头，双击摇尾巴，拖拽有专门动画。
- **多个角色**：每个角色有自己的形象、人设（System Prompt）、API 地址、模型、Key、音色。角色可以导入 `.zip` 角色包。
- **OpenAI 兼容 API**：DeepSeek、OpenAI、硅基流动、Moonshot、Ollama 等，只要兼容 `/chat/completions`，支持 SSE 流式回复。
- **语音**：
  - 输出：系统 TTS，逐个角色选择音色，可调音高 / 语速，自动朗读回复。
  - 输入：系统语音识别，点麦克风说话，识别完自动发送。
- **本地优先**：人设、聊天记录、API Key 都只存在手机私有目录，不经过第三方服务器。
- **内置角色**：「大肥鱼」10 组动作（idle / thinking / success / error / poke / head_pat / tail / eat_token / dragging / release），24fps 动画 WebP。
- **状态联动**：发消息时角色进入 thinking，成功/失败切对应动作；桌宠气泡会同步显示回复。

## 还没做的

- 后台主动说话（WorkManager / 定时唤醒）——目前只有你发消息时她才会说话。
- 云端 TTS / 音色克隆——现在是系统 TTS，声音取决于手机里的语音引擎。
- 聊天记录加密——API Key 是明文存在应用私有目录里，别把手机借给不信任的人。
- 编译验证——这份工程是在没有 Android SDK 的机器上写的，**还没真正编译过**；第一次用 Android Studio 打开可能还要修几个编译错误。

## 环境

- Windows / macOS / Linux 均可
- Android Studio（Ladybug 或更新版本），自带 JDK 17+
- Android SDK 35（Android Studio 首次启动时按默认安装即可）
- 手机 Android 9（API 28）以上

## 第一步：装 Android Studio

1. 打开 <https://developer.android.google.cn/studio>（国内可访问）或 <https://developer.android.com/studio>。
2. 下载安装包，一路默认安装。
3. 首次启动选 **Standard**，让它下载 SDK。至少要装 **Android SDK Platform 35** 和 **Build-Tools 35.x**。

## 第二步：打开工程

用 Android Studio 的 **Open** 选择这个目录（`companion-android`）。

如果 Sync 报找不到 Gradle wrapper，先在 PowerShell 里跑一次：

```powershell
cd C:\Users\huang\Documents\deepseek-harness\default-workspace\companion-android
powershell -ExecutionPolicy Bypass -File .\setup-gradle.ps1
```

这个脚本会：

- 找到 Android Studio 自带的 JDK（`jbr`）
- 写出 `local.properties` 里的 SDK 路径
- 下载 Gradle 8.9，生成 `gradlew` / `gradle-wrapper.jar`
- 之后 Android Studio 就能正常 Sync

想直接命令行编译并出 APK：

```powershell
.\setup-gradle.ps1 -Build
```

产物在 `app\build\outputs\apk\debug\app-debug.apk`。

> 如果这台电脑一跑 Gradle 就蓝屏 / 重启，不要硬跑：用 [docs/CLOUD_BUILD.md](docs/CLOUD_BUILD.md) 里的 GitHub Actions 云端编译。
> 这台机器上已经配好的工具链可以用 `build-local.ps1`（修好蓝屏后再运行）。

> 国内网络下 Gradle 官方源可能很慢。`settings.gradle.kts` 已经把阿里云 Maven 镜像放在最前面；Gradle 发行版如果下载失败，可以手动把 `https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip` 下载后放到 `.tools\gradle-8.9-bin.zip` 再跑脚本。

## 第三步：在手机上跑

1. 手机开启开发者选项和 USB 调试，插上电脑。
2. Android Studio 右上角选设备，点 **Run**。
3. 第一次启动会要通知权限（悬浮窗服务的前台通知）。

没有手机就用模拟器：Device Manager → Create Device → 选 Pixel + API 35。

## 第一次配置

1. 底部 **角色** → 点「大肥鱼」。
2. 填三样东西：
   - **API 地址**：DeepSeek 是 `https://api.deepseek.com/v1`
   - **API Key**：`sk-...`（只存在手机本地）
   - **模型**：`deepseek-chat`（或 `deepseek-reasoner`、`gpt-4o-mini`、`qwen-plus`……）
3. 保存，回聊天页，发一句「你好」。

想换声音：同一个编辑页往下拉，「声音 → 选择」，挑一个系统中文音色，点「试听」。没有声音就去 **设置 → 语音 → 系统语音设置** 装一个中文 TTS 引擎（例如讯飞、Google 语音服务）。

## 桌宠怎么用

1. **设置 → 手机桌宠**，打开开关。
2. 第一次会跳到系统页面，允许「显示在其他应用上层」。
3. 角色出现在屏幕上后：
   - **拖动**：按住她拖到任意位置
   - **点一下**：回到聊天
   - **长按**：摸头
   - **双击**：摇尾巴
4. 部分国产系统（MIUI / ColorOS / EMUI）会在后台杀服务：需要在系统设置里允许自启动、关闭电池优化，否则她过一会儿会消失。

## 角色包格式

一个 zip，里面可以是根目录，也可以套一层文件夹：

```
my-character.zip
├── character.json
├── idle.webp
├── thinking.webp
├── success.webp
├── error.webp
├── poke.webp
└── preview.png        # 可选，列表缩略图
```

`character.json`：

```json
{
  "id": "my-character",
  "name": "小蓝",
  "subtitle": "一只会发呆的团子",
  "author": "你",
  "license": "CC BY-NC-SA 4.0",
  "fps": 24,
  "frameWidth": 240,
  "frameHeight": 240,
  "defaultClip": "idle",
  "clips": {
    "idle":     { "file": "idle.webp",     "loop": true,  "frames": 48, "durationMs": 2000 },
    "thinking": { "file": "thinking.webp", "loop": true,  "frames": 48, "durationMs": 2000 },
    "success":  { "file": "success.webp",  "loop": false, "frames": 36, "durationMs": 1500 },
    "error":    { "file": "error.webp",    "loop": false, "frames": 36, "durationMs": 1500 },
    "poke":     { "file": "poke.webp",     "loop": false, "frames": 36, "durationMs": 1500 }
  }
}
```

规则：

- 动画必须是**带透明通道的动态 WebP**（`save_all=True` 的 animated WebP）。
- 所有帧建议用同一画布尺寸；缩放由 App 做，保持比例。
- `clips` 里至少要有 `defaultClip` 指向的动作。
- 缺少 `thinking` / `success` / `error` 时会自动回退到默认动作。
- 单个包上限 120MB；只接受 `.webp`、`.png`、`.json`、`.md`、`.txt`。
- 导入后的角色保存在 `filesDir/characters/<id>/`，删 App 会一起删。

自带的 `dafeiyu` 素材是 MIT 许可，来自 [PC2005-cloud/dsh-pet](https://github.com/PC2005-cloud/dsh-pet)，经 [dsh-dafeiyu](https://github.com/QCYTSN/dsh-dafeiyu) 转码。自己导入的角色请确认你有分发权。

## 目录

```
companion-android/
├── app/src/main/java/com/dsh/companion/
│   ├── MainActivity.kt / MainViewModel.kt
│   ├── data/           角色包、人设、聊天记录、设置
│   ├── net/            OpenAI 兼容 SSE 客户端
│   ├── voice/          系统 TTS + 语音识别
│   ├── pet/            悬浮窗服务、动画、气泡
│   └── ui/             Compose 界面（聊天 / 角色 / 设置）
├── app/src/main/assets/characters/dafeiyu/   内置角色
├── tools/build_character.py                  从 dsh-dafeiyu 重新生成内置角色
└── setup-gradle.ps1                          配置 JDK / SDK / Gradle
```

## 重新生成内置角色

```powershell
& 'C:\Users\huang\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe' .\tools\build_character.py
```

前提是 `C:\Users\huang\.dsh\profiles\desktop\node_modules\dsh-dafeiyu` 已安装。脚本会裁剪透明边、降采样到 344px 高、24fps 编码成 WebP，并重做图标。

## 已知限制

- 悬浮窗在部分系统上会被电池优化杀掉，需要白名单。
- 系统 TTS 的音色质量取决于手机引擎；有些音色需要联网。
- 语音识别用系统服务，没网或没装识别服务时不可用。
- 长回复的朗读是整段排队，不能在句间打断（点停止会停）。
- 还没有后台主动消息、记忆摘要、工具调用。

## 许可

代码 MIT。内置角色素材 MIT（见 `app/src/main/assets/characters/dafeiyu/NOTICE.md`）。
「大肥鱼」是社区称呼，与 DeepSeek 官方无关。
