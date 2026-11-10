"""Boards, Code, Desk, Pulse, Goals, Admin and auth screens."""
from screens_a import *

LABEL_COLS = {"Design": ("#ffffff", "#d9466f"), "Copy": ("#ffffff", "#e8892b"), "Launch": ("#ffffff", "#5a4bf5"),
              "Legal": ("#ffffff", "#2f6fde"), "Social": ("#ffffff", "#0e9f8e"), "Blocked": ("#ffffff", "#d23b3b")}


# ================================================================ BOARDS
def bcard(parent, title, labels=(), cover=None, meta=(), who=()):
    c = V(parent, 0, sw="fill", fill=SURF, radius=8, effects=SH1, clip=True, name="Card/" + title[:20])
    if cover:
        box(c, None, h=92, sw="fill", fills=[{"type": "linearGradient", "stops": cover}], name="Cover")
    b = V(c, 8, pad=[10, 12], sw="fill")
    if labels:
        r = H(b, 4, wrap=True, sw="fill")
        for l in labels:
            fg, bg = LABEL_COLS[l]
            x = H(r, 0, pad=[1, 8], radius=4, fill=bg)
            T(x, l, 11, "Semi Bold", fg)
    T(b, title, 13.5, "Medium", TEXT, sw="fill", lh="140%")
    if meta or who:
        r = H(b, 10, sw="fill")
        for ic, t, tone in meta:
            x = H(r, 4, pad=[1, 4] if tone else 0, radius=4, fill=tone)
            icon(x, ic, 14, "#ffffff" if tone else MUTED)
            if t:
                T(x, t, 12, "Medium", "#ffffff" if tone else MUTED)
        box(r, None, h=1, sw="fill")
        if who:
            avatars(r, list(who), 24)
    return c


def boards_board():
    s = screen("Boards / Board", est=900, fill="#3b2f8f")
    setp(s, fills=[{"type": "linearGradient", "stops": ["#4a3ce0", "#c2417a"], "from": [0, 0], "to": [1, 1]}])
    topnav(s)
    bh = H(s, 12, pad=[12, 20], sw="fill", fill="#14122b40", name="Board header")
    T(bh, "Q4 Launch", 18, "Bold", "#ffffff")
    icon(bh, "star", 17, "#ffffff")
    x = H(bh, 6, pad=[4, 10], radius=6, fill="#ffffff33")
    icon(x, "users", 15, "#ffffff")
    T(x, "Workspace visible", 12.5, "Medium", "#ffffff")
    x = H(bh, 6, pad=[4, 10], radius=6, fill="#ffffff")
    icon(x, "kanban", 15, TEXT)
    T(x, "Board", 12.5, "Semi Bold", TEXT)
    for t, ic in [("Table", "table"), ("Calendar", "calendar"), ("Timeline", "timeline")]:
        x = H(bh, 6, pad=[4, 10], radius=6)
        icon(x, ic, 15, "#ffffff")
        T(x, t, 12.5, "Medium", "#ffffff")
    box(bh, None, h=1, sw="fill")
    for t, ic in [("Automation", "bolt"), ("Filter", "filter")]:
        x = H(bh, 6, pad=[4, 10], radius=6)
        icon(x, ic, 15, "#ffffff")
        T(x, t, 12.5, "Medium", "#ffffff")
    avatars(bh, PEOPLE[:5], 28)
    x = H(bh, 6, pad=[6, 12], radius=6, fill="#ffffff")
    icon(x, "user", 15, TEXT)
    T(x, "Share", 12.5, "Semi Bold", TEXT)
    lists = H(s, 12, pad=[16, 20, 32, 20], sw="fill", cross="min", name="Lists", h=788, sh="fixed")
    data = [
        ("Ideas", [("Launch video storyboard", ["Design"], ["#ffd36e", "#ff8f6b"], [("message", "4", None)], ["Lena Fischer"]),
                   ("Partner co-marketing list", ["Launch"], None, [], []),
                   ("Referral reward experiment", ["Social"], None, [("paperclip", "2", None)], ["Marco Silva"])]),
        ("In progress", [("Landing page v3", ["Design", "Copy"], ["#8b80ff", "#4a3ce0"], [("clock", "Nov 12", "#e8892b"), ("checklist", "5/8", None)], ["Lena Fischer", "Grace Lee"]),
                         ("Press kit", ["Launch"], None, [("checklist", "2/6", None)], ["Aisha Khan"]),
                         ("App Store listing copy", ["Copy"], None, [("message", "7", None)], ["Marco Silva"])]),
        ("Review", [("Terms of service update", ["Legal", "Blocked"], None, [("clock", "Nov 9", "#d23b3b")], ["Omar Haddad"]),
                    ("Launch email sequence", ["Copy"], ["#5fd0b2", "#2f6fde"], [("checklist", "4/4", "#2f9a57")], ["Priya Nair", "Marco Silva"])]),
        ("Done", [("Pricing page", ["Design"], None, [("check", "Done", "#2f9a57")], ["Lena Fischer"]),
                  ("Beta waitlist export", ["Launch"], None, [], ["Ravi Kumar"]),
                  ("Social teaser posts", ["Social"], None, [("message", "12", None)], ["Aisha Khan"])]),
    ]
    for name, cards in data:
        l = V(lists, 8, pad=8, w=272, fill="#f1f2f4", radius=12, name="List/" + name)
        h = H(l, 8, pad=[4, 6], sw="fill")
        T(h, name, 13.5, "Semi Bold", TEXT, sw="fill")
        icon(h, "more", 16, MUTED)
        for c in cards:
            bcard(l, *c)
        a = H(l, 6, pad=[6, 6], sw="fill", radius=6)
        icon(a, "plus", 15, MUTED)
        T(a, "Add a card", 13, "Medium", MUTED)
    a = H(lists, 6, pad=[10, 12], w=272, radius=12, fill="#ffffff40", name="Add list")
    icon(a, "plus", 15, "#ffffff")
    T(a, "Add another list", 13.5, "Semi Bold", "#ffffff")


