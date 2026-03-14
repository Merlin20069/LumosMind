package com.example.passivetracker;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ChatActivity extends AppCompatActivity {

    private LinearLayout chatContainer;
    private EditText etMessage;
    private ScrollView scrollView;
    private ProgressBar loading;
    private final OkHttpClient client = new OkHttpClient();

    // OpenRouter API configuration
    private static final String OPENROUTER_API_KEY = "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";
    private static final String MODEL_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL_ID = "openai/gpt-3.5-turbo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatContainer = findViewById(R.id.chatContainer);
        etMessage = findViewById(R.id.etMessage);
        scrollView = findViewById(R.id.chatScrollView);
        loading = findViewById(R.id.chatLoading);
        ImageButton btnSend = findViewById(R.id.btnSend);

        // Initial welcome message from AI (Left side)
        addChatMessage("Hello! I'm your wellness assistant. How are you feeling today?", false);

        btnSend.setOnClickListener(v -> {
            String msg = etMessage.getText().toString().trim();
            if (!msg.isEmpty()) {
                sendMessage(msg);
            }
        });
    }

    private void sendMessage(String message) {
        // Add user message to UI (Right side)
        addChatMessage(message, true);
        etMessage.setText("");
        
        loading.setVisibility(View.VISIBLE);

        String systemPrompt = "You are a mental wellness support assistant. Rules:\n" +
                "1. Be empathetic and supportive.\n" +
                "2. Do NOT diagnose or give medical advice.\n" +
                "3. Classify user state into EXACTLY one: lonely, irritated, happy, sad, stressed.\n" +
                "4. Respond ONLY with a valid JSON object.\n" +
                "\n" +
                "Output format:\n" +
                "{\"llm_state\": \"state\", \"response\": \"empathetic message\"}";

        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("model", MODEL_ID);

        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", message);
        messages.add(userMsg);

        jsonBody.add("messages", messages);
        jsonBody.addProperty("temperature", 0.7);
        jsonBody.addProperty("stream", false);

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(MODEL_URL)
                .addHeader("Authorization", "Bearer " + OPENROUTER_API_KEY)
                .addHeader("HTTP-Referer", "https://passivetracker.example.com")
                .addHeader("X-Title", "PassiveTracker")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    Toast.makeText(ChatActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody responseBody = response.body();
                String result = responseBody != null ? responseBody.string() : "";

                if (response.isSuccessful()) {
                    runOnUiThread(() -> parseAIResponse(result));
                } else {
                    runOnUiThread(() -> {
                        loading.setVisibility(View.GONE);
                        Log.e("ChatAPI", "Error " + response.code() + ": " + result);
                        Toast.makeText(ChatActivity.this, "API Error: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void addChatMessage(String text, boolean isUser) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View chatItem;
        
        if (isUser) {
            chatItem = inflater.inflate(R.layout.item_chat_user, chatContainer, false);
        } else {
            chatItem = inflater.inflate(R.layout.item_chat_ai, chatContainer, false);
        }

        TextView tvMessage = chatItem.findViewById(R.id.tvMessage);
        tvMessage.setText(text);
        
        chatContainer.addView(chatItem);
        
        // Scroll to bottom
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void parseAIResponse(String rawJson) {
        try {
            JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
            String content = root.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .get("message").getAsJsonObject()
                    .get("content").getAsString();

            int jsonStart = content.indexOf("{");
            int jsonEnd = content.lastIndexOf("}");

            if (jsonStart != -1 && jsonEnd != -1) {
                String jsonStr = content.substring(jsonStart, jsonEnd + 1);
                JsonObject data = JsonParser.parseString(jsonStr).getAsJsonObject();

                String state = data.has("llm_state") ? data.get("llm_state").getAsString() : "NA";
                String response = data.has("response") ? data.get("response").getAsString() : "I hear you.";

                addChatMessage(response, false);

                SharedPreferences prefs = getSharedPreferences("WellnessPrefs", MODE_PRIVATE);
                prefs.edit().putString("llm_state", state.toLowerCase().trim()).apply();
            } else {
                addChatMessage(content.trim(), false);
            }
        } catch (Exception e) {
            Log.e("ChatActivity", "Parse error", e);
            addChatMessage("I understand. Tell me more about that.", false);
        } finally {
            loading.setVisibility(View.GONE);
        }
    }
}
