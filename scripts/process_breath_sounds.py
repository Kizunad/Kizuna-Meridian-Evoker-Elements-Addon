"""从 CC0 平静呼吸录音生成炼元五式与心火的呼吸 OGG。

用法：python3 scripts/process_breath_sounds.py <breathingsleeping 预览 OGG> <输出目录>

来源与 UI 附属的吐纳素材相同（christophe1138，breathingsleeping.wav 的公开 OGG 预览），
截取点、降噪链和增益也与 UI 附属一致；五个吐字声在降噪后按五脏诀原稿 §9.2 各加一种滤波，
再把峰值对齐到未滤波呼气的峰值，听感音量相近。嘘字另加 6 kHz 低通，与只留高频的呬字分开。
"""

import re
import subprocess
import sys
from pathlib import Path

INHALE_START = 1.35
INHALE_SECONDS = 1.90
EXHALE_START = 4.50
EXHALE_SECONDS = 2.10
SHORT_INHALE_SECONDS = 0.5
DENOISE = "highpass=f=120,afftdn=nf=-64:tn=1,volume=30dB"
BODY_BAND = "lowpass=f=5000"
FADE_IN = 0.12
FADE_OUT = 0.18
SHORT_FADE_OUT = 0.10
SAMPLE_RATE = 44100
VORBIS_QUALITY = 4
PLOSIVE_SECONDS = 0.1
PLOSIVE_GAIN = 0.35

# 吐字滤波：键为文件名，值为降噪后的滤波链（五脏诀原稿 §9.2）。
EXHALE_FILTERS = {
    "exhale_si": "highpass=f=4000,highpass=f=4000",
    "exhale_hu": "highpass=f=300,lowpass=f=1200",
    "exhale_he": "lowpass=f=1500,lowpass=f=1500",
    "exhale_xu": "highpass=f=2000,highpass=f=2000,lowpass=f=6000",
}


def segment(start, seconds):
    # 截取与降噪；asetpts 让渐变时刻从片段开头算起。
    return f"atrim=start={start}:duration={seconds},asetpts=PTS-STARTPTS,{DENOISE}"


def fades(seconds, fade_out):
    # 开头渐入、结尾渐出，避免片段边缘的爆音。
    return f"afade=t=in:d={FADE_IN},afade=t=out:st={seconds - fade_out}:d={fade_out}"


def peak(source, graph):
    # 用 volumedetect 读处理后的峰值 dB。
    result = subprocess.run(
        ["ffmpeg", "-hide_banner", "-nostats", "-i", str(source), "-af", graph + ",volumedetect", "-f", "null", "-"],
        check=True, capture_output=True, text=True)
    match = re.search(r"max_volume: (-?[0-9.]+) dB", result.stderr)
    if match is None:
        raise RuntimeError("volumedetect 没有给出峰值")
    return float(match.group(1))


def encode(source, graph, target):
    # 单声道 44.1 kHz Vorbis，与 UI 附属的呼吸素材同一规格。
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source), "-af", graph,
         "-ac", "1", "-ar", str(SAMPLE_RATE), "-c:a", "libvorbis", "-q:a", str(VORBIS_QUALITY), str(target)],
        check=True)


def encode_plosive(source, graph, target):
    # 吹字：在呼气开头叠一段 0.1 s 的低频噪声爆破，按指数衰减收住。
    noise = (f"anoisesrc=color=brown:sample_rate={SAMPLE_RATE}:duration={PLOSIVE_SECONDS},"
             f"lowpass=f=900,afade=t=out:d={PLOSIVE_SECONDS}:curve=exp,volume={PLOSIVE_GAIN}")
    mixed = f"[0:a]{graph}[breath];{noise}[burst];[breath][burst]amix=inputs=2:duration=first:normalize=0"
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source), "-filter_complex", mixed,
         "-ac", "1", "-ar", str(SAMPLE_RATE), "-c:a", "libvorbis", "-q:a", str(VORBIS_QUALITY), str(target)],
        check=True)


def main():
    source = Path(sys.argv[1])
    output = Path(sys.argv[2])
    inhale = segment(INHALE_START, INHALE_SECONDS)
    exhale = segment(EXHALE_START, EXHALE_SECONDS)
    encode(source, f"{inhale},{BODY_BAND},{fades(INHALE_SECONDS, FADE_OUT)}", output / "inhale.ogg")
    short = segment(INHALE_START, SHORT_INHALE_SECONDS)
    encode(source, f"{short},{BODY_BAND},{fades(SHORT_INHALE_SECONDS, SHORT_FADE_OUT)}",
           output / "inhale_short.ogg")

    reference = peak(source, f"{exhale},{BODY_BAND}")
    for name, character in EXHALE_FILTERS.items():
        filtered = f"{exhale},{character}"
        gain = reference - peak(source, filtered)
        encode(source, f"{filtered},volume={gain:.2f}dB,{fades(EXHALE_SECONDS, FADE_OUT)}",
               output / f"{name}.ogg")
    encode_plosive(source, f"{exhale},{BODY_BAND},{fades(EXHALE_SECONDS, FADE_OUT)}", output / "exhale_chui.ogg")


if __name__ == "__main__":
    main()
