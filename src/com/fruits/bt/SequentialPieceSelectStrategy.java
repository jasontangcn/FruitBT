package com.fruits.bt;

import com.fruits.bt.PeerMessage.RequestMessage;

public class SequentialPieceSelectStrategy {
	private final DownloadManager downloadManager;
	private final PeerConnectionManager connectionManager;

	public SequentialPieceSelectStrategy(DownloadManager downloadManager, PeerConnectionManager connectionManager) {
		this.downloadManager = downloadManager;
		this.connectionManager = connectionManager;
	}
}
