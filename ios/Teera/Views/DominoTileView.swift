import SwiftUI

public struct DominoTileView: View {
    public let domino: Domino
    public var isStanding: Bool = true
    public var isFaceDown: Bool = false
    public var isPlayable: Bool = false
    public var width: CGFloat = 48

    public init(
        domino: Domino,
        isStanding: Bool = true,
        isFaceDown: Bool = false,
        isPlayable: Bool = false,
        width: CGFloat = 48
    ) {
        self.domino = domino
        self.isStanding = isStanding
        self.isFaceDown = isFaceDown
        self.isPlayable = isPlayable
        self.width = width
    }

    private var height: CGFloat {
        isStanding ? width * 2.0 : width / 2.0
    }

    public var body: some View {
        ZStack {
            if isFaceDown {
                backView
            } else {
                faceView
            }
        }
        .frame(width: isStanding ? width : height, height: isStanding ? height : width)
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(isPlayable ? Felt.playable : Felt.boneEdge.opacity(0.4), lineWidth: isPlayable ? 2.5 : 1.0)
        )
        .shadow(color: isPlayable ? Felt.playable.opacity(0.6) : Color.black.opacity(0.35),
                radius: isPlayable ? 6 : 3, x: 1, y: 2)
    }

    private var cornerRadius: CGFloat {
        width * 0.12
    }

    private var backView: some View {
        RoundedRectangle(cornerRadius: cornerRadius)
            .fill(
                LinearGradient(
                    colors: [Felt.tileBack, Felt.tileBackEdge],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(Felt.boneEdge, lineWidth: 1)
            )
    }

    private var faceView: some View {
        Canvas { context, size in
            let rect = CGRect(origin: .zero, size: size)
            let path = Path(roundedRect: rect, cornerRadius: cornerRadius)

            // 1. Base ivory background with light gradient
            let gradient = Gradient(colors: [Felt.ivoryLit, Felt.ivory, Felt.ivoryShade])
            context.fill(path, with: .linearGradient(gradient, startPoint: CGPoint(x: 0, y: 0),
                                                    endPoint: CGPoint(x: size.width, y: size.height)))

            // 2. Dividing line & center brass pin
            if isStanding {
                let midY = size.height / 2.0
                var divPath = Path()
                divPath.move(to: CGPoint(x: size.width * 0.08, y: midY))
                divPath.addLine(to: CGPoint(x: size.width * 0.92, y: midY))
                context.stroke(divPath, with: .color(Felt.ivoryEdge), lineWidth: 1.5)

                // Divider shadow
                var divShadow = Path()
                divShadow.move(to: CGPoint(x: size.width * 0.08, y: midY + 1.0))
                divShadow.addLine(to: CGPoint(x: size.width * 0.92, y: midY + 1.0))
                context.stroke(divShadow, with: .color(Felt.ivoryLit), lineWidth: 0.8)

                // Center brass pin
                let pinRect = CGRect(x: size.width / 2.0 - 2, y: midY - 2, width: 4, height: 4)
                context.fill(Path(ellipseIn: pinRect), with: .color(Felt.brass))

                // 3. Draw top and bottom faces
                let topRect = CGRect(x: 0, y: 0, width: size.width, height: midY)
                let bottomRect = CGRect(x: 0, y: midY, width: size.width, height: midY)
                drawFace(in: &context, value: domino.low, rect: topRect, standing: true)
                drawFace(in: &context, value: domino.high, rect: bottomRect, standing: true)
            } else {
                let midX = size.width / 2.0
                var divPath = Path()
                divPath.move(to: CGPoint(x: midX, y: size.height * 0.08))
                divPath.addLine(to: CGPoint(x: midX, y: size.height * 0.92))
                context.stroke(divPath, with: .color(Felt.ivoryEdge), lineWidth: 1.5)

                // Center brass pin
                let pinRect = CGRect(x: midX - 2, y: size.height / 2.0 - 2, width: 4, height: 4)
                context.fill(Path(ellipseIn: pinRect), with: .color(Felt.brass))

                // Draw left and right faces
                let leftRect = CGRect(x: 0, y: 0, width: midX, height: size.height)
                let rightRect = CGRect(x: midX, y: 0, width: midX, height: size.height)
                drawFace(in: &context, value: domino.low, rect: leftRect, standing: false)
                drawFace(in: &context, value: domino.high, rect: rightRect, standing: false)
            }
        }
    }

    private func drawFace(in context: inout GraphicsContext, value: Int, rect: CGRect, standing: Bool) {
        let pips = TileArt.pipsOf(value)
        let pipColor = TileArt.colourOf(value)
        let radius = min(rect.width, rect.height) * 0.10

        for pip in pips {
            let px = standing ? pip.x : pip.y
            let py = standing ? pip.y : pip.x
            let center = CGPoint(x: rect.minX + px * rect.width, y: rect.minY + py * rect.height)

            // Drilled hole shadow
            let shadowCenter = CGPoint(x: center.x, y: center.y + radius * 0.22)
            let shadowRect = CGRect(x: shadowCenter.x - radius * 1.1, y: shadowCenter.y - radius * 1.1,
                                    width: radius * 2.2, height: radius * 2.2)
            context.fill(Path(ellipseIn: shadowRect), with: .color(Felt.pipShadow))

            // Spot color
            let spotRect = CGRect(x: center.x - radius, y: center.y - radius,
                                  width: radius * 2, height: radius * 2)
            context.fill(Path(ellipseIn: spotRect), with: .color(pipColor))

            // Specular light reflection on top-left of hole
            let hlCenter = CGPoint(x: center.x - radius * 0.28, y: center.y - radius * 0.30)
            let hlRadius = radius * 0.32
            let hlRect = CGRect(x: hlCenter.x - hlRadius, y: hlCenter.y - hlRadius,
                                width: hlRadius * 2, height: hlRadius * 2)
            context.fill(Path(ellipseIn: hlRect), with: .color(Color.white.opacity(0.40)))
        }
    }
}