def boards_card():
    s = screen("Boards / Card detail", est=1000, fill="#3b2f8f")
    setp(s, fills=[{"type": "linearGradient", "stops": ["#4a3ce0", "#c2417a"], "from": [0, 0], "to": [1, 1]}])
    topnav(s)
    ov = V(s, 0, sw="fill", pad=[40, 0], cross="center", fill="#14122b80", name="Scrim", minHeight=944)
    md = V(ov, 0, w=768, fill=SURF, radius=12, effects=SH3, clip=True, name="Card modal")
    box(md, None, h=140, sw="fill", fills=[{"type": "linearGradient", "stops": ["#8b80ff", "#4a3ce0"]}], name="Cover")
    b = H(md, 24, pad=[20, 24, 28, 24], sw="fill", cross="min")
    l = V(b, 16, sw="fill")
    r = H(l, 10, sw="fill")
    icon(r, "card", 20, MUTED)
    v = V(r, 2, sw="fill")
    T(v, "Landing page v3", 20, "Semi Bold", TEXT)
    T(v, "in list In progress", 12.5, "Regular", MUTED)
    r = H(l, 24)
    for lab, render in [("Members", lambda x: avatars(x, ["Lena Fischer", "Grace Lee"], 28)),
                        ("Labels", lambda x: [H(x, 0, pad=[3, 8], radius=4, fill=LABEL_COLS[k][1]) for k in ("Design", "Copy")]),
                        ("Due date", lambda x: (lozenge(x, "At risk"), T(x, "Nov 12, 17:00", 13, "Medium", TEXT)))]:
        v = V(r, 6)
        T(v, lab, 12, "Semi Bold", MUTED)
        x = H(v, 6)
        render(x)
    T(l, "Description", 14, "Semi Bold", TEXT)
    T(l, "Refresh the hero for the Q4 launch: new product shots, a 15-second loop of the offline mode, and a simplified sign-up form. Copy owner: Marco.", 13.5, "Regular", TEXT, sw="fill", lh="160%")
    ch = V(l, 8, sw="fill")
    r = H(ch, 8, sw="fill")
    icon(r, "checklist", 18, MUTED)
    T(r, "Checklist", 14, "Semi Bold", TEXT, sw="fill")
    T(r, "62%", 12, "Medium", MUTED)
    progress(ch, [(62, SUCCESS)], w=470, h=6)
    for d, t in [(True, "Final hero illustration"), (True, "Offline mode video loop"), (True, "Headline and sub-copy"), (True, "Sign-up form with 2 fields"),
                 (True, "Responsive QA"), (False, "Localise for DE, FR, JA"), (False, "Analytics events"), (False, "Legal review")]:
        rr = H(ch, 10, sw="fill")
        checkbox(rr, d)
        T(rr, t, 13.5, "Regular", MUTED if d else TEXT, sw="fill")
    T(l, "Activity", 14, "Semi Bold", TEXT)
    composer(l)
    comment(l, "Grace Lee", "Today, 10:42", "The video loop is in the shared drive. 6 MB — we can lazy-load it below the fold.")
    rt = V(b, 8, w=180)
    T(rt, "Add to card", 12, "Semi Bold", MUTED)
    for t, ic in [("Members", "user"), ("Labels", "tag"), ("Checklist", "checklist"), ("Dates", "clock"), ("Attachment", "paperclip"), ("Cover", "image")]:
        x = H(rt, 8, pad=[0, 10], h=32, sw="fill", radius=6, fill="#eceef2")
        icon(x, ic, 15, TEXT)
        T(x, t, 13, "Medium", TEXT)
    T(rt, "Power-ups", 12, "Semi Bold", MUTED)
    for t, ic in [("Link Track issue", "board"), ("Attach Pages doc", "page")]:
        x = H(rt, 8, pad=[0, 10], h=32, sw="fill", radius=6, fill="#eceef2")
        icon(x, ic, 15, TEXT)
        T(x, t, 13, "Medium", TEXT)
    T(rt, "Actions", 12, "Semi Bold", MUTED)
    for t, ic in [("Move", "arrow-right"), ("Copy", "page"), ("Archive", "folder")]:
        x = H(rt, 8, pad=[0, 10], h=32, sw="fill", radius=6, fill="#eceef2")
        icon(x, ic, 15, TEXT)
        T(x, t, 13, "Medium", TEXT)


# ================================================================ CODE
def repo_head(m, active):
    h = H(m, 10, sw="fill")
    app_tile(h, "Code", 28)
    T(h, "northwind / orbit-mobile", 20, "Semi Bold", TEXT, sw="fill")
    btn(h, "Watch", "secondary", "eye")
    btn(h, "Fork", "secondary", "branch")
    btn(h, "Clone", "primary", "download")
    tabs(m, ["Source", "Commits", "Branches", "Pull requests", "Pipelines", "Deployments"], active, [None, None, 14, 6, None, None])


def code_source():
    m = app_screen("Code / Source", "Code", "Source", None, est=1100)
    repo_head(m, 0)
    r = H(m, 10, sw="fill")
    x = H(r, 6, pad=[0, 10], h=32, radius=6, stroke=BSTRONG, fill=SURF)
    icon(x, "branch", 15, MUTED)
    T(x, "main", 13, "Medium", TEXT)
    icon(x, "chevron-down", 14, MUTED)
    T(r, "orbit-mobile / app / src", 13, "Medium", MUTED, sw="fill")
    btn(r, "Create pull request", "secondary", "pr")
    lc = H(m, 10, pad=[10, 14], sw="fill", fill=SURF, radius=10, stroke=BORDER)
    avatar(lc, "Grace Lee", 24)
    T(lc, "Grace Lee", 13, "Semi Bold", TEXT)
    T(lc, "feat(sync): retry outbox with jitter (ORB-414)", 13, "Regular", MUTED, sw="fill", trunc=True)
    T(lc, "a41c9e2", 12, "Regular", LINK, mono=True)
    lozenge(lc, "Passed")
    T(lc, "2 hours ago", 12, "Regular", SUBTLE)
    t = V(m, 0, sw="fill", fill=SURF, radius=10, stroke=BORDER, clip=True, name="Files")
    for nm, kind, msg, when in [("..", "folder", "", ""), ("main", "folder", "chore: bump compileSdk to 35", "3 days ago"),
                                ("sync", "folder", "feat(sync): retry outbox with jitter", "2 hours ago"), ("ui", "folder", "fix(map): handle rotation on API 35", "5 hours ago"),
                                ("test", "folder", "test(sync): conflict scenarios", "Yesterday"), ("build.gradle.kts", "page", "chore: Room 2.7", "3 days ago"),
                                ("proguard-rules.pro", "page", "build: keep Room entities", "1 week ago"), ("README.md", "page", "docs: offline mode setup", "2 days ago")]:
        rr = H(t, 12, pad=[0, 16], h=42, sw="fill", stroke=BORDER, sides=[0, 0, 1, 0])
        icon(rr, kind, 17, "#2f6fde" if kind == "folder" else MUTED)
        T(rr, nm, 13.5, "Medium" if kind == "folder" else "Regular", LINK if nm != ".." else MUTED, w=260)
        T(rr, msg, 13, "Regular", MUTED, sw="fill", trunc=True)
        T(rr, when, 12.5, "Regular", SUBTLE)
    rd = card(m, 12, 24, name="README")
    r = H(rd, 8, sw="fill", stroke=BORDER, sides=[0, 0, 1, 0], pad=[0, 0, 12, 0])
    icon(r, "book", 16, MUTED)
    T(r, "README.md", 13.5, "Semi Bold", TEXT)
    T(rd, "Orbit Mobile — Android", 24, "Bold", TEXT)
    T(rd, "The Orbit travel app for Android. Kotlin, Jetpack Compose, Room, WorkManager.", 14, "Regular", TEXT, sw="fill", lh="160%")
    T(rd, "Getting started", 17, "Semi Bold", TEXT)
    cb = V(rd, 4, pad=14, sw="fill", radius=8, fill=SUNK)
    for ln in ["git clone git@code.newxr.dev:northwind/orbit-mobile.git", "./gradlew :app:installDebug", "./gradlew test connectedCheck"]:
        T(cb, "$ " + ln, 12.5, "Regular", TEXT, mono=True)


