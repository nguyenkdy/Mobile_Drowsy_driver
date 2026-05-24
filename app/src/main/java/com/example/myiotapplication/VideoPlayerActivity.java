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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

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

import java.util.ArrayList;
import java.util.List;

public class VideoPlayerActivity extends AppCompatActivity {

    private static final String TAG = "VideoPlayer_Routine";

    private StyledPlayerView playerView;
    private ExoPlayer player;
    private ProgressBar loadingProgressBar;
    private TextView statusTextView;
    private Button btnAlertLive;
    private Button btnSelectVideo;

    private BlobStorageClient blobClient;
    private String currentVideoName;
    private Handler monitorHandler = new Handler(Looper.getMainLooper());
    private Runnable monitorRunnable;
    private boolean isAutoPlayMode = true;

    private DatabaseReference alertRef;

    private boolean wasSleeping = false;
    private boolean wasYawning = false;
    private static final String CHANNEL_ID = "drowsy_alerts";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_video_player);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
        createNotificationChannel();

        playerView = findViewById(R.id.playerView);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        statusTextView = findViewById(R.id.statusTextView);
        btnAlertLive = findViewById(R.id.btnAlertLive);
        btnSelectVideo = findViewById(R.id.btnSelectVideo);

        btnSelectVideo.setOnClickListener(v -> showVideoSelectorDialog());

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
                if (isAutoPlayMode) {
                    new Thread(() -> {
                        try {
                            if (blobClient.isNewVideoAvailable(currentVideoName)) {
                                runOnUiThread(() -> loadAndPlayLatestRoutine());
                            }
                        } catch (Exception e) { }
                    }).start();
                }
                monitorHandler.postDelayed(this, 5000);
            }
        };
        monitorHandler.postDelayed(monitorRunnable, 5000);
    }

    private void loadAndPlayLatestRoutine() {
        new Thread(() -> {
            try {
                BlobStorageClient.VideoBlob latestVideo = blobClient.getLatestVideo();
                if (latestVideo == null) {
                    runOnUiThread(() -> {
                        loadingProgressBar.setVisibility(View.GONE);
                        statusTextView.setText("Không tìm thấy video nào trong container!");
                    });
                    return;
                }
                currentVideoName = latestVideo.getName();
                String videoUrl = blobClient.getLatestVideoUrl();
                playVideoUrl(videoUrl, "Auto: " + currentVideoName);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingProgressBar.setVisibility(View.GONE);
                    statusTextView.setText("Lỗi lấy video: " + e.getMessage());
                    Toast.makeText(VideoPlayerActivity.this, "Lỗi lấy video: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void playVideoUrl(String videoUrl, String statusText) {
        if (videoUrl != null) {
            runOnUiThread(() -> {
                loadingProgressBar.setVisibility(View.VISIBLE);
                statusTextView.setVisibility(View.VISIBLE);
                statusTextView.setText("Đang tải: " + statusText);
                player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)));
                player.prepare();
                player.play();
            });
        }
    }

    private void showVideoSelectorDialog() {
        new Thread(() -> {
            try {
                List<BlobStorageClient.VideoBlob> videos = blobClient.getAllVideos();
                List<String> options = new ArrayList<>();
                options.add("[ 🔴 XEM LIVE STREAM ]");
                options.add("[ 🔄 BẬT TỰ ĐỘNG CHUYỂN VIDEO MỚI ]");
                
                for (BlobStorageClient.VideoBlob v : videos) {
                    options.add(v.getName());
                }

                runOnUiThread(() -> {
                    String[] items = options.toArray(new String[0]);
                    new android.app.AlertDialog.Builder(VideoPlayerActivity.this)
                        .setTitle("Chọn Video")
                        .setItems(items, (dialog, which) -> {
                            if (which == 0) {
                                Intent intent = new Intent(VideoPlayerActivity.this, LiveStreamActivity.class);
                                startActivity(intent);
                            } else if (which == 1) {
                                isAutoPlayMode = true;
                                loadAndPlayLatestRoutine();
                                Toast.makeText(this, "Đã bật: Tự động tải video mới nhất", Toast.LENGTH_SHORT).show();
                            } else {
                                isAutoPlayMode = false;
                                String selectedName = items[which];
                                currentVideoName = selectedName;
                                new Thread(() -> {
                                    String url = blobClient.getVideoUrl(selectedName);
                                    playVideoUrl(url, selectedName);
                                }).start();
                                Toast.makeText(this, "Đã tắt tự động: Đang phát " + selectedName, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Lỗi tải danh sách: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void setupFirebaseListener() {
        FirebaseDatabase database = FirebaseDatabase.getInstance(AppConfig.FIREBASE_DATABASE_URL);
        alertRef = database.getReference("iot_status");
        alertRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean isSleeping = snapshot.child("is_sleeping").getValue(Boolean.class);
                Boolean isYawning = snapshot.child("is_yawning").getValue(Boolean.class);

                boolean currentSleeping = isSleeping != null && isSleeping;
                boolean currentYawning = isYawning != null && isYawning;

                if (currentSleeping) {
                    btnAlertLive.setVisibility(View.VISIBLE);
                    if (!wasSleeping) {
                        showNotification("CẢNH BÁO NGUY HIỂM", "Phát hiện tài xế đang NGỦ GẬT!", 1);
                    }
                } else {
                    btnAlertLive.setVisibility(View.GONE);
                }

                if (currentYawning && !wasYawning) {
                    showNotification("CẢNH BÁO", "Tài xế đang ngáp - Có dấu hiệu buồn ngủ!", 2);
                }

                wasSleeping = currentSleeping;
                wasYawning = currentYawning;
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Lỗi đọc Firebase: " + error.getMessage());
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Cảnh báo tài xế";
            String description = "Thông báo khi phát hiện buồn ngủ hoặc ngáp";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void showNotification(String title, String message, int notifId) {
        Intent intent = new Intent(this, LiveStreamActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notificationManager.notify(notifId, builder.build());
        }
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
