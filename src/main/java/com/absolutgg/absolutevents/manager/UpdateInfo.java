package com.absolutgg.absolutevents.manager;

public final class UpdateInfo {

    private final String currentVersion;
    private final String latestVersion;
    private final String tagName;
    private final String releaseUrl;
    private final String downloadUrl;
    private final String assetName;
    private final String body;
    private final boolean updateAvailable;

    public UpdateInfo(
            String currentVersion,
            String latestVersion,
            String tagName,
            String releaseUrl,
            String downloadUrl,
            String assetName,
            String body,
            boolean updateAvailable
    ) {
        this.currentVersion = currentVersion;
        this.latestVersion = latestVersion;
        this.tagName = tagName;
        this.releaseUrl = releaseUrl;
        this.downloadUrl = downloadUrl;
        this.assetName = assetName;
        this.body = body;
        this.updateAvailable = updateAvailable;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getTagName() {
        return tagName;
    }

    public String getReleaseUrl() {
        return releaseUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getAssetName() {
        return assetName;
    }

    public String getBody() {
        return body;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public boolean hasDownload() {
        return downloadUrl != null && !downloadUrl.isBlank();
    }

    public boolean isValid() {
        return latestVersion != null && !latestVersion.isBlank();
    }
}