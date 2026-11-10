"""Hub, Track (issues & agile) and Pages (wiki) screens."""
from shell import *

PEOPLE = ["Tanmay Singh", "Grace Lee", "Ravi Kumar", "Aisha Khan", "Marco Silva", "Lena Fischer", "Omar Haddad", "Priya Nair"]

ISSUES = [
    ("ORB-412", "story", "Offline mode for saved itineraries", "In progress", 2, "Grace Lee", 5, ["mobile", "sync"]),
    ("ORB-418", "bug", "Crash when rotating map on Android 15", "To do", 1, "Ravi Kumar", 3, ["android"]),
    ("ORB-405", "task", "Migrate push service to v2 SDK", "In review", 3, "Tanmay Singh", 3, ["infra"]),
    ("ORB-399", "story", "Biometric sign-in on iOS", "Done", 2, "Aisha Khan", 8, ["ios", "auth"]),
    ("ORB-421", "bug", "Dark mode contrast on booking summary", "To do", 4, "Lena Fischer", 1, ["design"]),
    ("ORB-409", "story", "Share trip with friends via link", "In progress", 3, "Marco Silva", 5, ["growth"]),
    ("ORB-415", "task", "Add analytics events to checkout", "In review", 3, "Omar Haddad", 2, ["analytics"]),
    ("ORB-402", "bug", "Duplicate notifications after reinstall", "Done", 2, "Priya Nair", 3, ["push"]),
    ("ORB-423", "story", "Price alerts for saved routes", "To do", 3, "", 5, ["growth"]),
    ("ORB-417", "task", "Update App Store screenshots", "Done", 5, "Lena Fischer", 1, ["release"]),
]


# ================================================================ HUB
def hub_home():
    m = app_screen("Hub / For you", "Hub", "For you", est=1180)
    hero = H(m, 16, sw="fill", main="spaceBetween")
    hv = V(hero, 4)
    T(hv, "Good morning, Tanmay", 28, "Semi Bold", TEXT, ls=-0.6)
    T(hv, "Thursday, 6 November · You have 6 issues due this week and 2 reviews waiting", 14, "Regular", MUTED)
    btn(hero, "Customise", "secondary", "settings")
    # apps
    T(m, "Your apps", 15, "Semi Bold", TEXT)
    g = H(m, 12, sw="fill")
    for a, (col, ic, tag) in APPS.items():
        c = card(g, 10, 14, name="App card/" + a)
        app_tile(c, a, 36)
        T(c, a, 14, "Semi Bold", TEXT)
        T(c, tag, 12, "Regular", MUTED, sw="fill", lh="140%")
    row = H(m, 20, sw="fill", cross="min")
    left = V(row, 16, sw="fill")
    # assigned to me
    c = card(left, 0, 0, name="Assigned to me")
    h = H(c, 8, pad=[14, 16], sw="fill", main="spaceBetween", stroke=BORDER, sides=[0, 0, 1, 0])
    T(h, "Assigned to me", 15, "Semi Bold", TEXT)
    tabs_r = H(h, 4)
    for i, t in enumerate(["Open", "Due soon", "Done"]):
        x = H(tabs_r, 0, pad=[4, 10], radius=14, fill=SEL if i == 0 else None)
        T(x, t, 12.5, "Semi Bold" if i == 0 else "Medium", ACC_H if i == 0 else MUTED)
    for k, t, title, st, p, who, sp, lab in ISSUES[:6]:
        r = H(c, 10, pad=[0, 16], h=48, sw="fill", stroke=BORDER, sides=[0, 0, 1, 0], name=k)
        itype(r, t)
        T(r, k, 12.5, "Medium", MUTED, w=64)
        T(r, title, 13.5, "Medium", TEXT, sw="fill", trunc=True)
        prio_icon(r, p)
        lozenge(r, st)
        T(r, "Orbit Mobile", 12.5, "Regular", SUBTLE, w=90)
    # recent
    c = card(left, 0, 0, name="Recent")
    h = H(c, 8, pad=[14, 16], sw="fill", main="spaceBetween", stroke=BORDER, sides=[0, 0, 1, 0])
    T(h, "Recently viewed", 15, "Semi Bold", TEXT)
    T(h, "View all", 12.5, "Medium", LINK)
    for app, title, where, when in [("Pages", "Q4 mobile roadmap", "Engineering space", "12 min ago"),
                                    ("Code", "feat: offline itinerary cache #284", "orbit-mobile", "40 min ago"),
                                    ("Desk", "HELP-1093 · VPN not connecting from Lisbon", "IT Help", "1 h ago"),
                                    ("Boards", "Q4 Launch checklist", "Marketing", "Yesterday"),
                                    ("Goals", "Reach 1M monthly active travellers", "Company goals", "Yesterday")]:
        r = H(c, 12, pad=[0, 16], h=52, sw="fill", stroke=BORDER, sides=[0, 0, 1, 0], name=title)
        app_tile(r, app, 24)
        v = V(r, 1, sw="fill")
        T(v, title, 13.5, "Medium", TEXT, sw="fill", trunc=True)
        T(v, "%s · %s" % (app, where), 12, "Regular", MUTED)
        T(r, when, 12, "Regular", SUBTLE)
    right = V(row, 16, w=340)
    c = card(right, 12, 16, name="Reviews")
    section_head(c, "Waiting on you", "2")
    for t, sub, ic in [("Review PR #284 — offline cache", "orbit-mobile · Grace Lee", "pr"),
                       ("Approve page: Incident policy v3", "IT space · Omar Haddad", "page")]:
        r = H(c, 10, sw="fill", pad=10, radius=8, fill=SUNK)
        icon(r, ic, 18, ACCENT)
        v = V(r, 2, sw="fill")
        T(v, t, 13, "Medium", TEXT, sw="fill")
        T(v, sub, 12, "Regular", MUTED, sw="fill")
    c = card(right, 12, 16, name="Goals")
    section_head(c, "Your goals", "Open Goals")
    for g_, st, pct in [("Reach 1M monthly active travellers", "On track", 68), ("Cut crash rate below 0.2%", "At risk", 41),
                        ("Launch in 3 new markets", "On track", 75)]:
        v = V(c, 8, sw="fill")
        r = H(v, 8, sw="fill")
        T(r, g_, 13, "Medium", TEXT, sw="fill")
        lozenge(r, st)
        progress(v, [(pct, SUCCESS if st == "On track" else WARN)], w=306, h=6)
    c = card(right, 10, 16, name="Starred")
    section_head(c, "Starred")
    for a, t in [("Track", "Orbit Mobile"), ("Pages", "Engineering"), ("Desk", "IT Help"), ("Code", "orbit-api")]:
        r = H(c, 10, sw="fill")
        app_tile(r, a, 22)
        T(r, t, 13, "Medium", TEXT, sw="fill")
        icon(r, "star", 15, "#e5a50a")


