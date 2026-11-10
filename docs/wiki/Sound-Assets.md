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

## 呼吸录音

7 个 OGG 都来自同一条录音：[breathingsleeping.wav，christophe1138](https://freesound.org/people/christophe1138/sounds/554074/)，
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

## 状态图标

`assets/meridian_evoker/textures/mob_effect/` 下的五张 18 × 18 RGBA 图标是占位素材，
经 CPA（`cliproxy` 后端）用 `gpt-image-2.5-sunburst` 生成 1024 × 1024 原稿，
再最近邻缩到 18 × 18、透明度以 128 为界二值化、无抖动 12 色调色板。
