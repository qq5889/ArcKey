# ArcKey

ArcKey 是一款面向 Android 的压缩包解锁与解压工具，支持 ZIP、RAR、7z 等 7-Zip-JBinding-4Android 可识别的压缩格式。应用可以尝试手动密码、已选择的明文密码本，以及受长度和字符集限制的暴力破解候选；密码本和暴力破解均支持多线程同步尝试。

破解成功后，ArcKey 会显示解压密码，并将密码写入用户选择的密码本。

## 主要功能

- 支持从文件选择器选择压缩包，也支持其他应用通过“使用其他应用打开”或“分享”传入压缩包。
- 支持选择输出目录；如果 Android 能识别压缩包父目录，会默认引导用户授权压缩包所在目录。
- 解压后的文件夹名称与压缩包基础名一致；若同名目录已存在，会自动追加序号避免覆盖。
- 支持选择或新建 UTF-8 明文密码本，格式为一行一个密码。
- 第二次打开应用时，会自动带回上一次选择的密码本。
- 支持手动候选密码、密码本候选和暴力破解候选。
- 支持多线程破解，线程数可在界面中设置。
- 破解任务以 Android 前台服务运行，支持暂停、继续、取消和 checkpoint 恢复。
- 解压前会校验归档条目路径，阻止绝对路径、盘符路径和 `..` 路径穿越。

## 构建要求

- JDK 17 或更新版本。
- Android SDK Platform 37。
- Android Build Tools 36.0.0 或更新版本。
- 首次同步依赖时需要网络访问。

当前项目配置：

- `compileSdk 37`
- `targetSdk 36`
- `minSdk 26`
- Gradle Wrapper `9.5.0`

## 常用命令

```bash
./gradlew test
./gradlew assembleDebug
```

如果本机默认 Java 版本不是 17，可以显式指定：

```bash
JAVA_HOME=/Library/Java/temurin-17.jdk/Contents/Home ./gradlew test assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安全说明

- 仅可对自己拥有或已获授权的压缩包进行密码恢复。
- 密码本是用户选择的明文 `.txt` 文件，便于导入、导出和手动编辑，但需要用户自行妥善保管。
- 解压密码会在应用页面显示；通知栏和日志中不会显示密码。
- ArcKey 会将解压内容写入选定输出目录下的新文件夹，不会默认覆盖已有文件。

## 关键依赖

- Android Gradle Plugin `9.3.0`
- Kotlin `2.4.10`
- Jetpack Compose BOM `2026.06.01`
- `com.sorrowblue.sevenzipjbinding:7-Zip-JBinding-4Android:16.02-2.4`

## 提交规范

本项目提交信息采用 Conventional Commits 规范，并使用中文描述，例如：

```text
feat: 增加多线程破解能力
fix: 修复同名输出目录处理
docs: 编写中文项目说明
```
