"""Tiny DSL that emits a FigCraft `run` script (JSON array of commands)."""
import json
import re
from bbox import bbox

# ---------------------------------------------------------------- Newxr tokens
CHROME = "#14122b"; CHROME_2 = "#1f1c3d"; CTEXT = "#f2f1ff"; CMUTED = "#a6a3c7"
HEADER = CHROME; HTEXT = CTEXT; HMUTED = CMUTED; HPILL = CHROME_2; HACCENT = "#8b80ff"
SURF = "#ffffff"; SUNK = "#f5f6f8"; HOVER = "#f0f1f5"; SEL = "#eeecff"
BORDER = "#e4e6eb"; BSTRONG = "#cdd1d9"
TEXT = "#1d2130"; MUTED = "#5b6170"; SUBTLE = "#8a909e"; LINK = "#4b3fe0"
ACCENT = "#5a4bf5"; ACC_H = "#4536dc"; ACC_SOFT = "#eeecff"
SUCCESS = "#1f8a4c"; SUCCESS_SOFT = "#e3f5ea"; WARN = "#b76e00"; WARN_SOFT = "#fff3dc"
DANGER = "#d23b3b"; DANGER_SOFT = "#fde8e8"; INFO = "#2563c9"; INFO_SOFT = "#e5eefb"
VIOLET = "#7c3aed"; VIOLET_SOFT = "#f1e9ff"
APPS = {  # name: (colour, icon, tagline)
    "Track": ("#5a4bf5", "board", "Plan, track and ship work"),
    "Pages": ("#0e9f8e", "page", "Docs and knowledge for every team"),
    "Boards": ("#e8892b", "kanban", "Visual boards for anything"),
    "Code": ("#2f6fde", "code", "Git repos, pull requests, pipelines"),
    "Desk": ("#d9466f", "headset", "Service desk and help center"),
    "Pulse": ("#e5533d", "bolt", "Alerts, on-call and status pages"),
    "Goals": ("#2f9a57", "target", "Goals and project updates"),
}
PRIO = {1: "#d23b3b", 2: "#e5683d", 3: "#d99a00", 4: "#2563c9", 5: "#8a909e"}
PRIO_LABEL = {1: "Highest", 2: "High", 3: "Medium", 4: "Low", 5: "Lowest"}
S1 = "#5a4bf5"; S2 = "#0e9f8e"; S3 = "#e8892b"; CGRID = "#eef0f3"
NOTE_BG = "#fff8e1"; NOTE_BORDER = "#f2c94c"; COMMENT_BG = "#f5f6f8"; COMMENT_BORDER = "#cdd1d9"
SH1 = [{"type": "dropShadow", "color": "#1018280f", "offset": [0, 1], "radius": 3}]
SH2 = [{"type": "dropShadow", "color": "#10182818", "offset": [0, 6], "radius": 16}]
SH3 = [{"type": "dropShadow", "color": "#1018282e", "offset": [0, 16], "radius": 40}]
FONT = "Inter"; MONO = "DejaVu Sans Mono"

C = []
_n = [0]
LAYOUT = set()


def R(r):
    return {"$ref": r}


def nid():
    _n[0] += 1
    return "n%d" % _n[0]


def cmd(c, p=None, as_=None):
    d = {"command": c, "params": p or {}}
    if as_:
        d["as"] = as_
    C.append(d)


def node(t, parent=None, name=None, w=None, h=None, x=None, y=None, text=None, **props):
    r = nid()
    p = {"type": t, "select": False}
    if parent:
        p["parent"] = R(parent)
    if name:
        p["name"] = name
    if w is not None:
        p["width"] = w
    if h is not None:
        p["height"] = h
    if x is not None:
        p["x"] = x
    if y is not None:
        p["y"] = y
    if text is not None:
        p["text"] = text
    if props:
        p["props"] = props
    cmd("node.create", p, r)
    return r


def setp(ref, **props):
    cmd("node.set", {"ids": [R(ref)], "props": props})


