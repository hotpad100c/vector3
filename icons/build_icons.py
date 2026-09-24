
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import pathops
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.basePen import BasePen
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.svgLib.path import parse_path
from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
SVG_DIR = ROOT / "svg"
OUTPUT = ROOT.parent / "src/main/resources/assets/vector3/ui/icons.ttf"
PREVIEW = ROOT / "preview.png"

# Same metrics as Flashback's Material Icons font, so both line up when merged.
UPEM = 512
SCALE = UPEM / 24
INHERITED = ("fill", "fill-rule", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-dasharray")
CAPS = {"butt": pathops.LineCap.BUTT_CAP, "round": pathops.LineCap.ROUND_CAP, "square": pathops.LineCap.SQUARE_CAP}
JOINS = {"miter": pathops.LineJoin.MITER_JOIN, "round": pathops.LineJoin.ROUND_JOIN, "bevel": pathops.LineJoin.BEVEL_JOIN}
WHITE = {"#fff", "#ffffff", "white"}


def local(tag):
    return tag.rsplit("}", 1)[-1]


def numbers(text):
    return [float(n) for n in re.findall(r"-?\d*\.?\d+(?:e-?\d+)?", text or "")]


def ellipse_d(cx, cy, rx, ry):
    return (f"M{cx - rx},{cy} A{rx},{ry} 0 1 0 {cx + rx},{cy} "
            f"A{rx},{ry} 0 1 0 {cx - rx},{cy} Z")


def element_d(tag, a):
    f = lambda key, default=0.0: float(a.get(key, default))
    if tag == "path":
        return a.get("d", "")
    if tag == "rect":
        x, y, w, h = f("x"), f("y"), f("width"), f("height")
        rx = min(f("rx", a.get("ry", 0)), w / 2)
        ry = min(f("ry", a.get("rx", 0)), h / 2)
        if rx == 0 and ry == 0:
            return f"M{x},{y} H{x + w} V{y + h} H{x} Z"
        return (f"M{x + rx},{y} H{x + w - rx} A{rx},{ry} 0 0 1 {x + w},{y + ry} V{y + h - ry} "
                f"A{rx},{ry} 0 0 1 {x + w - rx},{y + h} H{x + rx} A{rx},{ry} 0 0 1 {x},{y + h - ry} "
                f"V{y + ry} A{rx},{ry} 0 0 1 {x + rx},{y} Z")
    if tag == "circle":
        return ellipse_d(f("cx"), f("cy"), f("r"), f("r"))
    if tag == "ellipse":
        return ellipse_d(f("cx"), f("cy"), f("rx"), f("ry"))
    if tag == "line":
        return f"M{f('x1')},{f('y1')} L{f('x2')},{f('y2')}"
    if tag in ("polyline", "polygon"):
        pts = numbers(a.get("points"))
        d = "M" + " L".join(f"{pts[i]},{pts[i + 1]}" for i in range(0, len(pts) - 1, 2))
        return d + (" Z" if tag == "polygon" else "")
    return None


def to_path(d, attrs, painting):
    """The element's fill or stroke outline, in font units."""
    path = pathops.Path()
    parse_path(d, TransformPen(path.getPen(), (SCALE, 0, 0, -SCALE, 0, UPEM)))
    if painting == "stroke":
        dashes = [n * SCALE for n in numbers(attrs.get("stroke-dasharray"))] or None
        path.stroke(float(attrs.get("stroke-width", 1)) * SCALE, CAPS[attrs.get("stroke-linecap", "butt")],
                    JOINS[attrs.get("stroke-linejoin", "miter")], 4, dashes)
        path.convertConicsToQuads()  # round caps and joins come out as conics, which ops can't take
    elif attrs.get("fill-rule") == "evenodd":
        path.fillType = pathops.FillType.EVEN_ODD
    return pathops.simplify(path)


def glyph_path(svg_file):
    result = pathops.Path()

    def visit(node, inherited):
        attrs = dict(inherited)
        attrs.update({k: v for k, v in node.attrib.items() if k in INHERITED})
        tag = local(node.tag)
        if "transform" in node.attrib:
            print(f"warning: {svg_file.name}: transform on <{tag}> is ignored", file=sys.stderr)
        d = element_d(tag, node.attrib)
        if d:
            for painting in ("fill", "stroke"):
                paint = attrs.get(painting, "#000" if painting == "fill" else "none").strip().lower()
                if paint == "none" or (painting == "fill" and tag in ("line", "polyline")):
                    continue
                shape = to_path(d, attrs, painting)
                op = pathops.PathOp.DIFFERENCE if paint in WHITE else pathops.PathOp.UNION
                nonlocal result
                result = pathops.op(result, shape, op)
        for child in node:
            visit(child, attrs)

    visit(ET.parse(svg_file).getroot(), {})
    return pathops.simplify(result, clockwise=True)


class Flattener(BasePen):
    def __init__(self):
        super().__init__(None)
        self.contours, self.current = [], []

    def _moveTo(self, p):
        self.current = [p]

    def _lineTo(self, p):
        self.current.append(p)

    def _curveToOne(self, p1, p2, p3):
        p0 = self.current[-1]
        for i in range(1, 13):
            t = i / 12
            mt = 1 - t
            self.current.append(tuple(mt ** 3 * a + 3 * mt * mt * t * b + 3 * mt * t * t * c + t ** 3 * e
                                      for a, b, c, e in zip(p0, p1, p2, p3)))

    def _qCurveToOne(self, p1, p2):
        p0 = self.current[-1]
        for i in range(1, 9):
            t = i / 8
            mt = 1 - t
            self.current.append(tuple(mt * mt * a + 2 * mt * t * b + t * t * c for a, b, c in zip(p0, p1, p2)))

    def _closePath(self):
        self.contours.append(self.current)
        self.current = []


def render(path, size):
    """Even-odd raster of a simplified path; enough for a preview."""
    pen = Flattener()
    path.draw(pen)
    image = Image.new("L", (size, size), 0)
    for contour in pen.contours:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).polygon([(x * size / UPEM, (UPEM - y) * size / UPEM) for x, y in contour], fill=255)
        image = ImageChops.logical_xor(image.convert("1"), mask.convert("1")).convert("L")
    return image


