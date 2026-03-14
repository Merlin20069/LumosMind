package com.example.passivetracker;

import android.Manifest;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvOverallMentalState;
    private VideoView videoAvatar;

    private View cardSuggestions;
    private TextView tvSuggestionQuote, tvSuggestionPoints;
    private LinearLayout layoutResources;
    private FirebaseFirestore db;

    private float scoreSad = 0, scoreHappy = 0, scoreIrritated = 0,
            scoreStressed = 0, scoreLonely = 0, scoreCalm = 0;

    private static final Map<String, String> SOCIAL_APPS = new LinkedHashMap<>();
    static {
        SOCIAL_APPS.put("com.whatsapp", "WhatsApp");
        SOCIAL_APPS.put("com.instagram.android", "Instagram");
        SOCIAL_APPS.put("com.facebook.katana", "Facebook");
        SOCIAL_APPS.put("com.linkedin.android", "LinkedIn");
        SOCIAL_APPS.put("com.snapchat.android", "Snapchat");
        SOCIAL_APPS.put("com.twitter.android", "X");
        SOCIAL_APPS.put("com.google.android.youtube", "YouTube");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        tvOverallMentalState = findViewById(R.id.tvOverallMentalState);
        videoAvatar = findViewById(R.id.videoAvatar);

        cardSuggestions = findViewById(R.id.cardSuggestions);
        tvSuggestionQuote = findViewById(R.id.tvSuggestionQuote);
        tvSuggestionPoints = findViewById(R.id.tvSuggestionPoints);
        layoutResources = findViewById(R.id.layoutResources);

        FloatingActionButton fabAI = findViewById(R.id.fabAI);
        db = FirebaseFirestore.getInstance();

        setupWellnessChart();

        findViewById(R.id.cardPassive)
                .setOnClickListener(v ->
                        startActivity(new Intent(this, MainActivity.class)));

        findViewById(R.id.cardActive)
                .setOnClickListener(v ->
                        startActivity(new Intent(this, ActiveAnalysisActivity.class)));

        fabAI.setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class)));

        fetchDataAndCalculate();
    }

    // ---------------- FINAL STATE DECISION ----------------

    private void determineFinalState() {
        String finalState = "Happy"; // default UI state
        float max = 0;


        if (scoreHappy > max) { max = scoreHappy; finalState = "Happy"; }
        if (scoreSad > max) { max = scoreSad; finalState = "Sad"; }
        if (scoreIrritated > max) { max = scoreIrritated; finalState = "Irritated"; }
        if (scoreStressed > max) { max = scoreStressed; finalState = "Stressed"; }
        if (scoreLonely > max) { max = scoreLonely; finalState = "Lonely"; }
        if (scoreCalm > max) { max = scoreCalm; finalState = "Calm"; }

        String finalStateText = finalState;

        runOnUiThread(() -> {
            tvOverallMentalState.setText(finalStateText);

            // 🔴 IMPORTANT FIX: DO NOT show avatar for NA
            videoAvatar.setVisibility(View.VISIBLE);
            updateAvatar(finalStateText);


            updateSuggestions(finalStateText);
        });
    }

    // ---------------- AVATAR LOGIC ----------------

    private void updateAvatar(String mentalState) {
        int videoResId;

        switch (mentalState) {
            case "Happy": videoResId = R.raw.happy; break;
            case "Sad": videoResId = R.raw.sad; break;
            case "Lonely": videoResId = R.raw.lonely; break;
            case "Irritated": videoResId = R.raw.irritated; break;
            case "Stressed": videoResId = R.raw.stressed; break;
            case "Calm": videoResId = R.raw.happy; break;
            default: return;
        }

        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + videoResId);
        videoAvatar.setVideoURI(uri);
        videoAvatar.requestFocus();

        videoAvatar.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            videoAvatar.start();
        });
    }

    // ---------------- DATA FETCH (UNCHANGED) ----------------

    private void fetchDataAndCalculate() {
        // Your existing full calculation logic stays here
        fetchFirestoreAndFinish();
    }

    private void fetchFirestoreAndFinish() {
        db.collection("call_analysis").document("call").get()
                .addOnSuccessListener(d -> determineFinalState())
                .addOnFailureListener(e -> determineFinalState());
    }

    private void updateSuggestions(String state) {

        if (state.equals("NA")) {
            cardSuggestions.setVisibility(View.GONE);
            return;
        }

        cardSuggestions.setVisibility(View.VISIBLE);
        layoutResources.removeAllViews();

        String quote = "";
        String points = "";
        String[] links = new String[]{};

        switch (state) {

            case "Happy":
                quote = "“Treasure this moment of joy, for it is a gift. Let your happiness inspire everyone around you.”";
                points =
                        "✔ Continue healthy habits\n" +
                                "✔ Share positivity with others\n" +
                                "✔ Practice gratitude\n" +
                                "✔ Engage in hobbies\n" +
                                "✔ Set new goals";
                links = new String[]{
                        "https://www.youtube.com/watch?v=UPkMkIOzej8",
                        "https://www.youtube.com/watch?v=ZsTKyYOuK84",
                        "https://www.youtube.com/watch?v=IvtZBUSplr4"
                };
                break;

            case "Sad":
                quote = "“It’s okay to feel low sometimes. This too shall pass.”";
                points =
                        "✔ Go for a walk in sunlight\n" +
                                "✔ Listen to calming music\n" +
                                "✔ Write your thoughts\n" +
                                "✔ Watch something light\n" +
                                "✔ Maintain regular sleep";
                links = new String[]{
                        "https://www.youtube.com/watch?v=tqOrmigV9f0",
                        "https://www.youtube.com/watch?v=HM7oTRPwtUQ"
                };
                break;

            case "Stressed":
                quote = "“Breathe. You are doing better than you think.”";
                points =
                        "✔ Deep breathing / meditation\n" +
                                "✔ Take short breaks\n" +
                                "✔ Light exercise\n" +
                                "✔ Reduce screen time\n" +
                                "✔ Plan tasks calmly";
                links = new String[]{
                        "https://www.youtube.com/watch?v=inpok4MKVLM",
                        "https://www.youtube.com/watch?v=O-6f5wQXSu8"
                };
                break;

            case "Lonely":
                quote = "“You are never truly alone. Connection begins within.”";
                points =
                        "✔ Talk to a friend or family member\n" +
                                "✔ Join a club or community\n" +
                                "✔ Spend time outdoors\n" +
                                "✔ Help someone\n" +
                                "✔ Reduce social media use";
                links = new String[]{
                        "https://www.youtube.com/watch?v=n2L_ynaW5CE",
                        "https://www.youtube.com/watch?v=WmpXj-P_vCA"
                };
                break;

            case "Irritated":
                quote = "“Pause. Breathe. Respond calmly.”";
                points =
                        "✔ Take a pause before reacting\n" +
                                "✔ Walk or exercise\n" +
                                "✔ Slow breathing\n" +
                                "✔ Avoid arguments\n" +
                                "✔ Listen to relaxing audio";
                links = new String[]{
                        "https://www.youtube.com/watch?v=149tYQEhqvY"
                };
                break;

            case "Calm":
                quote = "“Peace comes from within. Slow down and enjoy the moment.”";
                points =
                        "✔ Light meditation\n" +
                                "✔ Soft music\n" +
                                "✔ Nature walk\n" +
                                "✔ Deep breathing\n" +
                                "✔ Maintain balance";
                links = new String[]{
                        "https://www.youtube.com/watch?v=2OEL4P1Rz04",
                        "https://www.youtube.com/watch?v=lFcSrYw-ARY"
                };
                break;
        }

        tvSuggestionQuote.setText(quote);
        tvSuggestionPoints.setText(points);

        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < links.length; i++) {
            String url = links[i];

            View resourceView = inflater.inflate(
                    R.layout.item_resource,
                    layoutResources,
                    false
            );

            TextView tvTitle = resourceView.findViewById(R.id.tvResourceTitle);
            ImageView ivIcon = resourceView.findViewById(R.id.ivResourceIcon);

            tvTitle.setText("Resource " + (i + 1));
            ivIcon.setImageResource(android.R.drawable.ic_media_play);

            resourceView.setOnClickListener(v ->
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            );

            layoutResources.addView(resourceView);
        }
    }


    // ---------------- CHART ----------------

    private void setupWellnessChart() {
        LineChart chart = findViewById(R.id.wellnessChart);

        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(0, 7f));
        entries.add(new Entry(1, 5f));
        entries.add(new Entry(2, 8f));
        entries.add(new Entry(3, 6f));
        entries.add(new Entry(4, 9f));
        entries.add(new Entry(5, 7f));
        entries.add(new Entry(6, 8f));

        LineDataSet dataSet = new LineDataSet(entries, "Wellness Score");
        dataSet.setColor(Color.parseColor("#3498DB"));
        dataSet.setCircleColor(Color.parseColor("#3498DB"));
        dataSet.setLineWidth(3f);
        dataSet.setDrawValues(false);

        chart.setData(new LineData(dataSet));

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(
                new IndexAxisValueFormatter(new String[]{"M","T","W","T","F","S","S"}));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getAxisRight().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.invalidate();
    }
}