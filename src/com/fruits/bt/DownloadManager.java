package com.fruits.bt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

	public static final int REQUEST_MESSAGE_BATCH_SIZE = 3;
	private Map<String, List<Integer>> indexesRequesting = new HashMap<String, List<Integer>>();

	public DownloadManager(PeerConnectionManager connectionManager) throws IOException {
		this.connectionManager = connectionManager;
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
		// FileInputStream: If the specified file does not exits, throws exception.
		File tasksFile = new File(Client.DOWNLOAD_TASKS_TEMP_FILE);
		// TODO: File.length() to check whether the file is empty or not, is it enough?
		if (tasksFile.length() > 0) {
			// Load metadata for the files downloading/downloaded yet.
			ObjectInputStream ois = null;
			Object obj = null;
			try {
				ois = new ObjectInputStream(new FileInputStream(tasksFile));
				obj = ois.readObject();
			} catch (ClassNotFoundException e) {
				throw new IOException(e);
			} finally {
				try {
					if (ois != null)
						ois.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			this.downloadTasks = (Map<String, DownloadTask>) obj;
			System.out.println("Loaded download tasks from disk : " + this.downloadTasks + ".");
		} else {
			this.downloadTasks = new HashMap<String, DownloadTask>();
			ObjectOutputStream oos = null;
			try {
				oos = new ObjectOutputStream(new FileOutputStream(tasksFile));
				oos.writeObject(this.downloadTasks);
			} finally {
				try {
					if (oos != null)
						oos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			System.out.println("Created a new downloadTasks.");
		}
	}

	public void finalize() {
		for (DownloadTask task : this.downloadTasks.values()) {
			task.getFileMetadata().finalize();
		}
	}

	public void startDownloadFile(String infoHash) {
		DownloadTask downloadTask = this.downloadTasks.get(infoHash);
		downloadTask.setState(DownloadTask.DownloadState.STARTED);
		FileMetadata fileMetada = downloadTask.getFileMetadata();
		TorrentSeed seed = fileMetada.getSeed();
		Peer self = new Peer();
		// TODO: Set the correct peerId, infoHash and bitfield.
		self.setPeerId(Client.PEER_ID);
		self.setInfoHash(infoHash);
		System.out.println("self.infoHash : " + infoHash + ", self.bitfield : " + fileMetada.getBitfield() + ".");

		List<Peer> peers = trackerManager.getPeers(seed);
		System.out.println("Got peers from tracker server : [" + peers + "] for " + infoHash + ".");

		for (Peer peer : peers) {
			connectionManager.createOutgoingConnection(self, peer); // create may fail and get a NULL object, no problem, just ignore failed peer.
		}

		// TODO: Test code, only download the first slice that is not downloaded yet.
		/*
		Slice slice = fileMetada.getNextIncompletedSlice();
		System.out.println("Next slice to download : " + slice + ".");
		peerConnection.addMessageToSend(new RequestMessage(slice.getIndex(), slice.getBegin(), slice.getLength()));
		*/
	}

	public void startAllDownloadTasks() {
	}

	public void stopAllDownloadTasks() {
		Iterator<String> iterator = this.downloadTasks.keySet().iterator();
		while(iterator.hasNext()) {
			stopDownloadFile(iterator.next());
		}
	}

	
	public void stopDownloadFile(String infoHash) {
		// 0. Add a DownloadTask in DownloadManager.
		// 1. Open a temp file.
		// 2. Open download task file.
		// 3. Create outgoing connections, and may accept incoming connections.
		// 4. Some working variables, e.g. indexesRequesting.
		
		// 1. Close the connections.
		// 2. Remove the requesting index.
		// 3. Close download task file and temp file.
		this.connectionManager.closeConnections(infoHash);
		this.downloadTasks.get(infoHash).getFileMetadata().finalize();
	}

	public void pauseDownloadTask(String infoHash) {
	}

  // IOException may be caused by : failed to parse seed file or failed to create FileMetadata.
	// But this should not fail the system, just ignore the new seed.
	public void addDownloadTask(String seedFilePath) throws IOException {
		// 1. create a FileMetadata(including the information from torrent seed and downloading progress), then save it to disk.
		// 2. Peers from tracker is dynamic, so we do not need to persist it.
		// 3. 
		TorrentSeed seed = TorrentSeed.parseSeedFile(seedFilePath);
		DownloadTask task = new DownloadTask();

		FileMetadata fileMetadata = new FileMetadata(seed);
		task.setState(DownloadTask.DownloadState.PENDING);
		task.setFileMetadata(fileMetadata);

		this.downloadTasks.put(seed.getInfoHash(), task);
		System.out.println("New download task was added, downloadTasks length : " + this.downloadTasks.size() + ".");

		syncDownloadTasksToDisk();
	}

	public DownloadTask getDownloadTask(String infoHash) {
		return this.downloadTasks.get(infoHash);
	}

	// If task completed, shall we remove the corresponding download task?
	public void removeDownloadTask(String infoHash, boolean deleteTempFiles) {
		this.downloadTasks.remove(infoHash);
		
		if(deleteTempFiles) {
			// TODO:
		}
	}
	
	// Any file download task is updated, this method should be called to sync the changing to disk.
	private void syncDownloadTasksToDisk() throws IOException { // Fatal error, if sych failed, the metadata is not complete, system should shutdown.
		// TODO: Lock the file? Optimize the logic?
		// FileOutputStream : if the file does not exist, create a new one.
		
		// TODO: rewrite the code, we should not repeat opening then closing just for one slice writing, we should keep the file always open.
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(Client.DOWNLOAD_TASKS_TEMP_FILE));
		oos.writeObject(this.downloadTasks);
		oos.close();
		System.out.println("Latest downloadTasks : " + this.downloadTasks + ".");
	}

	public BitSet getBitfield(String infoHash) {
		DownloadTask downloadTask = this.downloadTasks.get(infoHash);
		if (downloadTask != null)
			return downloadTask.getFileMetadata().getBitfield();
		return null;
	}

	public ByteBuffer readSlice(String infoHash, Slice slice) {
		try {
			DownloadTask task = this.downloadTasks.get(infoHash);
			if (task != null)
				return task.getFileMetadata().readSlice(slice);
			return null;
		} catch (IOException e) {
			// TODO:
			// If exception caught, failed to open temp file, it's fatal error.
			e.printStackTrace();
			this.stopDownloadFile(infoHash);
			return null;
		}
	}

	// TODO: Register listeners to FileMetadata for events, e.g. slice write, piece completed, whole file completed?
	public void writeSlice(String infoHash, int index, int begin, int length, ByteBuffer data) {
		try {
			FileMetadata metadata = this.downloadTasks.get(infoHash).getFileMetadata();
			boolean isPieceCompleted = metadata.writeSlice(index, begin, length, data);

			syncDownloadTasksToDisk();

			// TODO: Notify all of peers that are interested in me.
			if (isPieceCompleted)
				this.connectionManager.notifyPeersIHavePiece(infoHash, index);
			
			// The file has been completed. Let somebody know?
			if(metadata.isAllPiecesCompleted()) {
				// TODO: XXXX
			}
			
		} catch (IOException e) {
			// TODO:
			// Failed to open temp file or failed to sych tasks to disk.
			// In any case, we should stop this task(do not need to fail the whole system).
			e.printStackTrace();
			this.stopDownloadFile(infoHash);
		}
	}

	public void requestMoreSlices(String infoHash, PeerConnection connection) {
		/*
		 * 1. Check the slices one by one and see whether there is peer which has this slice and is not choking me.
		 * 
		 * 2. Check the bitfield of the fastest peer and pick the slice this peer has but I do not have it yet. 
		 * 
		 * 3. Download slices from the peers who is unchoking me.
		 * 
		 * 
		 */
		FileMetadata fileMetadata = this.getDownloadTask(infoHash).getFileMetadata();
		if (fileMetadata.isAllPiecesCompleted())
			return;

		int index = connection.getIndexRequesting();

		if (index == -1) {
			BitSet selfBitfield = fileMetadata.getBitfield();
			BitSet peerBitfield = connection.getPeer().getBitfield();

			for (int i = 0; i < peerBitfield.size(); i++) {
				if (peerBitfield.get(i) && !selfBitfield.get(i)) {
					List<Integer> indexes = this.indexesRequesting.get(infoHash);
					if (indexes == null) {
						indexes = new ArrayList<Integer>();
						this.indexesRequesting.put(infoHash, indexes);
						indexes.add(i);
						index = i;
						break;
					} else {
						if (!indexes.contains(i)) {
							indexes.add(i);
							index = i;
							break;
						}
					}
				}
			}
		}

		if (index != -1) {
			System.out.println("Requesting slices with index : " + index + ".");
			List<Slice> slices = fileMetadata.getNextBatchIncompletedSlices(index, REQUEST_MESSAGE_BATCH_SIZE);

			if (slices.size() != 0) {
				// It may start a download a new piece or download the remaining slices of a piece.
				connection.setIndexRequesting(index);
				connection.setRequestsSent(slices.size());
				List<PeerMessage> messages = new ArrayList<PeerMessage>();
				for (Slice slice : slices) {
					messages.add(new PeerMessage.RequestMessage(slice.getIndex(), slice.getBegin(), slice.getLength()));
				}
				System.out.println("Batch RequestMessage, messages size : " + slices.size() + ".");
				connection.addMessageToSend(messages);
			} else {
				// This pieces have been completed, try to find next one to download.
				this.indexesRequesting.get(infoHash).remove(new Integer(index)); // Be careful, new Integer(index) NOT index.
				connection.setIndexRequesting(-1);
				requestMoreSlices(infoHash, connection);
			}
		}
	}

	public void cancelRequestingPiece(String infoHash, PeerConnection connection) {
		int indexRequesting = connection.getIndexRequesting();
		if (indexRequesting != -1) {
			this.indexesRequesting.get(infoHash).remove(new Integer(indexRequesting));
		}
	}
}
