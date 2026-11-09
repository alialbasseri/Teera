package com.teera.server

import com.teera.room.Pusher
import com.teera.room.RoomRegistry
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Reminders")

/**
 * The evening nudges: up to three a day, from after the afternoon prayer to midnight
 * (Baghdad time), each person at their own minute so the phones do not all buzz at once.
 * Nobody is nudged while they have the app open or opened it in the last two hours, and
 * people away for a while get fewer. Each person goes through the lines in their own
 * order, so with three a day nothing repeats for a month.
 */
class Reminders(
    private val books: SqlRecords,
    private val pusher: Pusher,
    private val registry: RoomRegistry,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The three windows, by hour of day: from, until (exclusive), in minutes since midnight. */
    private val slots = listOf(16 * 60 to 19 * 60, 19 * 60 to 22 * 60, 22 * 60 to 23 * 60 + 45)

    fun start(scope: CoroutineScope) = scope.launch(Dispatchers.IO) {
        while (isActive) {
            runCatching { round() }.onFailure { log.warn("reminders: {}", it.message) }
            delay(5 * 60 * 1000L)
        }
    }

    /** One look at the clock: who is due a nudge now. */
    fun round(): Int {
        val now = clock()
        val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), SqlRecords.ZONE)
        val minute = time.hour * 60 + time.minute
        val slot = slots.indexOfFirst { (from, until) -> minute in from until until }
        if (slot < 0) return 0
        val day = time.toLocalDate().toString()
        val board = runCatching { kotlinx.coroutines.runBlocking { books.leaderboard() } }.getOrNull()
        val live = registry.all().count { it.room.liveGame() != null }
        var sent = 0
        for (person in books.reachable()) {
            if (registry.online.containsKey(person.playerId)) continue
            if (now - person.lastSeen < 2 * 3600 * 1000L) continue
            // Away a week: one a day. Away a month: one every three days.
            val awayDays = (now - person.lastSeen) / (24 * 3600 * 1000L)
            if (awayDays >= 7 && slot != 1) continue
            if (awayDays >= 30 && time.dayOfYear % 3 != 0) continue
            // Their own minute in the window.
            val (from, until) = slots[slot]
            val offset = Math.floorMod("${person.playerId}|$day|$slot".hashCode(), until - from)
            if (minute < from + offset) continue
            if (books.reminded(person.playerId, day, slot)) continue

            val count = books.remindersSent(person.playerId)
            val title = board?.titles?.firstOrNull { it.playerId == person.playerId }?.title
            val rank = board?.players?.indexOfFirst { it.playerId == person.playerId }?.takeIf { it in 0..9 }?.plus(1)
            val (line, words) = ReminderLines.pick(person.playerId, count, person.name, title, rank, live)
            books.noteReminder(person.playerId, day, slot, line)
            pusher.notify(person.playerId, words.first, words.second, emptyMap())
            sent++
        }
        if (sent > 0) log.info("reminders sent: {}", sent)
        return sent
    }
}

/** What the reminders say. Ninety of them, and some about the person themselves. */
object ReminderLines {
    /** Picks the [count]th line for this person: their own order through the lines, and a personal one now and then. */
    fun pick(playerId: String, count: Int, name: String, title: String?, rank: Int?, live: Int): Pair<Int, Pair<String, String>> {
        val first = name.trim().split(" ").firstOrNull().orEmpty().ifBlank { "حبيبي" }
        when {
            title != null && count % 5 == 2 -> {
                val i = (count / 5) % TITLE.size
                return 1000 + i to TITLE[i].let { (t, b) -> t.replace("{title}", title) to b.replace("{title}", title).replace("{name}", first) }
            }
            rank != null && count % 5 == 4 -> {
                val i = (count / 5) % RANK.size
                return 2000 + i to RANK[i].let { (t, b) -> t to b.replace("{rank}", "$rank").replace("{name}", first) }
            }
            live > 0 && count % 7 == 6 -> {
                val i = (count / 7) % LIVE.size
                return 3000 + i to LIVE[i].let { (t, b) -> t to b.replace("{live}", "$live") }
            }
        }
        val order = LINES.indices.shuffled(kotlin.random.Random(playerId.hashCode()))
        val i = order[count % LINES.size]
        return i to LINES[i].let { (t, b) -> t.replace("{name}", first) to b.replace("{name}", first) }
    }

