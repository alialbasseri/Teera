import SwiftUI

public struct TableScreen: View {
    @ObservedObject var client = WebSocketClient.shared
    @State private var selectedTile: Domino? = nil
    @State private var showEmoteSheet = false

    public var body: some View {
        ZStack {
            // Table Felt Background
            RadialGradient(
                colors: [Felt.tableLight, Felt.table, Felt.deep],
                center: .center,
                startRadius: 80,
                endRadius: 400
            )
            .ignoresSafeArea()

            // Walnut table rail border
            RoundedRectangle(cornerRadius: 24)
                .stroke(
                    LinearGradient(
                        colors: [Felt.railTop, Felt.rail, Felt.railFoot],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 10
                )
                .ignoresSafeArea()

            if let view = client.playerView {
                VStack(spacing: 0) {
                    // Top header: Scores & Info
                    topBarView(view: view)
                        .padding(.horizontal, 16)
                        .padding(.top, 8)

                    // Table Surface with Opponents & Board
                    ZStack {
                        SeatsLayoutView(playerView: view)
                        BoardView(
                            board: view.board,
                            targetArms: candidateArms(for: selectedTile, in: view),
                            onArmSelected: { arm in
                                if let tile = selectedTile {
                                    playTile(tile, arm: arm, in: view)
                                }
                            }
                        )
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                    // Action Banner (Turn info / Teera / Rescue prompt)
                    statusBannerView(view: view)
                        .padding(.horizontal, 20)
                        .padding(.bottom, 6)

                    // Player Hand
                    HandView(
                        hand: view.hand,
                        legalMoves: view.legalMoves,
                        selectedTile: $selectedTile,
                        onPlayTile: { tile, arm in
                            playTile(tile, arm: arm, in: view)
                        }
                    )
                    .padding(.horizontal, 12)
                    .padding(.bottom, 12)
                }

                // Game Over Overlay
                if let lastGame = view.lastGame, view.phase == .GAME_OVER || view.phase == .MATCH_OVER {
                    gameEndOverlay(result: lastGame, view: view)
                }
            } else {
                VStack(spacing: 16) {
                    ProgressView()
                        .tint(Felt.brass)
                    Text("جاري الاتصال بالطاولة...")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Felt.parchment)
                }
            }
        }
        .sheet(isPresented: $showEmoteSheet) {
            emoteSelectionSheet
        }
    }

    private func topBarView(view: PlayerView) -> some View {
        HStack {
            Button {
                client.leaveRoom()
            } label: {
                Image(systemName: "chevron.right")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Felt.brass)
                    .padding(8)
                    .background(Felt.rail.opacity(0.8))
                    .clipShape(Circle())
            }

            Spacer()

            // Scores Banner (Reverse scores: Team that reaches target loses!)
            HStack(spacing: 20) {
                let myTeam = view.yourTeam
                let oppTeam = otherTeam(myTeam)
                let myScore = view.scores.indices.contains(myTeam) ? view.scores[myTeam] : 0
                let oppScore = view.scores.indices.contains(oppTeam) ? view.scores[oppTeam] : 0

                VStack(spacing: 2) {
                    Text("فريقنا")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Felt.muted)
                    Text("\(myScore) / \(view.rules.target)")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(myScore > oppScore ? Felt.danger : Felt.parchment)
                }

                Text("•")
                    .foregroundColor(Felt.brass)

                VStack(spacing: 2) {
                    Text("فريقهم")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Felt.muted)
                    Text("\(oppScore) / \(view.rules.target)")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(oppScore > myScore ? Felt.danger : Felt.parchment)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(Felt.railFoot.opacity(0.85))
            .clipShape(Capsule())
            .overlay(Capsule().stroke(Felt.brass.opacity(0.3), lineWidth: 1))

            Spacer()

            Button {
                showEmoteSheet.toggle()
            } label: {
                Image(systemName: "face.smiling.fill")
                    .font(.system(size: 18))
                    .foregroundColor(Felt.brass)
                    .padding(8)
                    .background(Felt.rail.opacity(0.8))
                    .clipShape(Circle())
            }
        }
    }

