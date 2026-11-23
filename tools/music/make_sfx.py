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


# ---------------- 캐릭터별 분할 소리 ----------------
def snip():
    t = T(0.06)
    return rng.uniform(-1, 1, len(t)) * np.exp(-t * 80)


def split_ink():
    """🐙 문어: 퍽! 먹물 + 보글"""
    t = T(0.35)
    splat = smooth(rng.uniform(-1, 1, len(t)), 30) * np.exp(-t * 12) * 3
    blub = [(0.1 + i * 0.06, tone(300 - i * 40 + 200 * T(0.08) / 0.08, 0.08) * np.sin(np.pi * T(0.08) / 0.08), 0.4) for i in range(3)]
    return mix((0, snip(), 0.6), (0.05, splat, 0.9), *blub, (0.12, shoot() * 0.5, 0.8))


def split_ice():
    """🐧❄️ 펭귄·눈송이: 쨍그랑 얼음 + 차가운 종소리"""
    t = T(0.4)
    crack = rng.uniform(-1, 1, len(t)) * np.exp(-t * 30)
    bells = sum(tone(f, 0.4) * np.exp(-t * (6 + i)) for i, f in enumerate((2637, 3520, 4186))) / 3
    return mix((0, crack, 0.6), (0.02, bells, 0.7), (0.12, shoot() * 0.5, 0.7))


def split_fire():
    """🐉 아기 용: 화르륵 불꽃"""
    t = T(0.5)
    roar = smooth(rng.uniform(-1, 1, len(t)), 12) * np.sin(np.pi * t / t[-1]) * 2.5
    low = tone(90 + 60 * np.sin(2 * np.pi * 7 * t), 0.5, "tri") * np.sin(np.pi * t / t[-1]) * 0.5
    return mix((0, snip(), 0.5), (0.04, roar + low, 0.9))


def split_magic():
    """🦄🌸 유니콘·핑크 고양이: 하프 반짝 갈라짐"""
    notes = [784, 988, 1175, 1568, 1976]
    parts = [(i * 0.045, tone(f, 0.4, "tri") * np.exp(-T(0.4) * 8), 0.4) for i, f in enumerate(notes)]
    return mix((0, snip(), 0.4), *parts, (0.2, sparkle(base=2400, count=4), 0.35))


def split_bamboo():
    """🐼 판다: 딱! 대나무 쪼개기"""
    t = T(0.25)
    knock = (tone(620, 0.25) + 0.6 * tone(1340, 0.25)) * np.exp(-t * 30)
    knock2 = (tone(540, 0.25) + 0.6 * tone(1180, 0.25)) * np.exp(-t * 30)
    return mix((0, knock, 0.8), (0.11, knock2, 0.7), (0.14, shoot() * 0.4, 0.7))


def split_boing():
    """🐰 연승 토끼: 뾰옹 하고 튀어나감"""
    t = T(0.35)
    f = 220 + 600 * np.abs(np.sin(2 * np.pi * 3 * t)) * np.exp(-t * 4)
    return mix((0, snip(), 0.4), (0.03, tone(f, 0.35, "tri") * np.exp(-t * 5), 0.8))


def split_gold():
    """💰🏆 복냥이·황금 햄스터: 금화 쨍그랑 갈라짐"""
    return mix((0, snip(), 0.6), (0.05, coin(), 0.7), (0.12, coin(), 0.5), (0.14, shoot() * 0.4, 0.7))


# ---------------- 캐릭터별 💰 받는 소리 ----------------
def coin_lucky():
    """💰 복냥이: 띠링 + 방울 딸랑"""
    t = T(0.3)
    bell = (tone(3300, 0.3) + tone(4100, 0.3)) * np.exp(-t * 14) * 0.5
    return mix((0, coin(), 0.8), (0.12, bell, 0.5))


def coin_dragon():
    """🐉 아기 용: 화르륵 + 띠링"""
    t = T(0.25)
    puff = smooth(rng.uniform(-1, 1, len(t)), 10) * np.sin(np.pi * t / t[-1]) * 2
    return mix((0, puff, 0.6), (0.1, coin(), 0.8))


def coin_rabbit():
    """🐰 연승 보상: 빰빠밤 + 동전 쏟아짐"""
    fan = [(0, 784), (0.1, 784), (0.2, 1047)]
    parts = [(s, tone(f, 0.3, "square") * np.exp(-T(0.3) * 7), 0.35) for s, f in fan]
    coins = [(0.3 + i * 0.07, coin(), 0.35) for i in range(4)]
    return mix(*parts, *coins)


def coin_unicorn():
    """🦄 무지개 축복: 하프 글리산도 + 동전"""
    notes = [523, 659, 784, 1047, 1319, 1568, 2093]
    parts = [(i * 0.04, tone(f, 0.5, "tri") * np.exp(-T(0.5) * 6), 0.3) for i, f in enumerate(notes)]
    return mix(*parts, (0.3, coin(), 0.6), (0.35, sparkle(base=2600, count=5), 0.3))


def buy():
    """🛒 상점 구매: 철컥 + 띠링(카칭) + 동전 쏟아짐 + 반짝"""
    t = T(0.08)
    clack = (smooth(rng.uniform(-1, 1, len(t)), 4) * np.exp(-t * 60) + tone(180, 0.08) * np.exp(-t * 40))
    bell = sum(tone(f, 0.6) * np.exp(-T(0.6) * (5 + i * 2)) for i, f in enumerate((1568, 2349, 3136))) / 3
    coins = [(0.25 + i * 0.06, coin(), 0.3) for i in range(4)]
    return mix((0, clack, 0.8), (0.08, bell, 0.9), *coins, (0.45, sparkle(base=2200, count=5), 0.3))


