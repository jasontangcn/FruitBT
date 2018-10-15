package com.fruits.bt;

import java.io.Serializable;

public class DownloadTask implements Serializable {
	private static final long serialVersionUID = -4946996326962166415L;

	public static enum DownloadState implements Serializable {
		PENDING(1), STARTED(2), PAUSED(3), STOPPED(4), COMPLETED(5);

		private int stateId;

		DownloadState(int stateId) {
			this.stateId = stateId;
		}
	}

	private DownloadState state;
	private FileMetadata fileMetadata;

	public DownloadTask() {

	}

	public DownloadState getState() {
		return state;
	}

	public void setState(DownloadState state) {
		this.state = state;
	}

	public FileMetadata getFileMetadata() {
		return fileMetadata;
	}

	public void setFileMetadata(FileMetadata fileMetadata) {
		this.fileMetadata = fileMetadata;
	}

	@Override
	public String toString() {
		return "DownloadTask [state=" + state + ", fileMetadata=" + fileMetadata + "].";
	}

}
