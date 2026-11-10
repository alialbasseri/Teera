import SwiftUI

public struct RulesView: View {
    @Environment(\.dismiss) private var dismiss

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    ruleSection(
                        title: "١. التشكيل والفِرق",
                        content: """
                        • ٤ لاعبين بالضبط، كل اثنين فريق، والشركاء متقابلون.
                        • طقم دبل ست (٢٨ حجر)، ٧ حجارة لكل لاعب.
                        • لا يوجد بنك (Boneyard) — الطقم كله موزّع على الأيدي.
                        """
                    )

                    ruleSection(
                        title: "٢. النقاط — معكوسة",
                        content: """
                        • هدف المباراة ٢٠١ أو ٢٥١ نقطة.
                        • الفريق الذي يصل إلى الرقم يخسر! فالنقاط عبء وليست مكسباً.
                        • التسجيل على الفريق ككل وليس على الأفراد.
                        """
                    )

                    ruleSection(
                        title: "٣. الفتحة وقاعدة الأربع جهات",
                        content: """
                        • كل جيم يُفتح بدبل.
                        • الجيم الأول يفتحه من بيده ٦|٦ حصراً.
                        • الجيمات التالية يفتحها الفائز بالجيم السابق بأي دبل.
                        • الدبل المفتوح يُلعب عليه من ٤ جهات، ويجب أن تُسكّر الجهات الأربع قبل أي لعب آخر.
                        """
                    )

                    ruleSection(
                        title: "٤. البول (بديل السحب)",
                        content: """
                        • إذا جاء دورك وليس بيدك حجر يُلعب، فإن اللاعب الذي قبلك (وهو دائماً خصمك) يختار:
                          ١) إما أن يعطيك حجراً من حجارته
                          ٢) أو يلعب بدلك من حجارته هو
                        • إذا أعطاك حجراً يركب تلعبه فوراً. إذا أعطى آخر حجر بيده يفوز فوراً ويأخذ اكس!
                        """
                    )

                    ruleSection(
                        title: "٥. نهاية الجيم — الاكس والطيرة",
                        content: """
                        • الاكس (X): من يُنهي كل حجارته يأخذ اكس ويخصم ٢٥ نقطة من فريقه.
                        • الطيرة: من تصبح يده خفيفة جداً (مجموع نقاطها صفر أو نقطة واحدة كـ ٠|٠ أو ٠|١) يحق له إعلان «طيرة» وإنهاء الجيم مباشرة دون انتظار الاكس!
                        """
                    )
                }
                .padding(20)
            }
            .background(Felt.deep.ignoresSafeArea())
            .navigationTitle("قواعد الطيرة")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("إغلاق") {
                        dismiss()
                    }
                    .foregroundColor(Felt.goldLit)
                }
            }
        }
    }

    private func ruleSection(title: String, content: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(Felt.goldLit)

            Text(content)
                .font(.system(size: 14, weight: .regular))
                .foregroundColor(Felt.parchment)
                .lineSpacing(4)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Felt.rail.opacity(0.85))
        .cornerRadius(12)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Felt.brass.opacity(0.2), lineWidth: 1))
    }
}
