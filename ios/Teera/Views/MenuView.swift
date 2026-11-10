import SwiftUI

public struct MenuView: View {
    @ObservedObject var client = WebSocketClient.shared
    @State private var showJoinPrompt = false
    @State private var joinCode = ""
    @State private var showRules = false
    @State private var editingName = false
    @State private var inputName = ""

    public var body: some View {
        ZStack {
            // Felt Background
            RadialGradient(
                colors: [Felt.tableLight, Felt.table, Felt.deep],
                center: .center,
                startRadius: 100,
                endRadius: 500
            )
            .ignoresSafeArea()

            VStack(spacing: 28) {
                Spacer()

                // Game Title
                VStack(spacing: 8) {
                    Text("طيرة")
                        .font(.system(size: 64, weight: .bold, design: .serif))
                        .foregroundColor(Felt.goldLit)
                        .shadow(color: Color.black.opacity(0.5), radius: 4, x: 2, y: 3)

                    Text("دومينو عراقي • ٤ لاعبين")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(Felt.brass.opacity(0.8))
                }

                // Player Profile Card
                HStack(spacing: 12) {
                    Circle()
                        .fill(Felt.railFoot)
                        .frame(width: 40, height: 40)
                        .overlay(
                            Image(systemName: "person.fill")
                                .foregroundColor(Felt.goldLit)
                        )

                    VStack(alignment: .leading, spacing: 2) {
                        Text(client.playerName)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Felt.parchment)
                        Text("الاسم المستعار")
                            .font(.system(size: 11))
                            .foregroundColor(Felt.muted)
                    }

                    Spacer()

                    Button {
                        inputName = client.playerName
                        editingName = true
                    } label: {
                        Image(systemName: "pencil")
                            .foregroundColor(Felt.brass)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .frame(maxWidth: 320)
                .background(Felt.rail.opacity(0.85))
                .cornerRadius(14)
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Felt.brass.opacity(0.3), lineWidth: 1))

                // Menu Buttons
                VStack(spacing: 14) {
                    Button {
                        SoundManager.shared.playClack()
                        client.createRoom(rules: .default)
                    } label: {
                        menuButtonLabel(title: "إنشاء طاولة جديدة", icon: "plus.circle.fill", isPrimary: true)
                    }

                    Button {
                        SoundManager.shared.playSlide()
                        showJoinPrompt = true
                    } label: {
                        menuButtonLabel(title: "دخول برمز الغرفة", icon: "arrow.right.circle.fill", isPrimary: false)
                    }

                    Button {
                        showRules = true
                    } label: {
                        menuButtonLabel(title: "قواعد اللعبة", icon: "book.fill", isPrimary: false)
                    }
                }
                .frame(maxWidth: 320)

                Spacer()

                // Sound toggle & Version
                HStack {
                    Button {
                        SoundManager.shared.soundEnabled.toggle()
                    } label: {
                        Image(systemName: SoundManager.shared.soundEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill")
                            .font(.system(size: 18))
                            .foregroundColor(Felt.brass)
                            .padding(10)
                            .background(Felt.rail.opacity(0.8))
                            .clipShape(Circle())
                    }

                    Spacer()

                    Text("الإصدار ١.٠ (iOS)")
                        .font(.system(size: 12))
                        .foregroundColor(Felt.muted)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 16)
            }
        }
        .sheet(isPresented: $showRules) {
            RulesView()
        }
        .alert("أدخل رمز الغرفة (٤ أحرف)", isPresented: $showJoinPrompt) {
            TextField("ABCD", text: $joinCode)
                .autocapitalization(.allCharacters)
            Button("دخول") {
                if !joinCode.isEmpty {
                    client.joinRoom(code: joinCode)
                }
            }
            Button("إلغاء", role: .cancel) {}
        }
        .alert("تعديل الاسم", isPresented: $editingName) {
            TextField("اسمك", text: $inputName)
            Button("حفظ") {
                if !inputName.trimmingCharacters(in: .whitespaces).isEmpty {
                    client.savePlayerName(inputName)
                }
            }
            Button("إلغاء", role: .cancel) {}
        }
    }

    private func menuButtonLabel(title: String, icon: String, isPrimary: Bool) -> some View {
        HStack {
            Image(systemName: icon)
                .font(.system(size: 18))
            Text(title)
                .font(.system(size: 16, weight: .bold))
            Spacer()
        }
        .foregroundColor(isPrimary ? Felt.ink : Felt.parchment)
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(isPrimary ? Felt.goldLit : Felt.rail.opacity(0.9))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(isPrimary ? Felt.brass : Felt.brass.opacity(0.3), lineWidth: 1)
        )
    }
}
