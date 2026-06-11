<div align="center">
  <img src="./public/image/lophine/lophine3.png" alt="Lophine Logo" width="300">
  
  # Lophine
  
  *Lophine 是一个基于Luminol的分支，具有许多有用的优化和可配置的原版特性，目标是在Folia上实现更多生电的内容（请注意，完整生电请使用Fabric）*
  
  ![Created At](https://img.shields.io/github/created-at/LuminolMC/Lophine?style=for-the-badge&color=blue)
  [![License](https://img.shields.io/github/license/LuminolMC/Lophine?style=for-the-badge&color=green)](LICENSE.md)
  [![Issues](https://img.shields.io/github/issues/LuminolMC/Lophine?style=for-the-badge&color=orange)](https://github.com/LuminolMC/Lophine/issues)
  
  ![Commit Activity](https://img.shields.io/github/commit-activity/w/LuminolMC/Lophine?style=for-the-badge&color=purple)
  ![CodeFactor Grade](https://img.shields.io/codefactor/grade/github/LuminolMC/Lophine?style=for-the-badge&color=yellow)
  ![GitHub all releases](https://img.shields.io/github/downloads/LuminolMC/Lophine/total?style=for-the-badge&color=red)
  
  ![Repo contributors](https://img.shields.io/github/contributors/LuminolMC/Lophine?style=for-the-badge&color=brightgreen)
  
  [English](./README_EN.md) | **中文**
</div>

---

## ✨ 核心特性

- 🔧 **可配置的原版特性** - 灵活调整游戏机制以适应不同服务器需求
- 📊 **Tpsbar 支持** - 实时显示服务器 TPS 状态
- 🐛 **Folia Bug 修复** - 针对 Folia 已知问题的专项修复
- 💾 **多存档格式支持** - 支持 linear 和 b_linear（linear 重新实现）存档格式
- 🔬 **生电功能增强** - 在 Folia 上实现更多生电内容（完整生电请使用 Fabric）
- 🛠️ **更多实用功能** - 持续添加有用的服务器功能

## 📥 下载

### 稳定版本
所有发布版本都可以在 [Releases](https://github.com/LuminolMC/Lophine/releases) 页面找到。

### 开发版本
如果您想体验最新功能，可以通过以下步骤自行构建。

### 构建步骤

```bash
# 克隆项目
git clone https://github.com/LuminolMC/Lophine.git
cd Lophine

# 应用补丁并构建 Paperclip JAR
./gradlew applyAllPatches && ./gradlew createMojmapPaperclipJar
```

构建完成后，您可以在 `lophine-server/build/libs` 目录中找到生成的 JAR 文件。

## 🔌 API 使用

### Gradle 配置

```kotlin
repositories {
    maven {
        url = "https://repo.menthamc.org/repository/maven-public/"
    }
}

dependencies {
    compileOnly("fun.bm.lophine:lophine-api:$VERSION")
}
```

### Maven 配置

```xml
<repositories>
    <repository>
        <id>menthamc</id>
        <url>https://repo.menthamc.org/repository/maven-public/</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>fun.bm.lophine</groupId>
        <artifactId>luminol-api</artifactId>
        <version>$VERSION</version>
    </dependency>
</dependencies>
```

## 🐛 问题反馈

当您遇到任何问题时，请向我们提问，我们将尽力解决。请记得：

- 📝 **清楚描述问题** - 详细说明问题的具体表现
- 📋 **提供完整日志** - 包含错误日志和相关配置信息
- 🔍 **环境信息** - 说明服务器版本、插件列表等环境详情
- 🔄 **复现步骤** - 如果可能，请提供问题复现的具体步骤

---

## ⭐ 请给我们一个 Star！

> 你的每一个免费的 ⭐Star 就是我们每一个前进的动力。

### Star 历史

<a href="https://star-history.com/#LuminolMC/Luminol&LuminolMC/LightingLuminol&LuminolMC/Lophine&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=LuminolMC/Luminol%2CLuminolMC/LightingLuminol%2CLuminolMC/Lophine&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=LuminolMC/Luminol%2CLuminolMC/LightingLuminol%2CLuminolMC/Lophine&type=Date" />
    <img alt="Star历史表" src="https://api.star-history.com/svg?repos=LuminolMC/Luminol%2CLuminolMC/LightingLuminol%2CLuminolMC/Lophine&type=Date" />
  </picture>
</a>

<div align="center">
  <b>如果这个项目对您有帮助，请不要忘记给我们一个 ⭐Star！</b>
</div>
