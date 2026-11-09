package com.usiu.queuely;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class ChangePasswordActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);
        QueuelyStore store = QueuelyStore.get(this);
        JSONObject user = store.currentUser();
        TextView hint = findViewById(R.id.textPasswordHint);
        if (user != null) {
            hint.setText("This is the first password sent to " + user.optString("email") + ". Set a new one before you continue.");
        }

        EditText current = findViewById(R.id.editCurrent);
        EditText next = findViewById(R.id.editNext);
        Button save = findViewById(R.id.btnSave);
        save.setOnClickListener(v -> {
            QueuelyStore.Outcome result = store.changePassword(
                    current.getText() == null ? "" : current.getText().toString(),
                    next.getText() == null ? "" : next.getText().toString());
            if (!result.ok) {
                Toast.makeText(this, result.error, Toast.LENGTH_LONG).show();
                return;
            }
            store.openHome(this);
            finish();
        });

        Button logout = findViewById(R.id.btnLogout);
        logout.setOnClickListener(v -> {
            store.logout();
            store.openHome(this);
            finish();
        });
    }
}
