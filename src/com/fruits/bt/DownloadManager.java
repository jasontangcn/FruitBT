package com.fruits.bt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fruits.bt.PeerMessage.PieceMessage;
import com.fruits.bt.PeerMessage.RequestMessage;

public class DownloadManager {	
	// private TrackerManager trackerManager = new TrackerManager();
	// private PeerConnectionManager connectionManager;
	// 1. Start to download one file(hash_info, bitfield).
	// 2. Get peers from tracker.
	// 3. Connect to peers and download slices.
	
	// 1. Start listener.
	// 2. Accept connect from peers.
	// 3. Upload slices to peers. 
	private TrackerManager trackerManager = new TrackerManager();
	
	private final PeerConnectionManager connectionManager;
	// info_hash -> DownloadTask
	private Map<String, DownloadTask> downloadTasks;
	
	private SequentialPieceSelectStrategy pieceSelectStrategy;
	
	public DownloadManager(PeerConnectionManager connectionManager) throws Exception {
		this.connectionManager = connectionManager;
		loadDownloadTasks();
		
		// TODO: Test code.
		/*
		this.addDownloadTask("D:\\\\TorrentDownload\\\\Wireshark-win32-1.10.0.exe.torrent");
		FileMetadata fileMetadata = this.downloadTasks.get("b3c8f8e50d3f3f701157f2c2517eee78588b48f2").getFileMetadata();
		List<Slice> slicesToDwonlaod = fileMetadata.getIncompletedSlices();
		for(Slice slice : slicesToDwonlaod) {
			ByteBuffer content = fileMetadata.readSliceData(slice);
			fileMetadata.writeSlice(slice.getIndex(), slice.getBegin(), slice.getLength(), content);
		}
		*/
		this.syncDownloadTasksToDisk();
		
		pieceSelectStrategy = new SequentialPieceSelectStrategy(this, connectionManager);
	}
	
	private void loadDownloadTasks() throws Exception {
		File downloadTasksFile = new File(Client.DOWNLOAD_TASKS_TEMP_FILE);
		// TODO: File.length() to check whether the file is empty or not, is it enough?
		if(downloadTasksFile.length() > 0) {
			// Load metadata for the files downloading or downloaded yet.
		    ObjectInputStream ois = new ObjectInputStream(new FileInputStream(downloadTasksFile));
		    Object obj = ois.readObject();
		    ois.close();
		    
		    this.downloadTasks = (Map<String, DownloadTask>)obj;
		    System.out.println("Loaded downloadTasks from disk : " + this.downloadTasks + ".");
		}else {
			this.downloadTasks = new HashMap<String, DownloadTask>();
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(downloadTasksFile));
			oos.writeObject(this.downloadTasks);
			oos.close();
			
			System.out.println("Create a new downloadTasks.");
		}
	}
	
	public void startDownloadFile(String infoHash) throws Exception {
		DownloadTask downloadTask = this.downloadTasks.get(infoHash);
		downloadTask.setDownloadState(DownloadTask.DownloadState.STARTED);
		FileMetadata fileMetada = downloadTask.getFileMetadata();
		TorrentSeed torrentSeed = fileMetada.getSeed();
		Peer self = new Peer();
		// TODO: Set the correct peerId, infoHash and bitfield.
		self.setPeerId(Client.PEER_ID);
		self.setInfoHash(infoHash);
		System.out.println("self.infoHash : " + infoHash + ", self.bitfield : " + fileMetada.getBitfield() + ".");
		self.setBitfield(fileMetada.getBitfield());
		
		List<Peer> peers = trackerManager.getPeers(torrentSeed);
		System.out.println("Got peers from tracker server : [" + peers + "] for " + infoHash + ".");
		
		PeerConnection peerConnection = null;
		for(Peer peer : peers) {
			peerConnection = connectionManager.createOutgoingConnection(self, peer);
		}
		
		// TODO: Test code, only download the first slice that is not downloaded yet.
		/*
		Slice slice = fileMetada.getNextIncompletedSlice();
		System.out.println("Next slice to download : " + slice + ".");
		peerConnection.addMessageToSend(new RequestMessage(slice.getIndex(), slice.getBegin(), slice.getLength()));
		*/
	}
	
	public void startDownloadAllFiles() throws IOException {
	}

	public void stopDownloadingFile(String infoHash) {
	}
	
	public void pauseDownloadFile(String infoHash) {
	}
	
	public void addDownloadTask(String seedFilePath) throws Exception {
		// 1. create a FileMetadata(including the information from torrent seed and downloading progress), then save it to disk.
		// 2. Peers from tracker is dynamic, so we do not need to persist it.
		// 3. 
		TorrentSeed torrentSeed = TorrentSeed.parseSeedFile(seedFilePath);
		DownloadTask downloadTask = new DownloadTask();
		
		FileMetadata fileMetadata = new FileMetadata(torrentSeed);
		downloadTask.setDownloadState(DownloadTask.DownloadState.PENDING);
		downloadTask.setFileMetadata(fileMetadata);
		
		this.downloadTasks.put(torrentSeed.getInfoHash(), downloadTask);
		System.out.println("A new download task was added, downloadTasks length : " + this.downloadTasks.size() + ".");
		
		syncDownloadTasksToDisk();
	}
	
	// Any file download task is updated, this method should be called to sync the changing to disk.
	private void syncDownloadTasksToDisk() throws Exception {
		// TODO: Lock the file? Optimize the logic?
		// FileOutputStream : if the file does not exist, create a new one.
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(Client.DOWNLOAD_TASKS_TEMP_FILE));
		oos.writeObject(this.downloadTasks);
		oos.close();
		System.out.println("Latest downloadTasks : " + this.downloadTasks + ".");
	}
	
	public BitSet getBitfield(String infoHash) {
		DownloadTask downloadTask = this.downloadTasks.get(infoHash);
		if(null != downloadTask)
			return downloadTask.getFileMetadata().getBitfield();
		else
			return null;
	}
	
	public ByteBuffer readSlice(String infoHash, Slice slice) throws Exception {
		return this.downloadTasks.get(infoHash).getFileMetadata().readSliceData(slice);
	}
	
	public void writeSlice(String infoHash, int index, int begin, int length, ByteBuffer data) throws Exception {
		boolean isPieceCompleted = this.downloadTasks.get(infoHash).getFileMetadata().writeSlice(index, begin, length, data);
		
		syncDownloadTasksToDisk();
		
		// TODO: Notify all of peers that are interested in me.
		if(isPieceCompleted)
			this.connectionManager.notifyPeersIHavePiece(infoHash, index);
	}
	
	public DownloadTask getDownloadTask(String infoHash) {
		return this.downloadTasks.get(infoHash);
	}
	
	public void downloadMoreSlices(String infoHash) throws Exception {
		this.pieceSelectStrategy.downloadNextSlice(infoHash);
	}
}
