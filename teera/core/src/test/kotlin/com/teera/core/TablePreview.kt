package com.teera.core

import java.io.File
import kotlin.test.Test

/**
 * Draws a table to an SVG so the look of it can be reviewed without a phone.
 *
 * A tool rather than a test: it asserts nothing about taste, only that the geometry
 * renders. The picture it writes is what gets looked at, and the Compose screen is
 * built to match it.
 */
class TablePreview {

    @Test
    fun `render a table in play`() {
        val board = openedBoard(d(6, 6), d(6, 3), d(6, 2), d(6, 1), d(6, 0))
            .place(d(3, 5), Arm.NORTH)
            .place(d(5, 5), Arm.NORTH)
            .place(d(2, 4), Arm.EAST)
            .place(d(1, 1), Arm.SOUTH)
            .place(d(0, 6), Arm.WEST)
            .place(d(6, 4), Arm.WEST)

        val hand = listOf(d(6, 6), d(5, 4), d(4, 3), d(3, 3), d(2, 0), d(1, 5), d(0, 0))
        val out = File(System.getProperty("teera.preview.dir") ?: "build/preview").apply { mkdirs() }

        File(out, "table.svg").writeText(
            Preview.screen(board, hand, playable = setOf(d(5, 4), d(3, 3), d(1, 5))),
        )
        File(out, "choosing.svg").writeText(
            Preview.screen(
                board,
                hand,
                playable = setOf(d(5, 4), d(3, 3), d(1, 5)),
                chosen = d(5, 4),
                liveArms = setOf(Arm.NORTH, Arm.WEST),
            ),
        )
        println("PREVIEW=${out.absolutePath}")
    }
}

/** The look of the table, written once here and matched in the Compose screen. */
object Preview {
    const val FELT_DARK = "#0B3119"
    const val FELT_MID = "#14532D"
    const val FELT_LIGHT = "#1E7340"
    const val RAIL = "#2A1C10"
    const val RAIL_EDGE = "#4A3B22"
    const val IVORY = "#FBF6EC"
    const val IVORY_EDGE = "#D8CCB6"
    const val BRASS = "#E0AE4E"
    const val PARCHMENT = "#F3EDE1"
    const val MUTED = "#9DB0A2"
    const val PLAYABLE = "#8DE8A8"

    private const val W = 400
    private const val H = 860
    private const val RAIL_H = 92
    private const val SEATS_H = 72
    private const val HAND_H = 150

    fun screen(
        board: Board,
        hand: List<Domino>,
        playable: Set<Domino>,
        chosen: Domino? = null,
        liveArms: Set<Arm> = emptySet(),
    ): String = buildString {
        val boardTop = RAIL_H + SEATS_H
        val boardH = H - boardTop - HAND_H

        append("""<svg xmlns="http://www.w3.org/2000/svg" width="$W" height="$H" viewBox="0 0 $W $H">""")
        append(defs())
        append("""<rect width="$W" height="$H" fill="$FELT_MID"/>""")

        // The table: felt with the light falling in the middle and the corners dropping away.
        append("""<rect y="$boardTop" width="$W" height="$boardH" fill="url(#felt)"/>""")
        append("""<rect y="$boardTop" width="$W" height="$boardH" fill="url(#vignette)"/>""")

        append(rail())
        append(seats())
        append(table(board, boardTop.toFloat(), boardH.toFloat(), liveArms))
        append(hand(hand, playable, chosen))
        append("</svg>")
    }

