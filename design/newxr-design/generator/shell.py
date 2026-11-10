from lib import *

COMP = {}
AV_COLORS = [("#e9e5ff", "#4536dc"), ("#dff5ef", "#0b7a6d"), ("#fff0dd", "#a65d00"), ("#e4eefc", "#1d55b0"),
             ("#fde6ee", "#b02a55"), ("#e6f4e9", "#1f7a43"), ("#efe7fb", "#6b2fc7")]


def avatar(parent, name, size=32, bg=None, fg=None):
    ini = "".join(w[0] for w in name.split()[:2]).upper()
    b, f = AV_COLORS[sum(map(ord, name)) % len(AV_COLORS)]
    a = H(parent, 0, w=size, h=size, fill=bg or b, radius=size / 2, main="center", name="Avatar/" + name)
    T(a, ini, max(9, size * 0.38), "Semi Bold", fg or f)
    return a


def avatars(parent, names, size=24):
    r = H(parent, -6, name="Avatar stack")
    for n in names:
        a = avatar(r, n, size)
        setp(a, stroke="#ffffff", strokeWeight=2, strokeAlign="outside")
    return r


STATUS = {
    "To do": (MUTED, "#eceef2"), "Backlog": (MUTED, "#eceef2"), "Open": (MUTED, "#eceef2"),
    "In progress": (INFO, INFO_SOFT), "In review": (VIOLET, VIOLET_SOFT), "Done": (SUCCESS, SUCCESS_SOFT),
    "Blocked": (DANGER, DANGER_SOFT), "Waiting for customer": (WARN, WARN_SOFT), "Waiting for support": (INFO, INFO_SOFT),
    "Resolved": (SUCCESS, SUCCESS_SOFT), "Escalated": (DANGER, DANGER_SOFT), "Merged": (VIOLET, VIOLET_SOFT),
    "Approved": (SUCCESS, SUCCESS_SOFT), "Needs work": (WARN, WARN_SOFT), "Declined": (DANGER, DANGER_SOFT),
    "Passed": (SUCCESS, SUCCESS_SOFT), "Failed": (DANGER, DANGER_SOFT), "Running": (INFO, INFO_SOFT),
    "On track": (SUCCESS, SUCCESS_SOFT), "At risk": (WARN, WARN_SOFT), "Off track": (DANGER, DANGER_SOFT),
    "Paused": (MUTED, "#eceef2"), "Completed": (VIOLET, VIOLET_SOFT), "Acknowledged": (WARN, WARN_SOFT),
    "Closed": (MUTED, "#eceef2"), "Operational": (SUCCESS, SUCCESS_SOFT), "Degraded": (WARN, WARN_SOFT),
    "Outage": (DANGER, DANGER_SOFT), "Admin": (VIOLET, VIOLET_SOFT), "Member": (MUTED, "#eceef2"),
    "Guest": (WARN, WARN_SOFT), "Active": (SUCCESS, SUCCESS_SOFT), "Invited": (INFO, INFO_SOFT),
    "Draft": (MUTED, "#eceef2"), "Published": (SUCCESS, SUCCESS_SOFT), "P1": (DANGER, DANGER_SOFT),
    "P2": ("#c4541f", "#fdebe2"), "P3": (WARN, WARN_SOFT), "P4": (INFO, INFO_SOFT),
}


def lozenge(parent, label, bold=True):
    fg, bg = STATUS.get(label, (MUTED, "#eceef2"))
    p = H(parent, 0, pad=[2, 6], fill=bg, radius=4, name="Status/" + label)
    T(p, label.upper(), 11, "Bold" if bold else "Semi Bold", fg, ls=0.2)
    return p


def label_chip(parent, text, fg=MUTED, bg="#eceef2"):
    p = H(parent, 0, pad=[2, 8], fill=bg, radius=10, name="Label/" + text)
    T(p, text, 11.5, "Medium", fg)
    return p


ITYPE = {"story": ("#2f9a57", "bookmark"), "bug": ("#d23b3b", "bug"), "task": ("#2563c9", "check"),
         "epic": ("#7c3aed", "bolt"), "subtask": ("#0e9f8e", "list")}


