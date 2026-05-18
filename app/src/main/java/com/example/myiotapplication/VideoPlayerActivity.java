package com.example.myiotapplication;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class VideoPlayerActivity extends AppCompatActivity {

    private static final String TAG = "VideoPlayer_Routine";

    private StyledPlayerView playerView;
    private ExoPlayer player;
    private ProgressBar loadingProgressBar;
    private TextView statusTextView;
    private Button btnAlertLive;

    private BlobStorageClient blobClient;
    private String currentVideoName;
    private Handler monitorHandler = new Handler(Looper.getMainLooper());
    private Runnable monitorRunnable;

    private DatabaseReference alertRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_video_player);

        playerView = findViewById(R.id.playerView);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        statusTextView = findViewById(R.id.statusTextView);
        btnAlertLive = findViewById(R.id.btnAlertLive);

        // Sự kiện khi bấm nút Cảnh báo -> Chuyển sang UI Live Stream
        btnAlertLive.setOnClickListener(v -> {
            Intent intent = new Intent(VideoPlayerActivity.this, LiveStreamActivity.class);
            startActivity(intent);
        });

        initializeRoutinePlayer();
        setupFirebaseListener();
    }

    private void initializeRoutinePlayer() {
        try {
            blobClient = new BlobStorageClient(AppConfig.AZURE_CONNECTION_STRING, AppConfig.CONTAINER_NAME);
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
            player.setRepeatMode(Player.REPEAT_MODE_ALL);

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        loadingProgressBar.setVisibility(View.GONE);
                        statusTextView.setText("Routine: " + currentVideoName);
                        statusTextView.postDelayed(() -> statusTextView.setVisibility(View.GONE), 2000);
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        loadingProgressBar.setVisibility(View.VISIBLE);
                        statusTextView.setVisibility(View.VISIBLE);
                        statusTextView.setText("Đang tải Routine...");
                    }
                }
            });

            loadAndPlayLatestRoutine();
            startRoutineMonitoring();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi Azure: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startRoutineMonitoring() {
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                new Thread(() -> {
                    try {
                        if (blobClient.isNewVideoAvailable(currentVideoName)) {
                            runOnUiThread(() -> loadAndPlayLatestRoutine());
                        }
                    } catch (Exception e) { }
                }).start();
                monitorHandler.postDelayed(this, 5000);
            }
        };
        monitorHandler.postDelayed(monitorRunnable, 5000);
    }

    private void loadAndPlayLatestRoutine() {
        new Thread(() -> {
            try {
                BlobStorageClient.VideoBlob latestVideo = blobClient.getLatestVideo();
                if (latestVideo == null) return;
                currentVideoName = latestVideo.getName();
                String videoUrl = blobClient.getLatestVideoUrl();
                if (videoUrl != null) {
                    runOnUiThread(() -> {
                        loadingProgressBar.setVisibility(View.VISIBLE);
                        player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)));
                        player.prepare();
                        player.play();
                    });
                }
            } catch (Exception e) { }
        }).start();
    }

    private void setupFirebaseListener() {
        // Gắn TRỰC TIẾP link Database vào đây để App không bị lạc
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://my-iot-project-658d2-default-rtdb.asia-southeast1.firebasedatabase.app/");
        alertRef = database.getReference("iot_status/is_sleeping");

        alertRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean isSleeping = snapshot.getValue(Boolean.class);
                Log.d(TAG, "Tín hiệu Firebase báo về: is_sleeping = " + isSleeping); // In ra logcat để kiểm tra

                if (isSleeping != null && isSleeping) {
                    // Hiện nút Đỏ báo động
                    btnAlertLive.setVisibility(View.VISIBLE);
                } else {
                    // Ẩn nút đi
                    btnAlertLive.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Lỗi đọc Firebase: " + error.getMessage());
            }
        });
    }

    @Override
    protected void onPause() { super.onPause(); if (player != null) player.pause(); }

    @Override
    protected void onResume() { super.onResume(); if (player != null) player.play(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (monitorHandler != null) monitorHandler.removeCallbacks(monitorRunnable);
        if (player != null) player.release();
    }
}