#!/usr/bin/env python3
"""
Builds the app's text face: El Messiri's Arabic with Readex Pro's Latin letters and digits.

El Messiri's own Latin did not sit well beside its Arabic; Readex Pro's was drawn to keep
company with Arabic. Each Latin glyph is redrawn from Readex Pro into El Messiri, scaled so
the capitals stand as tall as El Messiri's, and the result is renamed, as the Open Font
Licence asks of a modified font.

    python3.13 tools/fonts/merge_latin.py <ElMessiri[wght].ttf> <ReadexPro[HEXP,wght].ttf> app/src/main/res/font
"""
import sys
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.recordingPen import DecomposingRecordingPen

# Printable ASCII, the middle dot, the minus sign and a few marks the app shows.
CODEPOINTS = list(range(0x21, 0x7F)) + [0xB7, 0x2212, 0x2013, 0x2014, 0x2026, 0xD7]


def build(messiri_path, readex_path, weight, out_path, style):
    arabic = instancer.instantiateVariableFont(TTFont(messiri_path), {"wght": weight})
    latin = instancer.instantiateVariableFont(TTFont(readex_path), {"wght": weight + 50, "HEXP": 0})

    cap_arabic = arabic["OS/2"].sCapHeight or 0.7 * arabic["head"].unitsPerEm
    cap_latin = latin["OS/2"].sCapHeight or 0.7 * latin["head"].unitsPerEm
    scale = cap_arabic / cap_latin

    cmap_a = arabic.getBestCmap()
    cmap_l = latin.getBestCmap()
    glyphs_l = latin.getGlyphSet()
    glyf = arabic["glyf"]
    hmtx = arabic["hmtx"]
    replaced = 0
    for cp in CODEPOINTS:
        name_a = cmap_a.get(cp)
        name_l = cmap_l.get(cp)
        if not name_a or not name_l:
            continue
        # Readex builds accented and some plain letters from parts; flatten them first.
        outline = DecomposingRecordingPen(glyphs_l)
        glyphs_l[name_l].draw(outline)
        pen = TTGlyphPen(None)
        outline.replay(TransformPen(pen, (scale, 0, 0, scale, 0, 0)))
        glyph = pen.glyph()
        glyf[name_a] = glyph
        glyph.recalcBounds(glyf)
        advance = round(latin["hmtx"][name_l][0] * scale)
        hmtx[name_a] = (advance, getattr(glyph, "xMin", 0))
        replaced += 1

    family = "Teera Messiri"
    for record in arabic["name"].names:
        if record.nameID in (1, 16):
            record.string = family
        elif record.nameID == 4:
            record.string = f"{family} {style}"
        elif record.nameID == 6:
            record.string = f"TeeraMessiri-{style}"
        elif record.nameID == 3:
            record.string = f"TeeraMessiri-{style};merged"
    arabic.save(out_path)
    print(f"{out_path}: {replaced} Latin glyphs from Readex Pro, scale {scale:.3f}")


if __name__ == "__main__":
    messiri, readex, out = sys.argv[1:4]
    build(messiri, readex, 500, f"{out}/el_messiri_medium.ttf", "Medium")
    build(messiri, readex, 700, f"{out}/el_messiri_bold.ttf", "Bold")