def equip():
    """✨ 장착: 뿅 하고 반짝"""
    t = T(0.18)
    f = 500 + 900 * (t / t[-1]) ** 0.7
    return mix((0, tone(f, 0.18, "tri") * np.exp(-t * 6), 0.7), (0.1, sparkle(base=1800, count=4, gap=0.04), 0.4))


def match():
    """⚔️ 매칭 성공: 두구두구 → 짠! (VS)"""
    roll = [(i * 0.045, snare_hit(), 0.25 + i * 0.03) for i in range(8)]
    t = T(0.9)
    chord = sum(tone(f, 0.9, "square") for f in (523, 659, 784, 1047)) / 4 * np.exp(-t * 3.5)
    boom = np.sin(2 * np.pi * (50 + 160 * np.exp(-t * 20)) * t) * np.exp(-t * 6)
    return mix(*roll, (0.38, boom, 0.9), (0.38, chord, 0.5), (0.45, sparkle(base=2000, count=6), 0.35))


def snare_hit():
    t = T(0.08)
    return rng.uniform(-1, 1, len(t)) * np.exp(-t * 40)


def my_turn():
    """⏰ 내 차례: 띵동"""
    return mix((0, tone(1319, 0.5) * np.exp(-T(0.5) * 7), 0.6), (0.13, tone(1760, 0.6) * np.exp(-T(0.6) * 6), 0.6))


def tick():
    """⏱ 남은 시간 째깍"""
    t = T(0.07)
    return (tone(1900, 0.07) + 0.5 * tone(3800, 0.07)) * np.exp(-t * 70)


def fanfare(notes, chord, step=0.11, dur=1.0, wave_kind="square"):
    parts = [(i * step, tone(f, 0.35, wave_kind) * np.exp(-T(0.35) * 6) * 0.6 + tone(f * 2, 0.35) * np.exp(-T(0.35) * 8) * 0.25, 0.5)
             for i, f in enumerate(notes)]
    t = T(dur)
    ch = sum(tone(f, dur, "tri") + 0.4 * tone(f, dur, wave_kind) for f in chord) / len(chord) * np.exp(-t * 2.5)
    return mix(*parts, (len(notes) * step, ch, 0.55))


def checkin():
    """📅 출석: 빰빠밤!"""
    return mix((0, fanfare([784, 784, 1047], [523, 659, 784, 1047]), 1.0), (0.35, sparkle(base=2100, count=5), 0.35))


def mission():
    """🎯 미션 보상: 띠링 + 동전 쏟아짐"""
    coins = [(i * 0.07, coin(), 0.4) for i in range(6)]
    return mix((0, tone(1568, 0.4) * np.exp(-T(0.4) * 8), 0.5), *coins, (0.45, sparkle(base=2300, count=5), 0.3))


def rank_up():
    """⬆️ 계급 승급: 쭉 올라가는 레벨업 + 큰 화음"""
    notes = [523, 587, 659, 784, 880, 1047, 1175, 1319, 1568]
    parts = [(i * 0.055, tone(f, 0.2, "square") * np.exp(-T(0.2) * 12), 0.35) for i, f in enumerate(notes)]
    t = T(1.4)
    ch = sum(tone(f, 1.4, "tri") + 0.5 * tone(f, 1.4, "square") for f in (523, 659, 784, 1047, 1319)) / 5 * np.exp(-t * 1.8)
    boom = np.sin(2 * np.pi * (55 + 120 * np.exp(-t * 18)) * t) * np.exp(-t * 6)
    return mix(*parts, (0.5, ch, 0.6), (0.5, boom, 0.6), (0.55, sparkle(base=2000, count=7), 0.4), (0.85, sparkle(base=2600, count=5), 0.3))


def rank_down():
    """⬇️ 강등: 뿅… 아쉬움"""
    t = T(0.9)
    f = 700 * np.exp(-t * 1.6) + 150
    return mix((0, tone(f, 0.9, "tri") * np.exp(-t * 2.5), 0.7), (0.25, tone(330, 0.5) * np.exp(-T(0.5) * 5), 0.3))


def weekly():
    """🏆 주간 랭킹 보상: 왕관 팡파르 + 동전"""
    fan = fanfare([523, 659, 784, 1047, 784, 1047], [523, 659, 784, 1047, 1319], step=0.1, dur=1.3, wave_kind="saw")
    coins = [(0.75 + i * 0.07, coin(), 0.3) for i in range(5)]
    return mix((0, fan, 0.9), *coins, (0.7, sparkle(base=2200, count=7), 0.35))


SOUNDS = {"turn": my_turn, "tick": tick, "checkin": checkin, "mission": mission, "rank_up": rank_up,
          "rank_down": rank_down, "weekly": weekly, "match": match, "buy": buy, "equip": equip, "select": select, "shoot": shoot, "hit": hit, "pop": pop, "split": split,
          "split_ink": split_ink, "split_ice": split_ice, "split_fire": split_fire, "split_magic": split_magic,
          "split_bamboo": split_bamboo, "split_boing": split_boing, "split_gold": split_gold,
          "potion": potion, "coin": coin_lucky, "coin_dragon": coin_dragon, "coin_rabbit": coin_rabbit,
          "coin_unicorn": coin_unicorn, "win": win, "lose": lose}


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
