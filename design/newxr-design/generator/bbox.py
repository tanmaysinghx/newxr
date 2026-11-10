"""Bounding box of SVG path data (absolute/relative M L H V C S Q T A Z), by sampling."""
import math
import re

_tok = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|-?(?:\d+\.?\d*|\.\d+)(?:e-?\d+)?")


def _arc_points(x1, y1, rx, ry, phi, fa, fs, x2, y2, n=24):
    if rx == 0 or ry == 0:
        return [(x2, y2)]
    phi = math.radians(phi)
    c, s = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = c * dx + s * dy, -s * dx + c * dy
    rx, ry = abs(rx), abs(ry)
    lam = x1p ** 2 / rx ** 2 + y1p ** 2 / ry ** 2
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx ** 2 * ry ** 2 - rx ** 2 * y1p ** 2 - ry ** 2 * x1p ** 2
    den = rx ** 2 * y1p ** 2 + ry ** 2 * x1p ** 2
    co = math.sqrt(max(0, num / den)) if den else 0
    if fa == fs:
        co = -co
    cxp, cyp = co * rx * y1p / ry, -co * ry * x1p / rx
    cx = c * cxp - s * cyp + (x1 + x2) / 2
    cy = s * cxp + c * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a
    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not fs and dt > 0:
        dt -= 2 * math.pi
    elif fs and dt < 0:
        dt += 2 * math.pi
    pts = []
    for i in range(1, n + 1):
        t = t1 + dt * i / n
        pts.append((cx + rx * math.cos(t) * c - ry * math.sin(t) * s, cy + rx * math.cos(t) * s + ry * math.sin(t) * c))
    return pts


def bbox(d):
    toks = _tok.findall(d)
    i = 0
    x = y = sx = sy = 0.0
    pts = []
    cmd = None
    lastc = None
    prev_ctrl = None

    def num():
        nonlocal i
        v = float(toks[i])
        i += 1
        return v
    while i < len(toks):
        if toks[i].isalpha():
            cmd = toks[i]
            i += 1
            if cmd in "Zz":
                x, y = sx, sy
                pts.append((x, y))
                continue
        rel = cmd.islower()
        C = cmd.upper()
        ox, oy = (x, y) if rel else (0, 0)
        if C == "M":
            x, y = ox + num(), oy + num()
            sx, sy = x, y
            pts.append((x, y))
            cmd = "l" if rel else "L"
        elif C == "L":
            x, y = ox + num(), oy + num()
            pts.append((x, y))
        elif C == "H":
            x = (x if rel else 0) + num()
            pts.append((x, y))
        elif C == "V":
            y = (y if rel else 0) + num()
            pts.append((x, y))
        elif C in "CSQT":
            n = {"C": 3, "S": 2, "Q": 2, "T": 1}[C]
            ctrl = [(ox + num(), oy + num()) for _ in range(n)]
            p0 = (x, y)
            if C == "C":
                p1, p2, p3 = ctrl
            elif C == "S":
                p1 = (2 * x - prev_ctrl[0], 2 * y - prev_ctrl[1]) if prev_ctrl and lastc in "CS" else (x, y)
                p2, p3 = ctrl
            elif C == "Q":
                q, p3 = ctrl
                p1 = (p0[0] + 2 / 3 * (q[0] - p0[0]), p0[1] + 2 / 3 * (q[1] - p0[1]))
                p2 = (p3[0] + 2 / 3 * (q[0] - p3[0]), p3[1] + 2 / 3 * (q[1] - p3[1]))
            else:
                p3 = ctrl[0]
                p1 = p2 = p0
            for k in range(1, 17):
                t = k / 16
                mt = 1 - t
                pts.append((mt ** 3 * p0[0] + 3 * mt * mt * t * p1[0] + 3 * mt * t * t * p2[0] + t ** 3 * p3[0],
                            mt ** 3 * p0[1] + 3 * mt * mt * t * p1[1] + 3 * mt * t * t * p2[1] + t ** 3 * p3[1]))
            prev_ctrl = p2
            x, y = p3
        elif C == "A":
            rx, ry, phi = num(), num(), num()
            fa, fs = int(num()), int(num())
            nx, ny = ox + num(), oy + num()
            pts += _arc_points(x, y, rx, ry, phi, fa, fs, nx, ny)
            x, y = nx, ny
        lastc = C
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    return min(xs), min(ys), max(xs), max(ys)