def code_pr():
    m = app_screen("Code / Pull request", "Code", "Pull requests", None, est=1200)
    h = H(m, 10, sw="fill", cross="min")
    v = V(h, 8, sw="fill")
    T(v, "feat: offline itinerary cache #284", 22, "Semi Bold", TEXT, sw="fill")
    r = H(v, 8)
    lozenge(r, "Open")
    avatar(r, "Grace Lee", 20)
    T(r, "Grace Lee wants to merge 11 commits from", 13, "Regular", MUTED)
    for b in ("feature/ORB-412-offline-cache", "main"):
        x = H(r, 0, pad=[1, 6], radius=4, fill=INFO_SOFT)
        T(x, b, 12, "Medium", INFO, mono=True)
        if b != "main":
            icon(r, "arrow-right", 14, MUTED)
    btn(h, None, "secondary", "more")
    btn(h, "Approve", "secondary", "check")
    mb = H(h, 0, h=32, radius=6, fill=SUCCESS, clip=True)
    x = H(mb, 6, pad=[0, 12], h=32)
    T(x, "Merge", 13, "Semi Bold", "#ffffff")
    x = H(mb, 0, w=30, h=32, main="center", stroke="#ffffff55", sides=[0, 0, 0, 1])
    icon(x, "chevron-down", 14, "#ffffff")
    tabs(m, ["Overview", "Commits", "Files changed", "Checks"], 2, [None, 11, 9, 4])
    row = H(m, 20, sw="fill", cross="min")
    l = V(row, 14, sw="fill")
    ft = V(l, 0, sw="fill", fill=SURF, radius=10, stroke=BORDER, clip=True, name="Diff")
    fh = H(ft, 8, pad=[10, 14], sw="fill", fill=SUNK, stroke=BORDER, sides=[0, 0, 1, 0])
    icon(fh, "chevron-down", 14, MUTED)
    T(fh, "app/src/sync/OfflineQueue.kt", 13, "Medium", TEXT, mono=True, sw="fill")
    T(fh, "+38", 12, "Semi Bold", SUCCESS)
    T(fh, "−6", 12, "Semi Bold", DANGER)
    checkbox(fh, True)
    T(fh, "Viewed", 12.5, "Regular", MUTED)
    lines = [(" ", 41, 41, "class OfflineQueue(private val dao: OutboxDao) {"), (" ", 42, 42, ""),
             ("-", 43, None, "    suspend fun flush() = dao.all().forEach { send(it) }"),
             ("+", None, 43, "    suspend fun flush() {"), ("+", None, 44, "        for (op in dao.pending()) {"),
             ("+", None, 45, "            val ok = retry(times = 5, jitter = 0.3) { send(op) }"),
             ("+", None, 46, "            if (ok) dao.markSent(op.id) else break"), ("+", None, 47, "        }"), ("+", None, 48, "    }"),
             (" ", 44, 49, ""), (" ", 45, 50, "    private suspend fun send(op: SyncOp): Boolean =")]
    for i, (k, a, b, code) in enumerate(lines):
        bg = SUCCESS_SOFT if k == "+" else (DANGER_SOFT if k == "-" else SURF)
        rr = H(ft, 0, h=24, sw="fill", fill=bg)
        for n in (a, b):
            c = H(rr, 0, pad=[0, 8], w=48, h=24, main="max", fill="#00000008")
            T(c, str(n) if n else "", 11.5, "Regular", SUBTLE, mono=True)
        T(rr, " %s  %s" % (k if k != " " else " ", code), 12.5, "Regular", TEXT, mono=True, sw="fill")
    cm = V(ft, 10, pad=[12, 16, 12, 112], sw="fill", fill=SUNK, stroke=BORDER, sides=[1, 0, 1, 0], name="Inline comment")
    comment(cm, "Ravi Kumar", "1 hour ago", "Should we cap total retry time? With 5 tries and back-off this can hold the worker for ~90 s on a bad network.")
    comment(cm, "Grace Lee", "40 minutes ago", "Good point — capped at 30 s in the next commit, the job reschedules itself after that.")
    for fn, a, d in [("app/src/sync/SyncWorker.kt", 54, 2), ("app/src/data/ItineraryDao.kt", 21, 0)]:
        fh = H(l, 8, pad=[10, 14], sw="fill", fill=SURF, radius=10, stroke=BORDER)
        icon(fh, "chevron-right", 14, MUTED)
        T(fh, fn, 13, "Medium", TEXT, mono=True, sw="fill")
        T(fh, "+%d" % a, 12, "Semi Bold", SUCCESS)
        T(fh, "−%d" % d, 12, "Semi Bold", DANGER)
    rt = V(row, 14, w=300)
    c = card(rt, 10, 16, name="Reviewers")
    T(c, "Reviewers", 13.5, "Semi Bold", TEXT)
    for who, st in [("Ravi Kumar", "Approved"), ("Tanmay Singh", "Needs work"), ("Aisha Khan", None)]:
        r = H(c, 8, sw="fill")
        avatar(r, who, 24)
        T(r, who, 13, "Regular", TEXT, sw="fill")
        if st:
            lozenge(r, st)
        else:
            T(r, "Pending", 12, "Regular", SUBTLE)
    c = card(rt, 10, 16, name="Checks")
    T(c, "Checks", 13.5, "Semi Bold", TEXT)
    for t, ok in [("Build · assembleRelease", True), ("Unit tests · 1,204 passed", True), ("Lint", True), ("Merge checks · 1 of 2 approvals", False)]:
        r = H(c, 8, sw="fill")
        icon(r, "check-circle" if ok else "clock", 16, SUCCESS if ok else WARN)
        T(r, t, 13, "Regular", TEXT, sw="fill")
    c = card(rt, 8, 16, name="Linked")
    T(c, "Linked issues", 13.5, "Semi Bold", TEXT)
    r = H(c, 8, sw="fill")
    itype(r, "story")
    T(r, "ORB-412 Offline mode…", 13, "Medium", LINK, sw="fill")
    lozenge(r, "In progress")


def code_pipelines():
    m = app_screen("Code / Pipelines", "Code", "Pipelines", None, est=1000)
    repo_head(m, 4)
    run = card(m, 14, 20, name="Pipeline run")
    r = H(run, 10, sw="fill")
    icon(r, "refresh", 18, INFO)
    T(r, "#1872 · feature/ORB-412-offline-cache", 15, "Semi Bold", TEXT, sw="fill")
    lozenge(r, "Running")
    btn(r, "Stop", "secondary", "pause", "sm")
    st = H(run, 0, sw="fill", name="Stages")
    for i, (nm, state, dur) in enumerate([("Build", "Passed", "2m 41s"), ("Unit tests", "Passed", "4m 08s"), ("Instrumented tests", "Running", "3m 12s"),
                                          ("Security scan", "Open", "—"), ("Deploy to beta", "Open", "—")]):
        col = {"Passed": SUCCESS, "Running": INFO, "Open": BSTRONG}[state]
        sv = V(st, 8, sw="fill", cross="center")
        rr = H(sv, 0, sw="fill")
        box(rr, None, h=2, sw="fill", fill=BORDER if i == 0 else (SUCCESS if i <= 2 else BORDER))
        b = H(rr, 0, w=32, h=32, radius=16, fill=col, main="center")
        icon(b, {"Passed": "check", "Running": "refresh", "Open": "clock"}[state], 16, "#ffffff", 2)
        box(rr, None, h=2, sw="fill", fill=SUCCESS if i < 2 else (BORDER if i < 4 else None))
        T(sv, nm, 13, "Semi Bold", TEXT)
        T(sv, dur, 12, "Regular", MUTED)
    lg = V(run, 3, pad=14, sw="fill", radius=8, fill="#1d2130", name="Log")
    for ln, col in [("> Task :app:connectedDebugAndroidTest", "#e6e4ff"), ("Starting 214 tests on Pixel_8_API_35(AVD)", "#a6a3c7"),
                    ("OfflineQueueTest > flush_retriesWithJitter PASSED", "#7ee2a8"), ("OfflineQueueTest > flush_stopsAfterCap PASSED", "#7ee2a8"),
                    ("ConflictPromptTest > cancelledBooking_showsDialog …", "#ffd36e")]:
        T(lg, ln, 12.5, "Regular", col, mono=True)
    cols = [("Pipeline", 1.0, "link"), ("Branch", 2.4, "mono"), ("Commit", 2.6, "muted"), ("Status", 1.0, "status"),
            ("Duration", 0.8, "muted"), ("Triggered by", 1.5, "user"), ("Started", 1.0, "muted")]
    rows = [["#1872", "feature/ORB-412-offline-cache", "feat(sync): retry outbox with jitter", "Running", "10m", "Grace Lee", "10 min ago"],
            ["#1871", "main", "chore: bump compileSdk to 35", "Passed", "14m", "Ravi Kumar", "2 h ago"],
            ["#1870", "fix/ORB-418-map-rotation", "fix(map): handle rotation on API 35", "Passed", "13m", "Ravi Kumar", "5 h ago"],
            ["#1869", "feature/ORB-409-share-trip", "feat: deep link for shared trips", "Failed", "6m", "Marco Silva", "Yesterday"],
            ["#1868", "main", "Merge PR #279 biometric sign-in", "Passed", "15m", "Aisha Khan", "Yesterday"]]
    table(m, cols, rows)


