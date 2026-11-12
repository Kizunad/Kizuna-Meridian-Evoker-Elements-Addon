"""从三条 CC0 录音生成中卷《附》施放声用的本仓 OGG：击掌、搓掌、吹气。

用法：python3 scripts/process_infuse_sounds.py <击掌预览> <搓掌预览> <呼吸预览 OGG> <sounds 目录>

- 击掌：semccab，hand clap.wav（freesound 154399）的公开 hq MP3 预览，取第一下击掌；
- 搓掌：bbrocer，Rubbing Hands Together.wav（freesound 382658）的公开 hq MP3 预览，取连续四下搓动，约 0.6 s。
  录音 2.070 s 处有一个 4 个采样宽的数字爆点，比搓动本身响 17 dB，截取前先用 adeclick 去掉；
- 吹气：christophe1138，breathingsleeping.wav（freesound 554074）的公开 OGG 预览，和炼元呼吸同一条录音。
  取第一口呼气最响处前后 0.4 s，限到气声频段，开头短渐入、后面长渐出，做成一口「噗」。
截取点按 10–25 ms 包络找的起音，见 docs/wiki/Sound-Assets.md。三条都按浮点峰值对齐到各自的目标峰值。
"""

import re
import subprocess
import sys
from pathlib import Path

CLAP_START = 1.49
CLAP_SECONDS = 0.40
CLAP_FILTER = "highpass=f=80"
CLAP_FADE_IN = 0.005
CLAP_FADE_OUT = 0.15
CLAP_PEAK_DB = -3.0

RUB_START = 1.68
RUB_SECONDS = 0.60
RUB_FILTER = "highpass=f=150,afftdn=nf=-64:tn=1"
RUB_FADE_IN = 0.03
RUB_FADE_OUT = 0.12
RUB_PEAK_DB = -10.0

PUFF_START = 4.76
PUFF_SECONDS = 0.40
PUFF_FILTER = "highpass=f=500,afftdn=nf=-64:tn=1,lowpass=f=6000"
PUFF_FADE_IN = 0.02
PUFF_FADE_OUT = 0.32
PUFF_PEAK_DB = -10.0

SAMPLE_RATE = 44100
VORBIS_QUALITY = 4


def segment(start, seconds, character, before=""):
    # 截取后从片段开头重新计时，再接本条录音的滤波；before 是要在截取前对整条录音做的处理。
    trim = f"atrim=start={start}:duration={seconds},asetpts=PTS-STARTPTS,{character}"
    return f"{before},{trim}" if before else trim


def fades(seconds, fade_in, fade_out):
    # 开头渐入、结尾渐出，避免片段边缘的爆音。
    return f"afade=t=in:d={fade_in},afade=t=out:st={seconds - fade_out:.3f}:d={fade_out}"


def peak(source, graph):
    # 用 astats 读处理后的浮点峰值 dB；volumedetect 按 16 位算，超过 0 dB 的峰会被截成 0。
    result = subprocess.run(
        ["ffmpeg", "-hide_banner", "-nostats", "-i", str(source),
         "-af", graph + ",astats=measure_perchannel=none:measure_overall=Peak_level", "-f", "null", "-"],
        check=True, capture_output=True, text=True)
    match = re.search(r"Peak level dB: (-?[0-9.]+)", result.stderr)
    if match is None:
        raise RuntimeError("astats 没有给出峰值")
    return float(match.group(1))


def encode(source, graph, target):
    # 单声道 44.1 kHz Vorbis，与炼元呼吸素材同一规格；bitexact 固定 Ogg 流序列号，重跑逐字节相同。
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source), "-af", graph,
         "-ac", "1", "-ar", str(SAMPLE_RATE), "-c:a", "libvorbis", "-q:a", str(VORBIS_QUALITY),
         "-fflags", "+bitexact", "-flags:a", "+bitexact", str(target)],
        check=True)


def produce(source, graph, target_peak_db, seconds, fade_in, fade_out, target):
    # 按处理后的峰值补增益，让成品峰值落在目标上，再加渐入渐出编码。
    gain = target_peak_db - peak(source, graph)
    encode(source, f"{graph},volume={gain:.2f}dB,{fades(seconds, fade_in, fade_out)}", target)


def main():
    clap_source, rub_source, breath_source, output = (Path(argument) for argument in sys.argv[1:5])
    produce(clap_source, segment(CLAP_START, CLAP_SECONDS, CLAP_FILTER), CLAP_PEAK_DB,
            CLAP_SECONDS, CLAP_FADE_IN, CLAP_FADE_OUT, output / "hands/clap.ogg")
    produce(rub_source, segment(RUB_START, RUB_SECONDS, RUB_FILTER, before="adeclick"), RUB_PEAK_DB,
            RUB_SECONDS, RUB_FADE_IN, RUB_FADE_OUT, output / "hands/rub.ogg")
    produce(breath_source, segment(PUFF_START, PUFF_SECONDS, PUFF_FILTER), PUFF_PEAK_DB,
            PUFF_SECONDS, PUFF_FADE_IN, PUFF_FADE_OUT, output / "breath/puff.ogg")


if __name__ == "__main__":
    main()