def box(parent=None, d="v", gap=0, pad=0, w=None, h=None, fill=None, name=None, sw=None, sh=None,
        main=None, cross=None, radius=None, stroke=None, sides=None, effects=None, clip=None, wrap=None,
        kind="frame", x=None, y=None, **extra):
    """Frame. d: 'v' | 'h' | None (no auto layout). sw/sh: fill | hug | fixed."""
    props = {"fill": fill if fill else "none"}
    if d:
        props["layout"] = "vertical" if d == "v" else "horizontal"
        props["gap"] = gap
        props["padding"] = pad
        if main:
            props["mainAlign"] = main
        if cross:
            props["crossAlign"] = cross
        if wrap:
            props["wrap"] = True
    in_layout = parent in LAYOUT
    if sw is None:
        sw = "fixed" if w is not None else ("hug" if d else "fixed")
    if sh is None:
        sh = "fixed" if h is not None else ("hug" if d else "fixed")
    if sw == "fill" and not in_layout:
        sw = "fixed"
    if sh == "fill" and not in_layout:
        sh = "fixed"
    if sw == "hug" and not d:
        sw = "fixed"
    if sh == "hug" and not d:
        sh = "fixed"
    props["sizingH"] = sw
    props["sizingV"] = sh
    if radius is not None:
        props["cornerRadius"] = radius
    if stroke:
        props["stroke"] = stroke
        if sides:
            t, r_, b, l = sides
            props.update(strokeTopWeight=t, strokeRightWeight=r_, strokeBottomWeight=b, strokeLeftWeight=l)
        else:
            props["strokeWeight"] = 1
        props["strokeAlign"] = "inside"
    if effects:
        props["effects"] = effects
    if clip is not None:
        props["clip"] = clip
    props.update(extra)
    ref = node(kind, parent, name, w if w is not None else (100 if sw == "fixed" else None),
               h if h is not None else (100 if sh == "fixed" else None), x, y, **props)
    if d:
        LAYOUT.add(ref)
    return ref


def H(parent, gap=8, **kw):
    kw.setdefault("cross", "center")
    return box(parent, "h", gap, **kw)


def V(parent, gap=8, **kw):
    return box(parent, "v", gap, **kw)


def T(parent, s, size=13, wt="Regular", color=TEXT, sw=None, w=None, align=None, name=None, lh=None,
      upper=False, ls=None, mono=False, trunc=False):
    props = {"fontFamily": MONO if mono else FONT, "fontStyle": wt, "fontSize": size, "fill": color}
    if lh:
        props["lineHeight"] = lh
    if align:
        props["textAlign"] = align
    if upper:
        props["textCase"] = "upper"
    if ls is not None:
        props["letterSpacing"] = ls
    width = None
    if sw == "fill" and parent in LAYOUT:
        props["sizingH"] = "fill"
        props["textAutoResize"] = "truncate" if trunc else "height"
    elif w:
        width = w
        props["textAutoResize"] = "truncate" if trunc else "height"
    if trunc:
        props["maxLines"] = 1
    return node("text", parent, name or s[:40], w=width, text=s, **props)


# ---------------------------------------------------------------- icons (frontend/src/app/shared/ui/icon/icons.ts)
ICONS = {}


def _num_tokens(d):
    return re.findall(r"[MmLlHhVvCcSsQqTtAaZz]|-?(?:\d+\.?\d*|\.\d+)(?:e-?\d+)?", d)


def scale_path(d, k):
    """Scale SVG path data by k, keeping arc rotation/flags."""
    out = []
    i = 0
    toks = []
    # arc flags can be packed ("011"), so tokenize arcs specially
    pos = 0
    pat = re.compile(r"\s*,?\s*([MmLlHhVvCcSsQqTtAaZz]|-?(?:\d+\.?\d*|\.\d+)(?:e-?\d+)?)")
    cur = None
    argi = 0
    lead = False
    while pos < len(d):
        m = pat.match(d, pos)
        if not m:
            pos += 1
            continue
        tok = m.group(1)
        if tok.isalpha():
            if not out and tok == "m":
                tok = "M"
                lead = True
            cur = tok
            argi = 0
            out.append(tok)
            pos = m.end()
            continue
        if cur == "M" and argi == 2 and lead:
            out.append("l")
            cur = "l"
            argi = 0
        if cur in "Aa" and argi % 7 in (3, 4):
            # single-char flag
            sm = re.compile(r"\s*,?\s*([01])").match(d, pos)
            out.append(sm.group(1))
            pos = sm.end()
            argi += 1
            continue
        v = float(tok)
        if cur in "Aa" and argi % 7 == 2:
            out.append(tok)
        else:
            out.append(("%.3f" % (v * k)).rstrip("0").rstrip("."))
        argi += 1
        pos = m.end()
    return " ".join(out)


def _attrs(s):
    return dict(re.findall(r'([a-z-]+)="([^"]*)"', s))