def hub_switcher():
    s = screen("Hub / App switcher", est=900)
    topnav(s)
    body = box(s, "h", 0, sw="fill", h=844, cross="min", name="Body")
    sidebar(body, "Hub", "Apps", 844)
    m = V(body, 20, pad=[24, 32], sw="fill")
    T(m, "Good morning, Tanmay", 28, "Semi Bold", TEXT)
    for _ in range(3):
        box(m, None, h=140, sw="fill", radius=10, fill="#eceef2")
    p = V(s, 4, w=380, pad=12, fill=SURF, radius=12, stroke=BORDER, effects=SH3, name="App switcher panel", x=10, y=50, absolute=True)
    T(p, "SWITCH TO", 11, "Semi Bold", SUBTLE, ls=0.6)
    for i, (a, (col, ic, tag)) in enumerate(APPS.items()):
        r = H(p, 12, pad=[8, 10], sw="fill", radius=8, fill=SEL if i == 0 else None, name="Switch/" + a)
        app_tile(r, a, 32)
        v = V(r, 1, sw="fill")
        T(v, "Newxr " + a, 13.5, "Semi Bold", TEXT)
        T(v, tag, 12, "Regular", MUTED)
        if i == 0:
            icon(r, "check", 16, ACCENT, 2)
    hr(p)
    T(p, "RECENT", 11, "Semi Bold", SUBTLE, ls=0.6)
    for a, t in [("Track", "Orbit Mobile"), ("Pages", "Engineering"), ("Code", "orbit-mobile")]:
        r = H(p, 10, pad=[6, 10], sw="fill")
        app_tile(r, a, 20)
        T(r, t, 13, "Regular", TEXT)
    hr(p)
    r = H(p, 10, pad=[8, 10], sw="fill")
    icon(r, "grid", 16, MUTED)
    T(r, "Explore more apps", 13, "Medium", TEXT, sw="fill")
    r = H(p, 10, pad=[8, 10], sw="fill")
    icon(r, "settings", 16, MUTED)
    T(r, "Administration", 13, "Medium", TEXT)


def hub_search():
    m = app_screen("Hub / Search", "Hub", "Recent", est=1000)
    sb = H(m, 10, pad=[0, 14], h=48, sw="fill", radius=10, stroke=ACCENT, fill=SURF, strokeWeight=2, name="Search field")
    icon(sb, "search", 18, ACCENT)
    T(sb, "offline sync", 16, "Regular", TEXT, sw="fill")
    T(sb, "128 results", 12.5, "Regular", MUTED)
    tabs(m, ["All", "Issues", "Pages", "Code", "Requests", "People"], 0, [128, 46, 31, 37, 9, 5])
    row = H(m, 24, sw="fill", cross="min")
    f = V(row, 16, w=220)
    for title, opts in [("App", ["Track", "Pages", "Code", "Desk"]), ("Last updated", ["Any time", "Past week", "Past month"]),
                        ("Contributor", ["Grace Lee", "Tanmay Singh", "Ravi Kumar"])]:
        T(f, title, 12.5, "Semi Bold", TEXT)
        for i, o in enumerate(opts):
            r = H(f, 8)
            checkbox(r, i == 0)
            if o in APPS:
                app_tile(r, o, 18)
            T(r, o, 13, "Regular", TEXT)
    res = V(row, 10, sw="fill")
    for app, title, snip, meta in [
        ("Track", "ORB-412 · Offline mode for saved itineraries", "…cache itineraries locally so travellers can open bookings without a connection. Offline sync conflicts resolve by…", "Story · In progress · Grace Lee"),
        ("Pages", "Offline sync — technical design", "This document describes the **offline sync** engine: a write-ahead queue, conflict rules and how the API…", "Engineering · updated 2 days ago"),
        ("Code", "src/sync/OfflineQueue.kt", "class OfflineQueue(private val store: Store) { fun enqueue(op: SyncOp) …", "orbit-mobile · main"),
        ("Code", "PR #284 feat: offline itinerary cache", "Adds a Room-backed cache and background sync worker. Closes ORB-412.", "Open · 3 approvals needed"),
        ("Desk", "HELP-1071 · Itinerary not updating offline", "Customer reports saved trips don't refresh after reconnecting to Wi-Fi…", "IT Help · Waiting for support"),
        ("Pages", "Mobile release checklist", "…verify offline sync on airplane mode before every release candidate…", "Mobile space · updated 3 weeks ago")]:
        c = card(res, 6, 16, name="Result/" + title)
        r = H(c, 10, sw="fill")
        app_tile(r, app, 22)
        T(r, title, 14, "Semi Bold", LINK, sw="fill")
        T(c, snip, 13, "Regular", MUTED, sw="fill", lh="150%")
        T(c, meta, 12, "Regular", SUBTLE)


