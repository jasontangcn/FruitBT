package com.fruits.bt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fruits.bt.DownloadTask.DownloadState;

/*
 * Typical scenarios:
 * 1. Add a seed to download, initial status may be 'STARTED' or 'PENDING'.
 * 2. Start to download.
 *    Create a download task and initialize the metadata(initialize internal data structure, e.g. pieces&slices, create temp file).
 *    Get peers from tracker server and create connections with peers.
 * 3. If I am unchoked by peers, start to request slices.
 */

public class DownloadManager {
	static final Logger logger = LoggerFactory.getLogger(DownloadManager.class);
	// private TrackerManager trackerManager = new TrackerManager();
	// private PeerConnectionManager connectionManager;
	// 1. Start to download one file(hash_info, bitfield).
	// 2. Get peers from the tracker.
	// 3. Connect to peers and start to download slices.

	// 1. Start listener.
	// 2. Accept connect from peers.
	// 3. Upload slices to peers. 
	private PeerFinder peerFinder = new PeerFinder(this);

	private PiecePicker piecePicker;
	private PeerConnectionManager connectionManager;
	// info_hash -> DownloadTask
	private Map<String, DownloadTask> downloadTasks;

	public DownloadManager() throws IOException {
		this.piecePicker = new PiecePicker(this);
		loadDownloadTasks();

		// TODO: Test code.
		/*
		this.addDownloadTask("D:\\\\TorrentDownload\\\\Wireshark-win32-1.10.0.exe.torrent");
		FileMetadata fileMetadata = this.downloadTasks.get("b3c8f8e50d3f3f701157f2c2517eee78588b48f2").getFileMetadata();
		List<Slice> slicesToDwonlaod = fileMetadata.getIncompletedSlices();
		for(Slice slice : slicesToDwonlaod) {
			ByteBuffer content = fileMetadata.readSlice(slice);
			fileMetadata.writeSlice(slice.getIndex(), slice.getBegin(), slice.getLength(), content);
		}
		*/
		this.syncDownloadTasksToDisk();
	}
	
	private void loadDownloadTasks() throws IOException { // If it fails, fail the system.
		// FileOutputStream: If the specified file does not exits, create a new one.
		// FileInputStream: If the specified file does not exits, throws an exception.
		File taskFile = new File(Client.DOWNLOAD_TASKS_FILE);
		// TODO: File.length() to check whether the file is empty or not, is it enough?
		// The length, in bytes, of the file denoted by this abstract pathname, or <code>0L</code> if the file does not exist.
		if (taskFile.length() > 0) {
			// Load metadata for the files that are in the progress of downloading/ or have been downloaded yet.
			ObjectInputStream ois = null;
			Object obj = null;
			try {
				ois = new ObjectInputStream(new FileInputStream(taskFile));
				obj = ois.readObject();
			} catch (ClassNotFoundException e) {
				throw new IOException(e);
			} finally {
				try {
					if (ois != null)
						ois.close();
				} catch (IOException ioe) {
					logger.error("", ioe);
				}
			}
			this.downloadTasks = (Map<String, DownloadTask>) obj;
			logger.info("Loaded tasks from disk : {}.", this.downloadTasks);
		} else {
			this.downloadTasks = new HashMap<String, DownloadTask>(); // infoHash -> DownloadTask
			ObjectOutputStream oos = null;
			try {
				oos = new ObjectOutputStream(new FileOutputStream(taskFile));
				oos.writeObject(this.downloadTasks);
			} finally {
				try {
					if (oos != null)
						oos.close();
				} catch (IOException e) {
					logger.error("", e);
				}
			}
			logger.info("Created a new task.");
		}
	}

	// IOException may be caused by : failed to parse seed file or failed to create FileMetadata.
	// But this should not fail the system, just ignore the new seed.
	public void addDownloadTask(String seedFilePath) throws IOException {
		// 1. create a FileMetadata(including the information from torrent seed and downloading progress), then save it to disk.
		// 2. Peers from tracker is dynamic, so we do not need to persist it.
		// 3. 
		TorrentSeed seed = TorrentSeed.parseSeedFile(seedFilePath);
		DownloadTask task = new DownloadTask(seed);
		task.setState(DownloadTask.DownloadState.PENDING);

		this.downloadTasks.put(Utils.bytes2HexString(seed.getInfoHash()), task);
		logger.trace("New task was added, task length : {}.", this.downloadTasks.size());

		syncDownloadTasksToDisk();
		// TODO: Just for test, remove it.
		this.startDownloadTask(Utils.bytes2HexString(seed.getInfoHash()));
	}


	public void finalize() {
		for (DownloadTask task : this.downloadTasks.values()) {
			task.getFileMetadata().close();
		}
	}
	
	public DownloadTask getDownloadTask(String infoHash) {
		return this.downloadTasks.get(infoHash);
	}
	
	public FileMetadata getFileMetadata(String infoHash) {
		DownloadTask task = this.downloadTasks.get(infoHash);
		if(task != null)
			return task.getFileMetadata();
		return null;
	}
	
