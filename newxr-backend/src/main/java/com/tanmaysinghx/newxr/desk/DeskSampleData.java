package com.tanmaysinghx.newxr.desk;

import com.tanmaysinghx.newxr.seed.SampleDataService.SampleDataContributor;
import com.tanmaysinghx.newxr.user.User;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** IT Help desk (from the design's Desk frame) plus People team, Facilities and Legal for the help center. */
@Component
public class DeskSampleData implements SampleDataContributor {

    private final ServiceDeskRepository desks;
    private final RequestTypeRepository types;
    private final ServiceRequestRepository requests;
    private final RequestCommentRepository comments;

    public DeskSampleData(ServiceDeskRepository desks, RequestTypeRepository types, ServiceRequestRepository requests,
                          RequestCommentRepository comments) {
        this.desks = desks;
        this.types = types;
        this.requests = requests;
        this.comments = comments;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public void load(User admin, Map<String, User> p) {
        if (desks.findByDeskKeyIgnoreCase("HELP").isPresent()) return;
        ServiceDesk it = desk("HELP", "IT Help", "Laptops, software, VPN and access", "#2f6fde", "monitor", 1093);
        ServiceDesk people = desk("PEOPLE", "People team", "Leave, payroll and benefits", "#2f9a57", "users", 214);
        ServiceDesk fac = desk("FAC", "Facilities", "Desks, badges and office issues", "#e8892b", "home", 388);
        ServiceDesk legal = desk("LEGAL", "Legal", "Contracts and NDAs", "#5a4bf5", "shield", 57);

        Map<String, RequestType> t = new HashMap<>();
        t.put("laptop", type(it, "Get a new laptop", "Hardware for new or existing staff", "monitor", "Hardware"));
        t.put("access", type(it, "Request access", "Apps, drives and shared mailboxes", "lock", "Access"));
        t.put("problem", type(it, "Report a problem", "Something is broken or not working", "alert", "Network"));
        t.put("software", type(it, "Install software", "Licensed apps from the catalogue", "download", "Software"));
        t.put("hardware", type(it, "Hardware fault", "Monitors, docks, printers and peripherals", "monitor", "Hardware"));
        type(people, "Book time off", "Annual leave, sick leave and parental leave", "calendar", "Leave");
        type(people, "Payroll question", "Payslips, tax and expenses", "help", "Payroll");
        type(fac, "Office badge", "New, lost or broken badges", "lock", "Badges");
        type(fac, "Report an office issue", "Heating, lighting, kitchen and meeting rooms", "home", "Office");
        type(legal, "Review a contract", "Customer, vendor and partner agreements", "page", "Contracts");
        type(legal, "Request an NDA", "Mutual or one-way NDAs", "shield", "Contracts");

        Instant now = Instant.now();
        User me = admin;
        // key, summary, reporter, type, status, priority, assignee, minutes ago, org
        ServiceRequest vpn = req(it, 1093, "VPN not connecting from Lisbon office", p.get("priya"), t.get("problem"), "WAITING_FOR_SUPPORT", "P2", me,
                now.minus(Duration.ofMinutes(492)), "Lisbon office", "VPN",
                "Since this morning nobody in the Lisbon office can connect to the VPN. The client shows \"TLS handshake failed\". "
                        + "We tried restarting laptops and the office router.");
        vpn.setFirstRespondedAt(vpn.getCreatedAt().plus(Duration.ofMinutes(26)));
        vpn.setLinked("PULSE-77");
        // Breached by 12 minutes: due 12 minutes ago.
        vpn.setResolutionDue(now.minus(Duration.ofMinutes(12)));
        requests.save(vpn);
        comment(vpn, me, "Confirmed the cert on vpn-lis-01 expired. Renewal request sent to Network — linked PULSE incident #77.", true,
                now.minus(Duration.ofMinutes(60)));
        comment(vpn, me, "Hi Priya — thanks for the logs. We've found the cause and expect the VPN back within the hour. I'll update you here.", false,
                now.minus(Duration.ofMinutes(40)));

        dueIn(req(it, 1091, "New laptop for incoming designer", p.get("lena"), t.get("laptop"), "IN_PROGRESS", "P3", me,
                now.minus(Duration.ofHours(22)), "Design", null, "Lena's new team member starts Monday. MacBook Pro 14\", 32 GB please."), now, 100);
        ServiceRequest drive = req(it, 1089, "Access to finance shared drive", p.get("omar"), t.get("access"), "WAITING_FOR_CUSTOMER", "P3", me,
                now.minus(Duration.ofHours(6)), "Finance", null, "Need read access to \\\\finance\\reports for the quarterly review.");
        drive.setSlaPausedAt(now.minus(Duration.ofHours(2)));
        requests.save(drive);
        comment(drive, me, "Could you ask your manager to approve this in the thread? Finance drive access needs a manager sign-off.", false,
                now.minus(Duration.ofHours(2)));
        dueIn(req(it, 1088, "Outlook keeps asking for password", p.get("marco"), t.get("software"), "IN_PROGRESS", "P2", me,
                now.minus(Duration.ofMinutes(458)), "Growth", null, "Every few minutes Outlook pops up a password prompt."), now, 22);
        dueIn(req(it, 1085, "Monitor flickers on dock", p.get("aisha"), t.get("hardware"), "WAITING_FOR_SUPPORT", "P4", me,
                now.minus(Duration.ofHours(65)), "Engineering", null, "External monitor flickers when connected through the USB-C dock."), now, 365);
        dueIn(req(it, 1083, "Add Ravi to on-call rota tool", p.get("ravi"), t.get("access"), "ESCALATED", "P3", me,
                now.minus(Duration.ofHours(20)), "Mobile platform", null, "Please add me to the on-call scheduling tool for Mobile."), now, 190);
        ServiceRequest printer = req(it, 1080, "Printer on floor 2 offline", p.get("grace"), t.get("hardware"), "RESOLVED", "P4", me,
                now.minus(Duration.ofDays(2)), "Engineering", null, "The floor 2 printer shows offline for everyone.");
        resolve(printer, Duration.ofHours(5), 5);
        comment(printer, me, "Replaced the network cable and re-added the printer to the print server. It's back online.", false, printer.getResolvedAt());

        req(it, 1090, "Zoom crashes when sharing screen", p.get("grace"), t.get("software"), "WAITING_FOR_SUPPORT", "P3", null,
                now.minus(Duration.ofMinutes(50)), "Engineering", null, "Zoom quits when I start sharing my screen on macOS.");
        req(it, 1092, "Wi-Fi drops in meeting room Atlas", p.get("marco"), t.get("problem"), "WAITING_FOR_SUPPORT", "P2", null,
                now.minus(Duration.ofMinutes(35)), "Growth", "Wi-Fi", "Wi-Fi disconnects every few minutes in Atlas.");
        req(it, 1087, "Orbit board access for contractors", p.get("aisha"), t.get("access"), "IN_PROGRESS", "P3", p.get("omar"),
                now.minus(Duration.ofHours(10)), "Engineering", null, "Two contractors need access to the Orbit board.");

        // Priya's history and earlier similar requests.
        ServiceRequest badge = req(it, 1061, "Badge for new contractor", p.get("priya"), t.get("access"), "RESOLVED", "P4", me,
                now.minus(Duration.ofDays(12)), "Lisbon office", null, "New contractor starts on Monday in Lisbon.");
        resolve(badge, Duration.ofHours(20), 5);
        ServiceRequest adobe = req(it, 1040, "Adobe licence for marketing", p.get("priya"), t.get("software"), "RESOLVED", "P4", me,
                now.minus(Duration.ofDays(24)), "Lisbon office", null, "One Creative Cloud seat for the marketing intern.");
        resolve(adobe, Duration.ofHours(30), 5);
        String[][] earlier = {
                {"1071", "VPN disconnects every hour", "Renewed the expired gateway certificate on vpn-ams-02 (see 'Renewing site VPN certificates')."},
                {"1052", "VPN TLS handshake failed in Berlin", "Gateway certificate had expired; renewed it and restarted the VPN service."},
                {"1034", "Cannot reach intranet over VPN", "Split-tunnel route was missing; pushed the updated VPN profile."}};
        int i = 0;
        for (String[] e : earlier) {
            ServiceRequest r = req(it, Integer.parseInt(e[0]), e[1], p.get(i == 0 ? "ravi" : i == 1 ? "lena" : "omar"), t.get("problem"), "RESOLVED", "P2",
                    me, now.minus(Duration.ofDays(9 + i * 9L)), null, "VPN", e[1] + ".");
            resolve(r, Duration.ofHours(3 + i), 4 + (i % 2));
            comment(r, me, e[2], false, r.getResolvedAt());
            i++;
        }
        // CSAT history for the 30-day stat: last month a little lower than this month.
        int[] older = {4, 5, 4, 4, 5, 4};
        for (int k = 0; k < older.length; k++) {
            ServiceRequest r = req(it, 1000 + k, "Password reset", p.get("lena"), t.get("access"), "CLOSED", "P4", me,
                    now.minus(Duration.ofDays(35 + k * 3L)), null, null, "Locked out after holiday.");
            resolve(r, Duration.ofHours(2), older[k]);
        }

        // A few requests in the other desks so the help center has life.
        req(people, 213, "Parental leave from January", p.get("ravi"), null, "IN_PROGRESS", "P3", p.get("aisha"),
                now.minus(Duration.ofDays(3)), null, null, "Planning 12 weeks from 6 January.");
        req(fac, 387, "Heating not working on floor 3", p.get("lena"), null, "WAITING_FOR_SUPPORT", "P2", null,
                now.minus(Duration.ofHours(4)), null, null, "Floor 3 east side is very cold since yesterday.");
        req(legal, 56, "NDA for Atlas Travel partnership", p.get("marco"), null, "WAITING_FOR_CUSTOMER", "P3", p.get("aisha"),
                now.minus(Duration.ofDays(1)), null, null, "Mutual NDA ahead of partnership talks.");
    }

    private ServiceDesk desk(String key, String name, String description, String color, String icon, int seq) {
        ServiceDesk d = new ServiceDesk();
        d.setDeskKey(key);
        d.setName(name);
        d.setDescription(description);
        d.setColor(color);
        d.setIcon(icon);
        d.setRequestSeq(seq);
        return desks.save(d);
    }

    private RequestType type(ServiceDesk d, String name, String description, String icon, String category) {
        RequestType t = new RequestType();
        t.setDesk(d);
        t.setName(name);
        t.setDescription(description);
        t.setIcon(icon);
        t.setCategory(category);
        return types.save(t);
    }

    private ServiceRequest req(ServiceDesk d, int number, String summary, User reporter, RequestType type, String status, String priority,
                               User assignee, Instant created, String org, String component, String description) {
        ServiceRequest r = new ServiceRequest();
        r.setDesk(d);
        r.setType(type);
        r.setRequestKey(d.getDeskKey() + "-" + number);
        r.setSummary(summary);
        r.setDescription(description);
        r.setReporter(reporter);
        r.setAssignee(assignee);
        r.setStatus(status);
        r.setPriority(priority);
        r.setOrganization(org);
        r.setComponent(component);
        r.setCreatedAt(created);
        r.setUpdatedAt(created.plus(Duration.ofMinutes(5)).isAfter(Instant.now()) ? Instant.now() : created.plus(Duration.ofMinutes(5)));
        DeskService.applyPolicy(r, null);
        if (!"WAITING_FOR_SUPPORT".equals(status)) {
            r.setFirstRespondedAt(created.plus(Duration.ofMinutes(20)));
        }
        return requests.save(r);
    }

    /** Moves the resolution due date so the queue shows the given minutes left. */
    private void dueIn(ServiceRequest r, Instant now, long minutes) {
        r.setResolutionDue(now.plus(minutes, ChronoUnit.MINUTES));
        requests.save(r);
    }

    private void resolve(ServiceRequest r, Duration took, int csat) {
        r.setResolvedAt(r.getCreatedAt().plus(took));
        r.setFirstRespondedAt(r.getCreatedAt().plus(Duration.ofMinutes(15)));
        r.setCsat(csat);
        r.setUpdatedAt(r.getResolvedAt());
        requests.save(r);
    }

    private void comment(ServiceRequest r, User author, String body, boolean internal, Instant at) {
        RequestComment c = new RequestComment();
        c.setRequest(r);
        c.setAuthor(author);
        c.setBody(body);
        c.setInternal(internal);
        c.setCreatedAt(at);
        c.setUpdatedAt(at);
        comments.save(c);
    }
}
