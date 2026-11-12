"""Builds assets/sounds/clear.wav (清盘「哗啦」) by laying the CC0 bead clicks (click*.wav, freesound 840425) one after
another the way the clear animation runs: 9 rods left to right, 25 ms apart, each rod's group landing with a click,
a lighter second click 60 ms later; then normalised to -1 dBFS. Editing of recordings only: no synthesis, no reverb."""
import os, random, wave, array
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'sounds')
def load(n):
    with wave.open(os.path.join(S, n), 'rb') as w:
        assert w.getsampwidth() == 2 and w.getnchannels() == 1, n
        return w.getframerate(), array.array('h', w.readframes(w.getnframes()))
rate, a = load('click.wav'); _, b = load('click_2.wav'); _, c = load('click_3.wav'); _, d = load('drop.wav')
clips = [a, b, c]
random.seed(9)
total = int(rate * 0.7)
mix = [0.0] * total
def add(clip, at, gain):
    for i, v in enumerate(clip):
        j = at + i
        if j < total: mix[j] += v * gain
for rod in range(9):
    t = int(rate * (0.025 * rod + 0.2 * 0.7))
    add(clips[rod % 3], t, 0.55 + random.random() * 0.15)
    add(d, t + int(rate * 0.06), 0.25)
peak = max(abs(x) for x in mix) or 1
g = 32767 * 10 ** (-1 / 20) / peak
out = array.array('h', [int(max(-32768, min(32767, x * g))) for x in mix])
with wave.open(os.path.join(S, 'clear.wav'), 'wb') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate); w.writeframes(out.tobytes())
print('clear.wav', len(out) / rate, 's')
