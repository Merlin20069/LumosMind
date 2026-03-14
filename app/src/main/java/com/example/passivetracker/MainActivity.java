package com.example.passivetracker;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.buffer.BarBuffer;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.renderer.BarChartRenderer;
import com.github.mikephil.charting.utils.Utils;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final Map<String, String> SOCIAL_APPS = new LinkedHashMap<>();
    private static final int EVENT_KEYGUARD_DISMISSED = 7;
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int DAILY_GOAL_MINS = 300; 

    private SwipeRefreshLayout swipeRefresh;

    static {
        SOCIAL_APPS.put("com.whatsapp", "WhatsApp");
        SOCIAL_APPS.put("com.whatsapp.w4b", "WhatsApp Business");
        SOCIAL_APPS.put("com.instagram.android", "Instagram");
        SOCIAL_APPS.put("com.facebook.katana", "Facebook");
        SOCIAL_APPS.put("com.linkedin.android", "LinkedIn");
        SOCIAL_APPS.put("com.snapchat.android", "Snapchat");
        SOCIAL_APPS.put("com.indeed.android.jobsearch", "Indeed");
        SOCIAL_APPS.put("com.twitter.android", "X (Twitter)");
        SOCIAL_APPS.put("com.google.android.youtube", "YouTube");
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadData);
            swipeRefresh.setColorSchemeColors(Color.parseColor("#B794F6"));
        }

        Button btnActive = findViewById(R.id.btnActiveAnalysis);
        if (btnActive != null) {
            btnActive.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, ActiveAnalysisActivity.class));
            });
        }

        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Please grant Usage Stats permission", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CALL_LOG}, PERMISSION_REQUEST_CODE);
        } else {
            loadData();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            loadData();
        }
    }

    private void loadData() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);

        long now = System.currentTimeMillis();
        long dayMs = 24 * 60 * 60 * 1000L;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfToday = cal.getTimeInMillis();

        Map<Integer, Float> weekHours = new HashMap<>();
        String[] daysLabels = new String[7];
        SimpleDateFormat sdf = new SimpleDateFormat("EEE", Locale.getDefault());

        long totalUnlocksWeek = 0, todayUnlocks = 0;
        long totalNightUnlocksWeek = 0, todayNightUnlocks = 0;

        int incomingToday = 0, missedToday = 0;
        int incomingWeek = 0, missedWeek = 0;

        for (int i = 0; i < 7; i++) {
            long start = startOfToday - (6 - i) * dayMs;
            long end = (i == 6) ? now : start + dayMs;

            float totalMins = 0f;
            Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(start, end);
            for (UsageStats u : stats.values()) {
                totalMins += u.getTotalTimeInForeground() / 60000f;
            }
            weekHours.put(i, totalMins / 60f);

            int dailyUnlocks = getUnlockCount(usm, start, end);
            totalUnlocksWeek += dailyUnlocks;

            int nightUnlocks = getNightUnlockCount(usm, start, end);
            totalNightUnlocksWeek += nightUnlocks;

            CallStats dailyCalls = getCallStats(start, end);
            incomingWeek += dailyCalls.incoming;
            missedWeek += dailyCalls.missed;

            if (i == 6) {
                todayUnlocks = dailyUnlocks;
                todayNightUnlocks = nightUnlocks;
                incomingToday = dailyCalls.incoming;
                missedToday = dailyCalls.missed;
            }

            Calendar labelCal = Calendar.getInstance();
            labelCal.setTimeInMillis(start);
            daysLabels[i] = sdf.format(labelCal.getTime());
        }

        float totalWeekMins = 0;
        for (Float h : weekHours.values()) {
            if (h != null) totalWeekMins += (h * 60);
        }
        float todayMins = (weekHours.get(6) != null ? weekHours.get(6) : 0f) * 60;
        
        ((TextView) findViewById(R.id.tvAvg)).setText(String.format(Locale.getDefault(), "Avg: %d mins/day", (int) (totalWeekMins / 7)));
        ((TextView) findViewById(R.id.tvToday)).setText(String.format(Locale.getDefault(), "Today: %d mins", (int) todayMins));
        
        ProgressBar goalProgress = findViewById(R.id.goalProgress);
        TextView tvProgressPercent = findViewById(R.id.tvProgressPercent);
        if (goalProgress != null && tvProgressPercent != null) {
            int progress = (int) ((todayMins / DAILY_GOAL_MINS) * 100);
            goalProgress.setProgress(Math.min(progress, 100));
            tvProgressPercent.setText(String.format(Locale.getDefault(), "%d%%", Math.min(progress, 100)));
        }

        SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        TextView tvLastUpdated = findViewById(R.id.tvLastUpdated);
        if (tvLastUpdated != null) {
            tvLastUpdated.setText(String.format(Locale.getDefault(), "Last updated: %s", timeSdf.format(new Date())));
        }

        setupWeeklyBar(findViewById(R.id.weeklyChart), weekHours, daysLabels);
        displaySleepStats(usm, now, dayMs);
        showSocialMedia(usm, startOfToday, now, R.id.todaySocial, false);

        ((TextView) findViewById(R.id.tvTotalUnlocks)).setText(String.format(Locale.getDefault(), "Total Unlocks (Today): %d", todayUnlocks));
        ((TextView) findViewById(R.id.tvAvgUnlocks)).setText(String.format(Locale.getDefault(), "Avg Unlocks per Day: %d", (totalUnlocksWeek / 7)));
        
        TextView tvNightUnlocks = findViewById(R.id.tvNightUnlocks);
        if (tvNightUnlocks != null) {
            tvNightUnlocks.setText(String.format(Locale.getDefault(), "Unlocks at Night: %d", todayNightUnlocks));
        }
        
        TextView tvAvgNightUnlocks = findViewById(R.id.tvAvgNightUnlocks);
        if (tvAvgNightUnlocks != null) {
            tvAvgNightUnlocks.setText(String.format(Locale.getDefault(), "Avg Night Unlocks: %d", (totalNightUnlocksWeek / 7)));
        }

        ((TextView) findViewById(R.id.tvIncomingToday)).setText(String.format(Locale.getDefault(), "Total Incoming: %d", incomingToday));
        ((TextView) findViewById(R.id.tvMissedToday)).setText(String.format(Locale.getDefault(), "Missed Calls: %d", missedToday));

        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
    }

    private void displaySleepStats(UsageStatsManager usm, long now, long dayMs) {
        Map<Integer, Float> nightUsage = new HashMap<>();
        float nightTotalTime = 0f;
        Map<String, UsageStats> nightStats = usm.queryAndAggregateUsageStats(now - dayMs, now);
        for (UsageStats u : nightStats.values()) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(u.getLastTimeUsed());
            int h = c.get(Calendar.HOUR_OF_DAY);
            if (h >= 21 || h <= 6) {
                float m = u.getTotalTimeInForeground() / 60000f;
                Float currentVal = nightUsage.get(h);
                nightUsage.put(h, (currentVal != null ? currentVal : 0f) + m);
                nightTotalTime += m;
            }
        }
        setupSleepChart(findViewById(R.id.sleepChart), nightUsage);
        TextView tvSleepQuality = findViewById(R.id.tvSleepQuality);
        if (tvSleepQuality != null) {
            tvSleepQuality.setText(sleepState(nightTotalTime));
        }
    }

    private void setupSleepChart(LineChart chart, Map<Integer, Float> data) {
        if (chart == null) return;
        List<Entry> entries = new ArrayList<>();
        int[] hours = {21, 22, 23, 0, 1, 2, 3, 4, 5, 6};
        for (int i = 0; i < hours.length; i++) {
            Float val = data.get(hours[i]);
            entries.add(new Entry(i, val != null ? val : 0f));
        }

        LineDataSet ds = new LineDataSet(entries, "Usage (mins)");
        ds.setColor(Color.parseColor("#B794F6"));
        ds.setCircleColor(Color.parseColor("#B794F6"));
        ds.setLineWidth(2.5f);
        ds.setDrawFilled(true);
        ds.setFillColor(Color.parseColor("#B794F6"));
        ds.setFillAlpha(40);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setDrawValues(false);

        chart.setData(new LineData(ds));
        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"9P","10P","11P","12A","1A","2A","3A","4A","5A","6A"}));
        xAxis.setTextColor(Color.parseColor("#7F8C8D"));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        
        chart.getAxisLeft().setTextColor(Color.parseColor("#7F8C8D"));
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(Color.parseColor("#E0E0E0"));
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.animateX(1000);
        chart.invalidate();
    }

    private String sleepState(float mins) {
        if (mins < 15) return "EXCELLENT";
        if (mins < 45) return "GOOD";
        return "POOR";
    }

    private CallStats getCallStats(long start, long end) {
        CallStats stats = new CallStats();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return stats;
        String selection = CallLog.Calls.DATE + " >= ? AND " + CallLog.Calls.DATE + " <= ?";
        String[] selectionArgs = {String.valueOf(start), String.valueOf(end)};
        try (Cursor cursor = getContentResolver().query(CallLog.Calls.CONTENT_URI, null, selection, selectionArgs, null)) {
            if (cursor != null) {
                int typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE);
                while (cursor.moveToNext()) {
                    int type = cursor.getInt(typeIdx);
                    if (type == CallLog.Calls.INCOMING_TYPE) stats.incoming++;
                    else if (type == CallLog.Calls.MISSED_TYPE) stats.missed++;
                }
            }
        } catch (Exception ignored) {}
        return stats;
    }

    private static class CallStats { int incoming = 0; int missed = 0; }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private int getUnlockCount(UsageStatsManager usm, long start, long end) {
        int count = 0;
        UsageEvents events = usm.queryEvents(start, end);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == EVENT_KEYGUARD_DISMISSED) count++;
        }
        return count;
    }

    private int getNightUnlockCount(UsageStatsManager usm, long start, long end) {
        int count = 0;
        UsageEvents events = usm.queryEvents(start, end);
        UsageEvents.Event event = new UsageEvents.Event();
        Calendar cal = Calendar.getInstance();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == EVENT_KEYGUARD_DISMISSED) {
                cal.setTimeInMillis(event.getTimeStamp());
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                if (hour >= 21 || hour <= 6) count++;
            }
        }
        return count;
    }

    private void showSocialMedia(UsageStatsManager usm, long start, long end, int layoutId, boolean isAvg) {
        LinearLayout layout = findViewById(layoutId);
        if (layout == null) return;
        layout.removeAllViews();
        Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(start, end);
        PackageManager pm = getPackageManager();
        boolean found = false;
        for (Map.Entry<String, String> entry : SOCIAL_APPS.entrySet()) {
            if (stats.containsKey(entry.getKey())) {
                long mins = stats.get(entry.getKey()).getTotalTimeInForeground() / 60000;
                if (isAvg) mins /= 7;
                if (mins <= 0) continue;
                try {
                    View row = LayoutInflater.from(this).inflate(R.layout.item_app_usage, layout, false);
                    ((ImageView) row.findViewById(R.id.icon)).setImageDrawable(pm.getApplicationIcon(entry.getKey()));
                    ((TextView) row.findViewById(R.id.text)).setText(String.format(Locale.getDefault(), "%s : %d mins", entry.getValue(), (int)mins));
                    layout.addView(row);
                    found = true;
                } catch (Exception ignored) {}
            }
        }
        if (!found) {
            TextView tv = new TextView(this);
            tv.setText("No usage detected.");
            tv.setTextColor(Color.parseColor("#7F8C8D"));
            layout.addView(tv);
        }
    }

    private void setupWeeklyBar(BarChart chart, Map<Integer, Float> data, String[] labels) {
        if (chart == null) return;
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Float val = data.get(i);
            entries.add(new BarEntry(i, val != null ? val : 0f));
        }
        BarDataSet ds = new BarDataSet(entries, "Hours");
        ds.setColors(new int[]{Color.parseColor("#7B5FB8"), Color.parseColor("#B794F6")});
        ds.setDrawValues(false);

        chart.setData(new BarData(ds));
        chart.setRenderer(new RoundedBarChartRenderer(chart, chart.getAnimator(), chart.getViewPortHandler()));
        
        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#7F8C8D"));
        xAxis.setDrawGridLines(false);
        
        chart.getAxisLeft().setTextColor(Color.parseColor("#7F8C8D"));
        chart.getAxisLeft().setGridColor(Color.parseColor("#E0E0E0"));
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.animateY(1000);
        chart.invalidate();
    }

    public static class RoundedBarChartRenderer extends BarChartRenderer {
        private final RectF mBarRect = new RectF();
        public RoundedBarChartRenderer(BarChart chart, ChartAnimator animator, ViewPortHandler viewPortHandler) {
            super(chart, animator, viewPortHandler);
        }
        @Override
        protected void drawDataSet(Canvas c, IBarDataSet dataSet, int index) {
            BarBuffer buffer = mBarBuffers[index];
            buffer.setPhases(mAnimator.getPhaseX(), mAnimator.getPhaseY());
            buffer.feed(dataSet);
            mRenderPaint.setColor(dataSet.getColor());
            float radius = Utils.convertDpToPixel(12f);
            for (int j = 0; j < buffer.size(); j += 4) {
                if (!mViewPortHandler.isInBoundsLeft(buffer.buffer[j + 2])) continue;
                if (!mViewPortHandler.isInBoundsRight(buffer.buffer[j])) break;
                mBarRect.set(buffer.buffer[j], buffer.buffer[j + 1], buffer.buffer[j + 2], buffer.buffer[j + 3]);
                c.drawRoundRect(mBarRect, radius, radius, mRenderPaint);
            }
        }
    }
}
