#!/usr/bin/env python3
"""
Every sound in Teera, made from scratch.

Nothing here is sampled or downloaded: the oud is a plucked string modelled with the
Karplus-Strong algorithm, the darbuka is shaped noise and swept sine, and the tiles are
a click ringing at the frequencies a bone tile rings at on a wooden table. That means
there is no licence to worry about, and any sound can be retuned by editing this file.

    python3.13 tools/sound/synth.py app/src/main/res/raw

Writes Ogg Vorbis files named the way Android resources must be named.
"""
import math
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

RATE = 44100
RNG = np.random.default_rng(1951)  # fixed, so a rebuild makes identical files

# Maqam Hijaz on D: the augmented second between E-flat and F-sharp is the sound of it.
HIJAZ_D = [0, 1, 4, 5, 7, 8, 10, 12]
D3 = 146.83


def hz(degree, octave=0, root=D3):
    """Frequency of a step of Hijaz on D, counting degrees from the root."""
    octave += degree // 7
    step = HIJAZ_D[degree % 7]
    return root * 2 ** (octave + step / 12)


def silence(seconds):
    return np.zeros(int(seconds * RATE))


def env(n, attack=0.002, decay=0.2):
    t = np.arange(n) / RATE
    a = np.minimum(1.0, t / max(attack, 1e-6))
    return a * np.exp(-t / decay)


# ---------------------------------------------------------------- instruments

def pluck(freq, seconds=1.6, brightness=0.55, damping=0.996, pick=0.35):
    """
    A plucked string, Karplus-Strong. Computed a period at a time, since each period
    depends only on the one before it, so this stays fast in numpy.
    """
    n = int(seconds * RATE)
    period = max(2, int(RATE / freq))
    burst = RNG.uniform(-1, 1, period)
    # Shape the excitation: a softer pluck is a lowpassed burst.
    for _ in range(int((1 - brightness) * 6)):
        burst = 0.5 * (burst + np.roll(burst, 1))
    out = np.zeros(n + period + 1)
    out[:period] = burst
    i = period
    while i < n:
        end = min(i + period, n)
        prev = out[i - period:end - period + 1]
        out[i:end] = damping * 0.5 * (prev[:-1] + prev[1:])[: end - i] if len(prev) > 1 else 0
        i = end
    out = out[:n]
    # The pick hitting the string, which is most of what makes an oud sound like one.
    click = RNG.normal(0, 1, int(0.004 * RATE)) * np.linspace(1, 0, int(0.004 * RATE))
    out[: len(click)] += click * pick
    return out * env(n, 0.001, seconds * 0.45)


def oud(freq, seconds=1.4, level=0.7):
    """An oud: a pluck with a hollow body under it."""
    tone = pluck(freq, seconds, brightness=0.5, damping=0.9955)
    # The octave course rings shorter than the fundamental, as an oud's paired strings do.
    octave = pluck(freq * 2.0009, seconds * 0.6, brightness=0.4, damping=0.993)
    tone[: len(octave)] += 0.35 * octave
    return body(tone, 240, 0.18) * level


def qanun(freq, seconds=1.2, level=0.5):
    """A brighter, thinner pluck, like a qanun, for chimes."""
    return pluck(freq, seconds, brightness=0.85, damping=0.998, pick=0.15) * level


def body(signal, freq, amount):
    """Adds a resonance, the wooden belly of an instrument."""
    n = len(signal)
    t = np.arange(int(0.08 * RATE)) / RATE
    kernel = np.sin(2 * np.pi * freq * t) * np.exp(-t / 0.02)
    return signal + amount * np.convolve(signal, kernel, mode="full")[:n] / 20


def doum(level=0.9):
    """The deep stroke in the middle of a darbuka."""
    n = int(0.45 * RATE)
    t = np.arange(n) / RATE
    sweep = 95 + 70 * np.exp(-t / 0.03)
    phase = 2 * np.pi * np.cumsum(sweep) / RATE
    tone = np.sin(phase) * np.exp(-t / 0.18)
    thud = lowpass(RNG.normal(0, 1, n), 0.05) * np.exp(-t / 0.015) * 0.6
    return (tone + thud) * level


def tak(level=0.6, ring=1600):
    """The sharp stroke at the rim."""
    n = int(0.18 * RATE)
    t = np.arange(n) / RATE
    snap = highpass(RNG.normal(0, 1, n)) * np.exp(-t / 0.012)
    tone = np.sin(2 * np.pi * ring * t) * np.exp(-t / 0.035) * 0.5
    return (snap + tone) * level


