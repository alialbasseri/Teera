import SwiftUI

public extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}

public enum Felt {
    // Felt surfaces
    public static let deep = Color(hex: 0x07301C)
    public static let table = Color(hex: 0x0F4A2A)
    public static let tableLight = Color(hex: 0x1A6B3C)

    // Walnut cabinet & gold accents
    public static let rail = Color(hex: 0x3A2616)
    public static let railTop = Color(hex: 0x55371F)
    public static let railFoot = Color(hex: 0x1E1309)
    public static let goldDeep = Color(hex: 0xA67C2E)
    public static let goldLit = Color(hex: 0xF0D79A)
    public static let brass = Color(hex: 0xD9B166)
    public static let brassDim = Color(hex: 0x8A6A2A)
    public static let muted = Color(hex: 0xA79274)

    // Ivory bone tones
    public static let ivory = Color(hex: 0xFBF6EC)
    public static let ivoryLit = Color(hex: 0xFFFFFCF5)
    public static let ivoryEdge = Color(hex: 0xD8CCB6)
    public static let ivoryShade = Color(hex: 0xE2D8C6)
    public static let pip = Color(hex: 0x2F2A24)
    public static let pipShadow = Color(hex: 0x4A3B22, alpha: 0.35)
    public static let boneEdge = Color(hex: 0x6B5B44)
    public static let tileBack = Color(hex: 0x8E2727)
    public static let tileBackEdge = Color(hex: 0x5A1414)

    // UI Accents
    public static let ink = Color(hex: 0x14110C)
    public static let parchment = Color(hex: 0xFBF7EF)
    public static let danger = Color(hex: 0xC0392B)
    public static let playable = Color(hex: 0x8DE8A8)
    public static let behind = Color(hex: 0xE8907F)
}

public struct FontName {
    public static let ruqaa = "ArefRuqaa-Bold"
    public static let ruqaaRegular = "ArefRuqaa-Regular"
    public static let messiri = "ElMessiri-Bold"
    public static let messiriMedium = "ElMessiri-Medium"
}