    private fun defs() = buildString {
        append("<defs>")
        append("""<radialGradient id="felt" cx="50%" cy="42%" r="72%">""")
        append("""<stop offset="0%" stop-color="$FELT_LIGHT"/>""")
        append("""<stop offset="60%" stop-color="$FELT_MID"/>""")
        append("""<stop offset="100%" stop-color="$FELT_DARK"/></radialGradient>""")
        append("""<radialGradient id="vignette" cx="50%" cy="45%" r="75%">""")
        append("""<stop offset="60%" stop-color="#000" stop-opacity="0"/>""")
        append("""<stop offset="100%" stop-color="#000" stop-opacity="0.45"/></radialGradient>""")
        // A bone: warm face, a lighter top edge and a shaded foot, like a real tile.
        append("""<linearGradient id="bone" x1="0" y1="0" x2="0.25" y2="1">""")
        append("""<stop offset="0%" stop-color="#FFFCF5"/>""")
        append("""<stop offset="55%" stop-color="$IVORY"/>""")
        append("""<stop offset="100%" stop-color="$IVORY_EDGE"/></linearGradient>""")
        append("""<linearGradient id="back" x1="0" y1="0" x2="0.3" y2="1">""")
        append("""<stop offset="0%" stop-color="#8E2727"/><stop offset="100%" stop-color="#5A1414"/>""")
        append("""</linearGradient>""")
        append("""<linearGradient id="rail" x1="0" y1="0" x2="0" y2="1">""")
        append("""<stop offset="0%" stop-color="#3A2715"/><stop offset="100%" stop-color="#211509"/>""")
        append("""</linearGradient>""")
        append("""<filter id="glow" x="-60%" y="-60%" width="220%" height="220%">""")
        append("""<feGaussianBlur stdDeviation="6" result="b"/><feMerge><feMergeNode in="b"/>""")
        append("""<feMergeNode in="SourceGraphic"/></feMerge></filter>""")
        append("</defs>")
    }

    /** Scores read as a race you want to lose, so the lower number is the one in front. */
    private fun rail() = buildString {
        append("""<rect width="$W" height="$RAIL_H" fill="url(#rail)"/>""")
        append("""<rect y="${RAIL_H - 2}" width="$W" height="2" fill="$BRASS" opacity="0.35"/>""")

        append(scorePlate(14f, 16f, "نحن", 24, leading = true))
        append(scorePlate(120f, 16f, "هم", 61, leading = false))

        append(label(W - 16f, 34f, "جيم 4", MUTED, 13, "end"))
        append(label(W - 16f, 58f, "المباراة لـ 201", BRASS, 15, "end"))
        append(label(W - 16f, 78f, "اللي يوصل يخسر", MUTED, 10, "end"))
    }

    private fun scorePlate(x: Float, y: Float, who: String, score: Int, leading: Boolean) = buildString {
        val colour = if (leading) PLAYABLE else "#E8907F"
        append("""<rect x="$x" y="$y" width="96" height="58" rx="12" fill="#000" opacity="0.28"/>""")
        append(label(x + 14, y + 24, who, MUTED, 12))
        append(label(x + 14, y + 48, "$score", colour, 24))
    }

    private fun seats() = buildString {
        val names = listOf(Triple("سارة", 4, false), Triple("عمر", 6, true), Triple("نور", 3, false))
        names.forEachIndexed { index, (name, tiles, acting) ->
            val cx = 70f + index * 130f
            val cy = RAIL_H + 30f
            if (acting) {
                append("""<circle cx="$cx" cy="$cy" r="25" fill="none" stroke="$BRASS" stroke-width="2" filter="url(#glow)"/>""")
            }
            append("""<circle cx="$cx" cy="$cy" r="20" fill="#0B3119" stroke="${if (acting) BRASS else "#2E5B3F"}" stroke-width="1.5"/>""")
            append(label(cx, cy + 6, name.take(1), if (acting) BRASS else PARCHMENT, 17, "middle"))
            // How many bones they are holding, which is public in Teera.
            append("""<rect x="${cx + 11}" y="${cy + 8}" width="24" height="18" rx="9" fill="$RAIL" stroke="$BRASS" stroke-opacity="0.5"/>""")
            append(label(cx + 23, cy + 21, "$tiles", PARCHMENT, 11, "middle"))
            append(label(cx, cy + 40, name, if (acting) BRASS else MUTED, 12, "middle"))
        }
    }