def itype(parent, kind, size=16):
    col, ic = ITYPE[kind]
    b = H(parent, 0, w=size, h=size, radius=3, fill=col, main="center", name="Type/" + kind)
    icon(b, ic, size - 4, "#ffffff", 2.2)
    return b


def prio_icon(parent, p, size=16):
    ic = {1: "chevron-up", 2: "chevron-up", 3: "menu", 4: "chevron-down", 5: "chevron-down"}[p]
    return icon(parent, ic, size, PRIO[p], 2.4)


def prio(parent, p):
    r = H(parent, 6, name="Priority")
    prio_icon(parent=r, p=p)
    T(r, PRIO_LABEL[p], 13, "Regular", TEXT)
    return r


def app_tile(parent, app, size=28):
    col, ic, _ = APPS[app]
    t = H(parent, 0, w=size, h=size, radius=size * 0.28, fill=col, main="center", name="App/" + app)
    icon(t, ic, size * 0.6, "#ffffff", 2)
    return t


def logo(parent, size=20, color=TEXT, mark=26):
    g = H(parent, 8, name="Logo")
    m = box(g, None, w=mark, h=mark, radius=mark * 0.3, fill=ACCENT, name="Mark")
    k = mark / 26
    node("vector", m, "n", x=round(7.5 * k, 2), y=round(7.5 * k, 2),
         path="M0 %s V0 L%s %s V0" % (round(11 * k, 2), round(11 * k, 2), round(11 * k, 2)),
         fill="none", stroke="#ffffff", strokeWeight=2.6 * k, strokeCap="round", strokeJoin="round")
    node("ellipse", m, "dot", w=4 * k, h=4 * k, x=18.5 * k, y=3.5 * k, fill="#c9c3ff")
    T(g, "newxr", size, "Bold", color, ls=-0.6)
    return g


def build_topnav():
    hdr = box(None, "h", 12, pad=[0, 16, 0, 12], w=1440, h=56, fill=SURF, cross="center", kind="component",
              name="Top nav", x=0, y=0, stroke=BORDER, sides=[0, 0, 1, 0])
    left = H(hdr, 10, name="Left")
    sw = H(left, 0, w=32, h=32, radius=6, main="center", name="App switcher")
    icon(sw, "grid", 18, MUTED)
    logo(left)
    box(hdr, None, h=1, sw="fill", name="spacer")
    s = H(hdr, 8, pad=[0, 12], w=520, h=36, fill=SUNK, radius=8, stroke=BORDER, name="Search")
    icon(s, "search", 16, SUBTLE)
    T(s, "Search issues, pages, repos, people", 13.5, "Regular", SUBTLE, sw="fill")
    k = H(s, 0, pad=[1, 6], radius=4, stroke=BSTRONG, fill=SURF)
    T(k, "/", 11, "Medium", MUTED)
    b = H(hdr, 6, pad=[0, 14], h=36, fill=ACCENT, radius=8, name="Create")
    icon(b, "plus", 16, "#ffffff", 2)
    T(b, "Create", 13.5, "Semi Bold", "#ffffff")
    box(hdr, None, h=1, sw="fill", name="spacer")
    right = H(hdr, 4, name="Right")
    ai = H(right, 6, pad=[0, 10], h=32, radius=16, fill=VIOLET_SOFT, name="Ask Newxr AI")
    icon(ai, "sparkle", 15, VIOLET)
    T(ai, "Ask AI", 12.5, "Semi Bold", VIOLET)
    for ic in ("bell", "help", "settings"):
        c = box(right, None, w=36, h=36, name=ic)
        icon(box(c, "h", w=36, h=36, main="center", cross="center", x=0, y=0, radius=8), ic, 19, MUTED)
        if ic == "bell":
            bb = H(c, 0, pad=[0, 4], h=16, fill=DANGER, radius=8, x=20, y=2, name="count")
            T(bb, "4", 10, "Bold", "#ffffff")
    avatar(right, "Tanmay Singh", 32)
    return hdr


def build_components(x0, y0):
    COMP["topnav"] = build_topnav()
    setp(COMP["topnav"], x=x0, y=y0)


def topnav(parent):
    r = nid()
    cmd("instance.create", {"component": R(COMP["topnav"]), "parent": R(parent)}, r)
    setp(r, sizingH="fill")
    return r