def lowpass(x, alpha):
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += alpha * (v - acc)
        y[i] = acc
    return y


def highpass(x):
    return np.diff(x, prepend=0)


def highpass_fft(x, cutoff):
    """Strips everything below [cutoff] Hz, with a gentle shoulder rather than a wall."""
    spectrum = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(len(x), 1 / RATE)
    spectrum *= np.clip((freqs - cutoff * 0.5) / (cutoff * 0.5), 0, 1)
    return np.fft.irfft(spectrum, len(x))


def reverb(signal, seconds=1.4, wet=0.22):
    """
    A room: the signal convolved with a decaying cloud of noise. The low end is kept out
    of the room so a phone speaker does not turn the tail into mud.
    """
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    ir = RNG.normal(0, 1, n) * np.exp(-t / (seconds / 5))
    ir = lowpass(ir, 0.35)
    ir /= np.sqrt(np.sum(ir ** 2))
    size = 1 << int(math.ceil(math.log2(len(signal) + n)))
    wet_signal = np.fft.irfft(np.fft.rfft(signal, size) * np.fft.rfft(ir, size), size)
    wet_signal = highpass_fft(wet_signal, 220)
    out = np.zeros(len(signal) + n)
    out[: len(signal)] += signal * (1 - wet)
    out += wet_signal[: len(out)] * wet
    return out


def place(track, sound, at):
    start = int(at * RATE)
    end = start + len(sound)
    if end > len(track):
        track = np.concatenate([track, np.zeros(end - len(track))])
    track[start:end] += sound
    return track


# ---------------------------------------------------------------- tiles and table

def clack(variant=0):
    """A bone tile set down on a wooden table: a hard click, a ring, a thump."""
    rng = np.random.default_rng(100 + variant)
    n = int(0.22 * RATE)
    t = np.arange(n) / RATE
    click = highpass(rng.normal(0, 1, n)) * np.exp(-t / 0.0025)
    ring = sum(
        np.sin(2 * np.pi * f * (1 + rng.uniform(-0.04, 0.04)) * t) * np.exp(-t / d) * a
        for f, d, a in [(2850, 0.022, 0.35), (4150, 0.014, 0.25), (6400, 0.008, 0.15)]
    )
    thump = np.sin(2 * np.pi * (170 + 20 * variant) * t) * np.exp(-t / 0.035) * 0.6
    return reverb(click * 0.8 + ring + thump, 0.35, 0.12)


def knock():
    """Two knocks on the table: in a coffee house that is how you say you cannot go."""
    def one():
        n = int(0.16 * RATE)
        t = np.arange(n) / RATE
        return (
            np.sin(2 * np.pi * 135 * t) * np.exp(-t / 0.04)
            + 0.5 * np.sin(2 * np.pi * 620 * t) * np.exp(-t / 0.015)
            + 0.4 * highpass(RNG.normal(0, 1, n)) * np.exp(-t / 0.003)
        )
    track = silence(0.4)
    track = place(track, one(), 0.0)
    track = place(track, one() * 0.85, 0.13)
    return reverb(track, 0.5, 0.15)


def slide():
    """A tile pushed across the cloth to another player."""
    n = int(0.32 * RATE)
    t = np.arange(n) / RATE
    hiss = lowpass(RNG.normal(0, 1, n), 0.25) * np.sin(np.pi * t / t[-1]) ** 2 * 0.5
    track = np.concatenate([hiss, silence(0.05)])
    return place(track, clack(3) * 0.6, 0.3)


# ---------------------------------------------------------------- stings

def turn_chime():
    track = silence(1.2)
    track = place(track, qanun(hz(4, 1), 1.0, 0.35), 0.0)
    track = place(track, qanun(hz(7, 1), 1.0, 0.30), 0.11)
    return reverb(track, 1.2, 0.3)


def teera():
    """The flourish for a teera: a run up the maqam, a shimmer, and a doum to land it."""
    track = silence(2.6)
    for i, degree in enumerate(range(0, 11)):
        track = place(track, oud(hz(degree, 1), 0.7, 0.5), i * 0.055)
    for i in range(6):
        track = place(track, qanun(hz(7 + i % 3, 2), 0.8, 0.18), 0.6 + i * 0.07)
    track = place(track, doum(1.0), 0.62)
    track = place(track, tak(0.5), 0.62)
    return reverb(track, 1.6, 0.3)