# ================================================================ DESK
def desk_queue():
    def qnav(nav):
        nav_group(nav, "Queues", [("All open · 48", "inbox"), ("Assigned to me · 7", "user"), ("Unassigned · 12", "users"),
                                  ("SLA breaching · 3", "clock"), ("Hardware · 9", "monitor"), ("Access requests · 15", "lock")])
    m = app_screen("Desk / Queue", "Desk", "Queues", ["Service projects", "IT Help"], est=1000, extra=qnav)
    page_title(m, "Assigned to me", [("Export", "download"), "more"], "Raise request")
    k = H(m, 12, sw="fill")
    kpi(k, "Open", "7", "2 new today", MUTED, "inbox")
    kpi(k, "Time to first response", "38 min", "Target 1 h", SUCCESS, "clock")
    kpi(k, "Breaching soon", "2", "within 30 min", DANGER, "alert")
    kpi(k, "CSAT (30 days)", "4.7 / 5", "+0.2", SUCCESS, "smile")
    toolbar(m, "Search requests", ["Request type", "Status", "Priority"], None, ["settings"])

    def sla(text, tone):
        def f(c):
            x = H(c, 4, pad=[2, 8], radius=4, fill={"red": DANGER_SOFT, "amber": WARN_SOFT, "green": SUCCESS_SOFT}[tone])
            icon(x, "clock", 13, {"red": DANGER, "amber": WARN, "green": SUCCESS}[tone])
            T(x, text, 12, "Semi Bold", {"red": DANGER, "amber": WARN, "green": SUCCESS}[tone])
        return f
    cols = [("Key", 0.9, "link"), ("Summary", 2.8, "text"), ("Reporter", 1.4, "user"), ("Type", 1.3, "muted"), ("Status", 1.6, "status"),
            ("Priority", 0.9, "status"), ("Time to resolution", 1.4, "custom")]
    rows = [["HELP-1093", "VPN not connecting from Lisbon office", "Priya Nair", "Network", "Waiting for support", "P2", sla("-12m", "red")],
            ["HELP-1091", "New laptop for incoming designer", "Lena Fischer", "Hardware", "In progress", "P3", sla("1h 40m", "amber")],
            ["HELP-1089", "Access to finance shared drive", "Omar Haddad", "Access", "Waiting for customer", "P3", sla("Paused", "green")],
            ["HELP-1088", "Outlook keeps asking for password", "Marco Silva", "Software", "In progress", "P2", sla("22m", "red")],
            ["HELP-1085", "Monitor flickers on dock", "Aisha Khan", "Hardware", "Waiting for support", "P4", sla("6h 05m", "green")],
            ["HELP-1083", "Add Ravi to on-call rota tool", "Ravi Kumar", "Access", "Escalated", "P3", sla("3h 10m", "green")],
            ["HELP-1080", "Printer on floor 2 offline", "Grace Lee", "Hardware", "Resolved", "P4", sla("Met", "green")]]
    table(m, cols, rows, check=True, selected=0, footer="1–7 of 7")


def desk_request():
    m = app_screen("Desk / Request", "Desk", "Queues", ["IT Help", "Queues", "HELP-1093"], est=1100)
    row = H(m, 24, sw="fill", cross="min")
    l = V(row, 16, sw="fill")
    r = H(l, 10, sw="fill")
    ib = H(r, 0, w=28, h=28, radius=6, fill="#fde6ee", main="center")
    icon(ib, "globe", 16, "#d9466f")
    T(r, "VPN not connecting from Lisbon office", 22, "Semi Bold", TEXT, sw="fill")
    r = H(l, 8)
    btn(r, "Reply to customer", "primary", "mail")
    btn(r, "Add internal note", "secondary", "lock")
    btn(r, "Link incident", "secondary", "bolt")
    c = card(l, 10, 18, name="Request")
    r = H(c, 8)
    avatar(r, "Priya Nair", 28)
    v = V(r, 0)
    T(v, "Priya Nair raised this via portal", 13, "Semi Bold", TEXT)
    T(v, "Today at 08:14 · Request type: Network access", 12, "Regular", MUTED)
    T(c, "Since this morning nobody in the Lisbon office can connect to the VPN. The client shows “TLS handshake failed”. We tried restarting laptops and the office router.", 13.5, "Regular", TEXT, sw="fill", lh="160%")
    r = H(c, 8)
    for f_ in ("vpn-log.txt", "screenshot.png"):
        x = H(r, 6, pad=[6, 10], radius=6, stroke=BORDER, fill=SUNK)
        icon(x, "paperclip", 14, MUTED)
        T(x, f_, 12.5, "Medium", TEXT)
    sug = H(l, 12, pad=14, sw="fill", radius=10, fill=VIOLET_SOFT, cross="min", name="AI suggestion")
    icon(sug, "sparkle", 18, VIOLET)
    v = V(sug, 6, sw="fill")
    T(v, "Similar to 3 recent requests", 13.5, "Semi Bold", VIOLET)
    T(v, "Likely cause: the Lisbon gateway certificate expired on 6 Nov. Suggested article: “Renewing site VPN certificates”.", 13, "Regular", TEXT, sw="fill", lh="150%")
    rr = H(v, 8)
    btn(rr, "Insert article", "secondary", None, "sm")
    btn(rr, "Dismiss", "ghost", None, "sm")
    tabs(l, ["All", "Replies", "Internal notes", "History"], 0)
    e = H(l, 12, sw="fill", cross="min")
    avatar(e, "Tanmay Singh", 32)
    b = V(e, 6, sw="fill", pad=[10, 14], radius=8, fill=NOTE_BG, stroke=NOTE_BORDER)
    t = H(b, 8)
    T(t, "Tanmay Singh", 13, "Semi Bold", TEXT)
    label_chip(t, "Internal note", WARN, WARN_SOFT)
    T(b, "Confirmed the cert on vpn-lis-01 expired. Renewal request sent to Network — linked PULSE incident #77.", 13.5, "Regular", TEXT, sw="fill", lh="155%")
    comment(l, "Tanmay Singh", "08:40", "Hi Priya — thanks for the logs. We've found the cause and expect the VPN back within the hour. I'll update you here.")
    rt = V(row, 14, w=320)
    s = H(rt, 6, pad=[0, 12], h=32, radius=6, fill=INFO, w=200)
    T(s, "Waiting for support", 13, "Semi Bold", "#ffffff", sw="fill")
    icon(s, "chevron-down", 15, "#ffffff")
    c = card(rt, 10, 16, name="SLAs")
    T(c, "SLAs", 13.5, "Semi Bold", TEXT)
    for nm, v_, tone in [("Time to first response", "Met · 26 min", SUCCESS), ("Time to resolution", "Breached · -12 min", DANGER)]:
        x = V(c, 4, sw="fill", pad=10, radius=8, fill=SUNK)
        T(x, nm, 12, "Medium", MUTED)
        T(x, v_, 14, "Semi Bold", tone)
    dc = card(rt, 0, [6, 16], name="Details")
    T(dc, "Details", 13.5, "Semi Bold", TEXT)
    box(dc, None, h=8, sw="fill")
    field_row(dc, "Assignee", lambda v: (avatar(v, "Tanmay Singh", 22), T(v, "Tanmay Singh", 13, "Regular", TEXT)), 110)
    field_row(dc, "Priority", lambda v: lozenge(v, "P2"), 110)
    field_row(dc, "Organisation", "Lisbon office", 110)
    field_row(dc, "Components", lambda v: label_chip(v, "VPN"), 110)
    field_row(dc, "Linked", lambda v: T(v, "PULSE #77", 13, "Medium", LINK), 110)
    c = card(rt, 8, 16, name="Customer")
    T(c, "Customer", 13.5, "Semi Bold", TEXT)
    r = H(c, 10)
    avatar(r, "Priya Nair", 36)
    v = V(r, 0)
    T(v, "Priya Nair", 13.5, "Semi Bold", TEXT)
    T(v, "Ops manager · Lisbon", 12, "Regular", MUTED)
    T(c, "4 open requests · CSAT 5.0", 12, "Regular", MUTED)