# ---------------------------------------------------------------- sidebars
SIDEBARS = {
    "Hub": ("Your work", None, [("For you", "home"), ("Recent", "clock"), ("Starred", "star"), ("Apps", "grid"),
                                 ("Teams", "users"), ("Notifications", "bell")]),
    "Track": ("Orbit Mobile", "Software project", [("Summary", "home"), ("Timeline", "timeline"), ("Backlog", "backlog"),
                                                    ("Board", "board"), ("List", "list"), ("Reports", "chart"),
                                                    ("Releases", "tag"), ("Code", "code"), ("Pages", "page"), ("Project settings", "settings")]),
    "Pages": ("Engineering", "Team space", [("Overview", "home"), ("Blogs", "megaphone"), ("Whiteboards", "edit"),
                                            ("Templates", "page"), ("Space settings", "settings")]),
    "Boards": ("Q4 Launch", "Workspace board", [("Boards", "kanban"), ("Members", "users"), ("Calendar", "calendar"),
                                                ("Automation", "bolt"), ("Settings", "settings")]),
    "Code": ("orbit-mobile", "Repository", [("Source", "code"), ("Commits", "commit"), ("Branches", "branch"),
                                             ("Pull requests", "pr"), ("Pipelines", "play"), ("Deployments", "server"),
                                             ("Repository settings", "settings")]),
    "Desk": ("IT Help", "Service project", [("Queues", "inbox"), ("Customers", "users"), ("Knowledge base", "book"),
                                            ("Reports", "chart"), ("Channels", "message"), ("SLAs", "clock"), ("Project settings", "settings")]),
    "Pulse": ("Platform Ops", "Operations", [("Alerts", "bell"), ("Incidents", "alert"), ("On-call", "calendar"),
                                             ("Status page", "globe"), ("Integrations", "link"), ("Escalations", "arrow-up")]),
    "Goals": ("Northwind Inc.", "Workspace", [("Goals", "target"), ("Projects", "flag"), ("Updates", "megaphone"),
                                              ("Teams", "users"), ("Topics", "tag")]),
    "Admin": ("Northwind Inc.", "Organization", [("Overview", "home"), ("Users", "user"), ("Groups", "users"),
                                                  ("Products", "grid"), ("Billing", "card"), ("Security", "shield"), ("Audit log", "list")]),
}


def sidebar(parent, app, active, height=844, extra=None):
    title, sub, items = SIDEBARS[app]
    nav = V(parent, 2, pad=[16, 12], w=248, h=height, sh="fixed", clip=True, fill=SURF, name="Sidebar",
            stroke=BORDER, sides=[0, 1, 0, 0])
    head = H(nav, 10, pad=[4, 6, 12, 6], sw="fill", name="Context")
    if app in APPS:
        app_tile(head, app, 32)
    elif app == "Admin":
        t = H(head, 0, w=32, h=32, radius=9, fill=TEXT, main="center")
        icon(t, "shield", 18, "#ffffff", 2)
    else:
        avatar(head, "Tanmay Singh", 32)
    tv = V(head, 1, sw="fill")
    T(tv, title, 14, "Semi Bold", TEXT, sw="fill", trunc=True)
    if sub:
        T(tv, sub, 12, "Regular", MUTED)
    icon(head, "chevron-down", 14, SUBTLE)
    for label, ic in items:
        act = label == active
        row = H(nav, 10, pad=[0, 10], h=34, sw="fill", radius=6, fill=SEL if act else None, name="Nav/" + label)
        if act:
            pass
        icon(row, ic, 17, ACCENT if act else MUTED)
        T(row, label, 13.5, "Semi Bold" if act else "Medium", ACC_H if act else TEXT, sw="fill")
    if extra:
        extra(nav)
    return nav


def nav_group(nav, title, rows):
    box(nav, None, h=12, sw="fill")
    T(nav, title.upper(), 11, "Semi Bold", SUBTLE, ls=0.6)
    for r in rows:
        row = H(nav, 10, pad=[0, 10], h=30, sw="fill", radius=6, name="Nav/" + r[0])
        if len(r) > 1 and r[1] in ITYPE:
            itype(row, r[1], 14)
        elif len(r) > 1 and r[1] in APPS:
            app_tile(row, r[1], 18)
        elif len(r) > 1 and r[1]:
            icon(row, r[1], 15, MUTED)
        T(row, r[0], 13, "Regular", TEXT, sw="fill", trunc=True)


