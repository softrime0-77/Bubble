"""
Bubble 효과음 생성기 (외부 음원 없이 직접 합성 → 저작권 걱정 없음).

사용법 (numpy, ffmpeg 필요):
    python3 tools/music/make_sfx.py
결과:
    app/src/main/res/raw/sfx_*.ogg   (안드로이드)
    ios/Bubble/Music/sfx_*.m4a       (iOS)
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ANDROID_DIR = os.path.join(ROOT, "app", "src", "main", "res", "raw")
IOS_DIR = os.path.join(ROOT, "ios", "Bubble", "Music")
rng = np.random.default_rng(5)


def T(sec):
    return np.arange(int(sec * SR)) / SR


def tone(f, sec, kind="sine"):
    t = T(sec)
    f = np.broadcast_to(f, t.shape) if np.ndim(f) else np.full(t.shape, f)
    ph = np.cumsum(f) / SR
    if kind == "square":
        return np.where(ph % 1 < 0.5, 1.0, -1.0)
    if kind == "tri":
        return 4 * np.abs(ph % 1 - 0.5) - 1
    return np.sin(2 * np.pi * ph)


def smooth(x, k):
    ker = np.ones(k) / k
    return np.convolve(x, ker, "same")


def mix(*parts, length=None):
    n = length or max(len(p[1]) + int(p[0] * SR) for p in parts)
    out = np.zeros(n)
    for start, sig, gain in parts:
        i = int(start * SR)
        out[i:i + len(sig)] += sig[: max(0, n - i)] * gain
    return out


def sparkle(sec=0.35, base=1800, count=5, gap=0.045):
    parts = []
    for i in range(count):
        f = base * (1.12 ** i)
        t = T(0.25)
        parts.append((i * gap, tone(f, 0.25) * np.exp(-t * 14), 0.5))
    return mix(*parts)


def select():
    """내 방울 고르기: 통 하고 튀는 짧은 소리"""
    t = T(0.12)
    f = 700 + 500 * (t / t[-1])
    return tone(f, 0.12, "tri") * np.exp(-t * 28)


def shoot():
    """방울이 휙 날아감: 바람 소리 + 올라가는 음"""
    t = T(0.42)
    n = smooth(rng.uniform(-1, 1, len(t)), 6)
    env = np.sin(np.pi * np.clip(t / 0.42, 0, 1)) ** 1.5
    rise = tone(400 + 900 * t / t[-1], 0.42, "sine") * np.exp(-t * 5)
    return n * env * 0.9 + rise * 0.35


def hit():
    """맞았지만 안 터짐: 통! 하는 젤리 소리"""
    t = T(0.25)
    f = 380 * np.exp(-t * 6) + 160
    return tone(f, 0.25) * np.exp(-t * 14) + smooth(rng.uniform(-1, 1, len(t)), 3) * np.exp(-t * 60) * 0.3


def pop():
    """펑! 터짐: 쿵 + 파삭 + 반짝"""
    t = T(0.7)
    boom = np.sin(2 * np.pi * (45 + 220 * np.exp(-t * 22)) * t) * np.exp(-t * 7)
    crack = rng.uniform(-1, 1, len(t)) * np.exp(-t * 16)
    bub = tone(900 * np.exp(-t * 9) + 300, 0.7) * np.exp(-t * 18) * 0.5
    return mix((0, boom, 1.0), (0, crack, 0.45), (0, bub, 0.6), (0.06, sparkle(base=2000, count=4), 0.35))


def split():
    """가위로 싹둑 → 두 갈래로 휙"""
    t = T(0.06)
    snip = rng.uniform(-1, 1, len(t)) * np.exp(-t * 80)
    snip2 = snip.copy()
    whoosh = shoot() * 0.6
    return mix((0, snip, 0.9), (0.09, snip2, 0.8), (0.12, whoosh, 1.0))


def potion():
    """💖 부활물약: 유리 쨍 + 보글보글 + 마법 반짝 상승"""
    t = T(0.5)
    glass = sum(tone(f, 0.5) * np.exp(-t * (9 + i * 3)) for i, f in enumerate((2350, 3120, 4400))) / 3
    bubbles = []
    for i in range(6):
        tb = T(0.09)
        f = 500 + 120 * i + 900 * tb / tb[-1]
        bubbles.append((0.12 + i * 0.07, tone(f, 0.09) * np.sin(np.pi * tb / tb[-1]), 0.35))
    rise = []
    notes = [523, 659, 784, 1047, 1319, 1568]
    for i, f in enumerate(notes):
        tt = T(0.5)
        rise.append((0.35 + i * 0.07, tone(f, 0.5, "tri") * np.exp(-tt * 6), 0.28))
    return mix((0, glass, 0.8), *bubbles, *rise, (0.8, sparkle(base=2400, count=5), 0.35))


def coin():
    """💰 동전: 띠링"""
    t1, t2 = T(0.07), T(0.35)
    return mix((0, tone(1976, 0.07, "square") * 0.6, 0.5), (0.07, tone(2637, 0.35, "square") * np.exp(-t2 * 9), 0.5))


def win():
    """승리 징글: 도미솔도 ↑ + 반짝"""
    notes = [(0, 523), (0.11, 659), (0.22, 784), (0.33, 1047)]
    parts = [(s, tone(f, 0.5, "square") * np.exp(-T(0.5) * 5) * 0.5 + tone(f * 2, 0.5) * np.exp(-T(0.5) * 7) * 0.3, 0.5) for s, f in notes]
    chord = sum(tone(f, 0.9, "tri") for f in (523, 659, 784, 1047)) / 4 * np.exp(-T(0.9) * 3)
    return mix(*parts, (0.45, chord, 0.6), (0.5, sparkle(base=2100, count=6), 0.3))


def lose():
    """패배: 뿅뿅 내려가는 소리"""
    notes = [(0, 659), (0.18, 587), (0.36, 523), (0.54, 392)]
    return mix(*[(s, tone(f, 0.35, "tri") * np.exp(-T(0.35) * 6), 0.55) for s, f in notes])


SOUNDS = {"select": select, "shoot": shoot, "hit": hit, "pop": pop, "split": split,
          "potion": potion, "coin": coin, "win": win, "lose": lose}


def write_wav(path, x):
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.9
    fade = min(len(x), int(0.01 * SR))
    x[-fade:] *= np.linspace(1, 0, fade)
    pcm = (x * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    os.makedirs(ANDROID_DIR, exist_ok=True)
    os.makedirs(IOS_DIR, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in SOUNDS.items():
            wav = os.path.join(tmp, name + ".wav")
            write_wav(wav, fn())
            ogg = os.path.join(ANDROID_DIR, f"sfx_{name}.ogg")
            m4a = os.path.join(IOS_DIR, f"sfx_{name}.m4a")
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", ogg], check=True)
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "aac", "-b:a", "96k", m4a], check=True)
            print(f"{name:8s} ogg {os.path.getsize(ogg) // 1024}KB  m4a {os.path.getsize(m4a) // 1024}KB")


if __name__ == "__main__":
    main()
