# 仓库协作约定

- 简体中文沟通、文档和函数内注释；遵守 `~/.codex/AGENTS.md` 的代码形状规则。
- 正式包是纯数据附属：`shared/` 保存唯一功法目录与炼化配方 textproto，`fabric/` 保存声音、语言与图标，无 Java 入口。
  `shared/src/tooling/` 只在构建期把配方 textproto 导出为附属包，不进正式 JAR。
- 施放契约归功法库，身体执行（付费、器官做功、炼化、声音）归 Human，不复制其实现或协议。
- `gametest/` 只是验证装配：测试放在 Human 的 Fabric 包里，借包内入口准备身体，施放一律走 `TechniqueService`。
- 文档来源为 `docs/wiki/`。Gradle 使用 `flock /tmp/kizuna-gradle.lock` 串行并带 `--no-daemon`。
- 交付验证 build、目录、资源与真实玩家施放链；未经授权不提交、不推送、不创建远端。