def x_win():
    """Going out: a strummed chord over a doum, tak-tak, doum."""
    track = silence(2.8)
    for i, degree in enumerate([0, 2, 4, 7, 9]):
        track = place(track, oud(hz(degree, 1), 1.8, 0.45), i * 0.025)
    for at, hit in [(0.0, doum(1.0)), (0.32, tak(0.55)), (0.48, tak(0.5)), (0.64, doum(0.9))]:
        track = place(track, hit, at)
    for i, degree in enumerate([7, 9, 11, 14]):
        track = place(track, qanun(hz(degree, 1), 1.2, 0.25), 0.7 + i * 0.09)
    return reverb(track, 1.8, 0.3)


def blocked():
    """A locked table: two heavy doums and the maqam stepping down."""
    track = silence(2.0)
    track = place(track, doum(1.0), 0.0)
    track = place(track, doum(0.9), 0.28)
    for i, degree in enumerate([4, 2, 1, 0]):
        track = place(track, oud(hz(degree, 0), 1.0, 0.5), 0.55 + i * 0.18)
    return reverb(track, 1.4, 0.28)


def match_won():
    track = silence(4.0)
    phrase = [0, 2, 4, 5, 7, 9, 11, 14]
    for i, degree in enumerate(phrase):
        track = place(track, oud(hz(degree, 1), 1.0, 0.5), i * 0.12)
    for at in [0.0, 0.48, 0.96, 1.2, 1.44]:
        track = place(track, doum(0.8) if at in (0.0, 0.96) else tak(0.5), at)
    for i, degree in enumerate([0, 4, 7, 11]):
        track = place(track, oud(hz(degree, 1), 2.2, 0.4), 1.2 + i * 0.02)
    return reverb(track, 2.0, 0.32)


def match_lost():
    track = silence(4.0)
    for i, degree in enumerate([7, 5, 4, 2, 1, 0]):
        track = place(track, oud(hz(degree, 1), 1.4, 0.5), i * 0.32)
    track = place(track, doum(0.7), 1.9)
    return reverb(track, 2.0, 0.32)


# ---------------------------------------------------------------- soundboard

def gulp():
    """'ابلع': two swallowing bloops."""
    def one(top):
        n = int(0.16 * RATE)
        t = np.arange(n) / RATE
        f = top * np.exp(-t / 0.06) + 140
        return np.sin(2 * np.pi * np.cumsum(f) / RATE) * np.sin(np.pi * t / t[-1]) * 0.8
    track = silence(0.5)
    track = place(track, one(520), 0.0)
    track = place(track, one(420), 0.2)
    return reverb(track, 0.4, 0.1)


def drum_roll():
    """A darbuka roll, for the moment before someone finds out."""
    track = silence(1.6)
    at = 0.0
    gap = 0.09
    while at < 1.1:
        track = place(track, tak(0.35 + at * 0.3, ring=1500 + 300 * (int(at / gap) % 2)), at)
        at += gap
        gap = max(0.035, gap * 0.93)
    track = place(track, doum(1.0), 1.15)
    return reverb(track, 1.0, 0.25)


def sad():
    """For the losing side: a slow, sinking slide down the maqam."""
    n = int(1.8 * RATE)
    t = np.arange(n) / RATE
    f = hz(4, 0) * np.exp(-t * 0.35) * (1 + 0.012 * np.sin(2 * np.pi * 5.5 * t))
    phase = 2 * np.pi * np.cumsum(f) / RATE
    saw = sum(np.sin(k * phase) / k for k in range(1, 9))
    return reverb(lowpass(saw, 0.18) * env(n, 0.05, 0.9) * 0.6, 1.2, 0.25)


def slap():
    """A slap on the table, for a big play."""
    n = int(0.3 * RATE)
    t = np.arange(n) / RATE
    hit = highpass(RNG.normal(0, 1, n)) * np.exp(-t / 0.008)
    low = np.sin(2 * np.pi * 95 * t) * np.exp(-t / 0.07)
    return reverb(hit * 0.9 + low, 0.6, 0.2)


# ---------------------------------------------------------------- music

