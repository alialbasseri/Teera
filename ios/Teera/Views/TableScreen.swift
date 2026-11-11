import SwiftUI

public struct TableScreen: View {
    @ObservedObject var client = WebSocketClient.shared
    @State private var selectedTile: Domino? = nil
    @State private var showEmoteSheet = false
    /// When the player before a stuck one decides: give a bone, or play one in their place.
    @State private var rescueMode: RescueMode? = nil

    private enum RescueMode { case give, playFor }

    /// The moves the hand and the board offer right now: while rescuing, only those of
    /// the chosen kind, so a bone that could go either way does what the player picked.
    private func offeredMoves(in view: PlayerView) -> [Move] {
        guard view.phase == .RESCUE, view.isYourMove else { return view.legalMoves }
        switch rescueMode {
        case .give: return view.legalMoves.filter { $0.type == .give }
        case .playFor: return view.legalMoves.filter { $0.type == .playFor }
        case nil: return []
        }
    }

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
                        legalMoves: offeredMoves(in: view),
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
                Button("طيّر") {
                    SoundManager.shared.playTeera()
                    client.makeMove(.teera)
                }
                .buttonStyle(TeeraButtonStyle(color: Felt.goldDeep))

                Button("كمّل للاكس") {
                    client.makeMove(.carryOn)
                }
                .buttonStyle(TeeraButtonStyle(color: Felt.rail))
            } else if view.phase == .REDEAL_OFFER && view.actingSeat == view.yourSeat {
                Button("أعد الخلط (4 چفتات)") {
                    client.makeMove(.redeal)
                }
                .buttonStyle(TeeraButtonStyle(color: Felt.danger))

                Button("كمّل بإيدك") {
                    client.makeMove(.keepHand)
                }
                .buttonStyle(TeeraButtonStyle(color: Felt.rail))
            } else if view.phase == .RESCUE && view.isYourMove {
                let stuck = view.stuckSeat.flatMap { view.seatInfo($0)?.name } ?? ""
                switch rescueMode {
                case nil:
                    Text("\(stuck) گلى — تلعب تنطي؟")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Felt.brass)
                    Button("العب بداله") { rescueMode = .playFor; selectedTile = nil }
                        .buttonStyle(TeeraButtonStyle(color: Felt.goldDeep))
                        .disabled(!view.legalMoves.contains { $0.type == .playFor })
                    Button("انطيه بول") { rescueMode = .give; selectedTile = nil }
                        .buttonStyle(TeeraButtonStyle(color: Felt.rail))
                case .some(let mode):
                    Text(mode == .give ? "اختر البول اللي تنطيه" : "اختر البول اللي تلعبه")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Felt.playable)
                    Button("رجوع") { rescueMode = nil; selectedTile = nil }
                        .buttonStyle(TeeraButtonStyle(color: Felt.rail))
                }
            } else {
                Text(statusText(for: view))
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(view.isYourMove ? Felt.playable : Felt.parchment)
            }
        }
        .padding(.vertical, 6)
        // A new decision starts with no choice made.
        .onChange(of: view.phase) { _ in rescueMode = nil }
    }

    private func statusText(for view: PlayerView) -> String {
        if view.isYourMove {
            if view.board.isEmpty { return "افتح الگيم بچفت" }
            if selectedTile != nil { return "دوس على الجهة اللي تريدها" }
            return "دورك — اختر بول"
        } else if view.phase == .RESCUE && view.stuckSeat == view.yourSeat {
            let rescuer = view.actingSeat.flatMap { view.seatInfo($0)?.name } ?? ""
            return "گليت — \(rescuer) إما ينطيك بول أو يلعب بدالك"
        } else if let acting = view.actingSeat, let seat = view.seatInfo(acting) {
            return "\(seat.name) يفكّر…"
        }
        return " "
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
        rescueMode = nil
    }

    private func gameEndOverlay(result: GameResult, view: PlayerView) -> some View {
        ZStack {
            Color.black.opacity(0.65).ignoresSafeArea()

            VStack(spacing: 16) {
                Text(view.phase == .MATCH_OVER
                     ? (view.losingTeam == view.yourTeam ? "خسرتوا المباراة" : "فزتوا بالمباراة")
                     : (result.end == .X ? "اكس!" : (result.end == .TEERA ? "طيرة!" : "قفلت")))
                    .font(.system(size: 38, weight: .bold))
                    .foregroundColor(Felt.goldLit)

                if let winner = result.winnerSeat, let seat = view.seatInfo(winner) {
                    Text(result.end == .TEERA ? "\(seat.name) طيّر" : "\(seat.name) خلّص")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(Felt.parchment)
                }

                HStack(spacing: 30) {
                    VStack {
                        Text(teamName(0, in: view))
                            .foregroundColor(Felt.muted)
                        Text("+\(result.scored[0])")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(Felt.parchment)
                    }

                    VStack {
                        Text(teamName(1, in: view))
                            .foregroundColor(Felt.muted)
                        Text("+\(result.scored[1])")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(Felt.parchment)
                    }
                }
                .padding()
                .background(Felt.railFoot)
                .cornerRadius(12)

                if view.phase == .MATCH_OVER {
                    // The whole table goes back to the room together, for another match.
                    Button("ارجع للغرفة") {
                        client.backToLobby()
                    }
                    .buttonStyle(TeeraButtonStyle(color: Felt.brass))
                    Button("اطلع من الغرفة") {
                        client.leaveRoom()
                    }
                    .buttonStyle(TeeraButtonStyle(color: Felt.rail))
                } else {
                    Button("الگيم الجاي") {
                        client.nextGame()
                    }
                    .buttonStyle(TeeraButtonStyle(color: Felt.brass))
                }
            }
            .padding(24)
            .background(Felt.rail)
            .cornerRadius(20)
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Felt.brass, lineWidth: 2))
            .padding(32)
        }
    }

    /// A team as the table names it: its two players.
    private func teamName(_ team: Int, in view: PlayerView) -> String {
        let names = view.seats.filter { teamOf($0.seat) == team }.map { $0.isBot ? "بوت" : $0.name }
        return names.isEmpty ? "فريق \(team + 1)" : names.joined(separator: "/")
    }

    private var emoteSelectionSheet: some View {
        VStack(spacing: 16) {
            Text("الأصوات")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(Felt.parchment)
                .padding(.top, 16)

            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 12) {
                ForEach(Emote.board, id: \.self) { emote in
                    Button(emote.titleArabic) {
                        // The server sends it back to everyone, this phone included: it plays then.
                        client.sendEmote(emote)
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
