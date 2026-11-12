# 五脏诀美术

2026-11-11 补齐上卷「养」的卷轴物品图标、阅读背景和十式战斗图标。此前启动日志报
「卷轴 meridian_evoker:nourish_scroll 缺少贴图，使用功法库通用卷轴图标」，战斗 UI 也因目录没有
`presentation.icon_id` 而不显示图标。

2026-11-12 补中卷「附」的卷轴物品图、阅读背景和五式战斗图标，来源和后处理与上卷相同。

## 来源

- 全部主体由用户 srv 上的 CPA 生成：模型 `gpt-image-2.5-sunburst`，图片接口，`quality=high`、PNG、`style=none`。
- 调用脚本 `handoff/technique-art-20261028-work/cpa_generate.py` 读 `~/.config/kizuna/cpa-image.env`，日志不打印 key。
- 方形请求 1024×1024，CPA 实际返回 1254×1254 RGB；背景请求和返回都是 1536×1024。
- 原稿放仓内 `.local-art/{scroll,background,icon}/<名字>_v<版本>.png`，已加进 `.gitignore`，不进 JAR。
- 逐图提示词在 [art-prompts/](art-prompts/)；同一组的共用句（边框、装帧、禁止文字）每张都写全，只换主体。

## 成品

资源命名空间 `meridian_evoker`，放 `fabric/src/main/resources/assets/meridian_evoker/textures/`。路径由功法库约定：

- 卷轴物品 ID 是 `<卷>_scroll`；
- 阅读背景按卷路径取；
- 战斗图标按 `icon_id` 取 `gui/technique/<路径>.png`。

| 卷 | 32×32 卷轴物品图 | 1536×1024 阅读背景 | 提示词 |
|---|---|---|---|
| 上卷「养」 | [nourish_scroll.png](../../fabric/src/main/resources/assets/meridian_evoker/textures/item/nourish_scroll.png) | [nourish.png](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/scroll/nourish.png) | [卷轴](art-prompts/nourish_scroll_icon_prompt.txt)、[背景](art-prompts/nourish_background_prompt.txt) |
| 中卷「附」 | [infuse_scroll.png](../../fabric/src/main/resources/assets/meridian_evoker/textures/item/infuse_scroll.png) | [infuse.png](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/scroll/infuse.png) | [卷轴](art-prompts/infuse_scroll_icon_prompt.txt)、[背景](art-prompts/infuse_background_prompt.txt) |

- 卷轴：象牙纸卷，两端淡玉轴头，五色（青、赤、黄、白、黑）编绳与流苏，和其他书的木轴、铁箍卷轴区分开。
- 背景：两侧淡玉卷轴杆，上下沿五色细绳；四边留白处是五禽（鹤、熊、猿、鹿、虎）剪影，配本行颜色的云纹。中间 x 18%–82%、y 12%–88% 是浅色纸面，给正文用。
- 中卷卷轴：比上卷更长更细的象牙纸卷，淡玉轴头，两道五色编绳，挂一枚玉璧坠，不带流苏，和上卷一眼分开。
- 中卷背景：装帧与上卷相同（淡玉卷轴杆、五色细绳）。两侧留白处从上到下画本卷五行纹样：金是剑形寒光配金屑，土是裂地，火是火苗，水是浪头配霜花，木是闪电配两片叶。正文区与上卷相同。

全书各式共用一套边框：

- 墨黑外圈；
- 淡玉绿内圈；
- 深青灰底。

各式靠主体剪影和五行色区分：强身五式画五禽，炼元五式画对应脏器配吐纳气流。中卷五式画施放手势，配本行附行的样子。

