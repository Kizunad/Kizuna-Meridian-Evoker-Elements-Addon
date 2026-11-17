# 仓库协作约定

- 简体中文沟通、文档和函数内注释；遵守 `~/.codex/AGENTS.md` 的代码形状规则。
- 正式包是纯数据附属：`shared/` 保存唯一功法目录与炼化配方 textproto，`fabric/` 保存声音、语言与图标，无 Java 入口。
  `shared/src/tooling/` 只在构建期把配方 textproto 导出为附属包，不进正式 JAR。
- 施放契约归功法库，身体执行（付费、器官做功、炼化、声音）归 Human，不复制其实现或协议。
- `gametest/` 只是验证装配：测试放在 Human 的 Fabric 包里，借包内入口准备身体，施放一律走 `TechniqueService`。
- 文档来源为 `docs/wiki/`。
- 交付验证目录、资源与真实玩家施放链；未经授权不提交、不推送、不创建远端。

## 构建和测试

构建、测试一律走工作区的 `kz`（`~/Code/Kizuna-remote-session/scripts/kz`），不直接运行 `./gradlew`。细则以 `~/Code/Kizuna-remote-session/handoff/codex-worker-rules.md` 为准，动手前先读。

- 单测、构建和服务端 GameTest：`kz test ~/Code/<本仓的 worktree>`
- 要带上别的仓的 worktree 一起测：每个加一次 `--with ~/Code/<那个仓的 worktree>`
- 客户端测试：`kz client ~/Code/<本仓的 worktree>` 列出有哪些项，`kz client ~/Code/<本仓的 worktree> <项>` 跑其中一项
- 要自己写 Gradle 参数时：`kz gradle ~/Code/<本仓的 worktree> -- <参数>`
- 不提交、不推送；在自己的 worktree（`~/Code/wt-*`）里改，不改主目录。
