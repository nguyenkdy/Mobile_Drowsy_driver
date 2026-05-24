package com.example.myiotapplication;


import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;


import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


public class LiveStreamActivity extends AppCompatActivity {


    private StyledPlayerView livePlayerView;
    private ExoPlayer player;
    private ProgressBar liveProgressBar;
    private Button btnBackToRoutine;
    private TextView tvYawnAlertLive;
    private boolean isMovingBack = false;
    private long sleepStartTime = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_live_stream);


        livePlayerView = findViewById(R.id.livePlayerView);
        liveProgressBar = findViewById(R.id.liveProgressBar);
        btnBackToRoutine = findViewById(R.id.btnBackToRoutine);
        tvYawnAlertLive = findViewById(R.id.tvYawnAlertLive);


        btnBackToRoutine.setOnClickListener(v -> finish()); // Đóng màn hình Live, về Routine


        playCloudflareLive();
        listenToFirebaseToAutoClose();
    }


    private void playCloudflareLive() {
        player = new ExoPlayer.Builder(this).build();
        livePlayerView.setPlayer(player);
        liveProgressBar.setVisibility(View.VISIBLE);


        // Tạo độ trễ 4 giây chờ Cloudflare build file HLS
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                android.util.Log.d("LiveStream", "Starting to play URL: " + AppConfig.CLOUDFLARE_LIVE_STREAM_URL);


                MediaItem.LiveConfiguration liveConfig = new MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(3000).setMaxOffsetMs(5000).build();


                MediaItem mediaItem = new MediaItem.Builder()
                        .setUri(Uri.parse(AppConfig.CLOUDFLARE_LIVE_STREAM_URL))
                        .setLiveConfiguration(liveConfig).build();


                DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
                HlsMediaSource hlsMediaSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);


                player.setMediaSource(hlsMediaSource);
                player.prepare();
                player.play();
                android.util.Log.d("LiveStream", "Player is playing");


                player.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        android.util.Log.d("LiveStream", "Playback state changed to: " + state);
                        if (state == Player.STATE_READY) {
                            liveProgressBar.setVisibility(View.GONE);
                            android.util.Log.d("LiveStream", "Player is READY");
                        }
                        else if (state == Player.STATE_BUFFERING) {
                            liveProgressBar.setVisibility(View.VISIBLE);
                            android.util.Log.d("LiveStream", "Player is BUFFERING");
                        }
                        else if (state == Player.STATE_IDLE) {
                            android.util.Log.d("LiveStream", "Player is IDLE");
                        }
                        else if (state == Player.STATE_ENDED) {
                            android.util.Log.d("LiveStream", "Player ENDED");
                        }
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("LiveStream", "Error: " + e.getMessage(), e);
                Toast.makeText(this, "Lỗi Live: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, 4000);
    }


    // UX cực xịn: Nếu hết 30s buồn ngủ, App tự động đóng màn hình Live đưa bạn về Routine
    // Nút thắt tự động đóng màn hình
    private void listenToFirebaseToAutoClose() {
        // Gắn TRỰC TIẾP link Database vào getInstance()
        FirebaseDatabase.getInstance("https://my-iot-project-658d2-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("iot_status")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (isMovingBack) return; // Đã sắp đóng, không cần xử lý thêm

                        boolean currentSleeping = false;
                        boolean currentYawning = false;

                        // Parse sleeping
                        if (snapshot.hasChild("is_sleeping")) {
                            Object slp = snapshot.child("is_sleeping").getValue();
                            if (slp != null) {
                                currentSleeping = slp.toString().equalsIgnoreCase("true");
                            }
                        }

                        // Parse yawning
                        if (snapshot.hasChild("is_yawning")) {
                            Object ywn = snapshot.child("is_yawning").getValue();
                            if (ywn != null) {
                                currentYawning = ywn.toString().equalsIgnoreCase("true");
                            }
                        }

                        // Hiển thị banner ngáp
                        if (currentYawning) {
                            tvYawnAlertLive.setVisibility(View.VISIBLE);
                            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> tvYawnAlertLive.setVisibility(View.GONE), 3000);
                        }

                        android.util.Log.d("LiveStream", "Firebase is_sleeping: " + currentSleeping + ", isMovingBack: " + isMovingBack);


                        // Chỉ auto-close nếu:
                        // 1. is_sleeping = true khi vào (ghi nhận thời gian)
                        // 2. sau đó is_sleeping = false VÀ đã ở trong activity > 30 giây
                        if (currentSleeping) {
                            // Ghi nhận thời gian khi phát hiện ngủ gật
                            if (sleepStartTime == 0) {
                                sleepStartTime = System.currentTimeMillis();
                                android.util.Log.d("LiveStream", "Sleep detected, starting timer");
                            }
                        } else if (!currentSleeping && sleepStartTime > 0) {
                            // Chỉ auto-close nếu đã ngủ gật > 30 giây
                            long sleepDuration = System.currentTimeMillis() - sleepStartTime;
                            android.util.Log.d("LiveStream", "Sleep ended after " + sleepDuration + "ms");


                            if (sleepDuration >= 30000) { // 30 giây
                                isMovingBack = true;
                                Toast.makeText(LiveStreamActivity.this, "Đã hết cảnh báo, quay về Routine...", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        android.util.Log.e("LiveStream", "Firebase error: " + error.getMessage());
                    }
                });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) player.release();
    }
}
