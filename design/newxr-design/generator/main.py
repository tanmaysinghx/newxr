"""Build the Newxr design file. usage: python3 main.py OUT.json [--measure]"""
import sys
from screens_b import *

load_icons()

# ---------------------------------------------------------------- Cover
PAGE[0] = Page("Cover")
cv = box(None, "v", 28, pad=[96, 96], w=1600, h=960, name="Cover", x=0, y=0, clip=True,
         fills=[{"type": "linearGradient", "stops": ["#14122b", "#2b2470"], "from": [0, 0], "to": [1, 1]}])
logo(cv, 40, CTEXT, mark=52)
box(cv, None, h=40, w=10)
T(cv, "Newxr — product design", 64, "Bold", CTEXT, ls=-1.5)
T(cv, "One connected suite for software, knowledge, service and operations teams.", 24, "Regular", CMUTED, w=1100)
box(cv, None, h=24, w=10)
g = H(cv, 16, wrap=True, w=1400, crossGap=16)
for a, (col, ic, tag) in APPS.items():
    c = V(g, 10, w=320, pad=20, radius=14, fill="#ffffff10", stroke="#ffffff22")
    app_tile(c, a, 40)
    T(c, "Newxr " + a, 20, "Semi Bold", CTEXT)
    T(c, tag, 15, "Regular", CMUTED)
box(cv, None, h=24, w=10)
T(cv, "Light theme · 1440 px desktop frames · Inter · v2026.11", 16, "Regular", CMUTED)

# ---------------------------------------------------------------- Foundations
PAGE[0] = Page("Foundations & Components")
pg = PAGE[0]
pg.section("Colour", "Core tokens. Every swatch is also a paint style.")
groups = [("Chrome", [("chrome", CHROME), ("chrome-2", CHROME_2), ("chrome-text", CTEXT), ("chrome-muted", CMUTED)]),
          ("Surface", [("surface", SURF), ("surface-sunken", SUNK), ("surface-hover", HOVER), ("surface-selected", SEL), ("border", BORDER), ("border-strong", BSTRONG)]),
          ("Text", [("text", TEXT), ("text-muted", MUTED), ("text-subtle", SUBTLE), ("link", LINK)]),
          ("Brand & status", [("accent", ACCENT), ("accent-hover", ACC_H), ("accent-soft", ACC_SOFT), ("success", SUCCESS), ("warning", WARN), ("danger", DANGER), ("info", INFO), ("ai", VIOLET)]),
          ("Apps", [(a.lower(), v[0]) for a, v in APPS.items()])]
col = box(None, "v", 32, pad=40, w=1800, fill=SURF, radius=16, name="Colour", x=0, y=pg.y)
for gname, sw_ in groups:
    T(col, gname, 18, "Semi Bold", TEXT)
    r = H(col, 16, cross="min")
    for nm, hexv in sw_:
        c = V(r, 8, w=190)
        s = box(c, None, h=72, sw="fill", radius=10, fill=hexv, stroke=BORDER, name="Swatch/" + nm)
        cmd("style.create", {"type": "paint", "name": gname + "/" + nm, "from": R(s)})
        T(c, "--" + nm, 13, "Semi Bold", TEXT)
        T(c, hexv.upper(), 12, "Regular", MUTED, mono=True)
pg.y += 1100
pg.section("Typography", "Inter for UI; DejaVu Sans Mono for code, keys and logs.")
ty = box(None, "v", 20, pad=40, w=1800, fill=SURF, radius=16, name="Type scale", x=0, y=pg.y)
for nm, size, wt, sample in [("Display", 34, "Bold", "Every team's work, connected."), ("Page title", 24, "Semi Bold", "ORB Sprint 24"),
                             ("Section", 20, "Semi Bold", "Acceptance criteria"), ("Card title", 15, "Semi Bold", "Offline sync — technical design"),
                             ("Body", 13.5, "Regular", "Itineraries saved in the last 30 days open in airplane mode"),
                             ("Label", 12.5, "Medium", "Assignee"), ("Lozenge", 11, "Bold", "IN PROGRESS")]:
    r = H(ty, 24)
    T(r, nm, 13, "Medium", MUTED, w=160)
    T(r, "%s / %s" % (size, wt), 12, "Regular", SUBTLE, w=160, mono=True)
    t = T(r, sample, size, wt, TEXT)
    cmd("style.create", {"type": "text", "name": "Newxr/" + nm, "from": R(t)})
T(ty, "project = ORB AND status != Done  ·  git checkout -b feature/ORB-412", 13, "Regular", TEXT, mono=True)
pg.y += 640
pg.section("Components", "Building blocks shared by every app.")
cmp = box(None, "v", 28, pad=40, w=1800, fill=SURF, radius=16, name="Component sheet", x=0, y=pg.y)
T(cmp, "Buttons", 16, "Semi Bold", TEXT)
r = H(cmp, 12)
for k in ("primary", "secondary", "subtle", "ghost", "danger", "success"):
    btn(r, k.capitalize(), k, "plus" if k == "primary" else None)
