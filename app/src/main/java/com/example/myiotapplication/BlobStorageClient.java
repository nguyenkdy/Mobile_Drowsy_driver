package com.example.myiotapplication;

import android.util.Log;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlobStorageClient {

    private static final String TAG = "VideoPlayer_Blob";
    
    private BlobServiceClient blobServiceClient;
    private BlobContainerClient containerClient;
    private String containerName;

    public BlobStorageClient(String connectionString, String containerName) throws Exception {
        Log.d(TAG, "Initializing BlobStorageClient");
        this.containerName = containerName;
        
        if (connectionString == null || connectionString.isEmpty()) {
            throw new IllegalArgumentException("Connection string cannot be null or empty");
        }
        
        if (containerName == null || containerName.isEmpty()) {
            throw new IllegalArgumentException("Container name cannot be null or empty");
        }
        
        Log.d(TAG, "Creating BlobServiceClient from connection string");
        blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        Log.d(TAG, "BlobServiceClient created");
        
        Log.d(TAG, "Getting container client for: " + containerName);
        containerClient = blobServiceClient.getBlobContainerClient(containerName);
    }

    /**
     * Get the latest video blob from the container
     * Videos are identified by .mp4 extension
     */
    public String getLatestVideoUrl() throws Exception {
        Log.d(TAG, "Getting latest video URL...");
        VideoBlob latestVideo = getLatestVideo();
        if (latestVideo != null) {
            String finalUrl = containerClient.getBlobClient(latestVideo.getName()).getBlobUrl();
            Log.d(TAG, "Latest video URL: " + finalUrl);
            return finalUrl;
        }
        Log.w(TAG, "No latest video found");
        return null;
    }

    /**
     * Get URL for a specific video
     */
    public String getVideoUrl(String videoName) {
        return containerClient.getBlobClient(videoName).getBlobUrl();
    }

    /**
     * Get latest video blob metadata
     */
    public VideoBlob getLatestVideo() throws Exception {
        Log.d(TAG, "Getting latest video metadata...");
        List<VideoBlob> videos = getAllVideos();
        if (videos.isEmpty()) {
            Log.w(TAG, "No videos available");
            return null;
        }
        Log.d(TAG, "Latest video: " + videos.get(0).getName());
        return videos.get(0);
    }

    /**
     * Get all video blobs sorted by creation time (newest first)
     */
    public List<VideoBlob> getAllVideos() throws Exception {
        Log.d(TAG, "Getting all videos from container...");
        List<VideoBlob> videos = new ArrayList<>();

        try {
            int count = 0;
            for (BlobItem blobItem : containerClient.listBlobs()) {
                count++;
                String blobName = blobItem.getName();
                Log.d(TAG, "Found blob: " + blobName);
                
                if (blobName.toLowerCase().endsWith(".mp4")) {
                    Log.d(TAG, "  -> This is an MP4 file");
                    videos.add(new VideoBlob(
                            blobName,
                            blobItem.getProperties().getCreationTime(),
                            blobItem.getProperties().getLastModified()
                    ));
                } else {
                    Log.d(TAG, "  -> Skipping (not .mp4)");
                }
            }
            
            Log.d(TAG, "Total blobs found: " + count);
            Log.d(TAG, "MP4 videos found: " + videos.size());
        } catch (Exception e) {
            Log.e(TAG, "Error listing blobs", e);
            throw e;
        }

        // Sort by creation/modification time (newest first)
        videos.sort((a, b) -> {
            OffsetDateTime timeA = a.getModifiedTime() != null ? a.getModifiedTime() : a.getCreatedTime();
            OffsetDateTime timeB = b.getModifiedTime() != null ? b.getModifiedTime() : b.getCreatedTime();
            int comparison = timeB.compareTo(timeA);
            Log.d(TAG, "Comparing " + a.getName() + " vs " + b.getName() + " = " + comparison);
            return comparison;
        });

        Log.d(TAG, "Videos sorted. Latest: " + (videos.isEmpty() ? "none" : videos.get(0).getName()));
        return videos;
    }

    /**
     * Get SAS token for blob access
     * For simplicity, we use public access. For production, generate SAS tokens if needed.
     */
    private String generateSasToken() {
        // Container must have public/anonymous access for direct URL access
        return "";
    }

    /**
     * Check if a new video is available
     */
    public boolean isNewVideoAvailable(String currentVideoName) throws Exception {
        Log.d(TAG, "Checking for new video (current: " + currentVideoName + ")");
        VideoBlob latestVideo = getLatestVideo();
        boolean isNew = latestVideo != null && !latestVideo.getName().equals(currentVideoName);
        Log.d(TAG, "New video available: " + isNew);
        if (isNew) {
            Log.d(TAG, "New video: " + latestVideo.getName());
        }
        return isNew;
    }

    /**
     * Video blob information
     */
    public static class VideoBlob {
        private String name;
        private OffsetDateTime createdTime;
        private OffsetDateTime modifiedTime;

        public VideoBlob(String name, OffsetDateTime createdTime, OffsetDateTime modifiedTime) {
            this.name = name;
            this.createdTime = createdTime;
            this.modifiedTime = modifiedTime;
        }

        public String getName() {
            return name;
        }

        public OffsetDateTime getCreatedTime() {
            return createdTime;
        }

        public OffsetDateTime getModifiedTime() {
            return modifiedTime;
        }
    }
}



