package com.usiu.queuely;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONObject;

public class QueueStatusActivity extends AppCompatActivity {

    private QueuelyStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_queue_status);
        store = QueuelyStore.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.getMenu().add("Log out").setOnMenuItemClickListener(item -> {
            store.logout();
            store.openHome(this);
            finish();
            return true;
        });

        Button btnViewHistory = findViewById(R.id.btnViewHistory);
        btnViewHistory.setOnClickListener(v ->
                startActivity(new Intent(this, QueueHistoryActivity.class)));

        Button btnLeaveQueue = findViewById(R.id.btnLeaveQueue);
        btnLeaveQueue.setOnClickListener(v -> {
            QueuelyStore.Outcome result = store.leaveQueue();
            if (!result.ok) {
                Toast.makeText(this, result.error, Toast.LENGTH_LONG).show();
                return;
            }
            store.openHome(this);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        QueuelyStore.Visit visit = store.myVisit();
        if (visit == null) {
            store.openHome(this);
            finish();
            return;
        }
        JSONObject entry = visit.entry;
        TextView number = findViewById(R.id.textQueueNumber);
        TextView position = findViewById(R.id.textPosition);
        TextView wait = findViewById(R.id.textWaitTime);
        TextView ahead = findViewById(R.id.textStudentsAhead);
        TextView summary = findViewById(R.id.textVisitSummary);
        TextView note = findViewById(R.id.textQueueNote);
        number.setText(entry.optString("ticket"));
        position.setText(String.valueOf(visit.ahead + 1));
        wait.setText("~" + visit.waitMinutes + " min");
        if ("serving".equals(entry.optString("status"))) {
            ahead.setText("Datasheet open");
        } else if (visit.ahead == 0) {
            ahead.setText("You are at the front");
        } else {
            ahead.setText(visit.ahead + (visit.ahead == 1 ? " student" : " students"));
        }
        summary.setText(store.reasonLabel(entry.optString("reason")) + " · " + store.schoolShort(entry.optString("school")));
        org.json.JSONArray mail = store.myMail();
        if (mail.length() > 0) {
            JSONObject latest = mail.optJSONObject(0);
            String status = latest.optString("status");
            String label = "sent".equals(status) ? "Sent to " : "failed".equals(status) ? "Not sent to " : "Sending to ";
            String detail = latest.optString("detail");
            note.setText(label + latest.optString("to") + ". " + latest.optString("subject")
                    + (detail.isEmpty() ? "" : ". " + detail));
        } else {
            note.setText("Notices for this visit are emailed to your USIU address.");
        }
    }
}
