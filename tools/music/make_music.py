"""
Bubble 게임 배경음악 생성기.

외부 음원 없이 코드로 직접 합성하므로 저작권 걱정이 없습니다.
곡마다 템포·조성·코드 진행·악기·리듬을 다르게 해서 캐릭터 느낌을 냅니다.
16마디를 만들고 끝에서 넘친 소리를 앞에 다시 더해, 반복 재생해도 이음매가 없게 합니다.

사용법 (numpy, ffmpeg 필요):
    python3 tools/music/make_music.py
결과:
    app/src/main/res/raw/bgm_*.ogg   (안드로이드)
    ios/Bubble/Music/bgm_*.m4a       (iOS)
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 32000
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ANDROID_DIR = os.path.join(ROOT, "app", "src", "main", "res", "raw")
IOS_DIR = os.path.join(ROOT, "ios", "Bubble", "Music")

MAJOR = [0, 2, 4, 5, 7, 9, 11]
MINOR = [0, 2, 3, 5, 7, 8, 10]
PENTA = [0, 2, 4, 7, 9]
LYDIAN = [0, 2, 4, 6, 7, 9, 11]
DORIAN = [0, 2, 3, 5, 7, 9, 10]

# 코드: (근음 음계 단계, 단/장 3화음은 음계에서 자동)
POP = [0, 4, 5, 3]          # I V vi IV
POP2 = [0, 5, 3, 4]         # I vi IV V
MINOR_EPIC = [0, 5, 6, 4]   # i VI VII v
MINOR_SAD = [0, 3, 6, 4]
LYDIAN_PROG = [0, 1, 0, 1]

midi = lambda m: 440.0 * 2 ** ((m - 69) / 12)


# ---------------------------------------------------------------- 소리 재료
def t_axis(n):
    return np.arange(n) / SR


def adsr(n, a=0.005, d=0.12, s=0.5, r=0.04, gate=None):
    t = t_axis(n)
    gate = gate if gate is not None else n / SR
    e = np.minimum(1.0, t / max(a, 1e-4)) * (s + (1 - s) * np.exp(-t / max(d, 1e-4)))
    e *= np.clip((gate + r - t) / r, 0, 1)
    return e


def wave_of(kind, f, n, duty=0.5, vib=0.0):
    t = t_axis(n)
    if vib:
        f = f * (1 + vib * np.sin(2 * np.pi * 5.5 * t) * np.minimum(1, t / 0.15))
        ph = np.cumsum(f) / SR % 1
    else:
        ph = (f * t) % 1
    if kind == "square":
        return np.where(ph < duty, 1.0, -1.0)
    if kind == "tri":
        return 4 * np.abs(ph - 0.5) - 1
    if kind == "saw":
        return 2 * ph - 1
    if kind == "bell":  # FM 종소리
        return np.sin(2 * np.pi * ph + 2.2 * np.exp(-t * 6) * np.sin(2 * np.pi * ph * 3.5))
    if kind == "pluck":
        return (2 * ph - 1) * np.exp(-t * 9)
    if kind == "organ":
        return np.sin(2 * np.pi * ph) + 0.5 * np.sin(4 * np.pi * ph) + 0.25 * np.sin(6 * np.pi * ph)
    return np.sin(2 * np.pi * ph)


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):  # 짧은 소리에만 씀
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def soft(x, cutoff):
    """빠른 저역 통과 (이동 평균 두 번)"""
    k = max(1, int(SR / cutoff / 2))
    ker = np.ones(k) / k
    return np.convolve(np.convolve(x, ker, "same"), ker, "same")


class Track:
    def __init__(self, length):
        self.n = length
        self.buf = np.zeros(length + SR * 3)

    def add(self, t, sig, gain):
        i = int(round(t * SR))
        self.buf[i:i + len(sig)] += sig * gain

    def looped(self):
        out = self.buf[: self.n].copy()
        tail = self.buf[self.n:]
        out[: len(tail)] += tail  # 끝에서 넘친 소리를 앞에 더해 이음매 없게
        return out


rng = np.random.default_rng(11)
NOISE = rng.uniform(-1, 1, SR * 2)


def kick(punch=1.0):
    n = int(0.3 * SR)
    t = t_axis(n)
    return np.sin(2 * np.pi * (45 + 140 * punch * np.exp(-t * 32)) * t) * np.exp(-t * 11)


def snare(tone=200, length=0.18):
    n = int(length * SR)
    t = t_axis(n)
    return (NOISE[:n] * 0.8 + 0.4 * np.sin(2 * np.pi * tone * t)) * np.exp(-t * 20)


def hat(length=0.04, bright=1.0):
    n = int(length * SR)
    x = NOISE[1000:1000 + n] - soft(NOISE[1000:1000 + n], 3000) * (1 - 0.3 * bright)
    return x * np.exp(-t_axis(n) * 90)


def shaker():
    n = int(0.09 * SR)
    t = t_axis(n)
    return (NOISE[5000:5000 + n] - soft(NOISE[5000:5000 + n], 5000)) * np.sin(np.pi * t / t[-1]) ** 2


def sleigh():
    n = int(0.12 * SR)
    t = t_axis(n)
    s = sum(np.sin(2 * np.pi * f * t) for f in (2600, 3150, 3870, 4600)) / 4
    return s * np.exp(-t * 25) * (0.6 + 0.4 * np.sign(np.sin(2 * np.pi * 45 * t)))


def coin():
    n = int(0.25 * SR)
    t = t_axis(n)
    f = np.where(t < 0.06, midi(83), midi(88))
    return np.where((np.cumsum(f) / SR % 1) < 0.5, 1.0, -1.0) * np.exp(-t * 9) * 0.6


def woodblock(f=800):
    n = int(0.08 * SR)
    t = t_axis(n)
    return np.sin(2 * np.pi * f * t) * np.exp(-t * 60)


# ---------------------------------------------------------------- 작곡
def chord_notes(key, scale, degree, octave=4):
    root = key + 12 * (octave + 1)
    sc = scale + [s + 12 for s in scale] + [s + 24 for s in scale]
    return [root + sc[degree], root + sc[degree + 2], root + sc[degree + 4]]


def scale_note(key, scale, step, octave=5):
    sc = scale
    o, i = divmod(step, len(sc))
    return key + 12 * (octave + 1 + o) + sc[i]


def make_melody(theme, bars):
    """8분음표(또는 16분) 칸마다 음계 단계. None = 쉼표. 2마디 모티프를 변형하며 반복."""
    r = np.random.default_rng(theme["seed"])
    steps = theme.get("steps", 8)
    scale = theme["scale"]
    prog = theme["prog"]
    rhythm = theme["rhythm"]  # 한 마디 칸 수만큼 1(음)/0(쉼)/2(길게)
    motif = []
    cur = 2
    for b in range(2):
        deg = prog[b % len(prog)]
        bar = []
        for k in range(steps):
            if rhythm[k] == 0:
                bar.append(None)
                continue
            if k == 0:  # 강박은 코드 음
                cur = deg + r.choice([0, 2, 4])
            else:
                cur += int(r.choice([-2, -1, -1, 1, 1, 2, 0, 3]))
                cur = int(np.clip(cur, -1, len(scale) + 4))
            bar.append(cur)
        motif.append(bar)
    out = []
    for b in range(bars):
        src = motif[b % 2]
        deg_now = prog[b % len(prog)]
        deg_src = prog[(b % 2) % len(prog)]
        shift = deg_now - deg_src
        bar = []
        for k, v in enumerate(src):
            if v is None:
                bar.append(None)
                continue
            nv = v + shift
            if b >= bars // 2 and k % 4 == 3:  # 뒤 절반은 살짝 변주
                nv += 1 if (b + k) % 2 else -1
            if b % 4 == 3 and k >= steps - 2:  # 4마디 끝은 코드 근음으로 마무리
                nv = deg_now + (7 if len(scale) == 7 else 5) if k == steps - 1 else nv
            bar.append(nv)
        out.append(bar)
    return out


def render(theme):
    bpm = theme["bpm"]
    beat = 60 / bpm
    bar_len = 4 * beat
    bars = 16
    total = bars * bar_len
    tr = Track(int(round(total * SR)))
    key = theme["key"]
    scale = theme["scale"]
    prog = theme["prog"]
    steps = theme.get("steps", 8)
    step = bar_len / steps
    mel = make_melody(theme, bars)
    swing = theme.get("swing", 0.0)

    for b in range(bars):
        t0 = b * bar_len
        deg = prog[b % len(prog)]
        ch = chord_notes(key, scale, deg, theme.get("chord_oct", 4))
        bass_root = ch[0] - 24

        # 베이스
        for k, (pos, ln, oct_) in enumerate(theme["bass"]):
            n = int(ln * beat * SR)
            f = midi(bass_root + oct_)
            sig = wave_of(theme.get("bass_wave", "tri"), f, n) * adsr(n, d=0.1, s=0.5, gate=ln * beat * 0.85)
            tr.add(t0 + pos * beat, soft(sig, 1800), theme.get("bass_gain", 0.32))

        # 코드 (스탭 또는 아르페지오)
        if theme.get("arp"):
            for k in range(8):
                m = ch[k % 3] + (12 if k >= 3 and k % 2 else 0)
                n = int(beat / 2 * SR)
                sig = wave_of(theme.get("arp_wave", "pluck"), midi(m), n)
                tr.add(t0 + k * beat / 2, sig * adsr(n, d=0.12, s=0.2, gate=beat / 2 * 0.9), theme.get("arp_gain", 0.1))
        for pos, ln in theme.get("stabs", []):
            n = int(ln * beat * SR)
            sig = sum(wave_of(theme.get("chord_wave", "square"), midi(m), n, 0.25) for m in ch) / 3
            tr.add(t0 + pos * beat, soft(sig * adsr(n, d=0.08, s=0.3, gate=ln * beat * 0.8), 3500), theme.get("chord_gain", 0.08))
        if theme.get("pad"):
            n = int(bar_len * SR)
            sig = sum(wave_of("sine", midi(m), n) for m in ch) / 3
            tr.add(t0, sig * adsr(n, a=0.2, d=0.5, s=0.8, r=0.3, gate=bar_len * 0.9), theme["pad"])

        # 드럼
        for k in range(4):
            tb = t0 + k * beat
            if k in theme.get("kicks", (0, 1, 2, 3)):
                tr.add(tb, kick(theme.get("punch", 1.0)), theme.get("kick_gain", 0.55))
            if k in theme.get("snares", (1, 3)):
                tr.add(tb, snare(theme.get("snare_tone", 200)), theme.get("snare_gain", 0.22))
            off = beat / 2 + swing * beat / 2
            if theme.get("hats", True):
                tr.add(tb + off, hat(), theme.get("hat_gain", 0.12))
                if theme.get("hat16"):
                    tr.add(tb + beat / 4, hat(0.025), 0.06)
                    tr.add(tb + beat * 3 / 4, hat(0.025), 0.06)
            if theme.get("shaker"):
                tr.add(tb + off, shaker(), theme["shaker"])
            if theme.get("sleigh"):
                tr.add(tb + off, sleigh(), theme["sleigh"])
            if theme.get("wood") and k % 2 == 0:
                tr.add(tb + beat * 0.75, woodblock(theme["wood"]), 0.18)
        if theme.get("coin") and b % 2 == 1:
            tr.add(t0 + 3.5 * beat, coin(), theme["coin"])
        if b % 4 == 3 and theme.get("fill", True):  # 4마디마다 스네어 필인
            for i in range(4):
                tr.add(t0 + 3 * beat + i * beat / 4, snare(theme.get("snare_tone", 200), 0.1), 0.12 + i * 0.03)

        # 멜로디 (첫 2마디는 반주만 → 반복될 때 자연스럽게)
        if b >= theme.get("mel_from", 2) or b >= bars:
            for k, v in enumerate(mel[b]):
                if v is None:
                    continue
                ln = step
                if k + 1 < steps and mel[b][k + 1] is None:
                    ln = step * 2
                n = int(ln * SR)
                f = midi(scale_note(key, scale, v, theme.get("mel_oct", 5)))
                sig = wave_of(theme["lead"], f, n, theme.get("duty", 0.5), theme.get("vib", 0.0))
                if theme.get("lead2"):
                    sig = 0.65 * sig + 0.35 * wave_of(theme["lead2"], f * 2, n)
                sig = soft(sig, theme.get("lead_cut", 5000)) * adsr(n, d=theme.get("lead_decay", 0.12), s=0.55, gate=ln * 0.85)
                tk = t0 + k * step + (swing * step if k % 2 else 0)
                tr.add(tk, sig, theme.get("lead_gain", 0.11))
                if theme.get("echo"):
                    tr.add(tk + beat * 0.75, sig, theme.get("lead_gain", 0.11) * theme["echo"])

    out = tr.looped()
    out = out / (np.max(np.abs(out)) + 1e-9) * 0.8
    return out


# ---------------------------------------------------------------- 곡 목록
BASS_8 = [(i * 0.5, 0.5, 12 if i % 2 else 0) for i in range(8)]
BASS_4 = [(i, 1, 0) for i in range(4)]
BASS_BOUNCE = [(0, 0.75, 0), (0.75, 0.25, 12), (1.5, 0.5, 0), (2, 0.75, 0), (2.75, 0.25, 12), (3.5, 0.5, 7)]
BASS_WALK = [(0, 1, 0), (1, 1, 7), (2, 1, 12), (3, 1, 10)]
OFF_STABS = [(0.5, 0.5), (1.5, 0.5), (2.5, 0.5), (3.5, 0.5)]
R_BUSY = [1, 1, 0, 1, 1, 0, 1, 1]
R_BOUNCY = [1, 0, 1, 1, 0, 1, 1, 0]
R_CALM = [1, 0, 0, 1, 1, 0, 1, 0]
R_16 = [1, 0, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 1, 1, 0, 1]

THEMES = {
    # 매칭 대기: 째깍째깍 두근두근 (상대를 찾는 중)
    "matching": dict(bpm=108, key=9, scale=MINOR, prog=[0, 5, 3, 4], seed=7, lead="bell", lead_gain=0.1, rhythm=[1, 0, 0, 1, 0, 0, 1, 0],
                     bass=[(0, 0.5, 0), (0.5, 0.5, 0), (2, 0.5, 0), (2.5, 0.5, 0)], bass_wave="sine", stabs=[], arp=True, arp_wave="pluck",
                     arp_gain=0.09, kicks=(0, 2), snares=(), wood=1500, hat16=True, hat_gain=0.08, fill=False, mel_from=4),
    # 로비: 통통 튀는 기본 테마
    "lobby": dict(bpm=116, key=0, scale=MAJOR, prog=POP2, seed=3, lead="bell", lead_gain=0.13, rhythm=R_CALM,
                  bass=BASS_BOUNCE, stabs=OFF_STABS, chord_wave="tri", pad=0.03, shaker=0.06, hats=False, kicks=(0, 2), mel_oct=5),
    # 기본 고양이: 신나는 8비트
    "cat": dict(bpm=136, key=7, scale=MAJOR, prog=POP, seed=5, lead="square", duty=0.5, lead2="tri", rhythm=R_BUSY,
                bass=BASS_8, stabs=OFF_STABS, hat16=True),
    # 기본 햄스터: 통통 스타카토
    "hamster": dict(bpm=140, key=5, scale=MAJOR, prog=POP2, seed=8, lead="square", duty=0.25, rhythm=R_BOUNCY,
                    bass=BASS_BOUNCE, stabs=OFF_STABS, lead_decay=0.06, wood=1200),
    # 핑크 고양이: 달콤 반짝
    "pink_cat": dict(bpm=128, key=2, scale=MAJOR, prog=POP2, seed=21, lead="tri", lead2="bell", rhythm=R_BOUNCY,
                     bass=BASS_8, arp=True, arp_wave="bell", arp_gain=0.06, stabs=[], pad=0.03, shaker=0.05),
    # 까망 고양이: 살금살금 스윙 (단조)
    "black_cat": dict(bpm=118, key=9, scale=DORIAN, prog=[0, 3, 0, 4], seed=31, lead="saw", lead_cut=2200, rhythm=R_CALM,
                      bass=BASS_WALK, bass_wave="sine", stabs=[(1.5, 0.25), (3.5, 0.25)], swing=0.33, snares=(1, 3), kicks=(0, 2),
                      echo=0.3, snare_tone=180),
    # 눈송이 햄스터: 썰매 방울 겨울 테마
    "snow_hamster": dict(bpm=124, key=2, scale=MAJOR, prog=POP, seed=41, lead="bell", lead_gain=0.14, rhythm=R_BUSY,
                         bass=BASS_4, arp=True, arp_wave="bell", arp_gain=0.05, stabs=[], sleigh=0.12, hats=False, pad=0.04),
    # 황금 햄스터: 펑키 브라스
    "gold_hamster": dict(bpm=132, key=10, scale=MAJOR, prog=[0, 3, 4, 3], seed=51, lead="saw", lead_cut=3200, lead2="square",
                         rhythm=[1, 0, 1, 0, 1, 1, 0, 1], bass=BASS_BOUNCE, bass_wave="saw", stabs=[(0.5, 0.25), (1.75, 0.25), (2.5, 0.25), (3.75, 0.25)],
                         chord_wave="saw", hat16=True, coin=0.18),
    # 복냥이: 오음계 동전 테마
    "lucky_cat": dict(bpm=126, key=0, scale=PENTA, prog=[0, 3, 1, 3], seed=61, lead="square", duty=0.5, rhythm=R_BOUNCY,
                      bass=BASS_8, stabs=OFF_STABS, chord_wave="tri", coin=0.22, wood=900),
    # 문어: 흐느적 바닷속 (비브라토)
    "octopus": dict(bpm=112, key=4, scale=MINOR, prog=MINOR_SAD, seed=71, lead="sine", lead2="tri", vib=0.012, rhythm=R_CALM,
                    bass=BASS_WALK, bass_wave="sine", stabs=[], arp=True, arp_wave="sine", arp_gain=0.07, echo=0.35, kicks=(0, 2)),
    # 펭귄: 빠른 행진
    "penguin": dict(bpm=150, key=0, scale=MAJOR, prog=POP, seed=81, lead="square", duty=0.125, rhythm=R_BUSY, lead_decay=0.05,
                    bass=BASS_4, stabs=OFF_STABS, snares=(1, 3), hat16=True, sleigh=0.06),
    # 출석 판다: 느긋한 대나무 오음계
    "panda": dict(bpm=104, key=7, scale=PENTA, prog=[0, 3, 2, 3], seed=91, lead="pluck", lead_gain=0.16, rhythm=R_CALM,
                  bass=BASS_4, bass_wave="sine", stabs=[], arp=True, arp_wave="pluck", arp_gain=0.08, wood=700, hats=False, kicks=(0, 2)),
    # 아기 용: 웅장한 단조
    "dragon": dict(bpm=140, key=2, scale=MINOR, prog=MINOR_EPIC, seed=101, lead="saw", lead_cut=3000, lead2="square", rhythm=R_BUSY,
                   bass=BASS_8, bass_wave="saw", stabs=[(0, 0.5), (1.5, 0.5), (3, 0.5)], chord_wave="saw", punch=1.4, kick_gain=0.65,
                   hat16=True, pad=0.04),
    # 연승 토끼: 깡충깡충 초고속
    "rabbit": dict(bpm=160, key=4, scale=MAJOR, prog=POP2, seed=111, lead="square", duty=0.25, rhythm=R_BOUNCY,
                   bass=BASS_BOUNCE, stabs=OFF_STABS, hat16=True, lead_decay=0.07),
    # 유니콘: 몽환적인 무지개 (리디안)
    "unicorn": dict(bpm=122, key=5, scale=LYDIAN, prog=LYDIAN_PROG, seed=121, lead="bell", lead2="sine", lead_gain=0.13,
                    rhythm=R_BUSY, bass=BASS_4, bass_wave="sine", arp=True, arp_wave="bell", arp_gain=0.06, stabs=[], pad=0.05,
                    echo=0.35, shaker=0.05, kicks=(0, 2)),
}


def render_bright_lobby():
    """
    로비 곡: 홍보 숏츠 영상에 쓴 신나는 칩튠 (C-G-Am-F, 128BPM)을 16마디 반복 곡으로.
    끝에서 넘친 소리를 앞에 더해 이음매 없이 반복됨.
    """
    bpm = 128
    beat = 60 / bpm
    bars = 16
    total = bars * 4 * beat
    tr = Track(int(round(total * SR)))
    r = np.random.default_rng(3)

    def env(n, d=0.15, s=0.6, hold=None):
        return adsr(n, a=0.005, d=d, s=s, r=0.05, gate=hold)

    prog = [(48, [60, 64, 67]), (43, [59, 62, 67]), (45, [60, 64, 69]), (41, [60, 65, 69])]
    mel = [72, 76, 79, 76, 77, 76, 74, 72, 74, None, 71, 74, 79, 77, 76, 74,
           72, 76, 81, 79, 77, 76, 77, 79, 77, None, 76, 74, 72, None, 72, None]
    for b in range(bars):
        t0 = b * 4 * beat
        root, chord = prog[b % 4]
        for k in range(8):  # 베이스: 8분음표 통통
            n = int(beat / 2 * SR * 0.9)
            tr.add(t0 + k * beat / 2, wave_of("tri", midi(root + (12 if k % 2 else 0)), n) * env(n, d=0.08, s=0.3, hold=beat / 2 * 0.8), 0.22)
        for k in range(4):  # 코드: 엇박 스탭
            n = int(beat / 2 * SR)
            sig = sum(wave_of("square", midi(m), n, 0.25) for m in chord) / 3
            tr.add(t0 + k * beat + beat / 2, sig * env(n, d=0.06, s=0.15, hold=beat / 3), 0.07)
        for k in range(4):  # 드럼: 킥 · 하이햇 · 박수
            tk = t0 + k * beat
            n = int(0.25 * SR)
            tt = t_axis(n)
            tr.add(tk, np.sin(2 * np.pi * (50 + 120 * np.exp(-tt * 30)) * tt) * np.exp(-tt * 12), 0.55)
            hn = int(0.05 * SR)
            tr.add(tk + beat / 2, r.uniform(-1, 1, hn) * np.exp(-t_axis(hn) * 70), 0.10)
            if k in (1, 3):
                cn = int(0.18 * SR)
                tr.add(tk, r.uniform(-1, 1, cn) * np.exp(-t_axis(cn) * 18), 0.16)
        # 멜로디: 4마디 모티프 반복, 뒤 8마디는 한 옥타브 위 화음을 살짝 얹어 변화
        seg = mel[(b % 4) * 8:(b % 4) * 8 + 8]
        for k, m in enumerate(seg):
            if m is None:
                continue
            n = int(beat / 2 * SR)
            sig = wave_of("square", midi(m), n, 0.5) * 0.6 + wave_of("tri", midi(m + 12), n) * 0.4
            if b >= 8:
                sig = sig + wave_of("sine", midi(m + 12 + 4), n) * 0.18
            tr.add(t0 + k * beat / 2, sig * env(n, d=0.1, s=0.5, hold=beat / 2 * 0.85), 0.085)
    out = tr.looped()
    return out / (np.max(np.abs(out)) + 1e-9) * 0.8


def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    os.makedirs(ANDROID_DIR, exist_ok=True)
    os.makedirs(IOS_DIR, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, theme in THEMES.items():
            x = render_bright_lobby() if name == "lobby" else render(theme)
            wav = os.path.join(tmp, name + ".wav")
            write_wav(wav, x)
            ogg = os.path.join(ANDROID_DIR, f"bgm_{name}.ogg")
            m4a = os.path.join(IOS_DIR, f"bgm_{name}.m4a")
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "2", ogg], check=True)
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "aac", "-b:a", "64k", m4a], check=True)
            print(f"{name:13s} {len(x) / SR:5.1f}s  ogg {os.path.getsize(ogg) // 1024}KB  m4a {os.path.getsize(m4a) // 1024}KB")


if __name__ == "__main__":
    main()
