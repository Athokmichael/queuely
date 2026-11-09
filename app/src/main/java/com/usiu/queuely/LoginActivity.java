package com.usiu.queuely;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        QueuelyStore store = QueuelyStore.get(this);
        if (store.currentUser() != null) {
            store.openHome(this);
            finish();
            return;
        }
        setContentView(R.layout.activity_login);

        EditText email = findViewById(R.id.editEmail);
        EditText password = findViewById(R.id.editPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> {
            QueuelyStore.Outcome result = store.login(
                    textOf(email),
                    textOf(password));
            if (!result.ok) {
                Toast.makeText(this, result.error, Toast.LENGTH_LONG).show();
                return;
            }
            store.openHome(this);
            finish();
        });
    }

    private String textOf(EditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }
}
