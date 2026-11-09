package com.teera.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teera.app.ui.theme.Felt
import com.teera.core.Domino

/**
 * How Teera is played, as a friend would explain it at the table: short, in the words
 * people actually use, with the tiles shown where a picture says it faster.
 *
 * The full reference, with the edge cases, is RULES.md in the repository; this is the
 * version for players.
 */
@Composable
fun RulesScreen(onClose: () -> Unit, onTutorial: () -> Unit = {}) {
    WalnutRoom {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع", tint = Felt.Brass)
                }
                Spacer(Modifier.weight(1f))
                // The same rules, a page at a time with the bones drawn.
                BrassButton("تعلّم خطوة بخطوة", onTutorial)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("قوانين الطيرة", color = Felt.Brass, fontSize = 46.sp, style = MaterialTheme.typography.displayLarge)
                Text(
                    "دومينا عراقية · أربعة · فريقين",
                    color = Felt.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                RosetteRule(Modifier.padding(horizontal = 40.dp))
                Spacer(Modifier.height(14.dp))

                Section("الفكرة") {
                    Point("أربعة لاعبين، كل اثنين فريق، و**شريكك مقابيلك**.")
                    Point("طقم دومينا كامل (28 بول)، **7 بولات لكل واحد**، وماكو بنك ولا سحب.")
                    Point("النقاط عليك مو إلك: **الفريق اللي يوصل 201** (أو 251) **يخسر**.")
                    Point("الفرق تتبدّل بس لما تخلص المباراة.")
                }

                Section("الفتحة") {
                    Tiles(Domino(6, 6))
                    Point("كل گيم **ينفتح بچفت**، بدون استثناء.")
                    Point("أول گيم بالمباراة ينفتح **بالست ست**، ويفتحه اللي عنده.")
                    Point("بعدها يفتح **اللي ربح الگيم اللي قبله**. إذا ما عنده چفت، تنتقل الفتحة للي وراه.")
                }

                Section("الأربع جهات") {
                    Point("الچفت الفاتح ينلعب عليه من **أربع جهات**، ولازم تتسكّر الأربعة قبل أي لعب ثاني:")
                    Point("الثاني يلعب جهة، الثالث جهة، الرابع جهة، و**الفاتح يسكّر الرابعة**.", indent = true)
                    Point("بعدها تبقى **أربع أطراف مفتوحة** لآخر الگيم.")
                    Point("الچفت بنص الگيم ينحط بالعرض، **وينلعب عليه من جهة وحدة** بس.")
                }

                Section("إذا گليت") {
                    Point("ماكو سحب. **اللي قبلك** (ودايماً خصم) يختار:")
                    Point("**ينطيك بول** من عنده، **أو يلعب بدالك** من بولاته.", indent = true)
                    Point("وأنت **تخسر دورك** بالحالتين.")
                    Point("إذا هو هم ما عنده يلعب، **لازم ينطيك**، حتى لو تضرّه.")
                    Point("وإذا انطاك **آخر بول بإيده**، خلّص وياخذ **اكس**.")
                }

                Section("نهاية الگيم") {
                    Point("**اكس:** اللي يخلّص بولاته، فريقه ياخذ **ناقص 25**.")
                    Tiles(Domino(0, 0), Domino(0, 1))
                    Point("**طيرة:** إذا لعبت وبقى بإيدك **مجموع صفر أو واحد** (يعني 0|0 أو 0|1)، تكدر تطيّر وتنهي الگيم.")
                    Point("طرت بصفر = ما ينحسب عليك شي. طرت بواحد = **+1 على فريقك**.", indent = true)
                    Point("الطيرة **خيار**: تكدر تكمل إذا واثق تخلّص وتاخذ الاكس.", indent = true)
                    Point("بالاكس وبالطيرة، **الفريق الثاني** ينحسب عليه مجموع نقاط بولاتهم، وشريك اللي خلّص ما ينحسب عليه.")
                    Point("**قفلت:** إذا قفلت الگيم وماكو أحد يكدر يلعب، **ماكو نقاط**، تنعاد الگيم، **واللي قفلها يفتح**.")
                    Example("فريقكم على 200 وطيّرت بواحد؟ صرتوا 201… **وخسرتوا بإيدكم**.")
                }

                Section("إعادة التوزيع") {
                    Point("واحد بإيده **5 چفتات**، أو **6 بولات بنفس الرقم**: ينعاد التوزيع.")
                    Point("الفريق الضد **كلهم ما عندهم رقم الچفت الفاتح**: ينعاد التوزيع.")
                    Point("واحد بإيده **4 چفتات**: الخيار إله، يعيد أو يكمل.")
                }

                Section("ناخذ خاوة") {
                    Point("إذا الفريق الثاني خالف (إشارة أو تلميح للشريك)، دوس **«خاوة»**.")
                    Point("الطاولة تصوّت خلال 30 ثانية، وتنقبل إذا **وافق أكثر من النص**.")
                    Point("**كل 3 خاوات = 50 نقطة** على الفريق، وتتجمّع طول المباراة.")
                }

                Section("الحساب بسرعة") {
                    ScoreLine("", "فريق اللي خلّص", "الفريق الثاني", header = true)
                    ScoreLine("اكس", "−25 لفريقه", "مجموع بولاتهم")
                    ScoreLine("طيرة بصفر", "0", "مجموع بولاتهم")
                    ScoreLine("طيرة بواحد", "+1 لفريقه", "مجموع بولاتهم")
                    ScoreLine("قفلت", "ماكو نقاط", "ماكو نقاط")
                }

                Spacer(Modifier.height(8.dp))
                GoldButton("فهمت، يلا نلعب", onClose)
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/** A brass-edged panel with a heading, the way each rule group sits on the page. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF120B05).copy(alpha = 0.5f))
            .border(1.dp, Felt.Brass.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).rotate(45f).background(Felt.Brass))
            Spacer(Modifier.width(10.dp))
            Text(title, color = Felt.Brass, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** One rule. **Bold** spans in the text are drawn in gold. */
@Composable
private fun Point(text: String, indent: Boolean = false, muted: Boolean = false) {
    Row(Modifier.padding(start = if (indent) 18.dp else 0.dp, top = 3.dp, bottom = 3.dp)) {
        Text(
            if (indent) "–" else "•",
            color = Felt.Brass,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(16.dp),
        )
        Text(
            emphasised(text),
            color = if (muted) Felt.Muted else Felt.Parchment,
            style = if (muted) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
        )
    }
}

/** Turns **this** into gold, bold text; the rules are easier to skim with the key words lit. */
private fun emphasised(text: String) = androidx.compose.ui.text.buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) {
            pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = Felt.GoldLit,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                ),
            )
            append(part)
            pop()
        } else {
            append(part)
        }
    }
}

@Composable
private fun Tiles(vararg dominoes: Domino) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        dominoes.forEach { DominoTile(it, Modifier.size(34.dp, 68.dp)) }
    }
}

@Composable
private fun Example(text: String) {
    Text(
        emphasised(text),
        color = Felt.Parchment,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Felt.Danger.copy(alpha = 0.18f))
            .border(1.dp, Felt.Danger.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(10.dp),
    )
}

/** One row of the quick scoring table: how it ended, what the finisher's side gets, the other side. */
@Composable
private fun ScoreLine(end: String, finisher: String, others: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(end, color = Felt.GoldLit, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1.1f))
        Text(finisher, color = if (header) Felt.Muted else Felt.Parchment, style = style, modifier = Modifier.weight(1f))
        Text(others, color = if (header) Felt.Muted else Felt.Behind, style = style, modifier = Modifier.weight(1.1f))
    }
}