def svg_to_path(markup):
    parts = []
    for tag, attrs in re.findall(r"<(path|circle|rect|line|ellipse|polyline)([^>]*)/?>", markup):
        a = _attrs(attrs)
        if tag == "path":
            parts.append(a["d"].strip())
        elif tag == "circle":
            cx, cy, r = float(a["cx"]), float(a["cy"]), float(a["r"])
            k = 0.5523 * r
            parts.append(f"M{cx} {cy - r}C{cx + k} {cy - r} {cx + r} {cy - k} {cx + r} {cy}C{cx + r} {cy + k} {cx + k} {cy + r} {cx} {cy + r}"
                         f"C{cx - k} {cy + r} {cx - r} {cy + k} {cx - r} {cy}C{cx - r} {cy - k} {cx - k} {cy - r} {cx} {cy - r}Z")
        elif tag == "rect":
            x, y = float(a.get("x", 0)), float(a.get("y", 0))
            w, h = float(a["width"]), float(a["height"])
            rx = float(a.get("rx", 0))
            if rx:
                parts.append(f"M{x + rx} {y}h{w - 2 * rx}a{rx} {rx} 0 0 1 {rx} {rx}v{h - 2 * rx}a{rx} {rx} 0 0 1 {-rx} {rx}"
                             f"h{-(w - 2 * rx)}a{rx} {rx} 0 0 1 {-rx} {-rx}v{-(h - 2 * rx)}a{rx} {rx} 0 0 1 {rx} {-rx}z")
            else:
                parts.append(f"M{x} {y}h{w}v{h}h{-w}z")
        elif tag == "line":
            parts.append(f"M{a['x1']} {a['y1']}L{a['x2']} {a['y2']}")
    return parts


def load_icons(_=None):
    from icons import ICON_SVG
    for k, v in ICON_SVG.items():
        ICONS[k] = svg_to_path(v)


def icon(parent, name, size=16, color=MUTED, weight=None):
    f = box(parent, None, w=size, h=size, name="icon/" + name)
    k = size / 24.0
    for d in ICONS.get(name, ICONS["info"]):
        p = scale_path(d, k)
        x0, y0, _, _ = bbox(p)
        node("vector", f, name, x=round(x0, 3), y=round(y0, 3), path=p,
             fill="none", stroke=color, strokeWeight=weight or (1.75 if size >= 20 else 1.5),
             strokeCap="round", strokeJoin="round")
    return f


# ---------------------------------------------------------------- primitives
def btn(parent, label, kind="secondary", ic=None, size="md"):
    h = 32 if size == "md" else 28
    fill, fg, stroke = {
        "primary": (ACCENT, "#ffffff", None),
        "secondary": (SURF, TEXT, BSTRONG),
        "ghost": (None, TEXT, None),
        "danger": (SURF, DANGER, "#e8b4b4"),
        "dark": (HPILL, HTEXT, None),
    }[kind]
    b = H(parent, 6, pad=[0, 12 if label else 8], h=h, fill=fill, radius=6, stroke=stroke, name="Button/" + (label or ic))
    if ic:
        icon(b, ic, 16, fg)
    if label:
        T(b, label, 13, "Medium", fg)
    return b


def pill(parent, label, fg, bg, dot=False):
    p = H(parent, 6, pad=[2, 8], fill=bg, radius=10, name="Badge/" + label)
    if dot:
        node("ellipse", p, "dot", w=6, h=6, fill=fg)
    T(p, label, 11.5, "Medium", fg)
    return p


STATE_COLORS = {
    "New": (INFO, INFO_SOFT), "In Progress": (WARN, WARN_SOFT), "On Hold": (MUTED, SUNK),
    "Resolved": (SUCCESS, SUCCESS_SOFT), "Closed": (MUTED, SUNK), "Canceled": (MUTED, SUNK),
    "Assess": (INFO, INFO_SOFT), "Authorize": (WARN, WARN_SOFT), "Scheduled": (INFO, INFO_SOFT),
    "Implement": (WARN, WARN_SOFT), "Review": (INFO, INFO_SOFT), "Approved": (SUCCESS, SUCCESS_SOFT),
    "Requested": (WARN, WARN_SOFT), "Rejected": (DANGER, DANGER_SOFT), "Published": (SUCCESS, SUCCESS_SOFT),
    "Draft": (MUTED, SUNK), "Retired": (MUTED, SUNK), "Operational": (SUCCESS, SUCCESS_SOFT),
    "Degraded": (WARN, WARN_SOFT), "Non-Operational": (DANGER, DANGER_SOFT), "In Use": (SUCCESS, SUCCESS_SOFT),
    "In Stock": (INFO, INFO_SOFT), "In Maintenance": (WARN, WARN_SOFT), "Open": (INFO, INFO_SOFT),
    "Work in Progress": (WARN, WARN_SOFT), "Pending": (WARN, WARN_SOFT), "Fulfillment": (WARN, WARN_SOFT),
    "Delivered": (SUCCESS, SUCCESS_SOFT), "Root Cause Analysis": (WARN, WARN_SOFT), "Fix in Progress": (WARN, WARN_SOFT),
    "Known Error": (DANGER, DANGER_SOFT), "Active": (SUCCESS, SUCCESS_SOFT), "Inactive": (MUTED, SUNK),
    "Planning": (INFO, INFO_SOFT), "Building": (WARN, WARN_SOFT), "Testing": (WARN, WARN_SOFT),
    "Deploying": (WARN, WARN_SOFT), "Complete": (SUCCESS, SUCCESS_SOFT), "Loaded": (INFO, INFO_SOFT),
    "Processed": (SUCCESS, SUCCESS_SOFT), "Error": (DANGER, DANGER_SOFT), "Expiring": (WARN, WARN_SOFT),
    "Breached": (DANGER, DANGER_SOFT), "On track": (SUCCESS, SUCCESS_SOFT), "At risk": (WARN, WARN_SOFT),
    "Waiting for approval": (WARN, WARN_SOFT), "Closed Complete": (SUCCESS, SUCCESS_SOFT),
}