    private fun table(board: Board, top: Float, height: Float, liveArms: Set<Arm>) = buildString {
        val layout = BoardLayout.compute(board)
        if (layout.placements.isEmpty()) return@buildString

        // Leave room for the targets, which sit a little beyond the ends of the arms.
        val pad = 14f
        val reach = BoardLayout.TILE_LENGTH / 2f + 0.5f
        val unit = minOf(
            (W - pad * 2) / (layout.width + reach * 2),
            (height - pad * 2) / (layout.height + reach * 2),
        )
        val originX = (W - layout.width * unit) / 2f - layout.minX * unit
        val originY = top + (height - layout.height * unit) / 2f - layout.minY * unit

        for (p in layout.placements) {
            val left = if (p.arm == Arm.WEST || p.arm == Arm.NORTH) p.outer else p.inner
            val right = if (p.arm == Arm.WEST || p.arm == Arm.NORTH) p.inner else p.outer
            val cx = originX + p.centreX * unit
            val cy = originY + p.centreY * unit
            val w = BoardLayout.TILE_LENGTH * unit * 0.97f
            val h = BoardLayout.TILE_WIDTH * unit * 0.97f
            append("""<g transform="translate($cx,$cy) rotate(${p.rotationDegrees})">""")
            append(bone(-w / 2f, -h / 2f, w, h, left, right, vertical = false))
            if (p.isCentre) {
                append("""<rect x="${-w / 2f}" y="${-h / 2f}" width="$w" height="$h" rx="${h * 0.16f}" """)
                append("""fill="none" stroke="$BRASS" stroke-opacity="0.75" stroke-width="2"/>""")
            }
            append("</g>")
        }

        // Where a chosen tile can go: a target big enough to hit with a thumb.
        for (arm in liveArms) {
            val last = layout.placements.lastOrNull { it.arm == arm } ?: continue
            val reach = BoardLayout.TILE_LENGTH / 2f + 0.5f
            val (ax, ay) = when (arm) {
                Arm.EAST -> (last.centreX + reach) to last.centreY
                Arm.WEST -> (last.centreX - reach) to last.centreY
                Arm.NORTH -> last.centreX to (last.centreY - reach)
                Arm.SOUTH -> last.centreX to (last.centreY + reach)
            }
            val cx = originX + ax * unit
            val cy = originY + ay * unit
            append("""<circle cx="$cx" cy="$cy" r="${unit * 0.52f}" fill="$PLAYABLE" opacity="0.18"/>""")
            append("""<circle cx="$cx" cy="$cy" r="${unit * 0.34f}" fill="none" stroke="$PLAYABLE" stroke-width="2.5" stroke-dasharray="4 4"/>""")
            append(label(cx, cy + 5, "${last.outer}", PLAYABLE, 15, "middle"))
        }
    }

    private fun hand(hand: List<Domino>, playable: Set<Domino>, chosen: Domino?) = buildString {
        val top = H - HAND_H
        append("""<rect y="$top" width="$W" height="$HAND_H" fill="url(#rail)"/>""")
        append("""<rect y="$top" width="$W" height="2" fill="$BRASS" opacity="0.35"/>""")
        append(label(16f, top + 26f, "دورك — اختر بول", BRASS, 14))

        val tileW = 46f
        val tileH = 92f
        val gap = 5f
        var x = (W - (hand.size * tileW + (hand.size - 1) * gap)) / 2f
        val base = top + 44f

        hand.forEach { bone ->
            val live = bone in playable
            val picked = bone == chosen
            // A playable bone stands a little proud of the rest, the way you push one
            // forward before you lay it.
            val y = base - if (picked) 14f else if (live) 7f else 0f
            if (picked) {
                append("""<rect x="${x - 3}" y="${y - 3}" width="${tileW + 6}" height="${tileH + 6}" rx="10" fill="$BRASS" opacity="0.3" filter="url(#glow)"/>""")
            }
            append(bone(x, y, tileW, tileH, bone.low, bone.high, vertical = true, faded = !live))
            if (live && !picked) {
                append("""<rect x="$x" y="$y" width="$tileW" height="$tileH" rx="8" fill="none" stroke="$PLAYABLE" stroke-opacity="0.8" stroke-width="2"/>""")
            }
            if (picked) {
                append("""<rect x="$x" y="$y" width="$tileW" height="$tileH" rx="8" fill="none" stroke="$BRASS" stroke-width="2.5"/>""")
            }
            x += tileW + gap
        }
    }

