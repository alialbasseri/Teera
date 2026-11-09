package com.teera.room

/** What is happening on the server right now, for the top of the developer's page. */
data class LiveNow(val online: Int, val rooms: Int, val playing: Int, val watchers: Int, val peakToday: Int)

private fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** The developer's page: now, today, the last two weeks, and who plays on what. Arabic, for a phone. */
fun adminPage(live: LiveNow, stats: AdminStats?): String {
    fun card(value: Any, label: String) = """<div class="card"><b>$value</b><span>$label</span></div>"""
    fun hours(minutes: Int) = if (minutes < 60) "$minutes د" else "%.1f س".format(minutes / 60.0)

    val body = StringBuilder()
    body.append("<h2>هسه</h2><div class=\"grid\">")
    body.append(card(live.online, "فاتحين اللعبة"))
    body.append(card(live.playing, "لعبات ماشية"))
    body.append(card(live.rooms, "غرف مفتوحة"))
    body.append(card(live.watchers, "يتفرجون"))
    body.append(card(live.peakToday, "أعلى عدد اليوم"))
    body.append("</div>")

    if (stats == null) {
        body.append("<p class=\"muted\">قاعدة البيانات مو مربوطة، فماكو أرقام غير اللي فوگ.</p>")
    } else {
        body.append("<h2>اليوم</h2><div class=\"grid\">")
        body.append(card(stats.openedToday, "فتحوا اللعبة"))
        body.append(card(stats.playedToday, "لعبوا"))
        body.append(card(stats.newToday, "جدد"))
        body.append(card(stats.matchesToday, "مباريات بدت"))
        body.append(card(stats.finishedToday, "مباريات خلصت"))
        body.append(card(stats.gamesToday, "گيمات"))
        body.append(card(hours(stats.minutesToday), "وقت اللعب"))
        body.append(card(stats.remindersToday, "تذكيرات انرسلت"))
        body.append("</div>")

        // Two bars a day: people who opened the app, and people who played.
        val top = (stats.days.maxOfOrNull { maxOf(it.opened, it.played) } ?: 0).coerceAtLeast(1)
        val w = 22
        val h = 120
        val svg = StringBuilder("""<svg viewBox="0 0 ${stats.days.size * w + 10} ${h + 34}" class="chart">""")
        stats.days.forEachIndexed { i, d ->
            val x = 5 + i * w
            val ho = d.opened * h / top
            val hp = d.played * h / top
            svg.append("""<rect x="$x" y="${h - ho}" width="9" height="$ho" fill="#d9b166"><title>${d.day}: فتحوا ${d.opened}</title></rect>""")
            svg.append("""<rect x="${x + 10}" y="${h - hp}" width="9" height="$hp" fill="#8de8a8"><title>${d.day}: لعبوا ${d.played}</title></rect>""")
            svg.append("""<text x="${x + 10}" y="${h + 14}" text-anchor="middle">${d.day.takeLast(2)}</text>""")
            if (d.opened > 0) svg.append("""<text x="${x + 5}" y="${h - ho - 3}" text-anchor="middle" class="n">${d.opened}</text>""")
        }
        svg.append("</svg>")
        body.append("<h2>آخر أسبوعين</h2>")
        body.append("""<p class="legend"><i style="background:#d9b166"></i>فتحوا اللعبة <i style="background:#8de8a8"></i>لعبوا</p>""")
        body.append(svg)
        body.append("<table><tr><th>اليوم</th><th>فتحوا</th><th>لعبوا</th><th>مباريات</th><th>گيمات</th><th>وقت</th></tr>")
        stats.days.reversed().forEach { d ->
            body.append("<tr><td>${d.day.substring(5)}</td><td>${d.opened}</td><td>${d.played}</td><td>${d.matches}</td><td>${d.games}</td><td>${hours(d.minutes)}</td></tr>")
        }
        body.append("</table>")

        body.append("<h2>الكل</h2><div class=\"grid\">")
        body.append(card(stats.totalPlayers, "لاعب"))
        body.append(card(stats.totalMatches, "مباراة"))
        body.append(card(stats.totalGames, "گيم"))
        body.append(card("${stats.averageMatchMinutes} د", "معدل المباراة"))
        body.append(card(stats.reachable, "توصلهم الإشعارات"))
        body.append(card(stats.tournaments, "بطولة"))
        body.append("</div>")

        body.append("<h2>النسخ (آخر 30 يوم)</h2><table><tr><th>النسخة</th><th>ناس</th></tr>")
        stats.versions.forEach { (code, people) ->
            body.append("<tr><td>${if (code <= 0) "قبل 1.4.1" else "build $code"}</td><td>$people</td></tr>")
        }
        body.append("</table>")

        body.append("<h2>أكثر اللعّابين (آخر 30 يوم)</h2><table><tr><th>اللاعب</th><th>مباريات</th></tr>")
        stats.topPlayers.forEach { (name, n) -> body.append("<tr><td>${esc(name)}</td><td>$n</td></tr>") }
        body.append("</table>")
    }

    return """<!doctype html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex">
<title>طيرة · الأرقام</title>
<style>
 body{margin:0;background:#1e1309;color:#fbf7ef;font-family:system-ui,-apple-system,"Segoe UI",Tahoma,sans-serif;padding:16px}
 h1{color:#d9b166;margin:4px 0 12px}
 h2{color:#d9b166;font-size:17px;margin:22px 0 8px}
 .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(104px,1fr));gap:8px}
 .card{background:#2b1c10;border:1px solid #5a4422;border-radius:12px;padding:10px;text-align:center}
 .card b{display:block;font-size:24px;color:#f0d79a}
 .card span{font-size:12px;color:#a79274}
 table{width:100%;border-collapse:collapse;font-size:14px}
 th,td{padding:6px;border-bottom:1px solid #3a2616;text-align:center}
 th{color:#a79274;font-weight:normal}
 .chart{width:100%;height:auto;background:#2b1c10;border-radius:12px;padding:6px 0}
 .chart text{fill:#a79274;font-size:8px}
 .chart .n{fill:#f0d79a;font-size:7px}
 .legend{font-size:12px;color:#a79274}
 .legend i{display:inline-block;width:10px;height:10px;border-radius:2px;margin:0 4px 0 10px}
 .muted{color:#a79274}
</style></head><body>
<h1>طيرة · الأرقام</h1>
$body
<p class="muted" style="margin-top:24px">الأيام بتوقيت بغداد. «فتحوا» = فتحوا التطبيق متصلين بالسيرفر · «لعبوا» = گعدوا بمباراة بدت ذاك اليوم.</p>
</body></html>"""
}
