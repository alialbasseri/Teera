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
    const val FELT_DARK = "#07301C"
    const val FELT_MID = "#0F4A2A"
    const val FELT_LIGHT = "#1A6B3C"
    const val WALNUT_TOP = "#55371F"
    const val WALNUT = "#3A2616"
    const val WALNUT_FOOT = "#1E1309"
    const val LAPIS = "#16355F"
    const val IVORY = "#FBF6EC"
    const val IVORY_EDGE = "#D8CCB6"
    const val GOLD = "#D9B166"
    const val GOLD_DEEP = "#A67C2E"
    const val PARCHMENT = "#F3EDE1"
    const val MUTED = "#A79274"
    const val PLAYABLE = "#8DE8A8"
    const val BRASS = GOLD

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
        append("""<rect width="$W" height="$H" fill="$WALNUT_FOOT"/>""")

        // The table: felt laid inside a gilt frame, the way a good board is finished.
        append("""<rect y="$boardTop" width="$W" height="$boardH" fill="url(#walnut)"/>""")
        val inset = 11
        append("""<rect x="$inset" y="${boardTop + inset}" width="${W - inset * 2}" height="${boardH - inset * 2}" rx="8" fill="url(#felt)"/>""")
        append("""<rect x="$inset" y="${boardTop + inset}" width="${W - inset * 2}" height="${boardH - inset * 2}" rx="8" fill="url(#weave)"/>""")
        append("""<rect x="$inset" y="${boardTop + inset}" width="${W - inset * 2}" height="${boardH - inset * 2}" rx="8" fill="url(#vignette)"/>""")
        append(frame(inset.toFloat(), boardTop + inset.toFloat(), (W - inset * 2).toFloat(), (boardH - inset * 2).toFloat()))

        append(rail())
        append(seats())
        append(table(board, boardTop.toFloat(), boardH.toFloat(), liveArms))
        append(hand(hand, playable, chosen))
        append("</svg>")
    }

    /** Amiri: a classical naskh, and the single biggest change to how the game reads. */
    private val FONT = """<style>@font-face{font-family:'Amiri';src:url('file://%s') format('truetype');}
        text{font-family:'Amiri',serif;}</style>""".format(
        System.getProperty("teera.font") ?: "",
    )

    private fun defs() = buildString {
        append("<defs>")
        append(FONT)
        // A rosette from the glazed brick of the Ishtar gate, used along the frame.
        append("""<g id="rosette">""")
        (0 until 8).forEach { i ->
            append("""<ellipse cx="0" cy="-5.2" rx="2.0" ry="5.2" fill="$GOLD" transform="rotate(${i * 45})"/>""")
        }
        append("""<circle r="2.1" fill="$GOLD_DEEP"/></g>""")
        append("""<pattern id="weave" width="7" height="7" patternUnits="userSpaceOnUse">""")
        append("""<rect width="7" height="7" fill="none"/>""")
        append("""<path d="M0 7 L7 0" stroke="#000" stroke-opacity="0.07" stroke-width="1.4"/>""")
        append("""<path d="M0 0 L7 7" stroke="#FFF" stroke-opacity="0.035" stroke-width="1.4"/></pattern>""")
        append("""<linearGradient id="gold" x1="0" y1="0" x2="0" y2="1">""")
        append("""<stop offset="0%" stop-color="#F0D79A"/><stop offset="45%" stop-color="$GOLD"/>""")
        append("""<stop offset="100%" stop-color="$GOLD_DEEP"/></linearGradient>""")
        append("""<linearGradient id="walnut" x1="0" y1="0" x2="0" y2="1">""")
        append("""<stop offset="0%" stop-color="$WALNUT_TOP"/><stop offset="55%" stop-color="$WALNUT"/>""")
        append("""<stop offset="100%" stop-color="$WALNUT_FOOT"/></linearGradient>""")
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

        append("""<filter id="glow" x="-60%" y="-60%" width="220%" height="220%">""")
        append("""<feGaussianBlur stdDeviation="6" result="b"/><feMerge><feMergeNode in="b"/>""")
        append("""<feMergeNode in="SourceGraphic"/></feMerge></filter>""")
        append("</defs>")
    }

    /** Scores read as a race you want to lose, so the lower number is the one in front. */
    /** A hairline of gold inside the frame, with a rosette at each corner. */
    private fun frame(x: Float, y: Float, w: Float, h: Float) = buildString {
        append("""<rect x="${x + 3}" y="${y + 3}" width="${w - 6}" height="${h - 6}" rx="6" fill="none" stroke="url(#gold)" stroke-width="1.6" opacity="0.9"/>""")
        append("""<rect x="${x + 7}" y="${y + 7}" width="${w - 14}" height="${h - 14}" rx="4" fill="none" stroke="$GOLD_DEEP" stroke-width="0.8" opacity="0.6"/>""")
        listOf(x + 16 to y + 16, x + w - 16 to y + 16, x + 16 to y + h - 16, x + w - 16 to y + h - 16)
            .forEach { (cx, cy) -> append("""<use href="#rosette" transform="translate($cx,$cy) scale(1.15)"/>""") }
        // A row of smaller rosettes along the top and bottom rails of the frame.
        var rx = x + 58
        while (rx < x + w - 50) {
            append("""<use href="#rosette" transform="translate($rx,${y + 9}) scale(0.55)" opacity="0.75"/>""")
            append("""<use href="#rosette" transform="translate($rx,${y + h - 9}) scale(0.55)" opacity="0.75"/>""")
            rx += 42
        }
    }

    private fun rail() = buildString {
        append("""<rect width="$W" height="$RAIL_H" fill="url(#walnut)"/>""")
        append("""<rect y="${RAIL_H - 3}" width="$W" height="1.5" fill="url(#gold)" opacity="0.85"/>""")

        append(scorePlate(14f, 16f, "نحن", 24, leading = true))
        append(scorePlate(120f, 16f, "هم", 61, leading = false))

        append(label(W - 16f, 34f, "جيم 4", MUTED, 13, "end"))
        append(label(W - 16f, 58f, "المباراة لـ 201", BRASS, 15, "end"))
        append(label(W - 16f, 78f, "اللي يوصل يخسر", MUTED, 10, "end"))
    }

    /** An engraved brass plaque, the way a score is kept on a good table. */
    private fun scorePlate(x: Float, y: Float, who: String, score: Int, leading: Boolean) = buildString {
        val colour = if (leading) PLAYABLE else "#E8907F"
        append("""<rect x="$x" y="$y" width="98" height="60" rx="8" fill="#120B05" opacity="0.55"/>""")
        append("""<rect x="${x + 1.5f}" y="${y + 1.5f}" width="95" height="57" rx="7" fill="none" stroke="url(#gold)" stroke-width="1.2" opacity="0.8"/>""")
        append(label(x + 14, y + 25, who, MUTED, 15))
        append(label(x + 14, y + 50, "$score", colour, 26))
    }

    private fun seats() = buildString {
        val names = listOf(Triple("سارة", 4, false), Triple("عمر", 6, true), Triple("نور", 3, false))
        names.forEachIndexed { index, (name, tiles, acting) ->
            val cx = 70f + index * 130f
            val cy = RAIL_H + 30f
            if (acting) {
                append("""<circle cx="$cx" cy="$cy" r="25" fill="none" stroke="$BRASS" stroke-width="2" filter="url(#glow)"/>""")
            }
            append("""<circle cx="$cx" cy="$cy" r="21" fill="$LAPIS"/>""")
            append("""<circle cx="$cx" cy="$cy" r="21" fill="none" stroke="url(#gold)" stroke-width="${if (acting) 2.2 else 1.2}" opacity="${if (acting) 1 else 0.65}"/>""")
            append(label(cx, cy + 6, name.take(1), if (acting) BRASS else PARCHMENT, 17, "middle"))
            // How many bones they are holding, which is public in Teera.
            append("""<rect x="${cx + 11}" y="${cy + 8}" width="24" height="18" rx="9" fill="$WALNUT" stroke="$GOLD" stroke-opacity="0.6"/>""")
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
        append("""<rect y="$top" width="$W" height="$HAND_H" fill="url(#walnut)"/>""")
        append("""<rect y="$top" width="$W" height="1.5" fill="url(#gold)" opacity="0.85"/>""")
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