def hub_notifications():
    s = screen("Hub / Notifications", est=900)
    topnav(s)
    body = box(s, "h", 0, sw="fill", h=844, cross="min", name="Body")
    sidebar(body, "Hub", "Notifications", 844)
    m = V(body, 20, pad=[24, 32], sw="fill")
    T(m, "Good morning, Tanmay", 28, "Semi Bold", TEXT)
    for _ in range(3):
        box(m, None, h=140, sw="fill", radius=10, fill="#eceef2")
    p = V(s, 0, w=440, fill=SURF, radius=12, stroke=BORDER, effects=SH3, name="Notifications panel", x=980, y=52, absolute=True, clip=True)
    h = H(p, 8, pad=[16, 16, 8, 16], sw="fill", main="spaceBetween")
    T(h, "Notifications", 17, "Semi Bold", TEXT)
    r = H(h, 8)
    T(r, "Only unread", 12.5, "Regular", MUTED)
    toggle(r, True)
    tb = H(p, 0, pad=[0, 8], sw="fill")
    tabs(tb, ["Direct", "Watching", "Mentions"], 0, [4, 12, None])
    for who, verb, what, app, when, unread in [
        ("Grace Lee", "requested your review on", "PR #284 feat: offline itinerary cache", "Code", "5m", True),
        ("Ravi Kumar", "mentioned you in", "ORB-418 Crash when rotating map", "Track", "22m", True),
        ("Omar Haddad", "shared a page", "Incident policy v3", "Pages", "1h", True),
        ("Pulse", "paged you —", "P2 · checkout-api latency > 2s", "Pulse", "2h", True),
        ("Lena Fischer", "commented on", "Q4 Launch checklist", "Boards", "Yesterday", False),
        ("Aisha Khan", "posted an update on", "Cut crash rate below 0.2%", "Goals", "Yesterday", False)]:
        r = H(p, 12, pad=[12, 16], sw="fill", cross="min", fill=SEL if unread else None, stroke=BORDER, sides=[0, 0, 1, 0])
        c = box(r, None, w=36, h=36)
        av = avatar(box(c, "h", w=32, h=32, x=0, y=0), who, 32)
        t = box(c, None, w=18, h=18, x=18, y=18)
        app_tile(box(t, "h", w=18, h=18, x=0, y=0), app, 18)
        v = V(r, 3, sw="fill")
        T(v, "%s %s %s" % (who, verb, what), 13, "Regular", TEXT, sw="fill", lh="145%")
        T(v, when, 12, "Regular", SUBTLE)
        if unread:
            node("ellipse", r, "unread", w=8, h=8, fill=ACCENT)
    f = H(p, 0, pad=12, sw="fill", main="center")
    T(f, "Mark all as read", 13, "Medium", LINK)


# ================================================================ TRACK
def kcard(parent, k, t, title, st, p, who, sp, lab, flag=False):
    c = V(parent, 10, pad=12, sw="fill", fill=SURF, radius=8, stroke=BORDER, effects=SH1, name="Card/" + k)
    T(c, title, 13.5, "Medium", TEXT, sw="fill", lh="140%")
    if lab:
        r = H(c, 6, wrap=True, sw="fill")
        for l in lab:
            label_chip(r, l, "#4536dc" if l in ("mobile", "ios", "android") else MUTED, ACC_SOFT if l in ("mobile", "ios", "android") else "#eceef2")
    f = H(c, 6, sw="fill")
    itype(f, t)
    T(f, k, 12, "Medium", MUTED, sw="fill")
    if flag:
        icon(f, "flag", 14, DANGER)
    prio_icon(f, p, 15)
    sb = H(f, 0, pad=[0, 6], h=18, radius=9, fill="#eceef2")
    T(sb, str(sp), 11, "Semi Bold", MUTED)
    if who:
        avatar(f, who, 22)
    else:
        a = H(f, 0, w=22, h=22, radius=11, fill="#eceef2", main="center")
        icon(a, "user", 13, SUBTLE)
    return c


def track_board():
    m = app_screen("Track / Board", "Track", "Board", ["Projects", "Orbit Mobile"], est=1000)
    page_title(m, "ORB Sprint 24", [("3 days left", "clock"), "share", "more"], "Complete sprint", "Goal: Ship offline itineraries to beta")
    toolbar(m, "Search board", ["Epic", "Type", "Label"], ["Tanmay Singh", "Grace Lee", "Ravi Kumar", "Aisha Khan"], ["Group: None", "chart", "settings"])
    cols = H(m, 12, sw="fill", cross="min", name="Columns")
    for st in ["To do", "In progress", "In review", "Done"]:
        items = [i for i in ISSUES if i[3] == st]
        c = V(cols, 8, pad=[10, 8, 12, 8], sw="fill", fill="#ebecf1", radius=10, name="Column/" + st, minHeight=640)
        h = H(c, 8, pad=[2, 4, 6, 4], sw="fill")
        T(h, st.upper(), 12, "Semi Bold", MUTED, ls=0.4)
        T(h, str(len(items)), 12, "Medium", SUBTLE, sw="fill")
        if st == "Done":
            icon(h, "check", 15, SUCCESS, 2)
        for i in items:
            kcard(c, *i, flag=i[0] == "ORB-418")
        if st == "To do":
            r = H(c, 6, pad=[6, 6], sw="fill", radius=6)
            icon(r, "plus", 15, MUTED)
            T(r, "Create issue", 13, "Medium", MUTED)


def track_backlog():
    m = app_screen("Track / Backlog", "Track", "Backlog", ["Projects", "Orbit Mobile"], est=1000)
    page_title(m, "Backlog", ["share", "more"])
    toolbar(m, "Search backlog", ["Epic", "Type", "Version"], ["Tanmay Singh", "Grace Lee", "Ravi Kumar"], ["Insights"])

    def group(title, sub, rows, btn_label, stats):
        g = V(m, 0, sw="fill", fill=SURF, radius=10, stroke=BORDER, clip=True, name="Group/" + title)
        h = H(g, 10, pad=[12, 14], sw="fill", fill=SUNK, stroke=BORDER, sides=[0, 0, 1, 0])
        icon(h, "chevron-down", 15, MUTED)
        T(h, title, 14, "Semi Bold", TEXT)
        T(h, sub, 12.5, "Regular", MUTED, sw="fill")
        for n, col in stats:
            b = H(h, 0, pad=[1, 7], radius=9, fill=col)
            T(b, str(n), 11.5, "Semi Bold", "#ffffff" if col != "#dfe1e6" else TEXT)
        btn(h, btn_label, "secondary", None, "sm")
        btn(h, None, "ghost", "more", "sm")
        for k, t, title_, st, p, who, sp, lab in rows:
            r = H(g, 10, pad=[0, 14], h=42, sw="fill", stroke=BORDER, sides=[0, 0, 1, 0], name=k)
            icon(r, "drag", 14, BSTRONG)
            itype(r, t)
            T(r, k, 12.5, "Medium", MUTED, w=64)
            T(r, title_, 13.5, "Regular", TEXT, sw="fill", trunc=True)
            for l in lab[:1]:
                label_chip(r, l)
            lozenge(r, st)
            prio_icon(r, p)
            sb = H(r, 0, pad=[0, 6], h=18, radius=9, fill="#eceef2")
            T(sb, str(sp), 11, "Semi Bold", MUTED)
            if who:
                avatar(r, who, 22)
            else:
                box(r, None, w=22, h=22, radius=11, fill="#eceef2")
        a = H(g, 6, pad=[10, 14], sw="fill")
        icon(a, "plus", 15, MUTED)
        T(a, "Create issue", 13, "Medium", MUTED)
    group("ORB Sprint 24", "3 Nov – 17 Nov · 10 issues", ISSUES[:7], "Complete sprint", [(14, "#dfe1e6"), (13, INFO), (12, SUCCESS)])
    group("ORB Sprint 25", "17 Nov – 1 Dec · 4 issues", ISSUES[7:], "Start sprint", [(9, "#dfe1e6"), (0, INFO), (0, SUCCESS)])
    group("Backlog", "23 issues", [("ORB-430", "story", "Trip timeline widget for home screen", "To do", 3, "", 5, ["ios"]),
                                   ("ORB-431", "bug", "Currency rounds incorrectly for JPY", "To do", 2, "Omar Haddad", 2, ["payments"]),
                                   ("ORB-432", "task", "Remove legacy GraphQL client", "To do", 4, "", 3, ["infra"]),
                                   ("ORB-433", "story", "Accessibility pass on onboarding", "To do", 3, "Lena Fischer", 3, ["a11y"])], "Create sprint", [(13, "#dfe1e6")])


