package com.usiu.queuely;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

public class QueueHistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_queue_history);
        QueuelyStore store = QueuelyStore.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        LinearLayout list = findViewById(R.id.historyList);
        JSONArray history = store.myHistory();
        if (history.length() == 0) {
            list.addView(note("No closed visits yet."));
        }
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            JSONArray changes = item.optJSONArray("changes");
            String changeText = "No course change";
            if (changes != null && changes.length() > 0) {
                StringBuilder builder = new StringBuilder();
                for (int c = 0; c < changes.length(); c++) {
                    JSONObject change = changes.optJSONObject(c);
                    if (builder.length() > 0) {
                        builder.append(", ");
                    }
                    builder.append("add".equals(change.optString("action")) ? "Add " : "Drop ");
                    builder.append(change.optString("code"));
                }
                changeText = builder.toString();
            }
            list.addView(note(item.optString("ticket") + " · " + store.reasonLabel(item.optString("reason"))
                    + "\n" + store.schoolName(item.optString("school"))
                    + "\n" + changeText));
        }

        Button btnBackToQueue = findViewById(R.id.btnBackToQueue);
        btnBackToQueue.setOnClickListener(v -> finish());
    }

    private TextView note(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(getColor(R.color.usiu_navy));
        view.setTextSize(15);
        view.setPadding(0, 0, 0, Math.round(16 * getResources().getDisplayMetrics().density));
        return view;
    }
}
