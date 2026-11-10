import SwiftUI

public struct SeatPlayerView: View {
    public let seat: Seat?
    public let tileCount: Int
    public let isActing: Bool
    public let isPartner: Bool

    public init(seat: Seat?, tileCount: Int, isActing: Bool, isPartner: Bool) {
        self.seat = seat
        self.tileCount = tileCount
        self.isActing = isActing
        self.isPartner = isPartner
    }

    public var body: some View {
        VStack(spacing: 4) {
            ZStack {
                Circle()
                    .fill(isPartner ? Felt.lapis.opacity(0.8) : Felt.rail.opacity(0.8))
                    .frame(width: 44, height: 44)
                    .overlay(
                        Circle()
                            .stroke(isActing ? Felt.goldLit : Felt.brass.opacity(0.4), lineWidth: isActing ? 2.5 : 1)
                    )

                Image(systemName: seat?.isBot == true ? "cpu" : "person.fill")
                    .font(.system(size: 20))
                    .foregroundColor(isActing ? Felt.goldLit : Felt.parchment)

                // Remaining tile count badge
                Text("\(tileCount)")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Felt.ink)
                    .padding(4)
                    .background(Felt.brass)
                    .clipShape(Circle())
                    .offset(x: 16, y: 14)
            }

            Text(seat?.name ?? "مقعد فارغ")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Felt.parchment)
                .lineLimit(1)
        }
    }
}

public struct SeatsLayoutView: View {
    public let playerView: PlayerView

    public init(playerView: PlayerView) {
        self.playerView = playerView
    }

    private var mySeat: Int { playerView.yourSeat }
    private var partnerSeat: Int { (mySeat + 2) % 4 }
    private var rightSeat: Int { (mySeat + 1) % 4 }
    private var leftSeat: Int { (mySeat + 3) % 4 }

    public var body: some View {
        ZStack {
            // Partner at Top
            SeatPlayerView(
                seat: playerView.seatInfo(partnerSeat),
                tileCount: playerView.handCounts.indices.contains(partnerSeat) ? playerView.handCounts[partnerSeat] : 0,
                isActing: playerView.actingSeat == partnerSeat,
                isPartner: true
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .padding(.top, 16)

            // Right Opponent
            SeatPlayerView(
                seat: playerView.seatInfo(rightSeat),
                tileCount: playerView.handCounts.indices.contains(rightSeat) ? playerView.handCounts[rightSeat] : 0,
                isActing: playerView.actingSeat == rightSeat,
                isPartner: false
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
            .padding(.trailing, 16)

            // Left Opponent
            SeatPlayerView(
                seat: playerView.seatInfo(leftSeat),
                tileCount: playerView.handCounts.indices.contains(leftSeat) ? playerView.handCounts[leftSeat] : 0,
                isActing: playerView.actingSeat == leftSeat,
                isPartner: false
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .padding(.leading, 16)
        }
    }
}