def track_issue():
    m = app_screen("Track / Issue", "Track", "Board", None, est=1200, pad=[16, 32, 40, 32])
    bar = H(m, 8, sw="fill")
    T(bar, "Projects  /  Orbit Mobile  /", 12.5, "Regular", MUTED)
    itype(bar, "epic", 14)
    T(bar, "ORB-380 Offline experience  /", 12.5, "Regular", MUTED)
    itype(bar, "story", 14)
    T(bar, "ORB-412", 12.5, "Medium", TEXT, sw="fill")
    for ic in ("eye", "thumbs", "share", "more"):
        btn(bar, None, "ghost", ic, "sm")
    row = H(m, 32, sw="fill", cross="min")
    left = V(row, 20, sw="fill")
    T(left, "Offline mode for saved itineraries", 24, "Semi Bold", TEXT, sw="fill", ls=-0.3)
    r = H(left, 8)
    for b, ic in [("Attach", "paperclip"), ("Add child", "list"), ("Link issue", "link"), ("Create branch", "branch")]:
        btn(r, b, "subtle", ic, "sm")
    T(left, "Description", 14, "Semi Bold", TEXT)
    d = V(left, 10, sw="fill")
    T(d, "As a traveller with a poor connection, I want my saved itineraries, tickets and maps to open without the network, so I can get through airports and stations without stress.", 14, "Regular", TEXT, sw="fill", lh="160%")
    T(d, "Acceptance criteria", 13.5, "Semi Bold", TEXT)
    for x in ["Itineraries saved in the last 30 days open in airplane mode", "Boarding passes render from cache within 500 ms",
              "Edits made offline sync when the device reconnects; conflicts prompt the user", "Storage use is capped at 200 MB with LRU eviction"]:
        rr = H(d, 10, sw="fill", cross="min")
        checkbox(rr, x.startswith("Itin") or x.startswith("Board"))
        T(rr, x, 13.5, "Regular", TEXT, sw="fill", lh="150%")
    ch = card(left, 0, 0, name="Child issues")
    h = H(ch, 8, pad=[12, 14], sw="fill", stroke=BORDER, sides=[0, 0, 1, 0])
    T(h, "Child issues", 13.5, "Semi Bold", TEXT, sw="fill")
    progress(h, [(60, SUCCESS)], w=160, h=6)
    T(h, "60% done", 12, "Medium", MUTED)
    for k, t, tt, st, who in [("ORB-413", "subtask", "Room schema for itinerary cache", "Done", "Grace Lee"),
                              ("ORB-414", "subtask", "Background sync worker", "Done", "Ravi Kumar"),
                              ("ORB-416", "subtask", "Conflict resolution UI", "In progress", "Lena Fischer"),
                              ("ORB-419", "subtask", "Storage eviction policy", "To do", "")]:
        rr = H(ch, 10, pad=[0, 14], h=42, sw="fill", stroke=BORDER, sides=[0, 0, 1, 0])
        itype(rr, t)
        T(rr, k, 12.5, "Medium", MUTED, w=62)
        T(rr, tt, 13.5, "Regular", TEXT, sw="fill")
        lozenge(rr, st)
        if who:
            avatar(rr, who, 22)
        else:
            box(rr, None, w=22, h=22, radius=11, fill="#eceef2")
    T(left, "Activity", 14, "Semi Bold", TEXT)
    tabs(left, ["Comments", "History", "Work log"], 0)
    composer(left)
    comment(left, "Ravi Kumar", "2 hours ago", "Sync worker is merged behind the offline_v2 flag. QA can test on build 4.12.0-rc2 — use the debug menu to simulate a dropped connection.", "👍 3")
    comment(left, "Grace Lee", "Yesterday at 16:20", "@Lena Fischer the conflict prompt needs copy for the case where a booking was cancelled server-side while offline. Can you add it to the Figma flow?")
    right = V(row, 14, w=340)
    sr = H(right, 8)
    s = H(sr, 6, pad=[0, 12], h=32, radius=6, fill=INFO)
    T(s, "In progress", 13, "Semi Bold", "#ffffff")
    icon(s, "chevron-down", 15, "#ffffff")
    btn(sr, None, "secondary", "bolt")
    ai = H(right, 10, pad=12, sw="fill", radius=10, fill=VIOLET_SOFT)
    icon(ai, "sparkle", 18, VIOLET)
    v = V(ai, 2, sw="fill")
    T(v, "Summarise with Newxr AI", 13, "Semi Bold", VIOLET)
    T(v, "14 comments, 3 linked PRs", 12, "Regular", MUTED)
    dc = card(right, 0, [6, 16], name="Details")
    T(dc, "Details", 14, "Semi Bold", TEXT, sw="fill")
    box(dc, None, h=8, sw="fill")
    field_row(dc, "Assignee", lambda v: (avatar(v, "Grace Lee", 24), T(v, "Grace Lee", 13, "Regular", TEXT)))
    field_row(dc, "Reporter", lambda v: (avatar(v, "Tanmay Singh", 24), T(v, "Tanmay Singh", 13, "Regular", TEXT)))
    field_row(dc, "Priority", lambda v: prio(v, 2))
    field_row(dc, "Labels", lambda v: (label_chip(v, "mobile"), label_chip(v, "sync")))
    field_row(dc, "Sprint", lambda v: T(v, "ORB Sprint 24", 13, "Regular", LINK))
    field_row(dc, "Story points", "5")
    field_row(dc, "Fix version", lambda v: T(v, "4.12.0", 13, "Regular", LINK))
    field_row(dc, "Due date", "14 Nov 2026")
    dv = card(right, 8, 16, name="Development")
    T(dv, "Development", 14, "Semi Bold", TEXT)
    for ic, t, st in [("branch", "2 branches", None), ("commit", "11 commits", None), ("pr", "PR #284", "In review"), ("play", "Build 4.12.0-rc2", "Passed")]:
        rr = H(dv, 8, sw="fill")
        icon(rr, ic, 16, MUTED)
        T(rr, t, 13, "Medium", LINK, sw="fill")
        if st:
            lozenge(rr, st)
    T(right, "Created 28 Oct 2026 · Updated 2 hours ago", 12, "Regular", SUBTLE)