def main():
    icons = []
    for svg_file in sorted(SVG_DIR.glob("*.svg")):
        match = re.fullmatch(r"([0-9a-fA-F]{4})_(\w+)", svg_file.stem)
        if not match:
            print(f"skipping {svg_file.name}: expected <codepoint>_<name>.svg", file=sys.stderr)
            continue
        icons.append((int(match[1], 16), match[2], glyph_path(svg_file)))

    glyphs = {".notdef": TTGlyphPen(None).glyph()}
    metrics = {".notdef": (UPEM, 0)}
    cmap = {}
    for codepoint, name, path in icons:
        pen = TTGlyphPen(None)
        path.draw(Cu2QuPen(pen, max_err=0.5, reverse_direction=False))
        glyphs[name] = pen.glyph()
        bounds = path.bounds
        metrics[name] = (UPEM, int(bounds[0]) if bounds else 0)
        cmap[codepoint] = name

    builder = FontBuilder(UPEM, isTTF=True)
    builder.setupGlyphOrder(list(glyphs))
    builder.setupCharacterMap(cmap)
    builder.setupGlyf(glyphs)
    builder.setupHorizontalMetrics(metrics)
    builder.setupHorizontalHeader(ascent=UPEM, descent=0)
    builder.setupNameTable({"familyName": "vector3 icons", "styleName": "Regular"})
    builder.setupOS2(sTypoAscender=UPEM, sTypoDescender=0, sTypoLineGap=0, usWinAscent=UPEM, usWinDescent=0)
    builder.setupPost()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    builder.save(OUTPUT)

    columns = 6
    cell = 150
    sheet = Image.new("RGB", (columns * cell, -(-len(icons) // columns) * cell), (40, 40, 44))
    label_font = ImageFont.load_default()
    for i, (codepoint, name, path) in enumerate(icons):
        x, y = i % columns * cell, i // columns * cell
        white = Image.new("RGB", (96, 96), (235, 235, 235))
        sheet.paste(white, (x + 8, y + 8), render(path, 96))
        sheet.paste(white.resize((20, 20)), (x + 112, y + 8), render(path, 20))
        ImageDraw.Draw(sheet).text((x + 8, y + 112), f"U+{codepoint:04X}\n{name}", fill=(200, 200, 200), font=label_font)
    sheet.save(PREVIEW)
    print(f"wrote {len(icons)} icons to {OUTPUT.relative_to(ROOT.parent)} and {PREVIEW.relative_to(ROOT.parent)}")


if __name__ == "__main__":
    main()
