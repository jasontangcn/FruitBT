package com.fruits.bt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import com.fruits.bt.PeerMessage.PieceMessage;

public class PiecePicker {
	public static final int BATCH_REQUEST_SIZE = 3;

	private final DownloadManager downloadManager;
	/* 
	 * Strategies:
	 * 1. Request slices in one piece from the same peer. 
	 * 2. Rarest first.
	 * 3. Random.
	 * 4. 'End Game' mode.
	 * 
	 * Scenarios:
	 * 1. A connection is unchoked, it starts to request slices.
	 * 
	 * 2. Requested slices received, request more.
	 * 
	 * 
	 * Information needed:
	 * 
	 * 1. Bitfields(self's, peers')
	 * 2. 
	 * 3. 
	 */
	private Random random = new Random();
	private List<PeerConnection> connections = new ArrayList<PeerConnection>();
	private Map<String, Map<Integer, Integer>> rarestFirsts = new HashMap<String, Map<Integer, Integer>>();
	private Map<String, List<BatchRequest>> batchRequests = new HashMap<String, List<BatchRequest>>();

	public PiecePicker(DownloadManager downloadManager) {
		this.downloadManager = downloadManager;
	}

	public void sliceReceived(PeerConnection connection) {
		System.out.println("PiecePicker: sliceReceived is called.");
		BatchRequest request = getBatchRequestInProgress(connection.getSelf().getInfoHash(), connection.getConnectionId());
		request.setReceived(request.getReceived() + 1);

		if (request.isCompleted()) {
			this.requestMoreSlices(connection);
		}
	}

	public void peerHaveNewPiece(String infoHash, int index) {
		System.out.println("PiecePicker: peerHaveNewPiece -> infoHash : " + infoHash + ", index = " + index + ".");
		
		Map<Integer, Integer> rarestFirst = this.rarestFirsts.get(infoHash);
		if (rarestFirst == null) {
			rarestFirst = new HashMap<Integer, Integer>();
			this.rarestFirsts.put(infoHash, rarestFirst);
		}
		Integer n = rarestFirst.get(Integer.valueOf(index));
		if (n == null) {
			rarestFirst.put(Integer.valueOf(index), Integer.valueOf(1));
		} else {
			rarestFirst.put(Integer.valueOf(index), Integer.valueOf(n.intValue() + 1));
		}
	}

	// Add connection that is unchoked.
	public void addConnection(PeerConnection connection) {
		System.out.println("PiecePicker: add a new connection : " + connection.getConnectionId() + ".");
		this.connections.add(connection);

		String infoHash = connection.getSelf().getInfoHash();
		List<BatchRequest> requests = this.batchRequests.get(infoHash);
		if (requests == null) {
			requests = new ArrayList<BatchRequest>();
			this.batchRequests.put(infoHash, requests);
		}

		Bitmap peerBitfield = connection.getPeer().getBitfield();
		Map<Integer, Integer> rarestFirst = this.rarestFirsts.get(infoHash);
		if (rarestFirst == null) {
			rarestFirst = new HashMap<Integer, Integer>();
			this.rarestFirsts.put(infoHash, rarestFirst);
		}
		for (int i = 0; i < peerBitfield.length(); i++) {
			if (peerBitfield.get(i)) {
				Integer n = rarestFirst.get(Integer.valueOf(i));
				if (n == null) {
					rarestFirst.put(Integer.valueOf(i), Integer.valueOf(1));
				} else {
					rarestFirst.put(Integer.valueOf(i), Integer.valueOf(n.intValue() + 1));
				}
			}
		}
	}

	// Remove connection that is choked or closed.
	public void removeConnection(PeerConnection connection) {
		System.out.println("PiecePicker: removed a connection : " + connection.getConnectionId() + ".");
		// TODO: Override the equals of PeerConnection then use API List.remove to remove the connection.
		Iterator<PeerConnection> iterator = this.connections.iterator();
		while (iterator.hasNext()) {
			PeerConnection conn = iterator.next();
			if (conn.getConnectionId() == connection.getConnectionId()) {
				iterator.remove();
				break;
			}
		}

		String infoHash = connection.getSelf().getInfoHash();
		Bitmap peerBitfield = connection.getPeer().getBitfield();
		Map<Integer, Integer> rarestFirst = this.rarestFirsts.get(infoHash);
		for (int i = 0; i < peerBitfield.length(); i++) {
			if (peerBitfield.get(i)) {
				// If the map previously contained a mapping for the key, the old value is replaced by the specified value. 
				// (A map m is said to contain a mapping for a key k if and only if m.containsKey(k) would return true.)
				rarestFirst.put(Integer.valueOf(i), Integer.valueOf(rarestFirst.get(Integer.valueOf(i)).intValue() - 1));
			}
		}
		
		this.removeBatchRequest(infoHash, connection.getConnectionId());
	}

	private BatchRequest getBatchRequestInProgress(String infoHash, String connectionId) {
		List<BatchRequest> requests = this.batchRequests.get(infoHash);
		for (BatchRequest request : requests) {
			if (request.getConnectionId() == connectionId) {
				return request;
			}
		}
		return null;
	}