def track_timeline():
    m = app_screen("Track / Timeline", "Track", "Timeline", ["Projects", "Orbit Mobile"], est=900)
    page_title(m, "Timeline", ["share", "more"], None)
    toolbar(m, "Search timeline", ["Epic", "Status"], None, ["Today", "Weeks", "Months", "Quarters"])
    g = V(m, 0, sw="fill", fill=SURF, radius=10, stroke=BORDER, clip=True, name="Gantt")
    months = ["Sep", "Oct", "Nov", "Dec", "Jan", "Feb"]
    colw = (1128 - 300) / len(months)
    h = H(g, 0, h=40, sw="fill", fill=SUNK, stroke=BORDER, sides=[0, 0, 1, 0])
    hl = H(h, 0, pad=[0, 14], w=300, h=40, stroke=BORDER, sides=[0, 1, 0, 0])
    T(hl, "Epic", 12, "Semi Bold", MUTED)
    for mo in months:
        c = H(h, 0, w=colw, h=40, main="center", stroke=BORDER, sides=[0, 1, 0, 0])
        T(c, mo + (" 2026" if mo in ("Sep",) else (" 2027" if mo == "Jan" else "")), 12, "Semi Bold", MUTED)
    epics = [("ORB-380", "Offline experience", 1.0, 2.3, S1, 72, "Grace Lee"), ("ORB-350", "Biometric sign-in", 0.2, 1.3, "#2f9a57", 100, "Aisha Khan"),
             ("ORB-390", "Social trip sharing", 1.5, 3.4, "#e8892b", 35, "Marco Silva"), ("ORB-400", "Price alerts", 2.4, 4.2, "#d9466f", 10, ""),
             ("ORB-410", "Android 15 readiness", 0.8, 2.1, "#2f6fde", 55, "Ravi Kumar"), ("ORB-420", "Checkout redesign", 3.0, 5.6, "#0e9f8e", 0, "Lena Fischer"),
             ("ORB-425", "Accessibility AA", 2.0, 4.8, VIOLET, 18, "Lena Fischer")]
    for k, name, a, b, col, pct, who in epics:
        r = H(g, 0, h=52, sw="fill", stroke=BORDER, sides=[0, 0, 1, 0], name=k)
        l = H(r, 8, pad=[0, 14], w=300, h=52, stroke=BORDER, sides=[0, 1, 0, 0])
        icon(l, "chevron-right", 14, MUTED)
        itype(l, "epic")
        T(l, k, 12, "Medium", MUTED)
        T(l, name, 13.5, "Medium", TEXT, sw="fill", trunc=True)
        lane = box(r, None, w=1128 - 300, h=52, name="Lane")
        for i in range(1, len(months)):
            box(lane, None, w=1, h=52, fill=CGRID, x=round(i * colw), y=0)
        bar = H(lane, 8, pad=[0, 10], w=round((b - a) * colw), h=28, radius=6, fill=col, x=round(a * colw), y=12, name="Bar")
        T(bar, "%d%%" % pct, 11.5, "Semi Bold", "#ffffff")
        if who:
            box(bar, None, h=1, sw="fill")
            avatar(bar, who, 20, "#ffffff", col)
    today = box(g, None, w=2, h=52 * len(epics) + 40, fill=DANGER, x=300 + round(2.2 * colw), y=0, absolute=True, name="Today")
    r = H(g, 6, pad=[12, 14], sw="fill")
    icon(r, "plus", 15, MUTED)
    T(r, "Create epic", 13, "Medium", MUTED)


def track_list():
    m = app_screen("Track / List", "Track", "List", ["Projects", "Orbit Mobile"], est=900)
    page_title(m, "List", [("Export", "download"), "share"], "Create issue")
    toolbar(m, "Search list", ["Type", "Status", "Assignee", "More filters"], None, ["Save filter", "settings"])
    q = H(m, 8, pad=[8, 12], sw="fill", radius=8, fill=SURF, stroke=BORDER)
    icon(q, "code", 15, MUTED)
    T(q, 'project = ORB AND sprint in openSprints() AND status != Done ORDER BY priority DESC', 12.5, "Regular", TEXT, mono=True, sw="fill")
    T(q, "Switch to basic", 12, "Medium", LINK)
    cols = [("Key", 1.1, "key"), ("Summary", 3.4, "text"), ("Status", 1.2, "status"), ("Assignee", 1.5, "user"),
            ("Priority", 1.0, "prio"), ("Points", 0.6, "muted"), ("Labels", 1.3, "chips"), ("Updated", 1.0, "muted")]
    rows = [[(i[0], i[1]), i[2], i[3], i[5], i[4], str(i[6]), i[7], u] for i, u in
            zip(ISSUES, ["2h ago", "3h ago", "5h ago", "1d ago", "1d ago", "2d ago", "2d ago", "3d ago", "4d ago", "5d ago"])]
    table(m, cols, rows, check=True, selected=1, footer="1–10 of 47 issues")


