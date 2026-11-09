package com.usiu.queuely;

import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

public class AdvisorActivity extends AppCompatActivity {

    private static final int SCHOOLS = 0;
    private static final int QUEUE = 1;
    private static final int SHEET = 2;

    private QueuelyStore store;
    private LinearLayout content;
    private LinearLayout actions;
    private int screen = SCHOOLS;
    private String school = "";
    private String notice = "";
    private int catalogIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_panel);
        store = QueuelyStore.get(this);
        content = findViewById(R.id.content);
        actions = findViewById(R.id.actions);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Course advisor");
        toolbar.getMenu().add("Log out").setOnMenuItemClickListener(item -> {
            store.logout();
            store.openHome(this);
            finish();
            return true;
        });
        if (savedInstanceState != null) {
            screen = savedInstanceState.getInt("screen", SCHOOLS);
            school = savedInstanceState.getString("school", "");
            notice = savedInstanceState.getString("notice", "");
        }
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("screen", screen);
        outState.putString("school", school);
        outState.putString("notice", notice);
    }

    private void render() {
        content.removeAllViews();
        actions.removeAllViews();
        if (screen == SHEET) {
            renderSheet();
        } else if (screen == QUEUE) {
            renderQueue();
        } else {
            renderSchools();
        }
    }

    private void renderSchools() {
        addText("Choose your school", true);
        addText("Hospitality sits inside Chandaria School of Business. Pick the school you advise before you open a student.", false);
        for (String id : QueuelyStore.SCHOOL_IDS) {
            MaterialButton button = actionButton(store.schoolName(id));
            button.setOnClickListener(v -> {
                school = id;
                notice = "";
                catalogIndex = 0;
                screen = QUEUE;
                render();
            });
            content.addView(button);
        }
    }

    private void renderQueue() {
        addText(store.schoolShort(school), true);
        JSONArray line = store.line(school);
        addText(line.length() + " in this queue", false);
        if (!notice.isEmpty()) {
            addText(notice, false);
        }
        if (line.length() == 0) {
            addText("No one is waiting in this school queue.", false);
        }
        for (int i = 0; i < line.length(); i++) {
            JSONObject entry = line.optJSONObject(i);
            String place = i == 0
                    ? ("serving".equals(entry.optString("status")) ? "Datasheet open" : "At the front")
                    : "Waiting";
            addText(entry.optString("ticket") + " · " + place + "\nSchool ID " + entry.optString("schoolId")
                    + " · " + store.reasonLabel(entry.optString("reason")) + "\n" + entry.optString("email"), true);
        }
        MaterialButton change = actionButton("Change school");
        change.setOnClickListener(v -> {
            school = "";
            notice = "";
            screen = SCHOOLS;
            render();
        });
        actions.addView(change);
        if (line.length() > 0) {
            TextInputEditText idField = field("School ID of the student at the front");
            MaterialButton open = actionButton("Open datasheet");
            open.setOnClickListener(v -> {
                QueuelyStore.Outcome result = store.openVisit(school, idField.getText() == null ? "" : idField.getText().toString());
                if (!result.ok) {
                    notice = result.error;
                    render();
                    return;
                }
                notice = "";
                screen = SHEET;
                render();
            });
            actions.addView(open);
        }
    }

    private void renderSheet() {
        JSONObject front = store.front(school);
        if (front == null || !"serving".equals(front.optString("status"))) {
            screen = QUEUE;
            render();
            return;
        }
        JSONObject sheet = store.datasheet(front.optString("schoolId"));
        if (sheet == null) {
            notice = "That school ID has no datasheet.";
            screen = QUEUE;
            render();
            return;
        }
        addText("Datasheet", true);
        addText(store.schoolName(school), false);
        if (!notice.isEmpty()) {
            addText(notice, false);
        }
        addText(front.optString("ticket") + " · " + sheet.optString("name") + "\nSchool ID " + sheet.optString("schoolId")
                + " · " + sheet.optString("program") + "\n" + sheet.optString("email") + " · "
                + store.reasonLabel(front.optString("reason")), true);
        addText("Registered courses", true);
        JSONArray courses = sheet == null ? new JSONArray() : sheet.optJSONArray("courses");
        if (courses == null) {
            courses = new JSONArray();
        }
        for (int i = 0; i < courses.length(); i++) {
            JSONObject course = courses.optJSONObject(i);
            String mark = course.optString("mark");
            addText(course.optString("code") + " · " + course.optString("title") + " · " + course.optString("time")
                    + (mark.isEmpty() ? "" : "\n" + mark + " · " + course.optString("registrar")), false);
            if (!"Drop approved by advisor".equals(mark)) {
                String code = course.optString("code");
                MaterialButton drop = actionButton("Drop " + code);
                drop.setOnClickListener(v -> {
                    QueuelyStore.Outcome result = store.dropCourse(school, code);
                    notice = result.ok
                            ? "Drop approved. The course stays on the sheet so the change is still visible."
                            : result.error;
                    render();
                });
                content.addView(drop);
            }
        }
        JSONArray catalog = store.catalog(school);
        addText("Add a course offered in this school", true);
        TextView picked = new TextView(this);
        picked.setText(label(catalog.optJSONObject(catalogIndex)));
        picked.setTextColor(getColor(R.color.usiu_navy));
        content.addView(picked);
        MaterialButton cycle = actionButton("Next course in the list");
        cycle.setOnClickListener(v -> {
            catalogIndex = (catalogIndex + 1) % catalog.length();
            render();
        });
        content.addView(cycle);
        MaterialButton add = actionButton("Approve add");
        add.setOnClickListener(v -> {
            QueuelyStore.Outcome result = store.addCourse(school, catalog.optJSONObject(catalogIndex).optString("code"));
            notice = result.ok ? "Add approved. It stays waiting for the registrar." : result.error;
            render();
        });
        content.addView(add);

        MaterialButton back = actionButton("Back to queue");
        back.setOnClickListener(v -> {
            notice = "";
            screen = QUEUE;
            render();
        });
        actions.addView(back);
        MaterialButton close = actionButton("Close queue");
        close.setOnClickListener(v -> {
            QueuelyStore.Outcome result = store.closeVisit(school);
            if (!result.ok) {
                notice = result.error;
                render();
                return;
            }
            String nextText = result.next == null
                    ? " No one else is waiting."
                    : " Next is school ID " + result.next.optString("schoolId") + ".";
            notice = result.changeText + " recorded and the visit is closed. Sending email to "
                    + result.entry.optString("email") + "." + nextText;
            screen = QUEUE;
            render();
        });
        actions.addView(close);
    }

    private String label(JSONObject course) {
        return course.optString("code") + " · " + course.optString("title") + " · " + course.optString("time");
    }

    private void addText(String value, boolean title) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(getColor(title ? R.color.usiu_navy : R.color.usiu_dark_gray));
        view.setTextSize(title ? 18 : 14);
        view.setPadding(0, dp(title ? 14 : 6), 0, dp(4));
        content.addView(view);
    }

    private TextInputEditText field(String hint) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(hint);
        TextInputEditText edit = new TextInputEditText(layout.getContext());
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(edit);
        content.addView(layout);
        return edit;
    }

    private MaterialButton actionButton(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
