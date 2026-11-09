package com.usiu.queuely;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

public class AdminActivity extends AppCompatActivity {

    private QueuelyStore store;
    private LinearLayout content;
    private String role = "student";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_panel);
        store = QueuelyStore.get(this);
        content = findViewById(R.id.content);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("People");
        toolbar.getMenu().add("Log out").setOnMenuItemClickListener(item -> {
            store.logout();
            store.openHome(this);
            finish();
            return true;
        });
        render();
    }

    private void render() {
        content.removeAllViews();
        addText("Add a student or advisor. Record the school ID the student already has. QueueLy does not create a new one.", false);
        JSONArray people = store.people();
        for (int i = 0; i < people.length(); i++) {
            JSONObject person = people.optJSONObject(i);
            String line = person.optString("email") + " · " + person.optString("role");
            if (!person.optString("schoolId").isEmpty()) {
                line += " · " + person.optString("schoolId");
            }
            line += person.optBoolean("mustChangePassword") ? " · first login" : " · ready";
            addText(line, true);
            if (!"admin".equals(person.optString("role"))) {
                String email = person.optString("email");
                MaterialButton reset = new MaterialButton(this);
                reset.setText("New password");
                reset.setOnClickListener(v -> {
                    QueuelyStore.Outcome result = store.resetPassword(email);
                    Toast.makeText(this, result.ok
                            ? "A new temporary password was emailed to " + email + "."
                            : result.error, Toast.LENGTH_LONG).show();
                    render();
                });
                content.addView(reset);
            }
        }

        addText("Add a person", true);
        addText("The USIU email is built from the name. Ann Mchol becomes amchol@usiu.ac.ke.", false);
        TextInputEditText givenName = field("Given name", InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        TextInputEditText surname = field("Surname", InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        MaterialButton student = choiceButton("Student");
        MaterialButton advisor = choiceButton("Course advisor");
        TextInputEditText schoolId = field("School ID they already have", InputType.TYPE_CLASS_NUMBER);
        student.setOnClickListener(v -> {
            role = "student";
            schoolId.setVisibility(View.VISIBLE);
        });
        advisor.setOnClickListener(v -> {
            role = "advisor";
            schoolId.setVisibility(View.GONE);
        });
        MaterialButton add = new MaterialButton(this);
        add.setText("Email first password");
        add.setOnClickListener(v -> {
            QueuelyStore.Outcome result = store.addPerson(
                    givenName.getText() == null ? "" : givenName.getText().toString(),
                    surname.getText() == null ? "" : surname.getText().toString(),
                    role,
                    schoolId.getText() == null ? "" : schoolId.getText().toString());
            if (!result.ok) {
                Toast.makeText(this, result.error, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "First password emailed to " + result.email + ". Temporary password " + result.temp + ".", Toast.LENGTH_LONG).show();
            render();
        });
        content.addView(add);

        addText("Outbox", true);
        JSONArray outbox = store.outbox();
        int limit = Math.min(4, outbox.length());
        if (limit == 0) {
            addText("No email is waiting.", false);
        }
        for (int i = 0; i < limit; i++) {
            JSONObject mail = outbox.optJSONObject(i);
            String status = mail.optString("status");
            String label = "sent".equals(status) ? "Sent to " : "failed".equals(status) ? "Not sent to " : "Sending to ";
            String detail = mail.optString("detail");
            addText(label + mail.optString("to") + (detail.isEmpty() ? "" : ". " + detail)
                    + "\n" + mail.optString("subject") + "\n" + mail.optString("body"), false);
        }
    }

    private void addText(String value, boolean title) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(getColor(title ? R.color.usiu_navy : R.color.usiu_dark_gray));
        view.setTextSize(title ? 18 : 14);
        view.setPadding(0, dp(title ? 16 : 8), 0, dp(4));
        content.addView(view);
    }

    private TextInputEditText field(String hint, int inputType) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(hint);
        TextInputEditText edit = new TextInputEditText(layout.getContext());
        edit.setInputType(inputType);
        layout.addView(edit);
        content.addView(layout);
        return edit;
    }

    private MaterialButton choiceButton(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        content.addView(button);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
