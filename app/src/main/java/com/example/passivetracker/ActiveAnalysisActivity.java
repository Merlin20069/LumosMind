package com.example.passivetracker;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.firestore.FirebaseFirestore;

public class ActiveAnalysisActivity extends AppCompatActivity {

    private TextView tvState, tvTranscripts;
    private FirebaseFirestore db;
    private SwipeRefreshLayout swipeRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_analysis);

        tvState = findViewById(R.id.tvStateValue);
        tvTranscripts = findViewById(R.id.tvTranscriptValue);
        swipeRefresh = findViewById(R.id.swipeRefreshActive);

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::fetchCallAnalysis);
            swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.purple_primary));
        }

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        fetchCallAnalysis();
    }

    private void fetchCallAnalysis() {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }

        // Fetch from: call_analysis (collection) -> call (document)
        db.collection("call_analysis").document("call")
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String emotionalState = documentSnapshot.getString("overall emotional state");
                    String transcript = documentSnapshot.getString("transcript");

                    tvState.setText(emotionalState != null ? emotionalState : "NA");
                    tvTranscripts.setText(transcript != null ? transcript : "NA");
                } else {
                    tvState.setText("NA");
                    tvTranscripts.setText("NA");
                    Toast.makeText(this, "No data found in Firestore", Toast.LENGTH_SHORT).show();
                }
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
            })
            .addOnFailureListener(e -> {
                tvState.setText("NA");
                tvTranscripts.setText("NA");
                Toast.makeText(this, "Error fetching data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
            });
    }
}
