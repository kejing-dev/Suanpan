package io.embodit.suanpan.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import kotlin.random.Random

enum class Sfx(val file: String, val gain: Float, val varied: Boolean) {
    /** Beads reach the beam: hard wood on wood (CC0 recording, three variants, ±5 % pitch). */
    CLICK("click", 1.0f, true),
    /** Beads drop back to the frame: lower, shorter. */
    DROP("drop", 0.7f, true),
    /** Clear: the clicks of nine rods in a row. */
    CLEAR("clear", 0.85f, false),
    /** Right answer: small bell. */
    DING("ding", 0.75f, false),
    /** Slow-mo step: a light tap. */
    TICK("drop", 0.4f, true),
}

/**
 * Short effects through one SoundPool (design sound; five-stones v0.1.5 template §15.2): every file peaks at -1 dBFS,
 * levels come from [Sfx.gain] × the volume step; variants rotate without repeating; one instance per kind at a time
 * (a new one stops the last); panned toward the rod that sounded (SoundPool stands in for the SDK's spatial audio,
 * see deviations). [muted] silences without unloading.
 */
class Sounds(context: Context) {
    @Volatile var muted = false
    @Volatile var volume = 1f

    private val pool = SoundPool.Builder().setMaxStreams(6)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = HashMap<String, List<Int>>()
    private val loaded = HashSet<Int>()
    private val lastStream = HashMap<Sfx, Int>()
    private val lastVariant = HashMap<Sfx, Int>()
    private val rng = Random(System.nanoTime())
    private val main = Handler(Looper.getMainLooper())

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded += id } }
        for (base in Sfx.entries.map { it.file }.distinct()) {
            val found = ArrayList<Int>()
            for (name in listOf("$base.wav", "${base}_2.wav", "${base}_3.wav")) {
                runCatching { context.assets.openFd("sounds/$name").use { fd -> found += pool.load(fd, 1) } }
            }
            ids[base] = found
        }
    }

    /** Plays [s]; [rod] 0..8 pans it (rod 8 = far left), null = centre; [rate] overrides the pitch. */
    fun play(s: Sfx, rod: Int? = null, rate: Float? = null, gain: Float = 1f, exclusive: Boolean = true) {
        if (muted) return
        val variants = ids[s.file].orEmpty().filter { synchronized(loaded) { it in loaded } }
        if (variants.isEmpty()) return
        val v = if (variants.size == 1) 0 else {
            var k = rng.nextInt(variants.size)
            if (k == lastVariant[s]) k = (k + 1) % variants.size
            k
        }
        lastVariant[s] = v
        val r = rate ?: if (s.varied) 0.95f + rng.nextFloat() * 0.1f else 1f
        val g = (s.gain * volume * gain).coerceIn(0f, 1f)
        val pan = rod?.let { (4 - it) / 4f * 0.35f } ?: 0f   // rod 0 (units) is right
        val left = g * (1f - pan.coerceAtLeast(0f))
        val right = g * (1f + pan.coerceAtMost(0f))
        if (exclusive) lastStream[s]?.let { pool.stop(it) }
        val stream = pool.play(variants[v], left, right, 1, 0, r)
        lastStream[s] = stream
        SpLog.i("SOUND ${s.name} rod=$rod rate=${"%.2f".format(r)} gain=${"%.2f".format(g)}")
    }

    /** Lesson passed: the bell three times, rising (1, 1.26, 1.5 — a major triad), 160 ms apart. */
    fun lessonPassed() {
        val rates = floatArrayOf(1f, 1.26f, 1.5f)
        rates.forEachIndexed { i, r -> main.postDelayed({ play(Sfx.DING, rate = r, gain = 0.85f, exclusive = false) }, 160L * i) }
    }

    fun release() { runCatching { pool.release() } }
}
