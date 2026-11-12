# 声音与图标素材

## 事件

强身五式：

| 事件 | 指向 | 时刻 | 音量 / 音高 |
|---|---|---|---|
| `metal_arms.flap` | 原版 `entity.parrot.fly` | 1.00、2.10 s | 0.6 / 0.8 |
| `metal_arms.cast` | 原版 `item.armor.equip_iron` | 2.60 s | 0.6 / 1.2 |
| `earth_legs.step` | 原版 `entity.polar_bear.step` | 1.40、2.70 s | 0.8 / 0.9 |
| `earth_legs.cast` | 原版 `block.rooted_dirt.place` | 3.20 s | 0.6 / 0.8 |
| `fire_heart.inhale` | 本仓 `breath/inhale_short` | 0.80 s | 0.4 / 1.2 |
| `fire_heart.cast` | 原版 `entity.warden.heartbeat` | 1.80 s | 0.7 / 1.3 |
| `water_return.step` | 原版 `entity.goat.step` | 0.90 s | 0.6 / 1.0 |
| `water_return.cast` | 原版 `block.pointed_dripstone.drip_water` | 2.40 s | 0.8 / 1.0 |
| `wood_sinew.pounce` | 原版 `entity.cat.hiss` | 2.80 s | 0.5 / 0.6 |
| `wood_sinew.cast` | 原版 `block.azalea_leaves.place` | 2.80 s | 0.5 / 0.8 |

炼元五式：从 1.0 s 起每 5 s 一遍呼吸，共 6 遍。每遍播 `refine.inhale`、本字的 `refine.exhale_<字>`，
吐字同时叠一声本行提示 `refine.cue_<行>`。吸气、吐字的音量音高原稿没给，先用 0.3 / 1.0。

| 式 | 吸气 / 吐字偏移 | 吐字声 | 本行提示 | 提示音量 / 音高 |
|---|---|---|---|---|
| 肺炼 | 0 / 2.0 s | `exhale_si` | 原版 `block.amethyst_block.chime` | 0.15 / 1.8 |
| 脾炼 | 0 / 1.0 s | `exhale_hu` | 原版 `block.sand.step` | 0.2 / 0.8 |
| 心炼 | 1.0 / 3.5 s | `exhale_he` | 原版 `entity.warden.heartbeat` | 0.3 / 1.0 |
| 肾炼 | 0 / 2.0 s | `exhale_chui` | 原版 `block.pointed_dripstone.drip_water` | 0.4 / 1.0 |
| 肝炼 | 2.5 / 0.5 s | `exhale_xu` | 原版 `block.azalea_leaves.place` | 0.3 / 1.0 |

中卷五式：全部以施放者为声源，时刻相对施放开始。五式开头都播一声 `infuse.raise`。

| 式 | 时刻 | 事件 | 指向 | 音量 / 音高 |
|---|---|---|---|---|
| 五式共用 | 0.00 s | `infuse.raise` | 原版 `entity.evoker.cast_spell` | 0.5 / 1.2 |
| 锐金 | 1.10 s | `metal_edge.wipe` | 原版 `block.grindstone.use` | 0.5 / 1.6 |
| 锐金 | 2.00 s | `metal_edge.cast` | 原版 `block.bell.use` | 0.3 / 2.0 |
| 震土 | 1.95 s | `earth_quake.stomp` | 原版 `entity.ravager.step` | 1.0 / 0.8 |
| 震土 | 2.00 s | `earth_quake.cast` | 原版 `block.rooted_dirt.break` | 0.8 / 0.7 |
| 灼火 | 0.70 s | `fire_burn.rub` | 本仓 `hands/rub` | 0.5 / 1.0 |
| 灼火 | 1.30 s | `fire_burn.blow` | 本仓 `breath/puff` | 0.6 / 1.3 |
| 灼火 | 1.70 s | `fire_burn.spark` | 原版 `item.flintandsteel.use` | 0.6 / 1.0 |
| 灼火 | 2.00 s | `fire_burn.cast` | 原版 `item.firecharge.use` | 0.5 / 1.3 |
| 寒水 | 0.70、1.00、1.35 s | `water_frost.press` | 原版 `block.powder_snow.step` | 0.5 / 1.0、0.9、0.8 |
| 寒水 | 2.00 s | `water_frost.cast` | 原版 `block.amethyst_block.chime` | 0.5 / 1.5 |
| 惊雷 | 1.40 s | `wood_thunder.clap` | 本仓 `hands/clap` | 0.8 / 1.0 |
| 惊雷 | 1.40 s | `wood_thunder.clap_spark` | 原版 `entity.lightning_bolt.impact` | 0.2 / 2.0 |
| 惊雷 | 2.00 s | `wood_thunder.cast` | 原版 `item.trident.thunder` | 0.2 / 1.6 |

惊雷的击掌要同时出两个音量、音高不同的声音，目录里一条 `sound` 效果只带一组音量音高，所以拆成同一时刻的两条。

附伤命中声：由 Damage 在目标位置播。相克时音量 ×1.5；被克时音量 ×0.6、音高 +0.2。

