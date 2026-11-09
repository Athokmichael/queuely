package com.usiu.queuely;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

public class RequestTypeActivity extends AppCompatActivity {

    static final String EXTRA_REQUEST = "extra_request";

    private static final int[] CARD_IDS = {
            R.id.cardAddCourse, R.id.cardDropCourse, R.id.cardConsultation
    };
    private static final int[] RADIO_IDS = {
            R.id.radioAddCourse, R.id.radioDropCourse, R.id.radioConsultation
    };

    private final MaterialCardView[] cards = new MaterialCardView[3];
    private final RadioButton[] radios = new RadioButton[3];
    private String selectedSchool = "";
    private int selectedRequest = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request_type);

        QueuelyStore store = QueuelyStore.get(this);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        selectedSchool = store.pendingSchool();
        if (savedInstanceState != null) {
            selectedSchool = savedInstanceState.getString(SchoolSelectionActivity.EXTRA_SCHOOL, selectedSchool);
            selectedRequest = savedInstanceState.getInt(EXTRA_REQUEST, -1);
        }

        TextView textSchoolName = findViewById(R.id.textSchoolName);
        textSchoolName.setText(store.schoolName(selectedSchool));

        for (int i = 0; i < CARD_IDS.length; i++) {
            cards[i] = findViewById(CARD_IDS[i]);
            radios[i] = findViewById(RADIO_IDS[i]);
            int index = i;
            cards[i].setOnClickListener(v -> selectRequest(index));
            radios[i].setOnClickListener(v -> selectRequest(index));
        }

        if (selectedRequest >= 0) {
            selectRequest(selectedRequest);
        }

        Button btnJoinQueue = findViewById(R.id.btnJoinQueue);
        btnJoinQueue.setOnClickListener(v -> {
            if (selectedRequest < 0) {
                Toast.makeText(this, R.string.please_select_request, Toast.LENGTH_SHORT).show();
                return;
            }
            QueuelyStore.Outcome result = QueuelyStore.get(this).joinQueue(
                    selectedSchool, QueuelyStore.REASON_IDS[selectedRequest]);
            if (!result.ok) {
                Toast.makeText(this, result.error, Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(this, QueueStatusActivity.class));
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SchoolSelectionActivity.EXTRA_SCHOOL, selectedSchool);
        outState.putInt(EXTRA_REQUEST, selectedRequest);
    }

    private void selectRequest(int index) {
        selectedRequest = index;
        int gold = getColor(R.color.usiu_gold);
        int navy = getColor(R.color.usiu_navy);
        int stroke = Math.round(2 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < cards.length; i++) {
            boolean selected = i == index;
            cards[i].setStrokeWidth(selected ? stroke : 0);
            cards[i].setStrokeColor(selected ? gold : navy);
            radios[i].setChecked(selected);
            radios[i].setButtonTintList(ColorStateList.valueOf(selected ? gold : navy));
        }
    }
}
