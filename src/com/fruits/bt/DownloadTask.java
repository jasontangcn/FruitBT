package com.fruits.bt;

import java.io.Serializable;

public class DownloadTask implements Serializable {
	private static final long serialVersionUID = -4946996326962166415L;

	public static enum DownloadState implements Serializable {
		PENDING(1), STARTED(2), PAUSED(3), STOPPED(4), COMPLETED(5);

		private int downloadStateId;

		DownloadState(int downloadStateId) {
			this.downloadStateId = downloadStateId;
		}
	}

	private DownloadState downloadState;
	private FileMetadata fileMetadata;
	
	public DownloadTask() {
		
	}

	public DownloadState getDownloadState() {
		return downloadState;
	}


	public void setDownloadState(DownloadState downloadState) {
		this.downloadState = downloadState;
	}


	public FileMetadata getFileMetadata() {
		return fileMetadata;
	}

	public void setFileMetadata(FileMetadata fileMetadata) {
		this.fileMetadata = fileMetadata;
	}


	@Override
	public String toString() {
		return "DownloadTask [downloadState=" + downloadState + ", fileMetadata=" + fileMetadata + "].";
	}
	
	
}
