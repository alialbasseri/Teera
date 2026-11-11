package com.teera.app.voice

import com.teera.app.PlatformApp
import com.teera.app.toInt16Array
import com.teera.app.toShortArray
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.khronos.webgl.Int16Array

/*
 * The browser's microphone and speakers for table talk. The browser's own echo cancelling
 * and noise suppression are asked for; the microphone is cut into the wire's frames
 * (16 kHz, 640 samples) here, and each friend's frames are scheduled back to back.
 */

private fun jsMicStart(onFrame: (Int16Array) -> Unit, onReady: (Boolean) -> Unit): Unit = js(
    """{
  const g = globalThis.teeraVoice = globalThis.teeraVoice || {};
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { onReady(false); return; }
  navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true, channelCount: 1 } })
    .then(stream => {
      const ctx = g.ctx = g.ctx || new (window.AudioContext || window.webkitAudioContext)();
      ctx.resume();
      const source = ctx.createMediaStreamSource(stream);
      const proc = ctx.createScriptProcessor(4096, 1, 1);
      const step = ctx.sampleRate / 16000;
      let pos = 0, filled = 0;
      const frame = new Int16Array(640);
      proc.onaudioprocess = e => {
        const d = e.inputBuffer.getChannelData(0);
        while (pos < d.length) {
          const s = Math.floor(pos), t = Math.min(d.length, Math.max(s + 1, Math.floor(pos + step)));
          let sum = 0;
          for (let i = s; i < t; i++) sum += d[i];
          frame[filled++] = Math.max(-1, Math.min(1, sum / (t - s))) * 32767;
          if (filled === 640) { onFrame(frame.slice()); filled = 0; }
          pos += step;
        }
        pos -= d.length;
      };
      const silent = ctx.createGain();
      silent.gain.value = 0;
      source.connect(proc); proc.connect(silent); silent.connect(ctx.destination);
      g.mic = { stream, source, proc, silent };
      onReady(true);
    }, () => onReady(false));
}""",
)

private fun jsMicStop(): Unit = js(
    """{
  const g = globalThis.teeraVoice;
  if (!g || !g.mic) return;
  try { g.mic.proc.onaudioprocess = null; g.mic.source.disconnect(); g.mic.proc.disconnect(); g.mic.silent.disconnect(); } catch (e) {}
  g.mic.stream.getTracks().forEach(t => t.stop());
  g.mic = null;
}""",
)

private fun jsPlayFrame(seat: Int, samples: Int16Array): Unit = js(
    """{
  const g = globalThis.teeraVoice = globalThis.teeraVoice || {};
  const ctx = g.ctx = g.ctx || new (window.AudioContext || window.webkitAudioContext)();
  if (ctx.state === 'suspended') ctx.resume();
  const n = samples.length;
  const buffer = ctx.createBuffer(1, n, 16000);
  const channel = buffer.getChannelData(0);
  for (let i = 0; i < n; i++) channel[i] = samples[i] / 32768;
  g.next = g.next || {};
  const now = ctx.currentTime;
  let at = g.next[seat] || 0;
  if (at < now + 0.03 || at > now + 0.6) at = now + 0.08;
  const node = ctx.createBufferSource();
  node.buffer = buffer; node.connect(ctx.destination); node.start(at);
  g.next[seat] = at + n / 16000;
}""",
)

@Suppress("UNUSED_PARAMETER")
class VoiceChat(app: PlatformApp) {
    var send: (ByteArray) -> Unit = {}

    private val _micOn = MutableStateFlow(false)
    val micOn: StateFlow<Boolean> = _micOn.asStateFlow()

    private val _talking = MutableStateFlow(false)
    val talking: StateFlow<Boolean> = _talking.asStateFlow()

    private val _speaking = MutableStateFlow<Set<Int>>(emptySet())
    val speaking: StateFlow<Set<Int>> = _speaking.asStateFlow()

    private val _hearing = MutableStateFlow(true)
    val hearing: StateFlow<Boolean> = _hearing.asStateFlow()

    var mutedSeats: Set<Int> = emptySet()

    private val scope = MainScope()
    private val heardAt = HashMap<Int, Long>()

    private val gate = TalkGate()
    private var previous: ShortArray? = null
    private var wasOpen = false
    private var seq = 0

    /**
     * On at once (the browser asks the player for the microphone itself), and off again if
     * they say no or there is none.
     */
    fun setMic(on: Boolean) {
        if (on == _micOn.value) return
        if (on) {
            _micOn.value = true
            jsMicStart(onFrame = { frame -> heard(frame.toShortArray()) }, onReady = { ok -> if (!ok) stopMic() })
        } else {
            stopMic()
        }
    }

    private fun stopMic() {
        jsMicStop()
        _micOn.value = false
        _talking.value = false
        wasOpen = false
    }

    private fun heard(frame: ShortArray) {
        if (!_micOn.value) return
        val open = gate.open(frame)
        if (open) {
            // The frame before the gate opened usually holds the start of the word.
            if (!wasOpen) previous?.let { send(VoiceWire.pack(seq++ and 0xFFFF, it)) }
            send(VoiceWire.pack(seq++ and 0xFFFF, frame))
        }
        if (open != wasOpen) _talking.value = open
        wasOpen = open
        previous = frame
    }

    fun setHearing(on: Boolean) {
        _hearing.value = on
        if (!on) _speaking.value = emptySet()
    }

    fun receive(relayed: ByteArray) {
        if (!_hearing.value) return
        val heard = VoiceWire.unpack(relayed) ?: return
        if (heard.seat in mutedSeats) return
        jsPlayFrame(heard.seat, heard.samples.toInt16Array())
        // The ring stays lit while frames keep coming, and goes out a moment after.
        val now = com.teera.app.uptimeMillis()
        heardAt[heard.seat] = now
        _speaking.update { it + heard.seat }
        scope.launch {
            delay(350)
            if (heardAt[heard.seat] == now) _speaking.update { it - heard.seat }
        }
    }

    fun stopAll() {
        setMic(false)
        _speaking.value = emptySet()
    }
}
