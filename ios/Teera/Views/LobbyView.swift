import SwiftUI

public struct LobbyView: View {
    @ObservedObject var client = WebSocketClient.shared
    @State private var selectedTarget = 201

    public var body: some View {
        ZStack {
            RadialGradient(
                colors: [Felt.table, Felt.deep],
                center: .center,
                startRadius: 100,
                endRadius: 500
            )
            .ignoresSafeArea()

            if let room = client.currentRoom {
                VStack(spacing: 20) {
                    // Top header: Room Code & Leave
                    HStack {
                        Button {
                            client.leaveRoom()
                        } label: {
                            Text("خروج")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Felt.danger)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(Felt.rail.opacity(0.8))
                                .cornerRadius(8)
                        }

                        Spacer()

                        // Room code with copy
                        VStack(spacing: 2) {
                            Text("رمز الغرفة")
                                .font(.system(size: 11))
                                .foregroundColor(Felt.muted)
                            Text(room.code)
                                .font(.system(size: 24, weight: .heavy, design: .monospaced))
                                .foregroundColor(Felt.goldLit)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 6)
                        .background(Felt.railFoot)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Felt.brass, lineWidth: 1))
                        .onTapGesture {
                            UIPasteboard.general.string = room.code
                        }

                        Spacer()

                        // Dummy spacer to balance header
                        Text("     ")
                    }
                    .padding(.horizontal)
                    .padding(.top, 10)

                    Text("ترتيب المقاعد (فريق ١: مقعد ٠ و ٢ • فريق ٢: مقعد ١ و ٣)")
                        .font(.system(size: 12))
                        .foregroundColor(Felt.parchment.opacity(0.7))

                    // 4 Seats Grid
                    VStack(spacing: 16) {
                        // Team 1 Header
                        Text("الفريق الأول")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(Felt.goldLit)

                        HStack(spacing: 16) {
                            seatCard(seatIndex: 0, room: room)
                            seatCard(seatIndex: 2, room: room)
                        }

                        // Team 2 Header
                        Text("الفريق الثاني")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(Felt.goldLit)
                            .padding(.top, 8)

                        HStack(spacing: 16) {
                            seatCard(seatIndex: 1, room: room)
                            seatCard(seatIndex: 3, room: room)
                        }
                    }
                    .padding(.horizontal)

                    Spacer()

                    // Bottom Host / Ready Actions
                    let isHost = room.hostId == client.playerId
                    let mySeat = room.seats.first { $0.playerId == client.playerId }
                    let isReady = mySeat != nil && room.ready.contains(client.playerId)

                    VStack(spacing: 12) {
                        if isHost {
                            Button {
                                client.startMatch()
                            } label: {
                                Text("ابدأ المباراة")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(Felt.ink)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 14)
                                    .background(room.seats.count == 4 ? Felt.goldLit : Felt.muted)
                                    .cornerRadius(12)
                            }
                            .disabled(room.seats.count != 4)
                        } else if mySeat != nil {
                            Button {
                                client.setReady(!isReady)
                            } label: {
                                Text(isReady ? "أنا جاهز ✓" : "اضغط عندما تجهز")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(isReady ? Felt.ink : Felt.parchment)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 14)
                                    .background(isReady ? Felt.playable : Felt.brassDim)
                                    .cornerRadius(12)
                            }
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 24)
                }
            }
        }
    }

    @ViewBuilder
    private func seatCard(seatIndex: Int, room: RoomInfo) -> some View {
        let seat = room.seats.first { $0.seat == seatIndex }
        let isHost = room.hostId == client.playerId

        VStack(spacing: 8) {
            Text("مقعد \(seatIndex + 1)")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(Felt.muted)

            if let s = seat {
                Text(s.name)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Felt.parchment)
                    .lineLimit(1)

                if s.isBot {
                    Text("بوت")
                        .font(.system(size: 11))
                        .foregroundColor(Felt.brass)
                } else if room.ready.contains(s.playerId) {
                    Text("جاهز")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(Felt.playable)
                }
            } else {
                Button("اجلس هنا") {
                    client.takeSeat(seatIndex)
                }
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(Felt.goldLit)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Felt.railFoot)
                .cornerRadius(6)

                if isHost {
                    Button("+ بوت") {
                        client.setBot(seat: seatIndex, enabled: true)
                    }
                    .font(.system(size: 11))
                    .foregroundColor(Felt.muted)
                }
            }
        }
        .frame(maxWidth: .infinity, minHeight: 90)
        .padding(8)
        .background(Felt.rail.opacity(0.85))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(seat != nil ? Felt.brass.opacity(0.4) : Felt.brass.opacity(0.15), lineWidth: 1)
        )
    }
}