def track_reports():
    m = app_screen("Track / Reports", "Track", "Reports", ["Projects", "Orbit Mobile"], est=1000)
    page_title(m, "Sprint report", [("ORB Sprint 24", "chevron-down"), ("Export", "download")])
    k = H(m, 12, sw="fill")
    kpi(k, "Committed", "41 pts", "10 issues", MUTED, "flag")
    kpi(k, "Completed", "12 pts", "29% of commitment", SUCCESS, "check-circle")
    kpi(k, "Scope change", "+5 pts", "2 issues added", WARN, "arrow-up")
    kpi(k, "Avg cycle time", "3.4 d", "-0.6 d vs last sprint", SUCCESS, "clock")
    r = H(m, 16, sw="fill", cross="min")
    line_chart(r, "Burndown", [41, 41, 38, 36, 36, 33, 30, 29], [41, 37, 33, 29, 25, 20, 16, 12], w=500, h=200)
    bar_chart(r, "Velocity (last 6 sprints)", ["S19", "S20", "S21", "S22", "S23", "S24"], [[34, 38, 31, 40, 42, 41], [30, 36, 29, 37, 40, 12]],
              legend=("Committed", "Completed"))
    r = H(m, 16, sw="fill", cross="min")
    c = card(r, 12, 20, name="Issues by status")
    T(c, "Issues by status", 14, "Semi Bold", TEXT)
    progress(c, [(30, "#b8bdc9"), (20, INFO), (20, VIOLET), (30, SUCCESS)], w=500, h=14)
    for st, n, col in [("To do", 3, "#b8bdc9"), ("In progress", 2, INFO), ("In review", 2, VIOLET), ("Done", 3, SUCCESS)]:
        rr = H(c, 8, sw="fill")
        box(rr, None, w=10, h=10, radius=2, fill=col)
        T(rr, st, 13, "Regular", TEXT, sw="fill")
        T(rr, str(n), 13, "Semi Bold", TEXT)
    c = card(r, 12, 20, name="Workload")
    T(c, "Workload by assignee", 14, "Semi Bold", TEXT)
    for who, pts in [("Grace Lee", 10), ("Ravi Kumar", 8), ("Tanmay Singh", 6), ("Lena Fischer", 7), ("Marco Silva", 5)]:
        rr = H(c, 10, sw="fill")
        avatar(rr, who, 22)
        T(rr, who, 13, "Regular", TEXT, w=120)
        progress(rr, [(pts * 10, S1)], w=300, h=8)
        T(rr, "%d pts" % pts, 12.5, "Medium", MUTED)


def track_create():
    s = screen("Track / Create issue", est=900)
    topnav(s)
    body = box(s, "h", 0, sw="fill", h=844, cross="min", name="Body")
    sidebar(body, "Track", "Board", 844)
    m = V(body, 16, pad=[24, 32], sw="fill")
    T(m, "ORB Sprint 24", 24, "Semi Bold", TEXT)
    cl = H(m, 12, sw="fill", cross="min")
    for _ in range(4):
        box(cl, None, h=560, sw="fill", radius=10, fill="#ebecf1")
    md = modal_overlay(s, 640, 60)
    h = H(md, 8, pad=[18, 20], sw="fill", main="spaceBetween")
    T(h, "Create issue", 18, "Semi Bold", TEXT)
    btn(h, None, "ghost", "x")
    b = V(md, 14, pad=[0, 20, 20, 20], sw="fill")
    T(b, "Required fields are marked with an asterisk *", 12.5, "Regular", MUTED)
    r = H(b, 12, sw="fill")
    input_box(r, "Orbit Mobile (ORB)", label="Project", kind="select", req=True)
    input_box(r, "Story", label="Issue type", kind="select", req=True)
    input_box(b, "", "What needs to be done?", label="Summary", req=True)
    input_box(b, "", "Describe the work, add context, paste screenshots…", kind="textarea", label="Description", h=110)
    r = H(b, 12, sw="fill")
    input_box(r, "Grace Lee", label="Assignee", kind="user")
    input_box(r, "Medium", label="Priority", kind="select")
    r = H(b, 12, sw="fill")
    input_box(r, "ORB Sprint 24", label="Sprint", kind="select")
    input_box(r, "mobile, sync", label="Labels", kind="select")
    f = H(md, 8, pad=[14, 20], sw="fill", fill=SUNK, stroke=BORDER, sides=[1, 0, 0, 0])
    checkbox(f, False)
    T(f, "Create another", 13, "Regular", TEXT, sw="fill")
    btn(f, "Cancel", "ghost")
    btn(f, "Create", "primary")


# ================================================================ PAGES
def page_tree(nav):
    box(nav, None, h=12, sw="fill")
    h = H(nav, 8, sw="fill")
    T(h, "CONTENT", 11, "Semi Bold", SUBTLE, ls=0.6, sw="fill")
    icon(h, "plus", 14, MUTED)
    for depth, t, act in [(0, "Engineering home", False), (0, "Architecture", False), (1, "Service map", False),
                          (1, "Offline sync — technical design", True), (1, "API guidelines", False), (0, "Runbooks", False),
                          (1, "On-call handbook", False), (1, "Incident policy v3", False), (0, "Onboarding", False),
                          (0, "Meeting notes", False), (0, "Retrospectives", False)]:
        r = H(nav, 6, pad=[0, 8, 0, 8 + depth * 16], h=30, sw="fill", radius=6, fill=SEL if act else None)
        icon(r, "chevron-right" if depth == 0 else "page", 13, MUTED)
        T(r, t, 13, "Semi Bold" if act else "Regular", ACC_H if act else TEXT, sw="fill", trunc=True)


