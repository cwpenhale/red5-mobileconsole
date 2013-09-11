package com.destinationradiodenver.mobileStreaming;

import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IStreamFilenameGenerator;

public class CustomFilenameGenerator implements IStreamFilenameGenerator {
	private  String recordPath;
	public String getRecordPath() {
		return recordPath;
	}

	public void setRecordPath(String recordPath) {
		this.recordPath = recordPath;
	}

	public String getPlaybackPath() {
		return playbackPath;
	}

	public void setPlaybackPath(String playbackPath) {
		this.playbackPath = playbackPath;
	}

	public boolean isResolvesAbsolutePath() {
		return resolvesAbsolutePath;
	}

	public void setResolvesAbsolutePath(boolean resolvesAbsolutePath) {
		this.resolvesAbsolutePath = resolvesAbsolutePath;
	}

	private String playbackPath;
	private boolean resolvesAbsolutePath;

	public String generateFilename(IScope scope, String name,
			GenerationType type) {
		// Generate filename without an extension.
		return generateFilename(scope, name, null, type);
	}

	public String generateFilename(IScope scope, String name, String extension,
			GenerationType type) {
		String filename;
		if (type == GenerationType.RECORD)
			filename = recordPath + name;
		else
			filename = playbackPath + name;

		if (extension != null)
			// Add extension
			filename += extension;

		return filename;
	}

	public boolean resolvesToAbsolutePath() {
		return resolvesAbsolutePath;
	}
}