# ---------------------------------------------------------------- canvas placement
class Page:
    first = True

    def __init__(self, name):
        if Page.first:
            cmd("page.rename", {"name": name})
            Page.first = False
        else:
            cmd("page.add", {"name": name})
        cmd("page.setBackground", {"color": "#e8e9f0"})
        self.y = 0
        self.x = 0
        self.rowh = 0

    def section(self, title, sub=None):
        if self.x:
            self.newrow()
        t = T(None, title, 64, "Bold", TEXT, name="Section/" + title)
        setp(t, x=0, y=self.y)
        if sub:
            s = T(None, sub, 22, "Regular", MUTED, name="Section note")
            setp(s, x=0, y=self.y + 90)
        self.y += 180

    def slot(self, w=1440, est=1000):
        if self.x and self.x + w > 3 * (1440 + 120):
            self.newrow()
        x, y = self.x, self.y
        self.x += w + 120
        self.rowh = max(self.rowh, est)
        return x, y

    def newrow(self):
        if self.rowh:
            self.y += self.rowh + 200
        self.x = 0
        self.rowh = 0


PAGE = [None]
FIX_HUG = []


import json as _json, os as _os
_HP = _os.path.join(_os.path.dirname(_os.path.abspath(__file__)), "heights.json")
HEIGHTS = _json.load(open(_HP)) if _os.path.exists(_HP) else {}
SCREENS = []


def screen(name, w=1440, minh=900, est=1000, fill=SUNK):
    x, y = PAGE[0].slot(w, HEIGHTS.get(name, est))
    s = box(None, "v", 0, w=w, sh="hug", fill=fill, name=name, x=x, y=y, clip=True, minHeight=minh)
    SCREENS.append((name, s))
    return s


def app_screen(name, app, active, crumbs=None, est=1000, extra=None, pad=None, gap=20, sidebar_h=None):
    s = screen(name, est=est)
    topnav(s)
    body = box(s, "h", 0, sw="fill", sh="hug", cross="min", name="Body", minHeight=844)
    FIX_HUG.append(body)
    sidebar(body, app, active, sidebar_h or max(844, HEIGHTS.get(name, 900) - 56), extra)
    main = V(body, gap, pad=pad or [20, 32, 40, 32], sw="fill", name="Main")
    if crumbs:
        bar = H(main, 6, sw="fill", name="Breadcrumbs")
        for i, c in enumerate(crumbs):
            if i:
                T(bar, "/", 12.5, "Regular", SUBTLE)
            T(bar, c, 12.5, "Regular", MUTED)
    return main


def page_title(parent, title, actions=(), primary=None, sub=None):
    h = H(parent, 12, sw="fill", main="spaceBetween", name="Title bar")
    l = V(h, 4)
    T(l, title, 24, "Semi Bold", TEXT, ls=-0.4)
    if sub:
        T(l, sub, 13.5, "Regular", MUTED)
    r = H(h, 8)
    for a in actions:
        if isinstance(a, tuple):
            btn(r, a[0], "secondary", a[1])
        else:
            btn(r, None, "secondary", a) if a in ICONS else btn(r, a, "secondary")
    if primary:
        btn(r, primary, "primary", "plus" if primary.startswith(("Create", "New", "Add", "Invite")) else None)
    return h


def toolbar(parent, search="Search", chips=(), people=None, right=()):
    t = H(parent, 10, sw="fill", name="Toolbar")
    s = H(t, 8, pad=[0, 10], w=220, h=32, radius=6, stroke=BSTRONG, fill=SURF, name="Filter search")
    icon(s, "search", 15, SUBTLE)
    T(s, search, 13, "Regular", SUBTLE)
    if people:
        avatars(t, people, 28)
    for c in chips:
        ch = H(t, 6, pad=[0, 10], h=32, radius=6, stroke=BSTRONG, fill=SURF, name="Filter/" + c)
        T(ch, c, 13, "Medium", TEXT)
        icon(ch, "chevron-down", 14, MUTED)
    box(t, None, h=1, sw="fill")
    for r in right:
        if r in ICONS:
            btn(t, None, "ghost", r)
        else:
            btn(t, r, "secondary")
    return t


