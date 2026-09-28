# 大乐透模拟器
> 安卓模拟购彩 APP：联网拉取体彩官方大乐透历史开奖数据，10 年历史回放 + 持续跟踪，机选/守号每期千注，看长期买彩票到底是赚是亏。

## 技术栈与版本
- Kotlin 2.0.21 + Jetpack Compose（Material 3，BOM 2024.12.01）
- AGP 8.7.3 / Gradle 8.9 / JDK 17（D:\dev\env\jdk-17.0.1）
- Android SDK 35（D:\dev\env\Android\Sdk；minSdk 26 / targetSdk 35）
- 零重型第三方依赖：HttpURLConnection + org.json（内置）+ SharedPreferences 缓存；仅显式依赖 kotlinx-coroutines-android 1.9.0

## 目录结构
```
大乐透模拟器/
├─ app/src/main/java/com/yy/lottosim/
│  ├─ MainActivity.kt            # 入口 + Scaffold 底部导航 + 全局状态（同步/回放/跟踪）
│  ├─ lotto/
│  │  ├─ LottoRules.kt           # 大乐透规则纯函数：机选生成、判奖、奖级奖金表、号码格式化
│  │  ├─ DrawRepository.kt       # 体彩官方接口拉取（分页全量/增量）+ SP 缓存 + 断网兜底
│  │  └─ SimulationEngine.kt     # 回放引擎（进度/取消）+ 持续跟踪记录 + 累计统计
│  └─ ui/
│     ├─ Theme.kt                # 体彩红+奖金金双主题（明/暗）
│     ├─ Format.kt               # 金额万/亿格式化等
│     ├─ ReplayScreen.kt         # 回放页：策略配置（年限/注数/机选守号+号码球选择器）→ 报告
│     ├─ TrackScreen.kt          # 跟踪页：开关、立即比对、累计战绩、每期明细
│     └─ DataScreen.kt           # 数据页：同步状态、全量重拉、最新 15 期开奖、说明
├─ gradle/libs.versions.toml      # 版本目录（AGP/Kotlin/Compose/coroutines）
└─ app/src/main/AndroidManifest.xml（INTERNET 权限）
```

## 核心规则（判奖依据）
前区 5 个（01-35）+ 后区 2 个（01-12），单注 2 元，追加不模拟：
一等奖 5+2 / 二等奖 5+1 为浮动奖金（统计取**当期实际单注奖金**）；
三等奖 5+0 固定 1 万；四等奖 4+2 固定 3000；五等奖 4+1 固定 300；六等奖 3+2 固定 200；
七等奖 4+0 固定 100；八等奖 3+1 或 2+2 固定 15；九等奖 3+0、2+1、1+2、0+2 固定 5。
一等奖单注概率 1/21,425,712（C(35,5)×C(12,2)）。

## 数据源与外部调用三要素
- 接口：`https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry?gameNo=85`（中国体彩网官方，全库约 2928 期）
- 超时：连接 10s / 读取 30s（DrawRepository 常量，单一权威来源）
- 重试：失败重试 2 次（退避 1s/3s），仅同步触发时重试
- 兜底：失败提示并回退本地缓存（缓存永不清空，断网仍可回放/查看）；首次同步失败显示空态可重试；一二等奖数据缺失时按 800万/12万 估算

## 构建与安装
```bash
cd D:\dev\project\android\daletou-simulator   # 必须在 ASCII 物理路径构建（AGP 禁中文路径；D:\dev\自研工具\大乐透模拟器 是指向此处的 junction）
./gradlew assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
环境变量：ANDROID_HOME=D:\dev\env\Android\Sdk、GRADLE_USER_HOME=D:\dev\env\.gradle、JAVA_HOME=D:\dev\env\jdk-17.0.1

## 端口
无（安卓 APP，不监听端口；登记于根目录 TOOLS.md）

## 数据文件说明
- 开奖缓存：SharedPreferences(`lotto_data`)，key `draw_history`（JSON 数组，每期含期号/日期/前区5/后区2/一二等单注奖金）
- 跟踪配置与记录：SharedPreferences(`lotto_sim`)，key `track_config` / `track_records`（每期一条含中奖注明细）
- 应用卸载即清除，无外部存储依赖

## 当前状态：源码 ↔ release 同步性
无 exe/apk 存档（安卓 APP 不适用 PyInstaller 发布流程；debug APK 构建产物不入库，安装走 adb）

## 已知限制
- 模拟不含追加投注（3 元/注玩法）
- 一二等奖按当期实际单注奖金 × 注数计算，不考虑奖池分享（同期多人中头奖时实际会均摊——回放中单期中多注一等奖按全额计，偏乐观）
- 持续跟踪的自动比对发生在打开 APP 时（无后台推送；开奖时间周一/三/六 21:25）
- 接口为体彩 webapi，若官方调整接口结构需同步适配 parsePage/parseDraw

## 迭代记录
- 2026-09-28 初始版本：历史回放（1/3/5/10 年 × 100-2000 注/期，机选/守号）+ 持续跟踪（每期自动比对、累计战绩、中奖注明细）+ 体彩官方数据同步（全量/增量/断网兜底）