| 行 | 事件 | 指向 | 音量 / 音高 |
|---|---|---|---|
| 金 | `hit.metal` | 原版 `block.chain.place` | 0.5 / 1.6 |
| 土 | `hit.earth` | 原版 `block.rooted_dirt.break` | 0.6 / 0.8 |
| 火 | `hit.fire` | 原版 `entity.generic.burn` | 0.5 / 1.0 |
| 水 | `hit.water` | 原版 `entity.player.hurt_freeze` | 0.5 / 1.0 |
| 木 | `hit.wood` | 原版 `entity.lightning_bolt.impact` | 0.2 / 2.0 |

## 呼吸录音

炼元的 7 个 OGG 和中卷的 `breath/puff.ogg` 都来自同一条录音：[breathingsleeping.wav，christophe1138](https://freesound.org/people/christophe1138/sounds/554074/)，
页面标明 [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)。UI 附属的吐纳素材取自同一条。
采用作者公开的 [OGG 预览](https://cdn.freesound.org/previews/554/554074_9054093-lq.ogg)（原 WAV 下载要登录）。

处理脚本 `scripts/process_breath_sounds.py`（需 FFmpeg），截取点和降噪链与 UI 附属一致：

- 公共：120 Hz 高通、`afftdn=nf=-64:tn=1` 降噪、增益 30 dB、120 ms 渐入、180 ms 渐出，单声道 44100 Hz Vorbis（质量 4）；
- `inhale.ogg`：1.35 s 起 1.90 s，5 kHz 低通；`inhale_short.ogg`：同起点 0.5 s，100 ms 渐出；
- 呼气段 4.50 s 起 2.10 s，五个吐字声各加一种滤波，再把峰值对齐到只做 5 kHz 低通的呼气：
  - `exhale_si`：高通 4 kHz（两级）；
  - `exhale_hu`：带通 300–1200 Hz；
  - `exhale_he`：低通 1.5 kHz（两级）；
  - `exhale_xu`：高通 2 kHz（两级），另加 6 kHz 低通，与只留高频的呬分开；
  - `exhale_chui`：5 kHz 低通的呼气，开头叠 0.1 s 低频褐噪声爆破（900 Hz 低通、指数衰减、增益 0.35）。

重新生成：`python3 scripts/process_breath_sounds.py <预览 OGG> fabric/src/main/resources/assets/meridian_evoker/sounds/breath`。

## 中卷的击掌、搓掌、吹气

处理脚本 `scripts/process_infuse_sounds.py`（需 FFmpeg）。三条都是单声道 44100 Hz Vorbis（质量 4），按浮点峰值对齐到各自的目标；编码带 `bitexact`，重跑逐字节相同。

| 文件 | 来源 | 截取 | 处理 | 峰值 |
|---|---|---|---|---|
| `hands/clap.ogg` | [hand clap.wav，semccab](https://freesound.org/people/semccab/sounds/154399/)，取 [hq MP3 预览](https://cdn.freesound.org/previews/154/154399_2592496-hq.mp3) | 1.49 s 起 0.40 s，第一下击掌 | 80 Hz 高通，5 ms 渐入、150 ms 渐出 | −3 dB |
| `hands/rub.ogg` | [Rubbing Hands Together.wav，bbrocer](https://freesound.org/people/bbrocer/sounds/382658/)，取 [hq MP3 预览](https://cdn.freesound.org/previews/382/382658_4297074-hq.mp3) | 1.68 s 起 0.60 s，连续搓四下 | 截取前整条 `adeclick`，150 Hz 高通，`afftdn=nf=-64:tn=1`，30 ms 渐入、120 ms 渐出 | −10 dB |
| `breath/puff.ogg` | 上节的呼吸录音，同一条 OGG 预览 | 4.76 s 起 0.40 s，第一口呼气最响处 | 500 Hz 高通，`afftdn=nf=-64:tn=1`，6 kHz 低通，20 ms 渐入、320 ms 渐出 | −10 dB |

- 三页都标明 [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)；原 WAV 下载要登录，所以和呼吸录音一样取公开预览。击掌、搓掌取 hq MP3，是公开预览里质量最高的一档。
- 搓掌录音 2.070 s 处有一个 4 个采样宽的数字爆点，比搓动本身响约 17 dB。不去掉的话按峰值对齐后搓动声会小 17 dB。`adeclick` 有窗口延迟，所以放在截取前对整条做。
- 吹气不像炼元的 `exhale_chui` 那样叠合成爆破：从呼气最响处截起，起音约 50 ms，靠长渐出收成一口「噗」。
- 击掌预览在击掌瞬间有约 10 个采样到了满幅（解码后最高 1.05），原录音这一下可能轻微削顶；整体降到 −3 dB 后成品不越界，听不出。

重新生成：`python3 scripts/process_infuse_sounds.py <击掌预览> <搓掌预览> <呼吸预览 OGG> fabric/src/main/resources/assets/meridian_evoker/sounds`。

## 状态图标

`assets/meridian_evoker/textures/mob_effect/` 下的五张 18 × 18 RGBA 图标是占位素材，
经 CPA（`cliproxy` 后端）用 `gpt-image-2.5-sunburst` 生成 1024 × 1024 原稿，
再最近邻缩到 18 × 18、透明度以 128 为界二值化、无抖动 12 色调色板。