def btn(parent, label, kind="secondary", ic=None, size="md"):
    h = 32 if size == "md" else 28
    fill, fg, stroke = {
        "primary": (ACCENT, "#ffffff", None),
        "secondary": (SURF, TEXT, BSTRONG),
        "ghost": (None, MUTED, None),
        "subtle": ("#eceef2", TEXT, None),
        "danger": (DANGER, "#ffffff", None),
        "success": (SUCCESS, "#ffffff", None),
    }[kind]
    b = H(parent, 6, pad=[0, 12 if label else 8], h=h, fill=fill, radius=6, stroke=stroke, name="Button/" + (label or ic))
    if ic:
        icon(b, ic, 16, fg, 2 if kind == "primary" else None)
    if label:
        T(b, label, 13, "Semi Bold" if kind == "primary" else "Medium", fg)
    return b


def tabs(parent, names, active=0, counts=None):
    t = H(parent, 4, sw="fill", stroke=BORDER, sides=[0, 0, 1, 0], name="Tabs", cross="max")
    for i, n in enumerate(names):
        tb = V(t, 0, pad=[8, 10, 0, 10], name="Tab/" + n)
        r = H(tb, 6)
        T(r, n, 13.5, "Semi Bold" if i == active else "Medium", ACC_H if i == active else MUTED)
        if counts and counts[i] is not None:
            label_chip(r, str(counts[i]), ACC_H if i == active else MUTED, ACC_SOFT if i == active else "#eceef2")
        box(tb, None, h=10, sw="fill")
        box(tb, None, h=2, sw="fill", fill=ACCENT if i == active else None, radius=1)
    return t


def card(parent, gap=12, pad=16, sw="fill", **kw):
    kw.setdefault("radius", 10)
    return V(parent, gap, pad=pad, fill=SURF, stroke=BORDER, effects=SH1, sw=sw, **kw)


def section_head(parent, title, link=None):
    h = H(parent, 8, sw="fill", main="spaceBetween")
    T(h, title, 15, "Semi Bold", TEXT)
    if link:
        T(h, link, 12.5, "Medium", LINK)
    return h


def cell(c, kind, v):
    if kind == "key":
        k, t = v
        itype(c, t, 16)
        T(c, k, 13, "Medium", LINK)
    elif kind == "link":
        T(c, v, 13, "Medium", LINK, sw="fill", trunc=True)
    elif kind == "status":
        lozenge(c, v)
    elif kind == "prio":
        prio(c, v)
    elif kind == "user":
        if v:
            avatar(c, v, 22)
            T(c, v, 13, "Regular", TEXT, sw="fill", trunc=True)
        else:
            T(c, "Unassigned", 13, "Regular", SUBTLE)
    elif kind == "mono":
        T(c, v, 12, "Regular", TEXT, mono=True, sw="fill", trunc=True)
    elif kind == "muted":
        T(c, v, 13, "Regular", MUTED, sw="fill", trunc=True)
    elif kind == "app":
        app_tile(c, v, 20)
        T(c, v, 13, "Medium", TEXT)
    elif kind == "chips":
        for x in v:
            label_chip(c, x)
    elif kind == "bar":
        bar = box(c, None, w=110, h=6, radius=3, fill="#eceef2")
        box(bar, None, w=max(4, 110 * v / 100), h=6, radius=3, fill=SUCCESS if v >= 70 else (WARN if v >= 40 else DANGER), x=0, y=0)
        T(c, "%d%%" % v, 12, "Medium", MUTED)
    elif kind == "custom":
        v(c)
    else:
        T(c, v if v else "—", 13, "Regular", TEXT if v else SUBTLE, sw="fill", trunc=True)