def desk_portal():
    s = screen("Desk / Help center", est=1000, fill=SURF)
    hdr = H(s, 12, pad=[0, 40], h=60, sw="fill", fill=SURF, stroke=BORDER, sides=[0, 0, 1, 0])
    logo(hdr, 18, mark=24)
    T(hdr, "Help center", 15, "Medium", MUTED, sw="fill")
    T(hdr, "My requests", 13.5, "Medium", TEXT)
    T(hdr, "Approvals", 13.5, "Medium", TEXT)
    avatar(hdr, "Priya Nair", 30)
    hero = V(s, 14, pad=[56, 0, 64, 0], sw="fill", cross="center", fills=[{"type": "linearGradient", "stops": ["#d9466f", "#5a4bf5"], "from": [0, 0], "to": [1, 1]}])
    T(hero, "Hi Priya, how can we help?", 34, "Bold", "#ffffff", ls=-0.6)
    sb = H(hero, 10, pad=[0, 18], w=640, h=52, radius=10, fill=SURF, effects=SH2)
    icon(sb, "search", 20, MUTED)
    T(sb, "Search for help articles or request types", 15, "Regular", SUBTLE, sw="fill")
    body = V(s, 20, pad=[40, 120, 56, 120], sw="fill")
    T(body, "Service projects", 18, "Semi Bold", TEXT)
    g = H(body, 16, sw="fill")
    for t, d, ic, col in [("IT Help", "Laptops, software, VPN and access", "monitor", "#2f6fde"), ("People team", "Leave, payroll and benefits", "users", "#2f9a57"),
                          ("Facilities", "Desks, badges and office issues", "home", "#e8892b"), ("Legal", "Contracts and NDAs", "shield", "#7c3aed")]:
        c = card(g, 10, 20, name="Portal/" + t)
        ib = H(c, 0, w=44, h=44, radius=10, fill=col, main="center")
        icon(ib, ic, 22, "#ffffff", 2)
        T(c, t, 15, "Semi Bold", TEXT)
        T(c, d, 13, "Regular", MUTED, sw="fill")
    row = H(body, 20, sw="fill", cross="min")
    c = card(row, 0, 0, name="Popular requests")
    h = H(c, 0, pad=[14, 18], sw="fill", stroke=BORDER, sides=[0, 0, 1, 0])
    T(h, "Popular request types", 15, "Semi Bold", TEXT)
    for t, d, ic in [("Get a new laptop", "Hardware for new or existing staff", "monitor"), ("Request access", "Apps, drives and shared mailboxes", "lock"),
                     ("Report a problem", "Something is broken or not working", "alert"), ("Install software", "Licensed apps from the catalogue", "download")]:
        r = H(c, 12, pad=[12, 18], sw="fill", stroke=BORDER, sides=[0, 0, 1, 0])
        ib = H(r, 0, w=32, h=32, radius=8, fill=INFO_SOFT, main="center")
        icon(ib, ic, 16, INFO)
        v = V(r, 1, sw="fill")
        T(v, t, 13.5, "Semi Bold", TEXT)
        T(v, d, 12.5, "Regular", MUTED)
        icon(r, "chevron-right", 16, SUBTLE)
    c = card(row, 0, 0, name="My requests")
    h = H(c, 0, pad=[14, 18], sw="fill", main="spaceBetween", stroke=BORDER, sides=[0, 0, 1, 0])
    T(h, "Your recent requests", 15, "Semi Bold", TEXT)
    T(h, "View all", 12.5, "Medium", LINK)
    for k, t, st in [("HELP-1093", "VPN not connecting from Lisbon office", "Waiting for support"), ("HELP-1061", "Badge for new contractor", "Resolved"),
                     ("HELP-1040", "Adobe licence for marketing", "Resolved")]:
        r = H(c, 12, pad=[14, 18], sw="fill", stroke=BORDER, sides=[0, 0, 1, 0])
        v = V(r, 2, sw="fill")
        T(v, t, 13.5, "Medium", TEXT)
        T(v, k, 12, "Regular", MUTED)
        lozenge(r, st)


# ================================================================ PULSE
def pulse_alerts():
    m = app_screen("Pulse / Alerts", "Pulse", "Alerts", ["Operations", "Platform Ops"], est=950)
    page_title(m, "Alerts", ["settings"], "Create alert")
    k = H(m, 12, sw="fill")
    kpi(k, "Open", "9", "3 unacknowledged", DANGER, "bell")
    kpi(k, "MTTA (7 days)", "3m 12s", "-41s", SUCCESS, "clock")
    kpi(k, "MTTR (7 days)", "42m", "+6m", WARN, "refresh")
    kpi(k, "On call now", "Ravi Kumar", "until 18:00 UTC", MUTED, "headset")
    toolbar(m, "Search alerts", ["Status: Open", "Priority", "Team", "Source"], None, ["Snoozed"])

    def pr(p):
        def f(c):
            lozenge(c, p)
        return f

    def src(t, ic):
        def f(c):
            icon(c, ic, 15, MUTED)
            T(c, t, 13, "Regular", TEXT)
        return f
    cols = [("Priority", 0.7, "custom"), ("Alert", 3.2, "text"), ("Status", 1.2, "status"), ("Source", 1.3, "custom"),
            ("Owner", 1.4, "user"), ("Count", 0.6, "muted"), ("Created", 1.0, "muted")]
    rows = [[pr("P1"), "checkout-api: 5xx rate above 5% for 5 min", "Open", src("Prometheus", "chart"), "Ravi Kumar", "14", "4 min ago"],
            [pr("P2"), "checkout-api p95 latency > 2s", "Acknowledged", src("Prometheus", "chart"), "Ravi Kumar", "6", "11 min ago"],
            [pr("P2"), "vpn-lis-01 certificate expired", "Acknowledged", src("Uptime", "globe"), "Tanmay Singh", "1", "2 h ago"],
            [pr("P3"), "orbit-db replica lag 45s", "Open", src("CloudWatch", "server"), "", "3", "2 h ago"],
            [pr("P3"), "Crash rate Android 4.12.0-rc2 > 0.5%", "Open", src("Crash reports", "bug"), "Grace Lee", "2", "3 h ago"],
            [pr("P4"), "Disk 82% on build-agent-07", "Closed", src("Agent", "monitor"), "Omar Haddad", "1", "Yesterday"]]
    table(m, cols, rows, check=True, selected=0, footer="6 of 9 open alerts")


