# 拍热量 SnapCal

拍一张照片，让 AI 估算这顿饭的热量；自动统计每天摄入，并结合 Health Connect 的运动消耗算出**每日热量差**。

目标与路线图见 [GOAL.md](GOAL.md)；所有公式和取舍的科学依据见 [docs/SCIENCE.md](docs/SCIENCE.md)。

## 功能

- **制定计划（首次启动）**：填年龄 / 身高 / 体重 / 活动量 / 目标体重和速度，算出基础代谢、每日总消耗、每日建议摄入、预计多少周到目标。体重变了在设置里“重新制定计划”。
- **拍一顿**：打开即进相机；可多拍几张或从相册选；可选补充一句（“半碗饭”“只吃了一半”）。
- **AI 估算**：返回分项明细与可信度；任何一项不对，直接改数字。
- **今日面板**：已摄入 / 消耗 / 热量差 / 距目标还能吃多少。
- **历史**：按天查看总摄入。
- **Health Connect**：读取 Google Fit、三星健康、Fitbit、Strava 等写入的消耗热量；把饮食写回 Health Connect。
- **桌面快捷方式**：长按图标 →「拍一顿」。
- **米家体脂秤 S400**：上秤自动记录体重和阻抗（被动接收蓝牙广播，米家 App 照常可用），计算体脂率趋势。

## 连接米家体脂秤 S400

1. 在 Mac 上取一次秤的密钥（用你自己的小米账号扫码登录，服务器选 `cn`）：
   ```bash
   git clone https://github.com/PiotrMachowski/Xiaomi-cloud-tokens-extractor
   cd Xiaomi-cloud-tokens-extractor && pip3 install -r requirements.txt
   python3 token_extractor.py
   ```
   在输出的设备列表里找到体脂秤，复制 **BLE KEY**（32 位十六进制）。
2. app → 体重 →「连接米家体脂秤」→ 允许蓝牙扫描 → 站上秤，点「使用这台」→ 粘贴 BLE KEY → 保存。
3. 光脚站上去，看到「已记录 xx kg，阻抗 xxx Ω」即成功。

## 支持的 AI

在 **设置** 里选择服务商，填 API Key，模型 ID 可手填或点推荐：

| 服务商 | 默认模型 | 接口 |
|---|---|---|
| Anthropic | `claude-opus-5-5` | 官方 Java SDK，Messages API + JSON Schema 结构化输出 |
| OpenAI | `gpt-6.1-sol` | Chat Completions + `json_schema` strict |
| Google Gemini | `gemini-3.8-flash` | `generateContent` + `responseJsonSchema` |
| Mistral | `mistral-medium-latest` | Chat Completions（OpenAI 兼容）+ `json_schema` |
| DeepSeek | `deepseek-flash` | Chat Completions（OpenAI 兼容）+ `json_object`；**国内无需代理** |

每个服务商都可以填 **自定义 Base URL**（走代理或兼容网关时用）。
Key 只存在手机本地（SharedPreferences，已关闭备份），不会进仓库。

## 构建

需要 JDK 17 和 Android SDK（platform 36）。本机已经装好：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk

./gradlew assembleRelease          # → app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest        # 单元测试
```

Release 包用 debug 签名，方便自己侧载。

## 安装到手机

1. 手机：设置 → 关于手机 → 连点“版本号”7 次开启开发者选项 → 打开 **USB 调试**（或无线调试）。
2. 数据线连上 Mac，然后：

```bash
$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk
```

也可以把 APK 发到手机上直接点开安装。

## 第一次使用

1. 打开 app，按引导填身体数据、活动量和目标，得到每日建议摄入。
2. 右上角 **设置** → 选一个服务商，填 API Key → 保存。
3. 可选：点 **授权 Health Connect**，并在你的运动 App 里打开“同步到 Health Connect”。
5. 回首页点 **拍一顿**。

## 消耗怎么算（详见 docs/SCIENCE.md）

```
当日消耗 = 维持热量 + 0.5 × (今天手表活动 − 你近 28 天的平均活动)
维持热量 = 前 ~4 周：Mifflin-St Jeor × PAL（默认 1.40，误差 ±250–350）
           之后：    记录摄入 − 体重趋势 × 每 kg 能量（自动校准）
```

- 体重预测用 NIH 动态模型，不用「7700 kcal = 1 kg」。
- 缺口 ≤ 维持热量 30%，摄入不低于 1500（男）/ 1200（女）kcal。
- 每天早上称一次体重，是校准准确度的关键。

## 代码结构

```
app/src/main/java/com/snapcal/app/
  ai/       Provider 列表、Prompt + JSON Schema、四家 Analyzer
  data/     Room（Meal）、设置、热量差计算、Repository
  health/   Health Connect 读写
  ui/       Compose 界面（首页 / 拍照 / 明细编辑 / 历史 / 设置）
  util/     图片压缩旋转、日期工具
```

## 许可证

[MIT](LICENSE)。米家 MiBeacon 协议解析参考了 Home Assistant [xiaomi-ble](https://github.com/Bluetooth-Devices/xiaomi-ble)（Apache-2.0）。

本应用的热量与身体成分估算仅供参考，不构成医疗建议。
