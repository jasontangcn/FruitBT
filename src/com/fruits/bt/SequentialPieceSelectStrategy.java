package com.fruits.bt;

import com.fruits.bt.PeerMessage.RequestMessage;

public class SequentialPieceSelectStrategy {
	private final DownloadManager downloadManager;
	private final PeerConnectionManager connectionManager;
	
	public SequentialPieceSelectStrategy(DownloadManager downloadManager, PeerConnectionManager connectionManager) {
		this.downloadManager = downloadManager;
		this.connectionManager = connectionManager;
	}
	
	public boolean downloadNextSlice(String infoHash) throws Exception {
		DownloadTask downloadTask = this.downloadManager.getDownloadTask(infoHash);
		if(null != downloadTask) {
			FileMetadata fileMetadata = downloadTask.getFileMetadata();
			Slice slice;
			
			while(null != (slice = fileMetadata.getNextIncompletedSlice())) {
				PeerConnection connection = connectionManager.canIDownloadThisSlice(infoHash, slice);
				if(null != connection) {
					connection.addMessageToSend(new RequestMessage(slice.getIndex(), slice.getBegin(), slice.getLength()));
					return true;
				}
			};
		}
		
		return false;
	}
}