def pulse_oncall():
    m = app_screen("Pulse / On-call", "Pulse", "On-call", ["Operations", "Platform Ops"], est=900)
    page_title(m, "On-call schedule", [("Override", "user"), ("Export iCal", "calendar")], None, "Platform primary · rotates weekly on Monday 09:00 UTC")
    now = H(m, 14, pad=16, sw="fill", radius=10, fill=SURF, stroke=BORDER)
    avatar(now, "Ravi Kumar", 44)
    v = V(now, 2, sw="fill")
    T(v, "On call now", 12, "Semi Bold", MUTED)
    T(v, "Ravi Kumar", 17, "Semi Bold", TEXT)
    T(v, "Primary until Mon 10 Nov, 09:00 UTC · next: Grace Lee", 12.5, "Regular", MUTED)
    btn(now, "Page", "danger", "phone")
    btn(now, "Message", "secondary", "message")
    g = V(m, 0, sw="fill", fill=SURF, radius=10, stroke=BORDER, clip=True, name="Schedule")
    days = ["Mon 3", "Tue 4", "Wed 5", "Thu 6", "Fri 7", "Sat 8", "Sun 9"]
    dw = (1128 - 180) / 7
    h = H(g, 0, h=40, sw="fill", fill=SUNK, stroke=BORDER, sides=[0, 0, 1, 0])
    box(h, None, w=180, h=40)
    for d in days:
        c = H(h, 0, w=dw, h=40, main="center", stroke=BORDER, sides=[0, 0, 0, 1], fill=SEL if d == "Thu 6" else None)
        T(c, d, 12.5, "Semi Bold", ACC_H if d == "Thu 6" else MUTED)
    for layer, blocks in [("Primary", [(0, 7, "Ravi Kumar", "#5a4bf5")]),
                          ("Secondary", [(0, 3.5, "Aisha Khan", "#0e9f8e"), (3.5, 7, "Omar Haddad", "#e8892b")]),
                          ("Overrides", [(4.6, 5.4, "Grace Lee", "#d9466f")]),
                          ("Final schedule", [(0, 4.6, "Ravi Kumar", "#5a4bf5"), (4.6, 5.4, "Grace Lee", "#d9466f"), (5.4, 7, "Ravi Kumar", "#5a4bf5")])]:
        r = H(g, 0, h=64, sw="fill", stroke=BORDER, sides=[0, 0, 1, 0], fill=SUNK if layer == "Final schedule" else None)
        l = H(r, 8, pad=[0, 14], w=180, h=64)
        T(l, layer, 13, "Semi Bold", TEXT)
        lane = box(r, None, w=1128 - 180, h=64)
        for i in range(1, 7):
            box(lane, None, w=1, h=64, fill=CGRID, x=round(i * dw), y=0)
        for a, b, who, col in blocks:
            bar = H(lane, 8, pad=[0, 8], w=round((b - a) * dw) - 4, h=36, radius=6, fill=col, x=round(a * dw) + 2, y=14, clip=True)
            avatar(bar, who, 22, "#ffffff", col)
            T(bar, who, 12.5, "Semi Bold", "#ffffff")
    box(g, None, w=2, h=40 + 64 * 4, fill=DANGER, x=180 + round(3.45 * dw), y=0, absolute=True, name="Now")
    c = card(m, 10, 18, name="Escalation policy")
    T(c, "Escalation policy · Platform critical", 14, "Semi Bold", TEXT)
    r = H(c, 10, sw="fill")
    for i, (t, d) in enumerate([("Notify on-call primary", "immediately · push, SMS"), ("Notify secondary", "after 5 min if not acked"),
                                ("Notify engineering manager", "after 15 min"), ("Page whole team", "after 30 min")]):
        x = V(r, 4, sw="fill", pad=12, radius=8, fill=SUNK)
        T(x, "STEP %d" % (i + 1), 11, "Semi Bold", SUBTLE, ls=0.6)
        T(x, t, 13, "Semi Bold", TEXT)
        T(x, d, 12, "Regular", MUTED)
        if i < 3:
            icon(r, "arrow-right", 16, SUBTLE)


def pulse_status():
    s = screen("Pulse / Public status page", est=1100, fill=SURF)
    w = V(s, 24, pad=[40, 0, 64, 0], sw="fill", cross="center")
    h = H(w, 10, w=880)
    t = H(h, 0, w=32, h=32, radius=8, fill="#14122b", main="center")
    T(t, "O", 16, "Bold", "#ffffff")
    T(h, "Orbit status", 20, "Bold", TEXT, sw="fill")
    btn(h, "Subscribe to updates", "primary", "bell")
    b = H(w, 12, pad=[18, 22], w=880, radius=10, fill=WARN, name="Overall")
    icon(b, "alert", 22, "#ffffff", 2)
    T(b, "Partial outage — Checkout", 17, "Semi Bold", "#ffffff", sw="fill")
    T(b, "Updated 4 min ago", 12.5, "Medium", "#fff3dc")
    inc = V(w, 10, pad=20, w=880, radius=10, stroke="#f2c94c", fill=NOTE_BG, name="Active incident")
    T(inc, "Elevated errors when paying for bookings", 16, "Semi Bold", TEXT)
    for st, msg, when in [("Identified", "The issue is caused by a failing payment provider region. We're shifting traffic to a healthy region.", "08:52 UTC"),
                          ("Investigating", "Some customers see errors at checkout. Bookings already made are not affected.", "08:41 UTC")]:
        r = H(inc, 12, sw="fill", cross="min")
        T(r, st, 13, "Semi Bold", TEXT, w=100)
        v = V(r, 2, sw="fill")
        T(v, msg, 13.5, "Regular", TEXT, sw="fill", lh="150%")
        T(v, when, 12, "Regular", MUTED)
    comp = V(w, 0, w=880, radius=10, stroke=BORDER, clip=True, name="Components")
    for nm, st, up in [("Website", "Operational", 99.99), ("Mobile apps", "Operational", 99.97), ("Search & availability", "Operational", 99.95),
                       ("Checkout", "Degraded", 99.62), ("Notifications", "Operational", 99.99), ("Partner API", "Operational", 99.98)]:
        r = V(comp, 10, pad=[14, 18], sw="fill", stroke=BORDER, sides=[0, 0, 1, 0])
        t_ = H(r, 8, sw="fill")
        T(t_, nm, 14, "Semi Bold", TEXT, sw="fill")
        T(t_, "%.2f%% uptime" % up, 12, "Regular", MUTED)
        lozenge(t_, st)
        bars = H(r, 2, sw="fill")
        for i in range(90):
            bad = (nm == "Checkout" and i in (40, 71, 89)) or (nm == "Search & availability" and i == 55)
            box(bars, None, w=7.5, h=28, radius=2, fill=(WARN if i != 89 else DANGER) if bad else "#3fb56f")
        ax = H(r, 0, sw="fill", main="spaceBetween")
        T(ax, "90 days ago", 11.5, "Regular", SUBTLE)
        T(ax, "Today", 11.5, "Regular", SUBTLE)
    T(w, "Powered by Newxr Pulse", 12, "Regular", SUBTLE)


