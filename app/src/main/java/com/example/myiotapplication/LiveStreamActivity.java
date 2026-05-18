package com.example.myiotapplication;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_live_stream);

        livePlayerView = findViewById(R.id.livePlayerView);
        liveProgressBar = findViewById(R.id.liveProgressBar);
        btnBackToRoutine = findViewById(R.id.btnBackToRoutine);

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

                player.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY) liveProgressBar.setVisibility(View.GONE);
                        else if (state == Player.STATE_BUFFERING) liveProgressBar.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                Toast.makeText(this, "Lỗi Live: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, 4000);
    }

    // UX cực xịn: Nếu hết 30s buồn ngủ, App tự động đóng màn hình Live đưa bạn về Routine
    // Nút thắt tự động đóng màn hình
    private void listenToFirebaseToAutoClose() {
        // Gắn TRỰC TIẾP link Database vào getInstance()
        FirebaseDatabase.getInstance("https://my-iot-project-658d2-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("iot_status/is_sleeping")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Boolean isSleeping = snapshot.getValue(Boolean.class);
                        if (isSleeping != null && !isSleeping) {
                            Toast.makeText(LiveStreamActivity.this, "Đã hết cảnh báo, quay về Routine...", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError error) { }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) player.release();
    }
}