    val LINES: List<Pair<String, String>> = listOf(
        "يلا نلعب 🎲" to "الطاولة فاضية والچاي حاضر، تعال",
        "وين الزلم؟" to "جماعتك يدورون رابع، لا تخليهم ينطرون",
        "{name}، وينك؟" to "صارلنا مدة ما شفناك على الطاولة",
        "الچاي برد ☕" to "وإنت بعدك ما فتحت غرفة",
        "گيم واحد بس" to "وبعدين نامي، وعد",
        "الطيرة تنادي" to "البولات مشتاقتلك",
        "مسا الخير 🌙" to "أحلى وكت للطيرة هسه",
        "عندك چفت الستات؟" to "تعال افتح بيه الگيم",
        "جماعتك فاضين" to "هسه وكتها، دزّلهم اللنك",
        "تعبان من الشغل؟" to "گيم طيرة يرد الروح",
        "الطاولة تنطرك" to "لا تخليها باردة",
        "صوت البولات" to "طق طق… اشتاقيت له؟",
        "يا بطل" to "ورّينا شطارتك اليوم",
        "خلي نسولف" to "افتح غرفة وشغّل المايك",
        "الليلة ليلتك" to "حاس إنك راح تاخذ اكس",
        "اشتقنا للخاوات" to "منو اليوم راح ياكلها؟",
        "{name} 👀" to "الكل يسأل عليك",
        "201 تنطرك" to "بس لا توصلها 😄",
        "الگعدة ما تكمل بدونك" to "تعال عدّل الطاولة",
        "سبلوش!" to "يلا خلي نسمعها منك اليوم",
        "شريكك ينطرك" to "لا تخليه يلعب ويا بوت",
        "ساعة طيرة؟" to "أحسن من ساعة سكرول",
        "البوتات زهگت" to "تريد واحد حقيقي يلعب وياها",
        "منو يتحدّاك؟" to "افتح غرفة وشوف",
        "الدومنة مصفوفة" to "بس ناقصتك انت، يلا رجّعوا الطاولة تحمى",
        "بالله ما طيغ" to "إلا تجي تلعب",
        "خوش وكت" to "الجو حلو للطيرة",
        "{name}، نشتاق" to "الطاولة ما الها طعم بدونك",
        "هسه وكت الطيرة" to "بعد العصر أحلى گيم",
        "دقيقة من وقتك" to "گيم سريع ضد البوتات؟",
        "عدّل مزاجك" to "طيرة وچاي، شتريد بعد",
        "الستات تنادي" to "6|6 بإيدك؟ افتح",
        "جماعة الكروب" to "منو فاتح؟ ادخل وعزمهم",
        "ترتيبك ينطرك" to "كل مباراة ترفعك",
        "اكس ولا طيرة؟" to "اليوم إنت تختار",
        "الليل طويل" to "خلي نقصّره بطيرة",
        "تعال اتحدى الصعب" to "البوت الصعب ينطرك",
        "شوكت آخر مرة فزت؟" to "اليوم يوم الثأر",
        "الطاولة مشتهيتك" to "لا تكسر خاطرها",
        "روح القعدة" to "إنت روح القعدة، وين رحت؟",
        "يلا شباب" to "خلي نسولف ونلعب",
        "حظك اليوم" to "يگولون حظك عالي، جرّب",
        "گلّيت؟" to "لا تهتم، تعال العب",
        "منو الأقوى؟" to "الترتيب يگول غير شي، تعال صلّحه",
        "بطولة؟" to "سوّي بطولة لجماعتك الليلة",
        "خاوة على الخصم" to "بس إذا لعبت",
        "صاير طيّار؟" to "ورّينا",
        "الگيم الجاي إلك" to "حاس بيها",
        "طرت بصفر؟" to "جرّبها اليوم",
        "ماكو أطيب من الطيرة" to "يلا افتح",
        "تعال نكسر الملل" to "گيمين وبس",
        "وين أيام زمان؟" to "الگعدة رجعت، بس بتليفونك",
        "{name} الأسطورة" to "الأساطير ما تغيب هلگد",
        "الطاولة جاهزة" to "بس ناقصها إنت",
        "الدومنة عراقية" to "والطيرة أحلاها، تعال",
        "شغّل المايك" to "واسمعهم شيگولون عليك 😄",
        "الشاي مخدّر" to "والبولات مخلوطة",
        "شنو رأيك بگيم؟" to "ما راح ياخذ وكت",
        "خلّصت شغلك؟" to "مكافأتك گيم طيرة",
        "الربع يدورون عليك" to "ادخل وشوف منو فاتح",
        "لعبة الليلة" to "طيرة، وبس",
        "ناخذ خاوة؟" to "على الغايب 😄",
        "تعال فز" to "النقاط الگليلة أحسن",
        "تمرين صغير" to "گيم ضد البوت السهل يحمّيك",
        "شكو ماكو؟" to "تعال گلّنا وإنت تلعب",
        "سهرة طيرة" to "أحلى سهرة",
        "جيب جماعتك" to "وتعال نلعب أربعة",
        "{name}، الطاولة تسأل" to "ليش ما إجيت اليوم؟",
        "البول الأخير" to "ينطر واحد يخلّصه، إنت؟",
        "اكسك ينطرك" to "-25 حلوة، تعال خذها",
        "إذا ملّيت" to "الطيرة دواها",
        "يلا هسه" to "قبل لا ينامون جماعتك",
        "آخر الليل" to "گيم أخير قبل النوم؟",
        "تحدّي الليلة" to "فوز بلا ولا خاوة",
        "الكأس 🏆" to "شوف منو صاير الأول",
        "الربع اتجمعوا؟" to "شوف اللعبات الماشية",
        "تفرّج على الأقل" to "أكو ناس دا يلعبون هسه",
        "الطاولة الخضرة" to "تشتاق لإيدك",
        "مو وكت نوم" to "وكت طيرة",
        "قافلها؟" to "لا تقفلها، العبها",
        "يلا ارجع" to "الكل راجع إلا إنت",
        "طيرة طيرة" to "يلا نسمعها",
        "إيدك حلوة" to "جرّبها اليوم",
        "مزاج طيرة" to "مزاجك يگول العب",
        "خمس دقايق" to "تكفي لگيم",
        "فرصة اليوم" to "تصعد بالترتيب",
        "ديربي الليلة" to "إنت وشريكك ضد الكل",
        "لا تخليها" to "الطيرة ما تنترك",
        "سلام عليكم" to "الطاولة ترد السلام، تعال",
        "إشتاقينا" to "البولات والچاي والسوالف",
    )