	public void setDownloadTaskState(String infoHash, DownloadState state) throws IOException {
		DownloadTask task = this.downloadTasks.get(infoHash);
		if(task != null)
			task.setState(state);
		syncDownloadTasksToDisk();
	}
	
	public void startDownloadTask(String infoHash) throws IOException { // IOException from syncDownloadTaksToDisk.
		DownloadTask downloadTask = this.downloadTasks.get(infoHash);
		downloadTask.setState(DownloadTask.DownloadState.STARTED);
		syncDownloadTasksToDisk();

		FileMetadata fileMetadata = downloadTask.getFileMetadata();
		TorrentSeed seed = fileMetadata.getSeed();
		Peer self = new Peer();
		// TODO: Set the correct peerId, infoHash and bitfield.
		// Always get self's bitfield when using it, but we need to store peer's bitfield in the instance.
		self.setPeerId(Client.PEER_ID);
		self.setInfoHashString(infoHash);
		logger.info("Started download task: infoHash : {}, bitfield : {}.", infoHash, fileMetadata.getBitfield());

		List<Peer> peers = peerFinder.getPeers(seed);
		logger.trace("Got peers from tracker server : {} for {}.", peers, infoHash);

		for (Peer peer : peers) {
			connectionManager.createOutgoingConnection(self, peer); // create may fail and get a NULL object, no problem, just ignore failed peer.
		}

		// TODO: Test code, only download the first slice that is not downloaded yet.
		/*
		Slice slice = fileMetada.getNextIncompletedSlice();
		logger.trace("Next slice to download : " + slice + ".");
		peerConnection.addMessageToSend(new RequestMessage(slice.getIndex(), slice.getBegin(), slice.getLength()));
		*/
	}
	
	public void pauseDownloadTask(String infoHash) {
	}
	
	public void restartDownloadTask(String infoHash) throws IOException {
	}
	
	// this should be called when client exits.
	public void stopDownloadTask(String infoHash) {
		// 0. Add a DownloadTask in DownloadManager.
		// 1. Open a temp file.
		// 2. Open download task file.
		// 3. Create outgoing connections, and may accept incoming connections.
		// 4. Some working variables, e.g. PiecePicker.batchRequests.

		// 1. Close the connections.
		// 2. Remove the requesting index from PiecePicker.
		// 3. Close task file and temp file.
		this.connectionManager.closePeerConnections(infoHash); 	//TODO: should not close the incoming connections.
		this.downloadTasks.get(infoHash).getFileMetadata().close();
		try {
			DownloadTask task = this.downloadTasks.get(infoHash);
			task.setState(DownloadState.STOPPED);
			syncDownloadTasksToDisk();
		} catch (IOException e) {
			// TODO: how to handle this exception?
			logger.error("", e);
		}
	}

	// If task is completed, shall we remove the corresponding download task?
	// If you delete a running download task, system will sequentially call stopDownloadTask and removeDownloadTask.
	public void removeDownloadTask(String infoHash, boolean deleteFiles) throws IOException {
		this.stopDownloadTask(infoHash);
		
		this.downloadTasks.remove(infoHash);
		this.syncDownloadTasksToDisk();
		
		if (deleteFiles) {
			this.downloadTasks.get(infoHash).getFileMetadata().delete();
		}
	}

	public void startAllDownloadTasks() throws IOException {
		Iterator<String> iterator = this.downloadTasks.keySet().iterator();
		while (iterator.hasNext()) {
			startDownloadTask(iterator.next());
		}
	}

	public void stopAllDownloadTasks() {
		Iterator<String> iterator = this.downloadTasks.keySet().iterator();
		while (iterator.hasNext()) {
			stopDownloadTask(iterator.next());
		}
	}

	// Any file download task is updated, this method should be called to sync the changing.
	void syncDownloadTasksToDisk() throws IOException { // Fatal error, if sych failed, the metadata is not complete, system should shutdown.
		// TODO: Lock the file? Optimize the logic?
		// FileOutputStream : if the file does not exist, create a new one.

		// TODO: rewrite the code, we should not repeat opening then closing just for one slice writing, 
		// we should keep the file always open.
		
		// public FileOutputStream(String name, boolean append) -> append mode
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(Client.DOWNLOAD_TASKS_FILE));
		oos.writeObject(this.downloadTasks);
		oos.close();
		//logger.trace("downloadTasks : " + this.downloadTasks + ".");
	}
	
	public void seeding(String seedFilePath) throws IOException {
		TorrentSeed seed = TorrentSeed.parseSeedFile(seedFilePath);
		peerFinder.getPeers(seed);
	}

	public void setConnectionManager(PeerConnectionManager connectionManager) {
		this.connectionManager = connectionManager;
	}
	
	public PeerConnectionManager getConnectionManager() {
		return this.connectionManager;
	}
	
	public PiecePicker getPiecePicker() {
		return this.piecePicker;
	}
}
