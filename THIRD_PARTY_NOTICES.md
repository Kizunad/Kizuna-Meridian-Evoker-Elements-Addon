# 第三方说明

PolyForm Noncommercial 1.0.0 仅适用于本项目有权许可的原创部分。

| 组件 | 版本 / 来源 | 许可与使用 |
|---|---|---|
| 平静呼吸录音 | christophe1138，[breathingsleeping.wav](https://freesound.org/people/christophe1138/sounds/554074/)，取作者公开的 [OGG 预览](https://cdn.freesound.org/previews/554/554074_9054093-lq.ogg) | CC0 1.0；由 `scripts/process_breath_sounds.py` 截取、降噪、滤波后作为 7 个 OGG 分发，详见[声音与图标素材](docs/wiki/Sound-Assets.md) |
| 状态效果图标 | 本项目经 CPA（`cliproxy` 后端）用 `gpt-image-2.5-sunburst` 生成，再缩到 18 × 18 | 本项目占位素材；不含第三方图像 |
| Kizuna's Meridian Mod Framework | 同级独立仓库 | PolyForm Noncommercial 1.0.0；构建期导出配方包与验证装配使用，不内嵌源码、生成类或 JAR |
| Kizuna's Meridian Technique Framework | 同级独立仓库 | PolyForm Noncommercial 1.0.0；必需运行依赖，读取本仓目录，不内嵌 |
| Kizuna's Meridian Human Addon | 同级独立仓库 | PolyForm Noncommercial 1.0.0；必需运行依赖，执行付费、做功、炼化与声音，不内嵌 |
| Kizuna's Meridian Vanilla Damage Addon | 同级独立仓库 | PolyForm Noncommercial 1.0.0；只在 GameTest 装配里随 Human 装入 |
| Gradle Wrapper | 9.8.0，[来源](https://github.com/gradle/gradle/tree/v9.8.0) | Apache License 2.0；从经脉框架复制启动脚本、Wrapper JAR 与校验配置，保留版权头及 JAR 内 LICENSE |
| Protocol Buffers | 4.36.2，由框架解析 | BSD 3-Clause；构建期导出与验证使用框架生成消息，不单独打包运行库 |
| Gson | 2.10.1 | Apache License 2.0；仅目录与资源验证测试 |
| JUnit / Hamcrest | 4.13.2 / 1.3 | EPL 1.0 / BSD 3-Clause；仅目录与资源验证测试 |

GameTest 装配使用 Minecraft 26.2（不混淆，无映射）、Fabric Loader 0.20.1、Fabric API 0.161.0+26.2、owo-lib 0.13.1+26.2 与 Fabric Loom 1.19.3。Minecraft 遵循 Mojang 条款；Fabric Loader、Fabric API、Loom 使用 Apache License 2.0，owo-lib 使用 MIT。它们都不内嵌到本附属 JAR。