def loop(bpm, bars, melody, rhythm, drone_every=1, level=0.5):
    """
    A seamless loop: melody and darbuka over a drone, with the reverb tail wrapped
    round to the start so the join cannot be heard.
    """
    beat = 60.0 / bpm
    length = bars * 4 * beat
    track = silence(length)
    for bar in range(bars):
        start = bar * 4 * beat
        if bar % drone_every == 0:
            track = place(track, oud(hz(0, -1), 4 * beat * 1.1, 0.2), start)
        for at, stroke in rhythm:
            hit = doum(0.3) if stroke == "D" else tak(0.2 if stroke == "t" else 0.28)
            track = place(track, hit, start + at * beat)
    at = 0.0
    for degree, beats in melody:
        if degree is not None:
            track = place(track, oud(hz(degree, 1), max(0.6, beats * beat * 1.4), 0.42), at)
        at += beats * beat
    wet = reverb(track, 1.8, 0.28)
    out = wet[: int(length * RATE)].copy()
    tail = wet[int(length * RATE):]
    out[: len(tail)] += tail
    # Phone speakers cannot make the lowest octave anyway; leaving it in only muddies.
    return highpass_fft(out, 70) * level


# Maqsum, the four-beat rhythm under most Iraqi and Arab popular music: doum tak-tak doum tak.
MAQSUM = [(0, "D"), (1, "T"), (1.5, "t"), (2.5, "D"), (3, "T")]
MAQSUM_SPARSE = [(0, "D"), (2.5, "D"), (3, "t")]

CALM_MELODY = [
    (4, 1), (5, 0.5), (4, 0.5), (2, 1), (1, 1),
    (2, 1.5), (1, 0.5), (0, 2),
    (None, 1), (2, 0.5), (4, 0.5), (5, 1), (7, 1),
    (5, 1.5), (4, 0.5), (2, 2),
    (4, 1), (5, 0.5), (7, 0.5), (8, 1), (7, 1),
    (5, 1), (4, 1), (2, 1), (1, 1),
    (2, 1.5), (1, 0.5), (0, 1), (-1, 1),
    (0, 4),
]

TENSE_MELODY = [
    (0, 0.5), (1, 0.5), (2, 0.5), (4, 0.5), (5, 0.5), (4, 0.5), (2, 0.5), (1, 0.5),
    (2, 0.5), (4, 0.5), (5, 0.5), (7, 0.5), (8, 1), (7, 1),
    (5, 0.5), (4, 0.5), (5, 0.5), (7, 0.5), (8, 0.5), (7, 0.5), (5, 0.5), (4, 0.5),
    (2, 1), (1, 1), (0, 2),
    (7, 0.5), (8, 0.5), (9, 0.5), (8, 0.5), (7, 0.5), (5, 0.5), (4, 0.5), (5, 0.5),
    (7, 1), (5, 1), (4, 1), (2, 1),
    (1, 0.5), (2, 0.5), (4, 0.5), (2, 0.5), (1, 0.5), (0, 0.5), (-1, 0.5), (1, 0.5),
    (0, 4),
]


# ---------------------------------------------------------------- output

def write(path_stem, signal, outdir, quality=4):
    peak = np.max(np.abs(signal)) or 1.0
    pcm = np.int16(np.clip(signal / peak * 0.89, -1, 1) * 32767)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        target = os.path.join(outdir, path_stem + ".ogg")
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", str(quality), target],
            check=True,
        )
        os.unlink(tmp.name)
    print(f"{target}  {os.path.getsize(target) // 1024} KB  {len(signal) / RATE:.1f}s")


def main(outdir):
    os.makedirs(outdir, exist_ok=True)
    for i in range(3):
        write(f"sfx_clack_{i}", clack(i), outdir)
    write("sfx_knock", knock(), outdir)
    write("sfx_slide", slide(), outdir)
    write("sfx_turn", turn_chime(), outdir)
    write("sfx_teera", teera(), outdir)
    write("sfx_x", x_win(), outdir)
    write("sfx_blocked", blocked(), outdir)
    write("sfx_match_won", match_won(), outdir)
    write("sfx_match_lost", match_lost(), outdir)
    write("emote_gulp", gulp(), outdir)
    write("emote_drumroll", drum_roll(), outdir)
    write("emote_sad", sad(), outdir)
    write("emote_slap", slap(), outdir)
    write("music_calm", loop(78, 12, CALM_MELODY, MAQSUM_SPARSE, drone_every=1), outdir, quality=3)
    write("music_tense", loop(116, 12, TENSE_MELODY, MAQSUM, drone_every=2), outdir, quality=3)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "out")
