package com.usiu.queuely;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

public class SchoolSelectionActivity extends AppCompatActivity {

    static final String EXTRA_SCHOOL = "extra_school";

    private static final int[] CARD_IDS = {
            R.id.cardChs, R.id.cardSst, R.id.cardBusiness, R.id.cardPam, R.id.cardScca
    };
    private static final int[] RADIO_IDS = {
            R.id.radioChs, R.id.radioSst, R.id.radioBusiness, R.id.radioPam, R.id.radioScca
    };
    private static final int[] LABEL_IDS = {
            R.id.labelSelectedChs, R.id.labelSelectedSst,
            R.id.labelSelectedBusiness, R.id.labelSelectedPam, R.id.labelSelectedScca
    };
    private static final String[] SCHOOL_IDS = {"chss", "sst", "business", "sphs", "scca"};

    private final MaterialCardView[] cards = new MaterialCardView[5];
    private final RadioButton[] radios = new RadioButton[5];
    private final TextView[] labels = new TextView[5];
    private int selectedSchool = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        QueuelyStore store = QueuelyStore.get(this);
        if (store.currentUser() == null) {
            store.openHome(this);
            finish();
            return;
        }
        setContentView(R.layout.activity_school_selection);
        TextView studentLine = findViewById(R.id.textStudentLine);
        studentLine.setText("School ID " + store.currentUser().optString("schoolId")
                + " · " + store.currentUser().optString("email"));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.getMenu().add("Log out").setOnMenuItemClickListener(item -> {
            store.logout();
            store.openHome(this);
            finish();
            return true;
        });

        for (int i = 0; i < CARD_IDS.length; i++) {
            cards[i] = findViewById(CARD_IDS[i]);
            radios[i] = findViewById(RADIO_IDS[i]);
            labels[i] = findViewById(LABEL_IDS[i]);
            int index = i;
            cards[i].setOnClickListener(v -> selectSchool(index));
            radios[i].setOnClickListener(v -> selectSchool(index));
        }

        if (savedInstanceState != null) {
            int restored = savedInstanceState.getInt(EXTRA_SCHOOL, -1);
            if (restored >= 0) {
                selectSchool(restored);
            }
        }

        Button btnContinue = findViewById(R.id.btnContinue);
        btnContinue.setOnClickListener(v -> {
            if (selectedSchool < 0) {
                Toast.makeText(this, R.string.please_select_school, Toast.LENGTH_SHORT).show();
                return;
            }
            QueuelyStore.get(this).setPendingSchool(SCHOOL_IDS[selectedSchool]);
            startActivity(new Intent(this, RequestTypeActivity.class));
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_SCHOOL, selectedSchool);
    }

    private void selectSchool(int index) {
        selectedSchool = index;
        int gold = getColor(R.color.usiu_gold);
        int navy = getColor(R.color.usiu_navy);
        int stroke = Math.round(2 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < cards.length; i++) {
            boolean selected = i == index;
            cards[i].setStrokeWidth(selected ? stroke : 0);
            cards[i].setStrokeColor(selected ? gold : navy);
            radios[i].setChecked(selected);
            radios[i].setButtonTintList(ColorStateList.valueOf(selected ? gold : navy));
            labels[i].setVisibility(selected ? View.VISIBLE : View.GONE);
        }
    }
}