    private func statusBannerView(view: PlayerView) -> some View {
        HStack {
            if view.phase == .TEERA_OFFER && view.actingSeat == view.yourSeat {
                Button("أعلن طيرة!") {
                    SoundManager.shared.playTeera()
                    client.makeMove(.teera)
                }
                .buttonStyle(TeeraButtonStyle(color: Felt.goldDeep))

                Button("استمر للاكس") {
                    client.makeMove(.carryOn)
                }
                .buttonStyle(TeeraButtonStyle(color: Felt.rail))
            } else if view.phase == .REDEAL_OFFER && view.actingSeat == view.yourSeat {
                Button("إعادة التوزيع (4 دبلات)") {
                    client.makeMove(.redeal)
                }
                .buttonStyle(TeeraButtonStyle(color: Felt.danger))

                Button("إبقاء اليد") {
                    client.makeMove(.keepHand)
                }
                .buttonStyle(TeeraButtonStyle(color: Felt.rail))
            } else {
                Text(statusText(for: view))
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(view.isYourMove ? Felt.playable : Felt.parchment)
            }
        }
        .padding(.vertical, 6)
    }

    private func statusText(for view: PlayerView) -> String {
        if view.isYourMove {
            if view.phase == .RESCUE {
                return "البول: خصمك مسدود، أعطه حجراً أو العب بدله!"
            }
            return "دورك تلعب"
        } else if let acting = view.actingSeat, let seat = view.seatInfo(acting) {
            return "بانتظار دور: \(seat.name)"
        }
        return "جاري اللعب..."
    }

    private func candidateArms(for tile: Domino?, in view: PlayerView) -> [Arm] {
        guard let tile = tile else { return [] }
        return view.legalMoves.compactMap { move in
            if move.domino == tile, let arm = move.arm {
                return arm
            }
            return nil
        }
    }

    private func playTile(_ tile: Domino, arm: Arm?, in view: PlayerView) {
        if view.phase == .RESCUE {
            // In rescue: decide whether it's give or playFor
            if let arm = arm {
                client.makeMove(.playFor(tile, arm: arm))
            } else {
                client.makeMove(.give(tile))
            }
        } else {
            client.makeMove(.play(tile, arm: arm))
        }
        selectedTile = nil
    }

    private func gameEndOverlay(result: GameResult, view: PlayerView) -> some View {
        ZStack {
            Color.black.opacity(0.65).ignoresSafeArea()

            VStack(spacing: 16) {
                Text(result.end == .X ? "اكس!" : (result.end == .TEERA ? "طيرة!" : "مسدودة!"))
                    .font(.system(size: 38, weight: .bold))
                    .foregroundColor(Felt.goldLit)

                if let winner = result.winnerSeat, let seat = view.seatInfo(winner) {
                    Text("الفائز: \(seat.name)")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(Felt.parchment)
                }

                HStack(spacing: 30) {
                    VStack {
                        Text("فريق ١")
                            .foregroundColor(Felt.muted)
                        Text("+\(result.scored[0])")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(Felt.parchment)
                    }

                    VStack {
                        Text("فريق ٢")
                            .foregroundColor(Felt.muted)
                        Text("+\(result.scored[1])")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(Felt.parchment)
                    }
                }
                .padding()
                .background(Felt.railFoot)
                .cornerRadius(12)

                Button("الجيم التالي") {
                    client.nextGame()
                }
                .buttonStyle(TeeraButtonStyle(color: Felt.brass))
            }
            .padding(24)
            .background(Felt.rail)
            .cornerRadius(20)
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Felt.brass, lineWidth: 2))
            .padding(32)
        }
    }

    private var emoteSelectionSheet: some View {
        VStack(spacing: 16) {
            Text("لوحة الصوت والرياكشن")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(Felt.parchment)
                .padding(.top, 16)

            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 12) {
                ForEach(Emote.allCases.filter { !$0.isFace }, id: \.self) { emote in
                    Button(emote.titleArabic) {
                        client.sendEmote(emote)
                        SoundManager.shared.playEmote(emote)
                        showEmoteSheet = false
                    }
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(Felt.parchment)
                    .padding(10)
                    .frame(maxWidth: .infinity)
                    .background(Felt.rail)
                    .cornerRadius(8)
                }
            }
            .padding()

            Spacer()
        }
        .presentationDetents([.fraction(0.45)])
        .background(Felt.deep.ignoresSafeArea())
    }
}

public struct TeeraButtonStyle: ButtonStyle {
    public let color: Color

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .bold))
            .foregroundColor(Felt.parchment)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(color)
            .cornerRadius(10)
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
    }
}
