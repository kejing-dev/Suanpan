"""Renders the loudness evidence (build.md 响度要有证据文件 / 只有短促反馈音的应用): 30 s of play at the default volume
(HIGH = 1.0) from the app's own sound files and gains (platform/Sounds.kt): about two bead taps a second (click / drop,
three / two variants, ±5 % pitch, tap gain 0.9, groups of 3+ beads 1.0), one clear at 10 s, one right-answer bell at
20 s, the lesson-passed triad at 26 s. RENDERED, not recorded (the emulator's audio cannot be captured here).

  python tools/render_loudness.py <out.wav>     -> prints voiced RMS / peak with the ui_gates method
"""
import array
import math
import os
import random
import sys
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'sounds')
RATE = 44100
GAIN = {'click': 1.0, 'drop': 0.7, 'clear': 0.85, 'ding': 0.75}


def load(name):
    with wave.open(os.path.join(S, name), 'rb') as w:
        assert w.getsampwidth() == 2 and w.getnchannels() == 1 and w.getframerate() == RATE, name
        return array.array('h', w.readframes(w.getnframes()))


def resample(x, rate):
    n = int(len(x) / rate)
    return [x[min(len(x) - 1, int(i * rate))] for i in range(n)]


def main(out):
    random.seed(30)
    clicks = [load(f) for f in ('click.wav', 'click_2.wav', 'click_3.wav')]
    drops = [load(f) for f in ('drop.wav', 'drop_2.wav')]
    clear, ding = load('clear.wav'), load('ding.wav')
    total = RATE * 30
    mix = [0.0] * total
    last = {}

    def put(clip, t, gain, rate=1.0, kind=None):
        at = int(t * RATE)
        data = resample(clip, rate) if rate != 1.0 else clip
        if kind:  # one instance per kind: the new one stops the last (SoundPool stop)
            start = last.get(kind)
            if start is not None:
                for i in range(at, min(total, start[1])):
                    mix[i] -= start[2][i - start[0]] * start[3] if 0 <= i - start[0] < len(start[2]) else 0
            last[kind] = (at, at + len(data), data, gain)
        for i, v in enumerate(data):
            j = at + i
            if j < total:
                mix[j] += v * gain

    t = 0.3
    while t < 29.5:
        if 9.8 < t < 10.8 or 19.8 < t < 21.2 or 25.8 < t < 27.4:
            t += 0.5
            continue
        to_beam = random.random() < 0.6
        group = random.random() < 0.25
        clip = random.choice(clicks if to_beam else drops)
        g = GAIN['click' if to_beam else 'drop'] * (1.0 if group else 0.9)
        put(clip, t, g, 0.95 + random.random() * 0.1, 'tap')
        t += 0.45 + random.random() * 0.15
    put(clear, 10.0, GAIN['clear'])
    put(ding, 20.0, GAIN['ding'])
    for i, r in enumerate((1.0, 1.26, 1.5)):
        put(ding, 26.0 + 0.16 * i, GAIN['ding'] * 0.85, r)
    peak = max(abs(v) for v in mix)
    lim = 32767 * 10 ** (-1 / 20)
    k = min(1.0, lim / peak)  # SoundPool sums streams; a mix above -1 dBFS would clip on the device
    pcm = array.array('h', [int(max(-32768, min(32767, v * k))) for v in mix])
    with wave.open(out, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE); w.writeframes(pcm.tobytes())
    # ui_gates.wav_levels
    data = pcm
    pk = max(abs(x) for x in data) or 1
    win = 882
    en = [sum(x * x for x in data[i:i + win]) / win for i in range(0, len(data) - win + 1, win)]
    thr = (pk * 0.0316) ** 2
    voiced = [e for e in en if e >= thr] or en
    rms = math.sqrt(sum(voiced) / len(voiced))
    whole = math.sqrt(sum(x * x for x in data) / len(data))
    print('OUT %s voiced RMS %.1f dBFS, peak %.1f dBFS, whole-file RMS %.1f dBFS, mix scale %.2f' % (
        out, 20 * math.log10(rms / 32768), 20 * math.log10(pk / 32768), 20 * math.log10(max(whole, 1) / 32768), k))


main(sys.argv[1])