	public boolean isBatchRequestInProgress(String infoHash, String connectionId) {
		BatchRequest request = this.getBatchRequestInProgress(infoHash, connectionId);
		if(request == null)
			return false;
		return true;
	}
	
	// Strategy:
	// PercentCompleted:
	//   < 30% Random
	//   < 90% Rarest First
	//   < 100% End Game

	// It's called in these situation:
	// 1. Received UnchokeMessage
	// 2. Received HaveMessage
	// 3. Received PieceMessage and previous batch is completed
	// 4. 
	public void requestMoreSlices(PeerConnection connection) {
		System.out.println("PiecePicker: start to work.");
		String infoHash = connection.getSelf().getInfoHash();
		String connectionId = connection.getConnectionId();
		
		FileMetadata metadata = this.downloadManager.getDownloadTask(infoHash).getFileMetadata();
		if(metadata.isAllPiecesCompleted())
			return;
		float percentCompleted = this.downloadManager.getPercentCompleted(infoHash);
		// Check whether I am requesting any piece.
		BatchRequest request = getBatchRequestInProgress(infoHash, connectionId);

		// I have not requested any piece, create a new batch request.
		if (request == null) {
			Bitmap selfBitfield = this.downloadManager.getBitfield(infoHash);
			Bitmap peerBitfield = connection.getPeer().getBitfield();

			List<Integer> indexes = new ArrayList<Integer>();
			// Find the pieces that peer has but I do not.
			for (int i = 0; i < peerBitfield.length(); i++) {
				if (peerBitfield.get(i) && !selfBitfield.get(i)) {
					indexes.add(Integer.valueOf(i));
				}
			}
			if (indexes.isEmpty())
				return;

			int index = -1;
			// Random
			if (percentCompleted < 0.3) {
				index = indexes.get(random.nextInt(indexes.size()));
			} else if (percentCompleted < 0.9) { // Rarest First
				Map<Integer, Integer> rarestFirst = this.rarestFirsts.get(infoHash);
				Comparator<Map.Entry<Integer, Integer>> comparator = new Comparator<Map.Entry<Integer, Integer>>() {
					@Override
					public int compare(Entry<Integer, Integer> p, Entry<Integer, Integer> k) {
						return p.getValue().intValue() - k.getValue().intValue();
					}
				};

				List<Map.Entry<Integer, Integer>> entries = new ArrayList<Map.Entry<Integer, Integer>>(rarestFirst.entrySet());
				Collections.sort(entries, comparator);
				for (Map.Entry<Integer, Integer> entry : entries) {
					Integer k = entry.getKey();
					if (indexes.contains(k)) {
						index = k.intValue();
						break;
					}
				}
			} else { /* < 1 , sequential */
				index = indexes.get(0);
			}
			
			System.out.println("PiecePicker: picked a new piece to request, index = " + index + ".");
			// Finally find a index to request.
			request = new BatchRequest();
			request.setConnectionId(connectionId);
			request.setIndex(index);
			this.batchRequests.get(infoHash).add(request);
		}
		
		System.out.println("PiecePicker: next index to request -> " + request.getIndex() + ".");
		
		// Now 
		// request is a new index 
		// or a request(from unchoking message) that has completed a batch(received piece)
		// or received have message.		
		List<Slice> slices = metadata.getNextBatchIncompletedSlices(request.getIndex(), PiecePicker.BATCH_REQUEST_SIZE);
		
		if(slices.size() != 0) {
			if(percentCompleted > 0.9) {
				// TODO: Implement the 'End Game' mode.
			}
			// If it's a new request, set the fields.
			// If one batch is completed, reset the fields.
			request.setReceived(0);
			request.setRequested(slices.size());
			List<PeerMessage> messages = new ArrayList<PeerMessage>();
			for (Slice slice : slices) {
				messages.add(new PeerMessage.RequestMessage(slice.getIndex(), slice.getBegin(), slice.getLength()));
			}
			System.out.println("Batch RequestMessage size : " + slices.size() + ".");
			connection.addMessageToSend(messages);
		}else {
			// This pieces have been completed, try to find next one to download.
			this.removeBatchRequest(infoHash, connectionId);
			System.out.println("One batch requet is done, proceed to request more.");
			requestMoreSlices(connection);
		}
	}

	public void removeBatchRequest(String infoHash, String connectionId) {
		List<BatchRequest> requests = this.batchRequests.get(infoHash);
		Iterator<BatchRequest> iterator = requests.iterator();
		while(iterator.hasNext()) {
			BatchRequest request = iterator.next();
			if(request.getConnectionId() == connectionId) {
				iterator.remove();
				return;
			}
		}
	}

	private class BatchRequest {
		private String connectionId;
		private int index;
		private int requested;
		private int received;

		public int getIndex() {
			return index;
		}

		public void setIndex(int index) {
			this.index = index;
		}

		public String getConnectionId() {
			return connectionId;
		}

		public void setConnectionId(String connectionId) {
			this.connectionId = connectionId;
		}

		public int getRequested() {
			return requested;
		}

		public void setRequested(int requested) {
			this.requested = requested;
		}

		public int getReceived() {
			return received;
		}

		public void setReceived(int received) {
			this.received = received;
		}

		public boolean isCompleted() {
			return this.received == this.requested;
		}
	}
}