def pages_space():
    m = app_screen("Pages / Space overview", "Pages", "Overview", None, est=1050, extra=page_tree, pad=[0, 0, 40, 0], gap=0)
    cov = V(m, 10, pad=[48, 48, 32, 48], sw="fill", fill="#0e9f8e", name="Cover",
            fills=[{"type": "linearGradient", "stops": ["#0e9f8e", "#2f6fde"]}])
    T(cov, "SPACE", 11.5, "Semi Bold", "#d7fff7", ls=0.8)
    T(cov, "Engineering", 34, "Bold", "#ffffff", ls=-0.6)
    T(cov, "How we build Orbit: architecture, runbooks, decisions and team rituals.", 15, "Regular", "#e7fbf7")
    r = H(cov, 8)
    avatars(r, PEOPLE[:5], 26)
    T(r, "42 contributors · 318 pages", 12.5, "Medium", "#e7fbf7")
    body = V(m, 20, pad=[28, 48], sw="fill")
    r = H(body, 8)
    btn(r, "Create", "primary", "plus")
    btn(r, "Templates", "secondary", "page")
    btn(r, "Whiteboard", "secondary", "edit")
    btn(r, "Star", "secondary", "star")
    T(body, "Pinned", 15, "Semi Bold", TEXT)
    g = H(body, 14, sw="fill")
    for t, d, ic in [("Architecture", "Service map, data flows, ADRs", "globe"), ("On-call handbook", "Escalation, paging and runbooks", "headset"),
                     ("Onboarding", "Your first two weeks at Orbit", "users")]:
        c = card(g, 8, 18, name="Pinned/" + t)
        ib = H(c, 0, w=36, h=36, radius=8, fill="#dff5ef", main="center")
        icon(ib, ic, 18, "#0b7a6d")
        T(c, t, 14.5, "Semi Bold", TEXT)
        T(c, d, 12.5, "Regular", MUTED, sw="fill")
    row = H(body, 20, sw="fill", cross="min")
    l = V(row, 0, sw="fill", fill=SURF, radius=10, stroke=BORDER, clip=True)
    hh = H(l, 0, pad=[14, 16], sw="fill", stroke=BORDER, sides=[0, 0, 1, 0])
    T(hh, "Recently updated", 14, "Semi Bold", TEXT)
    for t, who, when, kind in [("Offline sync — technical design", "Grace Lee", "2 hours ago", "page"),
                               ("Sprint 24 retrospective", "Tanmay Singh", "Yesterday", "page"),
                               ("ADR-019: Room over Realm for local storage", "Ravi Kumar", "2 days ago", "page"),
                               ("Q4 platform priorities", "Aisha Khan", "3 days ago", "megaphone"),
                               ("Incident policy v3", "Omar Haddad", "4 days ago", "page")]:
        r = H(l, 12, pad=[0, 16], h=56, sw="fill", stroke=BORDER, sides=[0, 0, 1, 0])
        icon(r, kind, 18, "#0e9f8e")
        v = V(r, 1, sw="fill")
        T(v, t, 13.5, "Medium", TEXT)
        T(v, "%s updated %s" % (who, when), 12, "Regular", MUTED)
        avatar(r, who, 24)
    rr = card(row, 12, 18, w=320, sw=None, name="Space info")
    T(rr, "About this space", 14, "Semi Bold", TEXT)
    for k, v in [("Owner", "Tanmay Singh"), ("Key", "ENG"), ("Visibility", "Everyone at Northwind"), ("Linked project", "Orbit Mobile")]:
        x = H(rr, 8, sw="fill")
        T(x, k, 12.5, "Regular", MUTED, w=96)
        T(x, v, 12.5, "Medium", LINK if k == "Linked project" else TEXT, sw="fill")


def pages_view():
    m = app_screen("Pages / Page", "Pages", "Overview", None, est=1500, extra=page_tree, pad=[16, 48, 48, 48])
    bar = H(m, 8, sw="fill")
    T(bar, "Engineering  /  Architecture", 12.5, "Regular", MUTED, sw="fill")
    btn(bar, "Edit", "secondary", "edit", "sm")
    btn(bar, "Share", "primary", "share", "sm")
    for ic in ("star", "eye", "more"):
        btn(bar, None, "ghost", ic, "sm")
    a = V(m, 18, w=820, name="Article")
    T(a, "Offline sync — technical design", 32, "Bold", TEXT, sw="fill", ls=-0.6)
    meta = H(a, 8)
    avatar(meta, "Grace Lee", 24)
    T(meta, "Owned by Grace Lee · Last updated 2 hours ago · 8 min read", 12.5, "Regular", MUTED)
    lozenge(meta, "Published")
    call = H(a, 12, pad=[12, 16], sw="fill", radius=8, fill=INFO_SOFT, cross="min", name="Info panel")
    icon(call, "info", 18, INFO)
    T(call, "This design is approved for 4.12. Changes after 10 Nov need sign-off from the mobile platform guild.", 13.5, "Regular", TEXT, sw="fill", lh="155%")
    T(a, "Context", 20, "Semi Bold", TEXT)
    T(a, "Travellers often open their itineraries in places with no connectivity — airports, trains, foreign networks. Today the app needs a live request to render a trip, which drives 18% of our 1-star reviews. This page describes a local-first sync engine for itineraries, tickets and maps.", 15, "Regular", TEXT, sw="fill", lh="170%")
    T(a, "Design", 20, "Semi Bold", TEXT)
    for x in ["Room database is the source of truth on device; the UI only reads from it.",
              "Writes go to a durable outbox and are replayed by a WorkManager job with exponential back-off.",
              "Server responses carry a version vector; conflicts surface a prompt instead of silently overwriting."]:
        r = H(a, 10, sw="fill", cross="min")
        bullet(r)
        T(r, x, 15, "Regular", TEXT, sw="fill", lh="165%")
    cb = V(a, 4, pad=16, sw="fill", radius=8, fill="#1d2130", name="Code block")
    for ln in ["@Entity(tableName = \"itinerary\")", "data class ItineraryEntity(", "    @PrimaryKey val id: String,",
               "    val payload: String,", "    val version: Long,", "    val syncedAt: Instant?", ")"]:
        T(cb, ln, 13, "Regular", "#e6e4ff", mono=True)
    T(a, "Rollout", 20, "Semi Bold", TEXT)
    tb = V(a, 0, sw="fill", radius=8, stroke=BORDER, clip=True, name="Table")
    for i, row_ in enumerate([("Phase", "Audience", "Date", "Status"), ("Internal dogfood", "Employees", "10 Nov", "Done"),
                              ("Beta", "5% Android, 5% iOS", "17 Nov", "In progress"), ("GA", "Everyone", "1 Dec", "To do")]):
        rr = H(tb, 0, sw="fill", fill=SUNK if i == 0 else SURF, stroke=BORDER if i < 3 else None, sides=[0, 0, 1, 0])
        for j, v in enumerate(row_):
            c = H(rr, 0, pad=[10, 12], w=205)
            if i and j == 3:
                lozenge(c, v)
            else:
                T(c, v, 13.5, "Semi Bold" if i == 0 else "Regular", TEXT)
    hr(a)
    r = H(a, 10)
    for e, n in [("👍", 12), ("🎉", 4), ("👀", 2)]:
        x = H(r, 4, pad=[4, 10], radius=14, stroke=BORDER, fill=SURF)
        T(x, "%s %d" % (e, n), 12.5, "Medium", TEXT)
    T(a, "3 comments", 15, "Semi Bold", TEXT)
    composer(a)
    comment(a, "Ravi Kumar", "1 hour ago", "Should the outbox also cover seat-selection changes? Those are the most common offline edits in the analytics.")
    comment(a, "Grace Lee", "45 minutes ago", "Good call — adding a section on seat changes. They'll need the same conflict prompt.", "❤️ 2")


