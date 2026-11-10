import SwiftUI

public struct Pip: Hashable {
    public let x: CGFloat
    public let y: CGFloat

    public init(_ x: CGFloat, _ y: CGFloat) {
        self.x = x
        self.y = y
    }
}

public enum TileArt {
    private static let NEAR: CGFloat = 0.26
    private static let MID: CGFloat = 0.50
    private static let FAR: CGFloat = 0.74

    public static func colourOf(_ value: Int) -> Color {
        switch value {
        case 1: return Color(hex: 0x35A8DE) // Light blue
        case 2: return Color(hex: 0x1F7A3A) // Dark green
        case 3: return Color(hex: 0xC9437F) // Pink
        case 4: return Color(hex: 0x8C8176) // Taupe
        case 5: return Color(hex: 0x2B4C9B) // Navy
        case 6: return Color(hex: 0xD9A21B) // Yellow
        default: return Color(hex: 0x2F2A24) // Dark ivory spot
        }
    }

    public static func pipsOf(_ value: Int) -> [Pip] {
        switch value {
        case 0: return []
        case 1: return [Pip(MID, MID)]
        case 2: return [Pip(NEAR, NEAR), Pip(FAR, FAR)]
        case 3: return [Pip(NEAR, NEAR), Pip(MID, MID), Pip(FAR, FAR)]
        case 4: return [Pip(NEAR, NEAR), Pip(FAR, NEAR), Pip(NEAR, FAR), Pip(FAR, FAR)]
        case 5: return [Pip(NEAR, NEAR), Pip(FAR, NEAR), Pip(MID, MID), Pip(NEAR, FAR), Pip(FAR, FAR)]
        case 6: return [
            Pip(NEAR, 0.20), Pip(NEAR, MID), Pip(NEAR, 0.80),
            Pip(FAR, 0.20), Pip(FAR, MID), Pip(FAR, 0.80)
        ]
        default: return []
        }
    }
}
