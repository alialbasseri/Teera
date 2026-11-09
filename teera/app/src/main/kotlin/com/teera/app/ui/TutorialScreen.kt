package com.teera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teera.app.ui.theme.Felt
import com.teera.core.Domino

/** One page of the lesson: a heading, a few lines, and the bones it is about. */
private data class Lesson(val title: String, val lines: List<String>, val tiles: List<Domino> = emptyList())

private fun d(a: Int, b: Int) = Domino.of(a, b)

/** The game as RULES.md has it, told to somebody sitting down for the first time. */
private val LESSONS = listOf(
    Lesson(
        "أهلاً بالطيرة",
        listOf(
            "أربعة لاعبين، كل اثنين فريق، وشريكك گدامك.",
            "كل واحد ياخذ 7 بولات، وماكو سحب: البولات كلها بالإيدين.",
        ),
        listOf(d(6, 6), d(3, 5), d(0, 1)),
    ),
    Lesson(
        "النقاط بالمقلوب",
        listOf(
            "بكل گيم، الفريق اللي يخسر تنحسب عليه نقاط البولات اللي بقت بإيدهم.",
            "الفريق اللي يوصل 201 (أو 251) يخسر المباراة. يعني النقاط عبء، والأقل أحسن.",
            "أو العبوا 10 گيمات، وبالآخر الأكثر نقاط يخسر.",
        ),
    ),
    Lesson(
        "الفتحة",
        listOf(
            "كل گيم ينفتح بچفت، بلا استثناء.",
            "أول گيم بالمباراة ينفتح بالـ 6|6، يفتحه اللي عنده.",
            "بعدين يفتح اللي فاز بالگيم اللي قبله. إذا ما عنده چفت، الفتحة تروح للي وراه.",
        ),
        listOf(d(6, 6)),
    ),
    Lesson(
        "الأربع جهات",
        listOf(
            "الچفت الفاتح ينلعب عليه من أربع جهات.",
            "لازم تتسكّر الجهات الأربع قبل أي لعب ثاني: الثاني والثالث والرابع كل واحد جهة، والفاتح يسكّر الرابعة.",
            "تلعب بول يركب على طرف: نفس الرقم.",
        ),
        listOf(d(6, 3), d(6, 6), d(6, 1)),
    ),
    Lesson(
        "گلّيت؟",
        listOf(
            "إذا إجه دورك وما عندك بول يركب، اللاعب اللي قبلك (وهو خصمك) يختار:",
            "ينطيك بول من بولاته، أو يلعب بدالك من بولاته.",
            "وإنت تخسر دورك كله. وإذا هو هم ما عنده بول يركب، لازم ينطيك.",
        ),
    ),
    Lesson(
        "الاكس",
        listOf(
            "اللي يخلّص كل بولاته يفوز بالگيم، وفريقه ياخذ اكس: ناقص 25.",
            "والفريق الثاني تنحسب عليه نقاط بولاتهم.",
            "وإذا انسدّت الطاولة ومحد يگدر يلعب: ماكو نقاط، والگيم يتعاد.",
        ),
    ),
    Lesson(
        "الطيرة",
        listOf(
            "إذا لعبت وبقى بإيدك 0|0 بس، أو 0|1 بس، تگدر تگول «طيرة» وتخلّص الگيم.",
            "بالطيرة ما تاخذ اكس، والخصم تنحسب عليه بولاته. وإذا طيّرت بواحد، ينحسب واحد على فريقك.",
            "الطيرة خيارك: إذا واثق تخلّص بولاتك، كمّل وخذ الاكس.",
        ),
        listOf(d(0, 0), d(0, 1)),
    ),
    Lesson(
        "ناخذ خاوة",
        listOf(
            "إذا شفت الخصم يلمّح أو يأشّر لشريكه، دوس «خاوة».",
            "الطاولة تصوّت 30 ثانية، وتنقبل إذا وافق أكثر من النص.",
            "كل 3 خاوات على فريق = 50 نقطة عليهم.",
        ),
    ),
)

/**
 * The lesson for somebody new: a page at a time, with the bones drawn, and at the end a
 * game against easy bots to try it.
 */
@Composable
fun TutorialScreen(onTry: () -> Unit, onClose: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val lesson = LESSONS[page]
    val last = page == LESSONS.lastIndex
    WalnutRoom {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "تخطّى",
                    color = Felt.Muted,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(8.dp).testTag("tutorial-skip"),
                )
                Spacer(Modifier.weight(1f))
                // Where you are in it: a dot a page.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LESSONS.indices.forEach { i ->
                        Box(
                            Modifier.size(if (i == page) 10.dp else 7.dp).clip(CircleShape)
                                .background(if (i == page) Felt.GoldLit else Felt.Muted.copy(alpha = 0.5f)),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(lesson.title, color = Felt.Brass, fontSize = 40.sp, style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if (lesson.tiles.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 18.dp)) {
                    lesson.tiles.forEach { DominoTile(it, Modifier.size(40.dp, 80.dp)) }
                }
            }
            lesson.lines.forEach { line ->
                Text(
                    line,
                    color = Felt.Parchment,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 5.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (last) {
                GoldButton("جرّب ضد البوتات (سهل)", onTry, Modifier.testTag("tutorial-try"))
                Spacer(Modifier.height(10.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (page > 0) BrassButton("رجوع", { page-- }, modifier = Modifier.weight(1f))
                if (last) BrassButton("خلصت", onClose, modifier = Modifier.weight(1f).testTag("tutorial-done"))
                else GoldButton("التالي", { page++ }, Modifier.weight(1f).testTag("tutorial-next"))
            }
        }
    }
}