def pages_editor():
    s = screen("Pages / Editor", est=1000, fill=SURF)
    tb = H(s, 10, pad=[0, 16], h=56, sw="fill", stroke=BORDER, sides=[0, 0, 1, 0], name="Editor bar")
    btn(tb, None, "ghost", "arrow-left")
    logo(tb, 18, mark=24)
    T(tb, "/  Engineering  /  Retrospectives", 13, "Regular", MUTED)
    lozenge(tb, "Draft")
    T(tb, "Saved just now", 12, "Regular", SUBTLE, sw="fill")
    avatars(tb, ["Grace Lee", "Ravi Kumar"], 28)
    btn(tb, "Preview", "secondary", "eye")
    btn(tb, "Publish", "primary")
    btn(tb, None, "ghost", "more")
    ft = H(s, 2, pad=[6, 16], sw="fill", stroke=BORDER, sides=[0, 0, 1, 0], name="Format toolbar", main="center", fill=SUNK)
    for i, ic in enumerate(["heading", "bold", "italic", "list", "checklist", "quote", "link", "image", "table", "code", "at", "smile", "sparkle"]):
        if i in (3, 6, 11):
            box(ft, None, w=1, h=20, fill=BSTRONG)
        b = H(ft, 0, w=32, h=32, radius=6, main="center", fill=SEL if ic == "bold" else None)
        icon(b, ic, 17, VIOLET if ic == "sparkle" else (ACC_H if ic == "bold" else MUTED))
    body = H(s, 0, sw="fill", main="center")
    a = V(body, 18, pad=[48, 0, 80, 0], w=760, name="Canvas")
    T(a, "Sprint 24 retrospective", 34, "Bold", TEXT, ls=-0.6)
    r = H(a, 8)
    for x in ["Date: 17 Nov 2026", "Team: Orbit Mobile", "Facilitator: @Tanmay Singh"]:
        label_chip(r, x)
    T(a, "What went well", 20, "Semi Bold", TEXT)
    for x in ["Offline sync landed behind a flag three days early", "Crash-free sessions up to 99.71%", "Pairing on the conflict UI cut review time in half"]:
        rr = H(a, 10, sw="fill", cross="min")
        bullet(rr)
        T(rr, x, 15, "Regular", TEXT, sw="fill", lh="165%")
    T(a, "What could be better", 20, "Semi Bold", TEXT)
    rr = H(a, 10, sw="fill", cross="min")
    bullet(rr)
    t = T(rr, "Scope grew by 5 points mid-sprint|", 15, "Regular", TEXT, sw="fill", lh="165%")
    sl = V(a, 2, w=320, pad=6, radius=10, fill=SURF, stroke=BORDER, effects=SH2, name="Slash menu")
    T(sl, "  INSERT", 11, "Semi Bold", SUBTLE, ls=0.6)
    for i, (ic, t_, d) in enumerate([("checklist", "Action item", "Track a to-do with an owner"), ("table", "Table", "Insert a 3×3 table"),
                                     ("board", "Track issue", "Embed or create an issue"), ("sparkle", "Write with AI", "Draft from a prompt"),
                                     ("info", "Info panel", "Highlight a note")]):
        r = H(sl, 10, pad=[8, 10], sw="fill", radius=6, fill=SEL if i == 0 else None)
        ib = H(r, 0, w=30, h=30, radius=6, fill=SUNK, main="center")
        icon(ib, ic, 16, VIOLET if ic == "sparkle" else MUTED)
        v = V(r, 0, sw="fill")
        T(v, t_, 13, "Medium", TEXT)
        T(v, d, 11.5, "Regular", MUTED)
    T(a, "Action items", 20, "Semi Bold", TEXT)
    for done, x, who in [(True, "Add mid-sprint scope check to stand-up", "Tanmay Singh"), (False, "Write conflict-prompt copy for cancelled bookings", "Lena Fischer")]:
        rr = H(a, 10, sw="fill")
        checkbox(rr, done)
        T(rr, x, 15, "Regular", MUTED if done else TEXT, sw="fill")
        avatar(rr, who, 24)


def pages_directory():
    m = app_screen("Pages / Spaces", "Pages", "Templates", None, est=900)
    page_title(m, "Spaces", [("Archived", "folder")], "Create space", "Every team's knowledge in one place")
    toolbar(m, "Filter spaces", ["Type", "Owner"], None, ["grid", "list"])
    g = H(m, 16, sw="fill", wrap=True, crossGap=16, name="Space grid")
    for name, key, desc, col, n in [("Engineering", "ENG", "Architecture, runbooks and ADRs", "#0e9f8e", 318), ("Product", "PRD", "Specs, research and roadmaps", "#5a4bf5", 204),
                                    ("Design", "DES", "Design system and critiques", "#d9466f", 156), ("IT Help", "IT", "Policies and how-to articles", "#2f6fde", 97),
                                    ("Marketing", "MKT", "Campaigns, brand and launches", "#e8892b", 141), ("People", "PPL", "Handbook, benefits and hiring", "#2f9a57", 88),
                                    ("Sales", "SAL", "Playbooks and battlecards", "#7c3aed", 63), ("Tanmay Singh", "~TS", "Personal space", "#5b6170", 21)]:
        c = V(g, 0, w=270, fill=SURF, radius=10, stroke=BORDER, effects=SH1, clip=True, name="Space/" + name)
        box(c, None, h=64, sw="fill", fill=col, name="Banner")
        b = V(c, 6, pad=[0, 16, 16, 16], sw="fill")
        ic = H(b, 0, w=44, h=44, radius=10, fill=SURF, main="center", stroke=BORDER, effects=SH1)
        setp(ic, absolute=False)
        T(ic, key[:2], 15, "Bold", col)
        T(b, name, 15, "Semi Bold", TEXT)
        T(b, desc, 12.5, "Regular", MUTED, sw="fill")
        r = H(b, 8, sw="fill")
        T(r, "%d pages" % n, 12, "Regular", SUBTLE, sw="fill")
        icon(r, "star", 15, "#e5a50a" if n > 150 else BSTRONG)