btn(r, None, "secondary", "settings")
btn(r, "Small", "secondary", None, "sm")
T(cmp, "Status lozenges", 16, "Semi Bold", TEXT)
r = H(cmp, 8, wrap=True, w=1700, crossGap=8)
for s_ in ["To do", "In progress", "In review", "Done", "Blocked", "Waiting for customer", "Resolved", "Escalated", "Open", "Merged",
           "Approved", "Needs work", "Passed", "Failed", "Running", "On track", "At risk", "Off track", "Completed", "Operational", "Degraded", "Outage", "P1", "P2", "P3", "P4"]:
    lozenge(r, s_)
T(cmp, "Issue types · priority · app tiles", 16, "Semi Bold", TEXT)
r = H(cmp, 20)
for k in ITYPE:
    x = H(r, 6)
    itype(x, k, 20)
    T(x, k.capitalize(), 13, "Regular", TEXT)
for p in range(1, 6):
    prio(r, p)
r = H(cmp, 20)
for a in APPS:
    x = H(r, 8)
    app_tile(x, a, 32)
    T(x, a, 13, "Medium", TEXT)
T(cmp, "Cards", 16, "Semi Bold", TEXT)
r = H(cmp, 20, cross="min")
kc = V(r, 0, w=264)
kcard(kc, *ISSUES[0])
bc = V(r, 0, w=264, pad=8, fill="#f1f2f4", radius=12)
bcard(bc, "Landing page v3", ["Design", "Copy"], ["#8b80ff", "#4a3ce0"], [("clock", "Nov 12", "#e8892b"), ("checklist", "5/8", None)], ["Lena Fischer", "Grace Lee"])
kv = V(r, 0, w=260)
kpi(kv, "Avg cycle time", "3.4 d", "-0.6 d vs last sprint", SUCCESS, "clock")
T(cmp, "Form controls", 16, "Semi Bold", TEXT)
r = H(cmp, 16, w=1100, cross="min")
input_box(r, "", "What needs to be done?", label="Summary", req=True)
input_box(r, "Medium", label="Priority", kind="select")
input_box(r, "Grace Lee", label="Assignee", kind="user")
input_box(r, "14 Nov 2026", label="Due date", kind="date")
r = H(cmp, 16)
toggle(r, True)
toggle(r, False)
checkbox(r, True)
checkbox(r, False)
for nm in PEOPLE[:5]:
    avatar(r, nm, 32)
avatars(r, PEOPLE[:5], 28)
T(cmp, "Tabs", 16, "Semi Bold", TEXT)
tw = V(cmp, 0, w=800)
tabs(tw, ["Overview", "Commits", "Files changed", "Checks"], 2, [None, 11, 9, 4])
T(cmp, "Icons", 16, "Semi Bold", TEXT)
ig = H(cmp, 10, wrap=True, w=1700, crossGap=10)
for nm in sorted(ICONS):
    c = V(ig, 6, w=88, pad=[12, 4], radius=8, fill=SUNK, cross="center", name="Icon/" + nm)
    icon(c, nm, 24, TEXT)
    T(c, nm, 10.5, "Regular", MUTED)
pg.y += 1900
pg.section("Main components", "Top nav is a main component; every app screen uses an instance of it.")
build_components(0, pg.y)
pg.y += 200

# ---------------------------------------------------------------- Screens
PAGES = [
    ("Hub", [("Home & navigation", [sign_in, hub_home, hub_switcher]), ("Find & notify", [hub_search, hub_notifications])]),
    ("Track — issues & agile", [("Plan", [track_board, track_backlog, track_timeline]), ("Work", [track_issue, track_list, track_create]), ("Measure", [track_reports])]),
    ("Pages — knowledge", [("Spaces", [pages_directory, pages_space]), ("Read & write", [pages_view, pages_editor])]),
    ("Boards — visual work", [("Board", [boards_board, boards_card])]),
    ("Code — repos & CI", [("Repository", [code_source, code_pr, code_pipelines])]),
    ("Desk — service management", [("Agent", [desk_queue, desk_request]), ("Customer", [desk_portal])]),
    ("Pulse — operations", [("Respond", [pulse_alerts, pulse_oncall]), ("Communicate", [pulse_status])]),
    ("Goals", [("Goals", [goals_list, goal_detail])]),
    ("Admin", [("Organization", [admin_users, admin_billing])]),
]
for pname, sections in PAGES:
    PAGE[0] = Page(pname)
    for title, fns in sections:
        PAGE[0].section(title)
        for fn in fns:
            fn()
        PAGE[0].newrow()

for b in FIX_HUG:
    setp(b, sizingV="hug")
if "--measure" in sys.argv:
    for name, ref in SCREENS:
        cmd("node.get", {"id": R(ref), "fields": ["name", "height"]})
cmd("page.select", {"index": 0})
print(save(sys.argv[1]), "commands,", len(SCREENS), "screens")