def state(parent, label):
    fg, bg = STATE_COLORS.get(label, (MUTED, SUNK))
    return pill(parent, label, fg, bg, dot=True)


def prio(parent, p):
    r = H(parent, 6, name="Priority")
    node("ellipse", r, "dot", w=8, h=8, fill=PRIO[p])
    T(r, PRIO_LABEL[p], 13, "Regular", TEXT)
    return r


def avatar(parent, name, size=32, bg=ACC_SOFT, fg=ACC_H):
    ini = "".join(w[0] for w in name.split()[:2]).upper()
    a = H(parent, 0, w=size, h=size, fill=bg, radius=size / 2, main="center", name="Avatar/" + name)
    T(a, ini, max(10, size * 0.38), "Semi Bold", fg)
    return a


def card(parent, gap=12, pad=16, sw="fill", **kw):
    return V(parent, gap, pad=pad, fill=SURF, radius=10, stroke=BORDER, effects=SH1, sw=sw, **kw)


def hr(parent, color=BORDER):
    return box(parent, None, h=1, fill=color, sw="fill", name="Divider")


def field_input(parent, value="", kind="input", placeholder=None, w=None, mandatory=False):
    """Text input / select / reference / textarea control."""
    h = 72 if kind == "textarea" else 32
    f = H(parent, 8, pad=[0, 10] if kind != "textarea" else [8, 10], h=h, w=w, sw=None if w else "fill",
          fill=SUNK if kind == "readonly" else SURF, radius=6, stroke=BSTRONG if kind != "readonly" else BORDER,
          cross="center" if kind != "textarea" else "min", name="Input")
    T(f, value or placeholder or "", 13, "Regular", TEXT if value else SUBTLE, sw="fill", trunc=kind != "textarea", lh="150%")
    if kind == "select":
        icon(f, "chevron-down", 16, MUTED)
    elif kind == "ref":
        icon(f, "search", 16, MUTED)
    elif kind == "date":
        icon(f, "calendar", 16, MUTED)
    return f


def labeled(parent, label, value="", kind="input", mandatory=False, label_w=150):
    """ITSM-style row: label left, control right."""
    row = H(parent, 12, sw="fill", name="Field/" + label, cross="center" if kind != "textarea" else "min")
    lab = H(row, 4, w=label_w, main="max", name="Label")
    if mandatory:
        T(lab, "*", 13, "Semi Bold", DANGER)
    T(lab, label, 13, "Regular", MUTED, align="right")
    if kind == "checkbox":
        cb = H(row, 8, sw="fill")
        chk = box(cb, "h", w=16, h=16, radius=4, fill=ACCENT if value else SURF, stroke=None if value else BSTRONG, main="center", cross="center")
        if value:
            icon(chk, "check", 12, "#ffffff", 2)
    else:
        field_input(row, value, kind)
    return row


def checkbox(parent, on=False):
    chk = box(parent, "h", w=16, h=16, radius=4, fill=ACCENT if on else SURF, stroke=None if on else BSTRONG,
              main="center", cross="center", name="Checkbox")
    if on:
        icon(chk, "check", 12, "#ffffff", 2)
    return chk


def toggle(parent, on=True):
    t = box(parent, "h", w=32, h=18, radius=9, fill=ACCENT if on else BSTRONG, pad=2,
            main="max" if on else "min", cross="center", name="Toggle")
    node("ellipse", t, "knob", w=14, h=14, fill="#ffffff")
    return t


def save(path):
    with open(path, "w") as fh:
        json.dump(C, fh)
    return len(C)