def table(parent, cols, rows, width=1128, check=False, name="Table", footer=None, selected=None, rowh=44):
    t = V(parent, 0, sw="fill", fill=SURF, radius=10, stroke=BORDER, clip=True, effects=SH1, name=name)
    avail = width - (44 if check else 0)
    tot = sum(c[1] for c in cols)
    widths = [max(56, int(avail * c[1] / tot)) for c in cols]
    hr_ = H(t, 0, h=38, sw="fill", fill=SUNK, stroke=BORDER, sides=[0, 0, 1, 0], name="Header row")
    if check:
        checkbox(H(hr_, 0, w=44, h=38, main="center"))
    for (label, _, kind), w in zip(cols, widths):
        c = H(hr_, 4, pad=[0, 12], w=w, h=38, name="Col/" + label)
        T(c, label, 12, "Semi Bold", MUTED)
    for i, row in enumerate(rows):
        rr = H(t, 0, h=rowh, sw="fill", fill=SEL if i == selected else None, stroke=BORDER if i < len(rows) - 1 else None,
               sides=[0, 0, 1, 0], name="Row %d" % (i + 1))
        if check:
            checkbox(H(rr, 0, w=44, h=rowh, main="center"), i == selected)
        for (label, _, kind), w, v in zip(cols, widths, row):
            c = H(rr, 6, pad=[0, 12], w=w, h=rowh, name=label)
            cell(c, kind, v)
    if footer:
        f = H(t, 8, pad=[0, 12], h=44, sw="fill", main="spaceBetween", stroke=BORDER, sides=[1, 0, 0, 0], name="Pagination")
        T(f, footer, 12.5, "Regular", MUTED)
        pg = H(f, 4)
        for ic in ("chevron-left", "chevron-right"):
            btn(pg, None, "secondary", ic, "sm")
    return t


def field_row(parent, label, render, label_w=128):
    r = H(parent, 12, sw="fill", name="Field/" + label, pad=[6, 0])
    T(r, label, 13, "Medium", MUTED, w=label_w)
    v = H(r, 6, sw="fill")
    if isinstance(render, str):
        T(v, render, 13, "Regular", TEXT, sw="fill")
    else:
        render(v)
    return r


def input_box(parent, value="", placeholder="", kind="input", w=None, h=None, label=None, req=False):
    wrap = V(parent, 6, sw=None if w else "fill", w=w, name="Input/" + (label or placeholder or value)[:24])
    if label:
        lr = H(wrap, 3)
        T(lr, label, 12.5, "Semi Bold", TEXT)
        if req:
            T(lr, "*", 12.5, "Semi Bold", DANGER)
    hh = h or (96 if kind == "textarea" else 36)
    f = H(wrap, 8, pad=[0, 12] if kind != "textarea" else [10, 12], h=hh, sw="fill", fill=SURF, radius=6,
          stroke=BSTRONG, cross="center" if kind != "textarea" else "min")
    if kind == "user":
        avatar(f, value, 22)
    T(f, value or placeholder, 13.5, "Regular", TEXT if value else SUBTLE, sw="fill", trunc=kind != "textarea", lh="150%")
    if kind in ("select", "user"):
        icon(f, "chevron-down", 16, MUTED)
    elif kind == "date":
        icon(f, "calendar", 16, MUTED)
    return wrap


def comment(parent, who, when, text, reactions=None):
    e = H(parent, 12, sw="fill", cross="min", name="Comment")
    avatar(e, who, 32)
    b = V(e, 6, sw="fill")
    top = H(b, 8)
    T(top, who, 13.5, "Semi Bold", TEXT)
    T(top, when, 12.5, "Regular", SUBTLE)
    T(b, text, 13.5, "Regular", TEXT, sw="fill", lh="155%")
    a = H(b, 12)
    for x in (["Reply", "Edit"] + ([reactions] if reactions else [])):
        T(a, x, 12.5, "Medium", MUTED)
    return e


def composer(parent, who="Tanmay Singh", ph="Add a comment…"):
    e = H(parent, 12, sw="fill", cross="min", name="Composer")
    avatar(e, who, 32)
    f = V(e, 8, sw="fill", pad=[10, 12], radius=8, stroke=BSTRONG, fill=SURF)
    T(f, ph, 13.5, "Regular", SUBTLE)
    r = H(f, 6, sw="fill")
    for ic in ("bold", "italic", "list", "link", "at", "image"):
        icon(r, ic, 16, SUBTLE)
    box(r, None, h=1, sw="fill")
    btn(r, "Save", "primary", None, "sm")
    T(parent, "Pro tip: press M to comment", 12, "Regular", SUBTLE)
    return e