# ================================================================ GOALS
def goals_list():
    m = app_screen("Goals / Goals", "Goals", "Goals", None, est=900)
    page_title(m, "Goals", [("Q4 2026", "calendar"), "share"], "Create goal", "Company and team goals, with weekly updates")
    toolbar(m, "Search goals", ["Owner", "Team", "Status"], None, ["list", "timeline"])
    t = V(m, 0, sw="fill", fill=SURF, radius=10, stroke=BORDER, clip=True, effects=SH1)
    h = H(t, 0, h=38, sw="fill", fill=SUNK, stroke=BORDER, sides=[0, 0, 1, 0])
    for lab, w in [("Goal", 520), ("Status", 140), ("Progress", 200), ("Owner", 160), ("Target", 108)]:
        c = H(h, 0, pad=[0, 14], w=w, h=38)
        T(c, lab, 12, "Semi Bold", MUTED)
    for depth, g, st, pct, who, due in [
        (0, "Reach 1M monthly active travellers", "On track", 68, "Aisha Khan", "Dec 31"),
        (1, "Ship offline itineraries to everyone", "On track", 72, "Grace Lee", "Dec 1"),
        (1, "Launch in 3 new markets", "On track", 75, "Marco Silva", "Dec 15"),
        (1, "Cut crash rate below 0.2%", "At risk", 41, "Ravi Kumar", "Nov 30"),
        (0, "Grow booking revenue 25% QoQ", "Off track", 22, "Omar Haddad", "Dec 31"),
        (1, "Checkout conversion above 4.5%", "Off track", 18, "Lena Fischer", "Dec 20"),
        (0, "Every team on Newxr by end of year", "Completed", 100, "Tanmay Singh", "Nov 1")]:
        r = H(t, 0, h=52, sw="fill", stroke=BORDER, sides=[0, 0, 1, 0])
        c = H(r, 8, pad=[0, 14, 0, 14 + depth * 24], w=520, h=52)
        icon(c, "chevron-down" if depth == 0 else "target", 15, MUTED if depth == 0 else "#2f9a57")
        T(c, g, 13.5, "Semi Bold" if depth == 0 else "Regular", TEXT, sw="fill", trunc=True)
        c = H(r, 0, pad=[0, 14], w=140, h=52)
        lozenge(c, st)
        c = H(r, 8, pad=[0, 14], w=200, h=52)
        progress(c, [(pct, {"On track": SUCCESS, "At risk": WARN, "Off track": DANGER, "Completed": VIOLET}[st])], w=120, h=6)
        T(c, "%d%%" % pct, 12, "Medium", MUTED)
        c = H(r, 8, pad=[0, 14], w=160, h=52)
        avatar(c, who, 22)
        T(c, who, 13, "Regular", TEXT, sw="fill", trunc=True)
        c = H(r, 0, pad=[0, 14], w=108, h=52)
        T(c, due, 13, "Regular", MUTED)


def goal_detail():
    m = app_screen("Goals / Goal", "Goals", "Goals", ["Goals", "Reach 1M monthly active travellers"], est=1100)
    row = H(m, 24, sw="fill", cross="min")
    l = V(row, 18, sw="fill")
    r = H(l, 10, sw="fill")
    ib = H(r, 0, w=36, h=36, radius=8, fill="#e6f4e9", main="center")
    icon(ib, "target", 20, "#2f9a57")
    T(r, "Cut crash rate below 0.2%", 24, "Semi Bold", TEXT, sw="fill")
    lozenge(r, "At risk")
    btn(r, "Post update", "primary", "megaphone")
    c = card(l, 12, 20, name="Metric")
    hh = H(c, 8, sw="fill")
    T(hh, "Crash rate (7-day)", 14, "Semi Bold", TEXT, sw="fill")
    T(hh, "Target 0.20% · Now 0.31%", 13, "Medium", WARN)
    line_chart(c, "Weekly crash rate", [0.62, 0.55, 0.51, 0.44, 0.40, 0.36, 0.33, 0.31], [0.62, 0.56, 0.5, 0.44, 0.38, 0.32, 0.26, 0.2], w=680, h=170, legend=("Actual", "Plan"))
    T(l, "Updates", 15, "Semi Bold", TEXT)
    for who, when, st, txt in [("Ravi Kumar", "Week of 3 Nov", "At risk", "Down to 0.31% from 0.36%. Android 15 map rotation crash (ORB-418) is now 40% of all crashes; fix is in review and lands in 4.12. Expect ~0.24% after rollout — still short of target, so we're adding a second pass on memory warnings."),
                               ("Ravi Kumar", "Week of 27 Oct", "On track", "Room migration removed the top two database crashes. Rate fell from 0.44% to 0.36%.")]:
        c = card(l, 10, 18, name="Update")
        r = H(c, 8, sw="fill")
        avatar(r, who, 28)
        v = V(r, 0, sw="fill")
        T(v, who, 13, "Semi Bold", TEXT)
        T(v, when, 12, "Regular", MUTED)
        lozenge(r, st)
        T(c, txt, 13.5, "Regular", TEXT, sw="fill", lh="160%")
        rr = H(c, 12)
        T(rr, "👏 6", 12.5, "Medium", MUTED)
        T(rr, "2 comments", 12.5, "Medium", LINK)
    rt = V(row, 14, w=320)
    c = card(rt, 0, [6, 16], name="About")
    T(c, "About", 13.5, "Semi Bold", TEXT)
    box(c, None, h=8, sw="fill")
    field_row(c, "Owner", lambda v: (avatar(v, "Ravi Kumar", 22), T(v, "Ravi Kumar", 13, "Regular", TEXT)), 96)
    field_row(c, "Team", "Mobile platform", 96)
    field_row(c, "Parent", lambda v: T(v, "Reach 1M MAT", 13, "Medium", LINK), 96)
    field_row(c, "Target date", "30 Nov 2026", 96)
    field_row(c, "Followers", lambda v: avatars(v, PEOPLE[:4], 22), 96)
    c = card(rt, 10, 16, name="Contributing work")
    T(c, "Contributing work", 13.5, "Semi Bold", TEXT)
    for a, t, st in [("Track", "ORB-418 Map rotation crash", "In review"), ("Track", "ORB-380 Offline experience", "In progress"),
                     ("Pages", "Crash triage playbook", "Published"), ("Pulse", "Crash rate alert", "Open")]:
        r = H(c, 8, sw="fill")
        app_tile(r, a, 20)
        T(r, t, 13, "Medium", TEXT, sw="fill", trunc=True)
        lozenge(r, st)


