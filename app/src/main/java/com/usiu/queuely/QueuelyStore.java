package com.usiu.queuely;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class QueuelyStore {

    private static final String PREFS = "queuely";
    private static final String DATA = "data-v2";
    private static final String SESSION = "session";
    private static final String PENDING_SCHOOL = "pending_school";

    public static final String[] SCHOOL_IDS = {"chss", "sst", "business", "sphs", "scca"};
    public static final String[] REASON_IDS = {"add", "drop", "consult"};

    private final Context appContext;
    private final SharedPreferences prefs;

    private QueuelyStore(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains(DATA)) {
            save(seed());
        }
    }

    public static QueuelyStore get(Context context) {
        return new QueuelyStore(context);
    }

    public static class Outcome {
        public boolean ok;
        public String error = "";
        public String temp = "";
        public String email = "";
        public String changeText = "";
        public JSONObject entry;
        public JSONObject next;

        static Outcome fail(String error) {
            Outcome outcome = new Outcome();
            outcome.ok = false;
            outcome.error = error;
            return outcome;
        }

        static Outcome ok() {
            Outcome outcome = new Outcome();
            outcome.ok = true;
            return outcome;
        }
    }

    public static class Visit {
        public JSONObject entry;
        public int ahead;
        public int waitMinutes;
    }

    public void openHome(Activity activity) {
        JSONObject user = currentUser();
        Class<?> destination = LoginActivity.class;
        if (user != null && user.optBoolean("mustChangePassword")) {
            destination = ChangePasswordActivity.class;
        } else if (user != null && "admin".equals(user.optString("role"))) {
            destination = AdminActivity.class;
        } else if (user != null && "advisor".equals(user.optString("role"))) {
            destination = AdvisorActivity.class;
        } else if (user != null && myVisit() != null) {
            destination = QueueStatusActivity.class;
        } else if (user != null) {
            destination = SchoolSelectionActivity.class;
        }
        Intent intent = new Intent(activity, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
    }

    public JSONObject currentUser() {
        String email = prefs.getString(SESSION, "");
        if (email.isEmpty()) {
            return null;
        }
        return userByEmail(load(), email);
    }

    public void logout() {
        prefs.edit().remove(SESSION).remove(PENDING_SCHOOL).commit();
    }

    public void setPendingSchool(String school) {
        prefs.edit().putString(PENDING_SCHOOL, school).commit();
    }

    public String pendingSchool() {
        return prefs.getString(PENDING_SCHOOL, "");
    }

    public Outcome login(String email, String password) {
        String normalized = email.trim().toLowerCase(Locale.US);
        if (!normalized.endsWith("@usiu.ac.ke")) {
            return Outcome.fail("Use your USIU email, ending in @usiu.ac.ke.");
        }
        JSONObject user = userByEmail(load(), normalized);
        if (user == null || !password.equals(user.optString("password"))) {
            return Outcome.fail("That email or password does not match a QueueLy account. Accounts are created by the system admin.");
        }
        prefs.edit().putString(SESSION, user.optString("email")).commit();
        return Outcome.ok();
    }

    public Outcome changePassword(String currentPassword, String nextPassword) {
        JSONObject data = load();
        JSONObject user = userByEmail(data, prefs.getString(SESSION, ""));
        if (user == null || !currentPassword.equals(user.optString("password"))) {
            return Outcome.fail("The current password is not correct.");
        }
        if (nextPassword.length() < 6) {
            return Outcome.fail("Choose a password of at least 6 characters.");
        }
        if (nextPassword.equals(currentPassword)) {
            return Outcome.fail("Choose a password that is different from the one you were given.");
        }
        try {
            user.put("password", nextPassword);
            user.put("mustChangePassword", false);
        } catch (JSONException exception) {
            return Outcome.fail("Could not save the password.");
        }
        save(data);
        return Outcome.ok();
    }

    public JSONArray people() {
        return load().optJSONArray("users");
    }

    public JSONArray outbox() {
        return load().optJSONArray("outbox");
    }

    public Outcome addPerson(String givenName, String surname, String role, String schoolId) {
        JSONObject data = load();
        String built = usiuEmail(data, givenName, surname);
        if (built.startsWith("error:")) {
            return Outcome.fail(built.substring(6));
        }
        String normalized = built;
        if (!"student".equals(role) && !"advisor".equals(role)) {
            return Outcome.fail("Choose student or advisor.");
        }
        String id = "";
        if ("student".equals(role)) {
            id = schoolId.trim();
            if (!id.matches("\\d{5,8}")) {
                return Outcome.fail("Enter the school ID the student already has, such as 670375.");
            }
            JSONArray users = data.optJSONArray("users");
            for (int i = 0; i < users.length(); i++) {
                if (id.equals(users.optJSONObject(i).optString("schoolId"))) {
                    return Outcome.fail("That school ID is already on another account.");
                }
            }
        }
        String temp = "Ql-" + (1000 + (int) (Math.random() * 9000));
        try {
            JSONObject user = new JSONObject();
            user.put("email", normalized);
            user.put("password", temp);
            user.put("role", role);
            user.put("mustChangePassword", true);
            user.put("name", titleName(givenName) + " " + titleName(surname));
            if ("student".equals(role)) {
                user.put("schoolId", id);
                user.put("program", "Program on the school datasheet");
                JSONArray starter = new JSONArray();
                starter.put(course("COM 1500", "Communication Skills", "Wed 09:00"));
                data.getJSONObject("sheets").put(id, starter);
            }
            data.getJSONArray("users").put(user);
            pushMail(data, normalized, "Your QueueLy password",
                    "A QueueLy account was created for " + normalized + ". Your first password is " + temp
                            + ". Sign in with your USIU email and change this password before you continue. QueueLy does not create a new student ID.");
        } catch (JSONException exception) {
            return Outcome.fail("Could not add that person.");
        }
        save(data);
        Outcome outcome = Outcome.ok();
        outcome.temp = temp;
        outcome.email = normalized;
        return outcome;
    }

    public Outcome resetPassword(String email) {
        JSONObject data = load();
        JSONObject user = userByEmail(data, email);
        if (user == null || "admin".equals(user.optString("role"))) {
            return Outcome.fail("That account cannot be reset here.");
        }
        String temp = "Ql-" + (1000 + (int) (Math.random() * 9000));
        try {
            user.put("password", temp);
            user.put("mustChangePassword", true);
            pushMail(data, user.optString("email"), "Your new QueueLy password",
                    "The system admin reset the QueueLy password for " + user.optString("email")
                            + ". Your temporary password is " + temp + ". Change it the next time you sign in.");
        } catch (JSONException exception) {
            return Outcome.fail("Could not reset that password.");
        }
        save(data);
        Outcome outcome = Outcome.ok();
        outcome.temp = temp;
        return outcome;
    }

    public String schoolName(String id) {
        switch (id) {
            case "business":
                return "Chandaria School of Business";
            case "sst":
                return "School of Science and Technology";
            case "chss":
                return "School of Humanities and Social Sciences";
            case "scca":
                return "School of Communication, Cinematic and Creative Arts";
            case "sphs":
                return "School of Pharmacy and Health Sciences";
            default:
                return "";
        }
    }

    public String schoolShort(String id) {
        switch (id) {
            case "business":
                return "Business";
            case "sst":
                return "Science and Technology";
            case "chss":
                return "Humanities and Social Sciences";
            case "scca":
                return "Communication and Creative Arts";
            case "sphs":
                return "Pharmacy and Health Sciences";
            default:
                return "";
        }
    }

    public String reasonLabel(String id) {
        switch (id) {
            case "add":
                return "Add Course";
            case "drop":
                return "Drop Course";
            case "consult":
                return "Academic Consultation";
            default:
                return id;
        }
    }

    public JSONArray line(String school) {
        return liveLine(load(), school);
    }

    public JSONObject front(String school) {
        JSONArray line = line(school);
        return line.length() == 0 ? null : line.optJSONObject(0);
    }

    public JSONObject datasheet(String schoolId) {
        JSONObject data = load();
        JSONArray users = data.optJSONArray("users");
        JSONObject user = null;
        for (int i = 0; i < users.length(); i++) {
            JSONObject candidate = users.optJSONObject(i);
            if (schoolId.equals(candidate.optString("schoolId"))) {
                user = candidate;
                break;
            }
        }
        if (user == null) {
            return null;
        }
        try {
            JSONObject sheet = new JSONObject();
            sheet.put("name", user.optString("name"));
            sheet.put("schoolId", user.optString("schoolId"));
            sheet.put("program", user.optString("program"));
            sheet.put("email", user.optString("email"));
            sheet.put("courses", data.getJSONObject("sheets").optJSONArray(schoolId));
            return sheet;
        } catch (JSONException exception) {
            return null;
        }
    }

    public JSONArray catalog(String school) {
        JSONArray courses = new JSONArray();
        if ("business".equals(school)) {
            courses.put(course("BUS 1010", "Introduction to Business", "Mon 09:00"));
            courses.put(course("ACC 2010", "Financial Accounting", "Tue 11:00"));
            courses.put(course("MKT 3010", "Marketing Management", "Wed 14:00"));
            courses.put(course("HTM 2200", "Hospitality Operations", "Thu 10:00"));
        } else if ("sst".equals(school)) {
            courses.put(course("SWE 3090", "Software Engineering", "Mon 10:00"));
            courses.put(course("MTH 2210", "Calculus II", "Tue 14:00"));
            courses.put(course("COM 1500", "Communication Skills", "Wed 09:00"));
            courses.put(course("IST 2040", "Database Systems", "Thu 11:00"));
            courses.put(course("SWE 4020", "Software Project", "Mon 10:00"));
        } else if ("chss".equals(school)) {
            courses.put(course("PSY 1100", "Introduction to Psychology", "Mon 08:00"));
            courses.put(course("SOC 2200", "Social Theory", "Tue 13:00"));
            courses.put(course("IRL 3100", "International Relations", "Thu 15:00"));
        } else if ("scca".equals(school)) {
            courses.put(course("JRN 2100", "News Writing", "Mon 11:00"));
            courses.put(course("FLM 2400", "Film Production", "Wed 16:00"));
            courses.put(course("CMM 1800", "Media Studies", "Fri 09:00"));
        } else if ("sphs".equals(school)) {
            courses.put(course("PHA 2100", "Pharmaceutical Chemistry", "Mon 08:00"));
            courses.put(course("NUR 2300", "Foundations of Nursing", "Tue 10:00"));
            courses.put(course("BIO 2500", "Human Physiology", "Thu 14:00"));
        }
        return courses;
    }

    public Outcome openVisit(String school, String enteredId) {
        JSONObject data = load();
        JSONArray line = liveLine(data, school);
        if (line.length() == 0) {
            return Outcome.fail("No one is waiting in this school queue.");
        }
        JSONObject front = line.optJSONObject(0);
        if (!front.optString("schoolId").equals(enteredId.trim())) {
            return Outcome.fail("That school ID is not the student at the front of this queue.");
        }
        try {
            if (!"serving".equals(front.optString("status"))) {
                front.put("status", "serving");
                JSONObject mailed = front.getJSONObject("mailed");
                if (!mailed.optBoolean("turn")) {
                    mailed.put("turn", true);
                    pushMail(data, front.optString("email"), "It is your turn with the course advisor",
                            "Student " + front.optString("schoolId") + ", the advisor has opened your datasheet at "
                                    + schoolName(school) + ". Ticket " + front.optString("ticket") + ".");
                }
                syncNextMails(data, school);
            }
        } catch (JSONException exception) {
            return Outcome.fail("Could not open that datasheet.");
        }
        save(data);
        Outcome outcome = Outcome.ok();
        outcome.entry = front;
        return outcome;
    }

    public Outcome addCourse(String school, String code) {
        JSONObject data = load();
        JSONArray line = liveLine(data, school);
        JSONObject front = line.length() == 0 ? null : line.optJSONObject(0);
        if (front == null || !"serving".equals(front.optString("status"))) {
            return Outcome.fail("Open the student at the front before adding a course.");
        }
        JSONObject course = findCourse(catalog(school), code);
        if (course == null) {
            return Outcome.fail("That course is not offered in this school.");
        }
        try {
            JSONArray sheet = data.getJSONObject("sheets").getJSONArray(front.optString("schoolId"));
            JSONArray active = activeCourses(sheet);
            if (findCourse(active, course.optString("code")) != null) {
                return Outcome.fail(course.optString("code") + " is already on this datasheet.");
            }
            for (int i = 0; i < active.length(); i++) {
                if (course.optString("time").equals(active.optJSONObject(i).optString("time"))) {
                    return Outcome.fail(course.optString("code") + " meets at the same time as another registered course.");
                }
            }
            JSONObject row = course("code-placeholder", "", "");
            row.put("code", course.optString("code"));
            row.put("title", course.optString("title"));
            row.put("time", course.optString("time"));
            row.put("mark", "Add approved by advisor");
            row.put("registrar", "Waiting for the registrar");
            sheet.put(row);
            JSONObject change = new JSONObject();
            change.put("action", "add");
            change.put("code", course.optString("code"));
            change.put("title", course.optString("title"));
            front.getJSONArray("changes").put(change);
        } catch (JSONException exception) {
            return Outcome.fail("Could not add that course.");
        }
        save(data);
        return Outcome.ok();
    }

    public Outcome dropCourse(String school, String code) {
        JSONObject data = load();
        JSONArray line = liveLine(data, school);
        JSONObject front = line.length() == 0 ? null : line.optJSONObject(0);
        if (front == null || !"serving".equals(front.optString("status"))) {
            return Outcome.fail("Open the student at the front before dropping a course.");
        }
        try {
            JSONArray sheet = data.getJSONObject("sheets").getJSONArray(front.optString("schoolId"));
            JSONObject row = null;
            for (int i = 0; i < sheet.length(); i++) {
                JSONObject candidate = sheet.optJSONObject(i);
                if (code.equals(candidate.optString("code"))
                        && !"Drop approved by advisor".equals(candidate.optString("mark"))) {
                    row = candidate;
                    break;
                }
            }
            if (row == null) {
                return Outcome.fail("That course is not an active registration on this datasheet.");
            }
            row.put("mark", "Drop approved by advisor");
            row.put("registrar", "Waiting for the registrar");
            JSONObject change = new JSONObject();
            change.put("action", "drop");
            change.put("code", row.optString("code"));
            change.put("title", row.optString("title"));
            front.getJSONArray("changes").put(change);
        } catch (JSONException exception) {
            return Outcome.fail("Could not drop that course.");
        }
        save(data);
        return Outcome.ok();
    }

    public Outcome closeVisit(String school) {
        JSONObject data = load();
        JSONObject advisor = currentUser();
        JSONArray line = liveLine(data, school);
        JSONObject front = line.length() == 0 ? null : line.optJSONObject(0);
        if (front == null || !"serving".equals(front.optString("status"))) {
            return Outcome.fail("Open the student at the front before you close the queue.");
        }
        try {
            front.put("status", "closed");
            front.put("closedAt", now());
            front.put("advisor", advisor.optString("email"));
            front.getJSONObject("mailed").put("closed", true);
            String changeText = changeText(front.optJSONArray("changes"));
            pushMail(data, front.optString("email"), "Your advisor visit is closed",
                    changeText + " recorded and your visit is closed. The record is waiting for the registrar. Ticket "
                            + front.optString("ticket") + ".");
            JSONObject historyItem = new JSONObject();
            historyItem.put("ticket", front.optString("ticket"));
            historyItem.put("email", front.optString("email"));
            historyItem.put("schoolId", front.optString("schoolId"));
            historyItem.put("school", front.optString("school"));
            historyItem.put("reason", front.optString("reason"));
            historyItem.put("changes", front.optJSONArray("changes"));
            historyItem.put("advisor", advisor.optString("email"));
            historyItem.put("closedAt", front.optString("closedAt"));
            JSONArray history = data.getJSONArray("history");
            JSONArray rebuilt = new JSONArray();
            rebuilt.put(historyItem);
            for (int i = 0; i < history.length(); i++) {
                rebuilt.put(history.opt(i));
            }
            data.put("history", rebuilt);
            syncNextMails(data, school);
            save(data);
            Outcome outcome = Outcome.ok();
            outcome.changeText = changeText;
            outcome.entry = front;
            JSONArray remaining = liveLine(data, school);
            outcome.next = remaining.length() == 0 ? null : remaining.optJSONObject(0);
            return outcome;
        } catch (JSONException exception) {
            return Outcome.fail("Could not close that visit.");
        }
    }

    public Outcome joinQueue(String school, String reason) {
        JSONObject data = load();
        JSONObject user = currentUser();
        if (user == null || !"student".equals(user.optString("role"))) {
            return Outcome.fail("Only a student can join a school queue.");
        }
        if (activeVisit(data, user.optString("email")) != null) {
            return Outcome.fail("You already have an open visit. Leave that queue before joining another.");
        }
        try {
            int number = data.optInt("nextTicket");
            String ticket = "A-" + String.format(Locale.US, "%03d", number);
            data.put("nextTicket", number + 1);
            JSONObject entry = new JSONObject();
            entry.put("ticket", ticket);
            entry.put("school", school);
            entry.put("email", user.optString("email"));
            entry.put("schoolId", user.optString("schoolId"));
            entry.put("reason", reason);
            entry.put("status", "waiting");
            entry.put("joinedAt", now());
            JSONObject mailed = new JSONObject();
            mailed.put("joined", true);
            mailed.put("next", false);
            mailed.put("turn", false);
            mailed.put("closed", false);
            entry.put("mailed", mailed);
            entry.put("changes", new JSONArray());
            data.getJSONArray("queue").put(entry);
            pushMail(data, user.optString("email"), "You joined the QueueLy line",
                    user.optString("name") + ", you joined " + schoolName(school) + " for " + reasonLabel(reason)
                            + ". Your ticket is " + ticket + ".");
            syncNextMails(data, school);
            save(data);
            Outcome outcome = Outcome.ok();
            outcome.entry = entry;
            return outcome;
        } catch (JSONException exception) {
            return Outcome.fail("Could not join the queue.");
        }
    }

    public Outcome leaveQueue() {
        JSONObject data = load();
        JSONObject user = currentUser();
        JSONObject entry = user == null ? null : activeVisit(data, user.optString("email"));
        if (entry == null) {
            return Outcome.fail("You are not in a queue.");
        }
        if ("serving".equals(entry.optString("status"))) {
            return Outcome.fail("The advisor has opened your datasheet, so this visit has to be closed by the advisor.");
        }
        try {
            entry.put("status", "closed");
            entry.put("left", true);
        } catch (JSONException exception) {
            return Outcome.fail("Could not leave the queue.");
        }
        save(data);
        return Outcome.ok();
    }

    public Visit myVisit() {
        JSONObject user = currentUser();
        if (user == null) {
            return null;
        }
        JSONObject data = load();
        JSONObject entry = activeVisit(data, user.optString("email"));
        if (entry == null) {
            return null;
        }
        JSONArray line = liveLine(data, entry.optString("school"));
        int index = 0;
        for (int i = 0; i < line.length(); i++) {
            if (entry.optString("ticket").equals(line.optJSONObject(i).optString("ticket"))) {
                index = i;
                break;
            }
        }
        Visit visit = new Visit();
        visit.entry = entry;
        visit.ahead = index;
        int minimum = "serving".equals(entry.optString("status")) ? 0 : 1;
        visit.waitMinutes = Math.max(index, minimum) * 6;
        return visit;
    }

    public JSONArray myHistory() {
        JSONObject user = currentUser();
        JSONArray matches = new JSONArray();
        if (user == null) {
            return matches;
        }
        JSONArray history = load().optJSONArray("history");
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (user.optString("email").equals(item.optString("email"))) {
                matches.put(item);
            }
        }
        return matches;
    }

    public JSONArray myMail() {
        JSONObject user = currentUser();
        JSONArray matches = new JSONArray();
        if (user == null) {
            return matches;
        }
        JSONArray outbox = load().optJSONArray("outbox");
        for (int i = 0; i < outbox.length(); i++) {
            JSONObject mail = outbox.optJSONObject(i);
            if (user.optString("email").equals(mail.optString("to"))) {
                matches.put(mail);
            }
        }
        return matches;
    }

    private JSONObject seed() {
        try {
            JSONObject data = new JSONObject();
            data.put("nextTicket", 44);
            JSONArray users = new JSONArray();
            users.put(user("admin@usiu.ac.ke", "admin", "QueueLy Admin", "", ""));
            users.put(user("advisor@usiu.ac.ke", "advisor", "Course Advisor", "", ""));
            users.put(user("amchol@usiu.ac.ke", "student", "Student 670375", "670375", "BSc Software Engineering"));
            users.put(user("jsmith@usiu.ac.ke", "student", "Student 671440", "671440", "BSc Information Systems"));
            data.put("users", users);
            JSONObject sheets = new JSONObject();
            JSONArray amchol = new JSONArray();
            amchol.put(course("SWE 3090", "Software Engineering", "Mon 10:00"));
            amchol.put(course("MTH 2210", "Calculus II", "Tue 14:00"));
            amchol.put(course("COM 1500", "Communication Skills", "Wed 09:00"));
            JSONArray second = new JSONArray();
            second.put(course("IST 2040", "Database Systems", "Thu 11:00"));
            second.put(course("COM 1500", "Communication Skills", "Wed 09:00"));
            sheets.put("670375", amchol);
            sheets.put("671440", second);
            data.put("sheets", sheets);
            JSONArray queue = new JSONArray();
            queue.put(visit("A-042", "amchol@usiu.ac.ke", "670375", "drop", "2026-11-09T08:10:00.000Z"));
            queue.put(visit("A-043", "jsmith@usiu.ac.ke", "671440", "add", "2026-11-09T08:24:00.000Z"));
            data.put("queue", queue);
            data.put("outbox", new JSONArray());
            data.put("history", new JSONArray());
            return data;
        } catch (JSONException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private JSONObject user(String email, String role, String name, String schoolId, String program) throws JSONException {
        JSONObject user = new JSONObject();
        user.put("email", email);
        user.put("password", "Queuely123");
        user.put("role", role);
        user.put("mustChangePassword", false);
        user.put("name", name);
        if (!schoolId.isEmpty()) {
            user.put("schoolId", schoolId);
            user.put("program", program);
        }
        return user;
    }

    private JSONObject course(String code, String title, String time) {
        JSONObject course = new JSONObject();
        try {
            course.put("code", code);
            course.put("title", title);
            course.put("time", time);
        } catch (JSONException exception) {
            throw new IllegalStateException(exception);
        }
        return course;
    }

    private JSONObject visit(String ticket, String email, String schoolId, String reason, String joinedAt) throws JSONException {
        JSONObject entry = new JSONObject();
        entry.put("ticket", ticket);
        entry.put("school", "sst");
        entry.put("email", email);
        entry.put("schoolId", schoolId);
        entry.put("reason", reason);
        entry.put("status", "waiting");
        entry.put("joinedAt", joinedAt);
        JSONObject mailed = new JSONObject();
        mailed.put("joined", true);
        mailed.put("next", false);
        mailed.put("turn", false);
        mailed.put("closed", false);
        entry.put("mailed", mailed);
        entry.put("changes", new JSONArray());
        return entry;
    }

    private JSONObject load() {
        try {
            return new JSONObject(prefs.getString(DATA, "{}"));
        } catch (JSONException exception) {
            return seed();
        }
    }

    private void save(JSONObject data) {
        synchronized (QueuelyStore.class) {
            prefs.edit().putString(DATA, data.toString()).commit();
        }
        deliverPending();
    }

    private void saveQuiet(JSONObject data) {
        synchronized (QueuelyStore.class) {
            prefs.edit().putString(DATA, data.toString()).commit();
        }
    }

    private void deliverPending() {
        new Thread(() -> {
            ArrayList<JSONObject> pending = new ArrayList<>();
            synchronized (QueuelyStore.class) {
                JSONObject data = load();
                JSONArray outbox = data.optJSONArray("outbox");
                if (outbox == null) {
                    return;
                }
                boolean changed = false;
                for (int i = 0; i < outbox.length(); i++) {
                    JSONObject mail = outbox.optJSONObject(i);
                    String status = mail.optString("status");
                    if ("ready".equals(status) || status.isEmpty()) {
                        try {
                            if (!mail.has("id")) {
                                mail.put("id", now() + "-" + i);
                            }
                            mail.put("status", "sending");
                            pending.add(new JSONObject(mail.toString()));
                            changed = true;
                        } catch (JSONException exception) {
                            return;
                        }
                    }
                }
                if (changed) {
                    saveQuiet(data);
                }
            }
            for (JSONObject mail : pending) {
                String error = postMail(mail);
                synchronized (QueuelyStore.class) {
                    JSONObject data = load();
                    JSONArray outbox = data.optJSONArray("outbox");
                    for (int i = 0; i < outbox.length(); i++) {
                        JSONObject item = outbox.optJSONObject(i);
                        if (mail.optString("id").equals(item.optString("id"))) {
                            try {
                                item.put("status", error == null ? "sent" : "failed");
                                item.put("detail", error == null ? "" : error);
                            } catch (JSONException exception) {
                                return;
                            }
                        }
                    }
                    saveQuiet(data);
                }
            }
        }).start();
    }

    private String postMail(JSONObject mail) {
        String[] urls = {
                appContext.getString(R.string.mail_server_url),
                "http://10.0.2.2:8787/api/mail"
        };
        String lastError = "The mail server is not running.";
        for (String url : urls) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(20000);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                JSONObject payload = new JSONObject();
                payload.put("to", mail.optString("to"));
                payload.put("subject", mail.optString("subject"));
                payload.put("body", mail.optString("body"));
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream stream = connection.getOutputStream();
                stream.write(bytes);
                stream.close();
                int code = connection.getResponseCode();
                InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String response = readStream(input);
                if (code >= 200 && code < 300) {
                    return null;
                }
                try {
                    return new JSONObject(response).optString("error", lastError);
                } catch (JSONException exception) {
                    return lastError;
                }
            } catch (Exception exception) {
                lastError = "The mail server is not running.";
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return lastError;
    }

    private String readStream(InputStream input) throws java.io.IOException {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toString("UTF-8");
    }

    private String lettersOnly(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.US);
    }

    private String titleName(String value) {
        String clean = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        StringBuilder builder = new StringBuilder();
        boolean capital = true;
        for (int i = 0; i < clean.length(); i++) {
            char letter = clean.charAt(i);
            if (capital && Character.isLetter(letter)) {
                builder.append(Character.toUpperCase(letter));
                capital = false;
            } else {
                builder.append(Character.toLowerCase(letter));
                capital = letter == ' ' || letter == '-';
            }
        }
        return builder.toString();
    }

    private String usiuEmail(JSONObject data, String givenName, String surname) {
        String given = lettersOnly(givenName);
        String family = lettersOnly(surname);
        if (given.length() < 1 || family.length() < 2) {
            return "error:Enter a given name and a surname. QueueLy builds the USIU email from them, for example Ann Mchol becomes amchol@usiu.ac.ke.";
        }
        for (int i = 1; i <= given.length(); i++) {
            String local = given.substring(0, i) + family;
            if (local.length() < 3 || local.length() > 30) {
                continue;
            }
            String email = local + "@usiu.ac.ke";
            if (userByEmail(data, email) == null) {
                return email;
            }
        }
        return "error:That name is already used. USIU emails look like jsmith@usiu.ac.ke.";
    }

    private JSONObject userByEmail(JSONObject data, String email) {
        JSONArray users = data.optJSONArray("users");
        if (users == null) {
            return null;
        }
        for (int i = 0; i < users.length(); i++) {
            JSONObject user = users.optJSONObject(i);
            if (email.equalsIgnoreCase(user.optString("email"))) {
                return user;
            }
        }
        return null;
    }

    private JSONArray liveLine(JSONObject data, String school) {
        ArrayList<JSONObject> rows = new ArrayList<>();
        JSONArray queue = data.optJSONArray("queue");
        for (int i = 0; i < queue.length(); i++) {
            JSONObject entry = queue.optJSONObject(i);
            if (school.equals(entry.optString("school")) && !"closed".equals(entry.optString("status"))) {
                rows.add(entry);
            }
        }
        Collections.sort(rows, Comparator.comparing(row -> row.optString("joinedAt")));
        JSONArray line = new JSONArray();
        for (JSONObject row : rows) {
            line.put(row);
        }
        return line;
    }

    private JSONObject activeVisit(JSONObject data, String email) {
        JSONArray queue = data.optJSONArray("queue");
        for (int i = 0; i < queue.length(); i++) {
            JSONObject entry = queue.optJSONObject(i);
            if (email.equals(entry.optString("email")) && !"closed".equals(entry.optString("status"))) {
                return entry;
            }
        }
        return null;
    }

    private void pushMail(JSONObject data, String to, String subject, String body) throws JSONException {
        JSONObject mail = new JSONObject();
        mail.put("id", now() + "-" + System.nanoTime());
        mail.put("to", to);
        mail.put("subject", subject);
        mail.put("body", body);
        mail.put("at", now());
        mail.put("status", "ready");
        mail.put("detail", "");
        JSONArray outbox = data.getJSONArray("outbox");
        JSONArray rebuilt = new JSONArray();
        rebuilt.put(mail);
        for (int i = 0; i < outbox.length(); i++) {
            rebuilt.put(outbox.opt(i));
        }
        data.put("outbox", rebuilt);
    }

    private void syncNextMails(JSONObject data, String school) throws JSONException {
        JSONArray line = liveLine(data, school);
        int servingAt = -1;
        for (int i = 0; i < line.length(); i++) {
            if ("serving".equals(line.optJSONObject(i).optString("status"))) {
                servingAt = i;
                break;
            }
        }
        if (servingAt < 0 || servingAt + 1 >= line.length()) {
            return;
        }
        JSONObject follower = line.optJSONObject(servingAt + 1);
        JSONObject mailed = follower.getJSONObject("mailed");
        if (!mailed.optBoolean("next")) {
            mailed.put("next", true);
            JSONObject user = userByEmail(data, follower.optString("email"));
            pushMail(data, follower.optString("email"), "You are next in the QueueLy line",
                    user.optString("name") + " (" + user.optString("schoolId") + "), you are next at "
                            + schoolName(school) + ". Only the student being served is ahead of you. Ticket "
                            + follower.optString("ticket") + ".");
        }
    }

    private JSONObject findCourse(JSONArray courses, String code) {
        for (int i = 0; i < courses.length(); i++) {
            JSONObject course = courses.optJSONObject(i);
            if (code.equals(course.optString("code"))) {
                return course;
            }
        }
        return null;
    }

    private JSONArray activeCourses(JSONArray sheet) {
        JSONArray active = new JSONArray();
        for (int i = 0; i < sheet.length(); i++) {
            JSONObject row = sheet.optJSONObject(i);
            if (!"Drop approved by advisor".equals(row.optString("mark"))) {
                active.put(row);
            }
        }
        return active;
    }

    private String now() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private String changeText(JSONArray changes) {
        if (changes == null || changes.length() == 0) {
            return "No course change";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < changes.length(); i++) {
            JSONObject change = changes.optJSONObject(i);
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append("add".equals(change.optString("action")) ? "Add" : "Drop");
            builder.append(" of ").append(change.optString("code"));
        }
        return builder.toString();
    }
}
