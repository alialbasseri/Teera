import SwiftUI

public struct BoardView: View {
    public let board: Board
    public var targetArms: [Arm] = []
    public var onArmSelected: ((Arm) -> Void)? = nil

    private let tileSize: CGFloat = 36

    public init(board: Board, targetArms: [Arm] = [], onArmSelected: ((Arm) -> Void)? = nil) {
        self.board = board
        self.targetArms = targetArms
        self.onArmSelected = onArmSelected
    }

    public var body: some View {
        ZStack {
            // Felt table center ornament
            Circle()
                .stroke(Felt.brass.opacity(0.25), lineWidth: 1.5)
                .frame(width: 240, height: 240)

            Circle()
                .stroke(Felt.brass.opacity(0.12), lineWidth: 1)
                .frame(width: 300, height: 300)

            // Centre opening double
            if let centre = board.centre {
                DominoTileView(domino: centre, isStanding: true, width: tileSize)
                    .overlay(
                        RoundedRectangle(cornerRadius: tileSize * 0.12)
                            .stroke(Felt.goldLit, lineWidth: 1.5)
                    )
            } else {
                RoundedRectangle(cornerRadius: tileSize * 0.12)
                    .strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [4]))
                    .foregroundColor(Felt.brass.opacity(0.4))
                    .frame(width: tileSize, height: tileSize * 2)
                    .overlay(
                        Text("6|6")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Felt.brass.opacity(0.6))
                    )
            }

            // North Arm
            armView(for: .NORTH)
                .offset(y: -110)

            // South Arm
            armView(for: .SOUTH)
                .offset(y: 110)

            // West Arm (Left)
            armView(for: .WEST)
                .offset(x: -110)

            // East Arm (Right)
            armView(for: .EAST)
                .offset(x: 110)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @ViewBuilder
    private func armView(for arm: Arm) -> some View {
        let tiles = board.tilesOn(arm)
        let isTarget = targetArms.contains(arm)

        VStack(spacing: 4) {
            if tiles.isEmpty {
                Button {
                    if isTarget { onArmSelected?(arm) }
                } label: {
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(isTarget ? Felt.playable : Felt.brass.opacity(0.2),
                                style: StrokeStyle(lineWidth: isTarget ? 2 : 1, dash: isTarget ? [] : [3]))
                        .background(isTarget ? Felt.playable.opacity(0.2) : Color.clear)
                        .frame(width: tileSize, height: tileSize)
                        .overlay(
                            Image(systemName: armSymbol(for: arm))
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(isTarget ? Felt.playable : Felt.brass.opacity(0.4))
                        )
                }
                .disabled(!isTarget)
            } else {
                // Show the end tile of the arm
                if let lastTile = tiles.last {
                    Button {
                        if isTarget { onArmSelected?(arm) }
                    } label: {
                        DominoTileView(
                            domino: lastTile.domino,
                            isStanding: arm == .NORTH || arm == .SOUTH,
                            isPlayable: isTarget,
                            width: tileSize * 0.85
                        )
                    }
                    .disabled(!isTarget)
                }
            }
        }
    }

    private func armSymbol(for arm: Arm) -> String {
        switch arm {
        case .NORTH: return "arrow.up"
        case .SOUTH: return "arrow.down"
        case .EAST: return "arrow.right"
        case .WEST: return "arrow.left"
        }
    }
}