# ================================================================ ADMIN
def admin_users():
    m = app_screen("Admin / Users", "Admin", "Users", ["Administration", "Northwind Inc."], est=950)
    page_title(m, "Users", [("Export", "download"), ("Bulk edit", "edit")], "Invite users", "312 of 400 seats used on the Standard plan")
    k = H(m, 12, sw="fill")
    kpi(k, "Active users", "312", "+18 this month", SUCCESS, "users")
    kpi(k, "Pending invites", "9", "3 expire this week", WARN, "mail")
    kpi(k, "Two-step verification", "94%", "Enforced for admins", SUCCESS, "shield")
    kpi(k, "Guests", "27", "External collaborators", MUTED, "user")
    toolbar(m, "Search by name or email", ["Role", "App access", "Status"], None, [])

    def access(apps):
        def f(c):
            for a in apps:
                app_tile(c, a, 20)
        return f
    cols = [("Name", 1.8, "user"), ("Email", 2.0, "muted"), ("Role", 0.9, "status"), ("App access", 1.9, "custom"),
            ("Status", 0.9, "status"), ("Last active", 1.0, "muted")]
    rows = [["Tanmay Singh", "tanmay@northwind.dev", "Admin", access(list(APPS)), "Active", "Now"],
            ["Grace Lee", "grace.lee@northwind.dev", "Member", access(["Track", "Pages", "Code", "Goals"]), "Active", "5 min ago"],
            ["Ravi Kumar", "ravi.k@northwind.dev", "Member", access(["Track", "Code", "Pulse"]), "Active", "12 min ago"],
            ["Aisha Khan", "aisha@northwind.dev", "Admin", access(["Track", "Pages", "Goals", "Desk"]), "Active", "1 h ago"],
            ["Lena Fischer", "lena@northwind.dev", "Member", access(["Track", "Pages", "Boards"]), "Active", "3 h ago"],
            ["Omar Haddad", "omar@northwind.dev", "Member", access(["Desk", "Pulse", "Pages"]), "Active", "Yesterday"],
            ["Sam Ortega", "sam@agency.example", "Guest", access(["Boards"]), "Invited", "—"]]
    table(m, cols, rows, check=True, footer="1–7 of 321")


def admin_billing():
    m = app_screen("Admin / Products & billing", "Admin", "Billing", ["Administration", "Northwind Inc."], est=1000)
    page_title(m, "Products & billing", [("Invoices", "download")], "Add product")
    r = H(m, 16, sw="fill", cross="min")
    c = card(r, 10, 20, name="Plan")
    T(c, "CURRENT PLAN", 11, "Semi Bold", SUBTLE, ls=0.6)
    T(c, "Standard · annual", 22, "Semi Bold", TEXT)
    T(c, "Renews 1 Feb 2027 · 400 seats", 13, "Regular", MUTED)
    progress(c, [(78, ACCENT)], w=480, h=8)
    T(c, "312 of 400 seats used", 12.5, "Medium", MUTED)
    rr = H(c, 8)
    btn(rr, "Upgrade to Premium", "primary")
    btn(rr, "Manage seats", "secondary")
    c = card(r, 8, 20, w=360, sw=None, name="Next bill")
    T(c, "NEXT INVOICE", 11, "Semi Bold", SUBTLE, ls=0.6)
    T(c, "$9,360.00", 28, "Semi Bold", TEXT, ls=-0.6)
    T(c, "Due 1 Dec 2026 · Visa •••• 4242", 13, "Regular", MUTED)
    T(c, "Update payment method", 13, "Medium", LINK)
    T(m, "Products", 15, "Semi Bold", TEXT)
    t = V(m, 0, sw="fill", fill=SURF, radius=10, stroke=BORDER, clip=True)
    for a, (col, ic, tag) in APPS.items():
        users = {"Track": 268, "Pages": 301, "Boards": 120, "Code": 94, "Desk": 41, "Pulse": 38, "Goals": 210}[a]
        price = {"Track": 8.6, "Pages": 6.1, "Boards": 5.0, "Code": 3.0, "Desk": 22.0, "Pulse": 9.5, "Goals": 0}[a]
        rr = H(t, 14, pad=[0, 18], h=64, sw="fill", stroke=BORDER, sides=[0, 0, 1, 0], name="Product/" + a)
        app_tile(rr, a, 32)
        v = V(rr, 1, w=320)
        T(v, "Newxr " + a, 14, "Semi Bold", TEXT)
        T(v, tag, 12, "Regular", MUTED)
        T(rr, "%d users" % users, 13, "Regular", TEXT, w=120)
        T(rr, "Free" if not price else "$%.2f / user / mo" % price, 13, "Regular", MUTED, w=180)
        box(rr, None, h=1, sw="fill")
        lozenge(rr, "Active")
        btn(rr, "Manage", "secondary", None, "sm")


# ================================================================ AUTH
def sign_in():
    s = screen("Auth / Sign in", est=900, fill=SURF)
    row = H(s, 0, sw="fill", h=900, cross="min")
    l = V(row, 0, w=720, h=900, main="center", cross="center", fill=SURF)
    f = V(l, 16, w=380)
    logo(f, 24, mark=32)
    box(f, None, h=12, w=10)
    T(f, "Log in to continue", 26, "Semi Bold", TEXT, ls=-0.5)
    T(f, "One account for Track, Pages, Boards, Code, Desk, Pulse and Goals.", 14, "Regular", MUTED, sw="fill", lh="150%")
    input_box(f, "tanmay@northwind.dev", label="Work email")
    input_box(f, "••••••••••••", label="Password")
    r = H(f, 8, sw="fill")
    checkbox(r, True)
    T(r, "Remember me", 13, "Regular", TEXT, sw="fill")
    T(r, "Forgot password?", 13, "Medium", LINK)
    b = H(f, 0, h=42, sw="fill", radius=8, fill=ACCENT, main="center")
    T(b, "Continue", 14, "Semi Bold", "#ffffff")
    r = H(f, 10, sw="fill")
    hr(r)
    T(r, "or", 12.5, "Regular", SUBTLE)
    hr(r)
    for t, ic in [("Continue with Google", "globe"), ("Continue with SSO", "lock"), ("Use a passkey", "shield")]:
        b = H(f, 8, h=42, sw="fill", radius=8, stroke=BSTRONG, main="center")
        icon(b, ic, 17, TEXT)
        T(b, t, 14, "Medium", TEXT)
    T(f, "Can't log in? · Create an account", 12.5, "Regular", MUTED)
    rt = V(row, 20, sw="fill", h=900, pad=64, main="center",
           fills=[{"type": "linearGradient", "stops": ["#14122b", "#4a3ce0"], "from": [0, 0], "to": [1, 1]}])
    T(rt, "Every team's work, connected.", 36, "Bold", "#ffffff", sw="fill", ls=-0.8)
    T(rt, "Plan in Track, write in Pages, ship from Code and keep customers happy in Desk — with one search, one login and AI that knows your work.", 16, "Regular", CMUTED, sw="fill", lh="160%")
    g = H(rt, 10, wrap=True, sw="fill", crossGap=10)
    for a, (col, ic, tag) in APPS.items():
        x = H(g, 8, pad=[8, 12], radius=10, fill="#ffffff14", stroke="#ffffff22")
        app_tile(x, a, 24)
        T(x, a, 13.5, "Semi Bold", "#ffffff")