def kpi(parent, label, value, delta=None, tone=SUCCESS, ic=None):
    c = card(parent, 6, 16, name="KPI/" + label)
    top = H(c, 8, sw="fill")
    if ic:
        icon(top, ic, 16, MUTED)
    T(top, label, 12.5, "Medium", MUTED, sw="fill")
    T(c, value, 28, "Semi Bold", TEXT, ls=-0.6)
    if delta:
        T(c, delta, 12, "Medium", tone)
    return c


def progress(parent, parts, w=None, h=8):
    """parts: [(pct, colour)]"""
    b = H(parent, 0, sw=None if w else "fill", w=w or None, h=h, radius=h / 2, fill="#eceef2", clip=True, name="Progress")
    tot = w or 400
    for pct, col in parts:
        box(b, None, w=max(1, tot * pct / 100), h=h, fill=col)
    return b


def bar_chart(parent, title, labels, series, colors=(S1, S2, S3), legend=None, h=180, w=None):
    c = card(parent, 14, 20, name="Chart/" + title, sw="fill" if not w else None, w=w)
    hd = H(c, 16, sw="fill", main="spaceBetween")
    T(hd, title, 14, "Semi Bold", TEXT)
    if legend:
        lg = H(hd, 14)
        for nm, col in zip(legend, colors):
            li = H(lg, 6)
            box(li, None, w=10, h=10, radius=2, fill=col)
            T(li, nm, 12, "Regular", MUTED)
    mx = max(max(s) for s in series)
    plot = H(c, 0, sw="fill", h=h + 10, cross="max", main="spaceBetween", stroke=BSTRONG, sides=[0, 0, 1, 0], name="Plot", pad=[0, 6])
    bw = 22 if len(series) == 1 else 11
    for i, lab in enumerate(labels):
        bars = H(plot, 3, cross="max", h=h, name=lab)
        for s, col in zip(series, colors):
            box(bars, None, w=bw, h=max(2, h * s[i] / mx), fill=col, radius=[3, 3, 0, 0])
    ax = H(c, 0, sw="fill", main="spaceBetween", pad=[0, 0])
    for lab in labels:
        T(ax, lab, 11, "Regular", MUTED, w=36, align="center")
    return c


def line_chart(parent, title, pts, ideal=None, w=520, h=200, legend=("Remaining", "Guideline")):
    c = card(parent, 14, 20, name="Chart/" + title, sw="fill")
    hd = H(c, 16, sw="fill", main="spaceBetween")
    T(hd, title, 14, "Semi Bold", TEXT)
    lg = H(hd, 14)
    for nm, col in zip(legend, (S1, SUBTLE)):
        li = H(lg, 6)
        box(li, None, w=12, h=3, radius=2, fill=col)
        T(li, nm, 12, "Regular", MUTED)
    plot = box(c, None, w=w, h=h, name="Plot")
    for i in range(5):
        box(plot, None, w=w, h=1, fill=CGRID, x=0, y=round(i * h / 4))
    mx = max(pts)
    n = len(pts) - 1

    def pth(vals):
        return " ".join(("M" if i == 0 else "L") + "%.1f %.1f" % (i * w / n, h - h * v / mx) for i, v in enumerate(vals))
    if ideal:
        node("vector", plot, "guideline", x=0, y=0, path=pth(ideal), fill="none", stroke=SUBTLE, strokeWeight=1.5,
             dashes=[5, 4])
    p = pth(pts)
    node("vector", plot, "series", x=0, y=round(h - h * max(pts) / mx, 1), path=p, fill="none", stroke=S1, strokeWeight=2.5,
         strokeJoin="round", strokeCap="round")
    return c


def modal_overlay(s, w, h_top=120):
    """Dim the screen; return a centred modal container (absolute)."""
    ov = box(s, "v", 0, w=1440, h=900, fill="#14122b66", name="Scrim", x=0, y=0, cross="center", pad=[h_top, 0],
             absolute=True)
    m = V(ov, 0, w=w, fill=SURF, radius=12, effects=SH3, name="Modal", clip=True)
    return m


def bullet(parent, color=TEXT):
    f = box(parent, None, w=6, h=24, name="bullet")
    node("ellipse", f, "dot", w=6, h=6, x=0, y=9, fill=color)
    return f