    /** For whoever holds a title: hang on to it. */
    val TITLE: List<Pair<String, String>> = listOf(
        "👑 {title}" to "حافظ على لقبك، أكو ناس يلحگونك",
        "لقبك بخطر" to "{name}، {title} لازم يدافع عن لقبه",
        "{title} وين؟" to "الكل يحاول ياخذ مكانك",
        "لا تنامي عليها" to "اللقب يروح إذا ما تلعب",
        "ملك بلا طاولة؟" to "تعال ثبّت لقبك",
        "{title} 👑" to "ورّيهم ليش إنت صاحب اللقب",
    )

    /** For the top ten: where they stand. */
    val RANK: List<Pair<String, String>> = listOf(
        "شوف إنت شكو" to "إنت بالمركز {rank}، تگدر تصعد؟",
        "المركز {rank}" to "{name}، واحد ورا ثاني يلحگك",
        "الترتيب تغيّر؟" to "إنت {rank}، تعال ثبّت مكانك",
        "قريب من القمة" to "المركز {rank}… گيمين ويصعد",
        "لا تنزل" to "مركزك {rank} يحتاجك تلعب",
    )

    /** When games are being played that anyone may watch. */
    val LIVE: List<Pair<String, String>> = listOf(
        "أكو لعب هسه" to "{live} لعبة ماشية، تعال تفرّج",
        "الطاولات شغالة" to "{live} لعبة دا تنلعب هسه",
        "تفرّج وسولف" to "أكو {live} لعبة ماشية، ادخل",
    )
}