    private fun label(x: Float, y: Float, body: String, colour: String, size: Int, anchor: String = "start") =
        """<text x="$x" y="$y" fill="$colour" font-size="$size" font-family="sans-serif" font-weight="600" text-anchor="$anchor">$body</text>"""

    private fun label(x: Int, y: Int, body: String, colour: String, size: Int, anchor: String = "start") =
        label(x.toFloat(), y.toFloat(), body, colour, size, anchor)

    /** One bone, with the bevel and the drilled spots that make it read as a real tile. */
    fun bone(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        faceA: Int,
        faceB: Int,
        vertical: Boolean,
        faded: Boolean = false,
    ): String = buildString {
        val r = minOf(w, h) * 0.17f
        append("""<g${if (faded) """ opacity="0.82"""" else ""}>""")
        append("""<rect x="$x" y="${y + h * 0.035f}" width="$w" height="$h" rx="$r" fill="#000" opacity="0.3"/>""")
        append("""<rect x="$x" y="$y" width="$w" height="$h" rx="$r" fill="url(#bone)"/>""")
        // The bevel: a bright lip along the top, a soft shadow along the foot.
        append("""<rect x="${x + 1}" y="${y + 1}" width="${w - 2}" height="${h - 2}" rx="${r - 1}" fill="none" stroke="#FFF" stroke-opacity="0.65" stroke-width="1"/>""")
        append("""<rect x="$x" y="$y" width="$w" height="$h" rx="$r" fill="none" stroke="#6B5B44" stroke-opacity="0.35" stroke-width="1"/>""")

        val divider = minOf(w, h) * 0.05f
        if (vertical) {
            append("""<line x1="${x + w * 0.14f}" y1="${y + h / 2}" x2="${x + w * 0.86f}" y2="${y + h / 2}" stroke="#6B5B44" stroke-opacity="0.45" stroke-width="$divider" stroke-linecap="round"/>""")
            append(face(faceA, x, y, w, h / 2f))
            append(face(faceB, x, y + h / 2f, w, h / 2f))
        } else {
            append("""<line x1="${x + w / 2}" y1="${y + h * 0.14f}" x2="${x + w / 2}" y2="${y + h * 0.86f}" stroke="#6B5B44" stroke-opacity="0.45" stroke-width="$divider" stroke-linecap="round"/>""")
            append(face(faceA, x, y, w / 2f, h))
            append(face(faceB, x + w / 2f, y, w / 2f, h))
        }
        append("</g>")
    }

    private fun face(value: Int, x: Float, y: Float, w: Float, h: Float): String = buildString {
        val radius = minOf(w, h) * 0.095f
        val colour = "#%06X".format(TileArt.colourOf(value))
        TileArt.pipsOf(value).forEach { pip ->
            val cx = x + pip.x * w
            val cy = y + pip.y * h
            append("""<circle cx="$cx" cy="${cy + radius * 0.22f}" r="${radius * 1.1f}" fill="#4A3B22" opacity="0.3"/>""")
            append("""<circle cx="$cx" cy="$cy" r="$radius" fill="$colour"/>""")
            append("""<circle cx="${cx - radius * 0.28f}" cy="${cy - radius * 0.3f}" r="${radius * 0.32f}" fill="#FFF" opacity="0.35"/>""")
        }
    }
}