| 式 | icon_id | 主体 | 32×32 | 64×64 | 提示词 |
|---|---|---|---|---|---|
| 金臂 | `meridian_evoker:metal_arms` | 展翅白鹤，银白 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/metal_arms.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/metal_arms_64.png) | [提示词](art-prompts/metal_arms_technique_icon_prompt.txt) |
| 厚土 | `meridian_evoker:earth_legs` | 立身晃步的熊，赭黄 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/earth_legs.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/earth_legs_64.png) | [提示词](art-prompts/earth_legs_technique_icon_prompt.txt) |
| 心火 | `meridian_evoker:fire_heart` | 举臂的猿，胸口一团心火，朱红 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/fire_heart.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/fire_heart_64.png) | [提示词](art-prompts/fire_heart_technique_icon_prompt.txt) |
| 归水 | `meridian_evoker:water_return` | 回首的鹿，后腿绕水浪，深蓝 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/water_return.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/water_return_64.png) | [提示词](art-prompts/water_return_technique_icon_prompt.txt) |
| 舒筋 | `meridian_evoker:wood_sinew` | 前扑的虎，身后落叶，叶绿 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/wood_sinew.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/wood_sinew_64.png) | [提示词](art-prompts/wood_sinew_technique_icon_prompt.txt) |
| 肺炼 | `meridian_evoker:refine_lung` | 双肺与上行气流，银白 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/refine_lung.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/refine_lung_64.png) | [提示词](art-prompts/refine_lung_technique_icon_prompt.txt) |
| 脾炼 | `meridian_evoker:refine_spleen` | 脾与环绕气流，赭黄 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/refine_spleen.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/refine_spleen_64.png) | [提示词](art-prompts/refine_spleen_technique_icon_prompt.txt) |
| 心炼 | `meridian_evoker:refine_heart` | 心与盘旋气流，朱红 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/refine_heart.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/refine_heart_64.png) | [提示词](art-prompts/refine_heart_technique_icon_prompt.txt) |
| 肾炼 | `meridian_evoker:refine_kidney` | 双肾与水滴，深蓝 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/refine_kidney.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/refine_kidney_64.png) | [提示词](art-prompts/refine_kidney_technique_icon_prompt.txt) |
| 肝炼 | `meridian_evoker:refine_liver` | 肝与新芽，叶绿 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/refine_liver.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/refine_liver_64.png) | [提示词](art-prompts/refine_liver_technique_icon_prompt.txt) |
| 锐金 | `meridian_evoker:metal_edge` | 竖起剑指，指尖上冲一道剑形寒光，银白 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/metal_edge.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/metal_edge_64.png) | [提示词](art-prompts/metal_edge_technique_icon_prompt.txt) |
| 震土 | `meridian_evoker:earth_quake` | 双掌下按、一足顿地，地面裂纹，赭黄 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/earth_quake.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/earth_quake_64.png) | [提示词](art-prompts/earth_quake_technique_icon_prompt.txt) |
| 灼火 | `meridian_evoker:fire_burn` | 双掌相搓，掌间火苗与火星，朱红 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/fire_burn.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/fire_burn_64.png) | [提示词](art-prompts/fire_burn_technique_icon_prompt.txt) |
| 寒水 | `meridian_evoker:water_frost` | 双掌下按，掌下浪头压平结霜，深蓝 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/water_frost.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/water_frost_64.png) | [提示词](art-prompts/water_frost_technique_icon_prompt.txt) |
| 惊雷 | `meridian_evoker:wood_thunder` | 双掌相击，掌下闪电与落叶，叶绿配黄绿 | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/wood_thunder.png) | [PNG](../../fabric/src/main/resources/assets/meridian_evoker/textures/gui/technique/wood_thunder_64.png) | [提示词](art-prompts/wood_thunder_technique_icon_prompt.txt) |

## 后处理

照 10-28 破门斩的做法，脚本是 [scripts/process_art.py](../../scripts/process_art.py)，成品全由原稿和脚本决定，不手修。

- **卷轴**：去品红底，判据是 R、B ≥ 130，G ≤ 140，且 R−G、B−G ≥ 60。然后依次：
  - 裁去透明边，最近邻缩到 30×30 以内；
  - 透明像素 RGB 归零后无抖动量化为 32 色；
  - 居中放进 32×32 RGBA，alpha 二值化为 0 或 255。
- **阅读背景**：保持 1536×1024 RGB。
- **战斗图标**：先最近邻缩成 64×64。与主要暗底 RGB 距离不超过 20 的颜色统一成本书底色 `#192532`，再无抖动量化为 32 色。最后重画两圈边框：
  - 外圈 `#08090A`，内圈 `#BEEABA`，颜色取自原稿实际画出的边框；
  - 64×64 每圈宽 2 像素；
  - 最近邻缩成 32×32 后每圈宽 1 像素。
- 主体不重画。盒式缩放对比过，更平滑但带糊边，不像像素画，所以和破门斩一样用最近邻。

重出某一张：

1. 改对应提示词；
2. 用 `handoff/art-20261111/art/run_cpa.sh <新版本> evoker:<名字>:<icon|scroll|background>` 出原稿；
3. 再跑 `python3 scripts/process_art.py v1 <种类>/<名字>=<新版本>`，只换这一张。

锐金图标用 v2 原稿：v1 画成一手横托、一手抹过刀光，主体偏左，缩到 32×32 后刀光看不出来；v2 改成居中竖起的剑指。
其余全部来自 v1 原稿。复现命令是 `python3 scripts/process_art.py v1 icon/metal_edge=v2`；换过某张后把新参数更新在这里。

## 校验

`verification` 的 `EvokerResourcesTest.scrollsBackgroundsAndTechniqueIconsCoverTheCatalog` 按目录逐项检查：

- 每卷有 32×32 卷轴物品图和 1536×1024 阅读背景；
- 每式的 `icon_id` 与式 ID 同名，32×32 和 64×64 图标都能解码。
