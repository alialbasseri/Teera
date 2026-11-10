import SwiftUI

public struct HandView: View {
    public let hand: [Domino]
    public let legalMoves: [Move]
    @Binding public var selectedTile: Domino?
    public var onPlayTile: ((Domino, Arm?) -> Void)?

    public init(
        hand: [Domino],
        legalMoves: [Move],
        selectedTile: Binding<Domino?>,
        onPlayTile: ((Domino, Arm?) -> Void)? = nil
    ) {
        self.hand = hand
        self.legalMoves = legalMoves
        self._selectedTile = selectedTile
        self.onPlayTile = onPlayTile
    }

    public var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(hand) { tile in
                    let playable = isTilePlayable(tile)
                    let isSelected = selectedTile == tile

                    DominoTileView(
                        domino: tile,
                        isStanding: true,
                        isPlayable: playable,
                        width: 44
                    )
                    .offset(y: isSelected ? -14 : 0)
                    .animation(.spring(response: 0.3, dampingFraction: 0.7), value: isSelected)
                    .onTapGesture {
                        handleTap(on: tile, playable: playable)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
        .background(
            Felt.railFoot.opacity(0.85)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Felt.brass.opacity(0.3), lineWidth: 1)
                )
        )
    }

    private func isTilePlayable(_ tile: Domino) -> Bool {
        legalMoves.contains { move in
            switch move.type {
            case .play, .give, .playFor:
                return move.domino == tile
            default:
                return false
            }
        }
    }

    private func candidateArms(for tile: Domino) -> [Arm] {
        legalMoves.compactMap { move in
            if move.domino == tile, let arm = move.arm {
                return arm
            }
            return nil
        }
    }

    private func handleTap(on tile: Domino, playable: Bool) {
        guard playable else { return }

        let arms = candidateArms(for: tile)
        if arms.count <= 1 {
            // Only one place to put it (or opening double)
            SoundManager.shared.playClack()
            onPlayTile?(tile, arms.first)
            selectedTile = nil
        } else {
            // Multiple arms available: select tile so user taps desired arm on board
            if selectedTile == tile {
                selectedTile = nil
            } else {
                selectedTile = tile
                SoundManager.shared.playSlide()
            }
        }
    }
}
