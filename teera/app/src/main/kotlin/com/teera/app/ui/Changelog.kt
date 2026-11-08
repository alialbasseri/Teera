package com.teera.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.teera.app.ui.theme.Felt

/** One version of the game and what it brought, newest first in [CHANGELOG]. */
data class Release(val version: String, val date: String, val notes: List<String>)

/** Everything each version brought. Add the new version at the top when bumping it. */
val CHANGELOG = listOf(
    Release(
        "1.6.0", "2026-11-09",
        listOf(
            "الترتيب 🏆: كأس بزاوية القائمة. نقاط كل لاعب تصعد وتنزل حسب قوة الخصم",
            "الثنائيات: أقوى شريكين لعبوا ويا بعض",
            "سجل المباريات: من «سجلّي» افتح أي مباراة، وشوف كل گيم وخطواته",
            "تنحسب المباراة بالترتيب إذا بكل فريق لاعب واحد على الأقل",
        ),
    ),
    Release(
        "1.5.1", "2026-11-09",
        listOf(
            "القائمة رجعت مرتبة: اسم اللعبة والرسمة بحجمهم الكامل",
            "«ارجع» و«افتح غرفة» بنفس السطر، و«تفرّج» صار ويا الروابط اللي جوّا",
            "من تلعب بدال واحد گالي والبول يركب بجهتين، يگلك «دوس على الجهة اللي تريدها»",
        ),
    ),
    Release(
        "1.5.0", "2026-11-09",
        listOf(
            "اللعبات الماشية: تشوف منو دا يلعب وتدخل تتفرج",
            "اللعبة تبين بالقائمة بس إذا واحد من لاعبيها وافق، من ⋮ «خلي لعبتنا تبين للكل»",
        ),
    ),
    Release(
        "1.4.2", "2026-11-09",
        listOf("القائمة رجعت ثابتة: ما تتحرك حتى ويا زر «ارجع للغرفة»"),
    ),
    Release(
        "1.4.1", "2026-11-08",
        listOf("من تنزل نسخة جديدة، تطلعلك نافذة بيها زر «نزّلها هسه»"),
    ),
    Release(
        "1.4.0", "2026-11-08",
        listOf(
            "إذا طلعت من الگيم أو انقطع النت، ترجع لكرسيك لاعب مو متفرج",
            "المتفرج يگدر يلعب بدال اللي انقطع، ومن يرجع ياخذ كرسيه",
            "المتفرج يشوف بولات كل اللي مخلّين بولاتهم تنشاف، ويشوف اللاعب اللي جوّا",
            "الست صارت تنرسم صح على البول",
            "البوت يتأنى شوية قبل لا يلعب",
            "غيّر النقاط (مثلاً 201 ← 251) إذا وافق الكل",
            "أصوات جديدة: سبلوش، والله دنيييكم",
            "إذا أحد أخذ سكرين شوت، الباقين تومض شاشتهم ويعرفون (أندرويد 14 وفوگ)",
            "تگدر تكبّر الطاولة بإصبعين",
            "التليفون يعرف طيرة كلعبة",
            "يبين ليش انعاد الخلط، أو ليش الفتحة راحت لغير واحد",
        ),
    ),
    Release(
        "1.3.0", "2026-11-07",
        listOf(
            "السيرفر صار يحفظ اللعّابين وكل مباراة وكل گيم بخطواته",
            "سجلّي بالبروفايل: كم مباراة لعبت، كم فزت وكم خسرت، وآخر مبارياتك ويا منو",
        ),
    ),
    Release(
        "1.2.0", "2026-11-07",
        listOf(
            "تشوف خطوات الگيم بعد ما يخلص: منو لعب شنو، منو گلى ومنو انطى",
            "رتّب بولاتك بكيفك: اضغط على البول وخلّي إصبعك، وسحبه لمكانه",
            "الطاولة تتحرك بنعومة من تنزل البولات",
            "زر واحد بس: دزّ اللنك",
            "كلمة «حط بوت» تبين على كل الكراسي الفاضية",
            "«حجاري» صارت «بولاتي»",
            "هاي الصفحة: تاريخ التحديثات",
        ),
    ),
    Release(
        "1.1.0", "2026-11-06",
        listOf(
            "ناخذ خاوة: تصويت على الفريق الثاني",
            "بول الفتحة ينزل گدام صاحبه، والطاولة ثابتة ما تفر",
            "متفرجين يحچون ويكتبون، واللاعب يگدر يخليهم يشوفون بولاته",
            "كتابة بالطاولة (چات)",
            "«برطو» و«هاااي الزلم» من تنزل بولات الطيرة",
        ),
    ),
    Release(
        "1.0.0", "2026-11-06",
        listOf(
            "اللنك يفتح اللعبة ويدخلك للغرفة مباشرة",
            "جماعتي: تضيف أصدقاءك بكودهم وتعزمهم من داخل اللعبة",
            "إذا انقطع النت ترجع لنفس الغرفة",
        ),
    ),
    Release(
        "0.9.0", "2026-11-05",
        listOf(
            "صورة الحساب والاسم يبينون على الطاولة",
            "اللاعبين حوالي الطاولة: شريكك فوگ والخصوم عالجوانب",
            "كلمات عراقية: چفت، گيم، قفلت، گلى",
            "أصوات جديدة: بالله ما طيغ، يلا شباب خلي نسولف، شيل",
            "«العب الچفت» بداية الگيم",
        ),
    ),
    Release(
        "0.8.0", "2026-11-05",
        listOf(
            "خط عربي جديد",
            "صفحة قوانين اللعبة",
            "الفرق تنسمّى بأسماء لاعبيها، والاكس تبين علامة",
        ),
    ),
    Release(
        "0.7.0", "2026-11-05",
        listOf("حچي بالصوت بين اللاعبين", "اسم اللعبة طيرة وأيقونة جديدة"),
    ),
    Release(
        "0.6.0", "2026-11-05",
        listOf("اللعب أونلاين على السيرفر، كل واحد من بيته", "دزّ الكود لجماعتك"),
    ),
    Release(
        "0.5.0", "2026-11-05",
        listOf("اللعب على نفس الواي فاي", "أصوات البولات والموسيقى وأزرار الأصوات"),
    ),
    Release(
        "0.3.0", "2026-11-04",
        listOf("أول طاولة: تلعب ضد البوتات"),
    ),
)

/** The updates page: every version and what it brought, newest first. */
@Composable
fun ChangelogDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Plaque {
            Text("تاريخ التحديثات", color = Felt.Brass, style = MaterialTheme.typography.headlineSmall)
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).testTag("changelog"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(CHANGELOG) { release ->
                    Column(Modifier.fillMaxWidth()) {
                        Row {
                            Text(
                                "نسخة ${release.version}",
                                color = Felt.Brass,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(release.date, color = Felt.Muted, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                        release.notes.forEach { note ->
                            Text(
                                "• $note",
                                color = Felt.Parchment,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp), color = Felt.Brass.copy(alpha = 0.25f))
                    }
                }
            }
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Felt.Brass, contentColor = Felt.Ink),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("رجوع", style = MaterialTheme.typography.labelLarge) }
        }
    }
}
