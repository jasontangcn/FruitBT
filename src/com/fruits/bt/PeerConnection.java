package com.fruits.bt;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import com.fruits.bt.PeerMessage.BitfieldMessage;
import com.fruits.bt.PeerMessage.CancelMessage;
import com.fruits.bt.PeerMessage.ChokeMessage;
import com.fruits.bt.PeerMessage.HaveMessage;
import com.fruits.bt.PeerMessage.InterestedMessage;
import com.fruits.bt.PeerMessage.KeepAliveMessage;
import com.fruits.bt.PeerMessage.NotInterestedMessage;
import com.fruits.bt.PeerMessage.PieceMessage;
import com.fruits.bt.PeerMessage.PortMessage;
import com.fruits.bt.PeerMessage.RequestMessage;
import com.fruits.bt.PeerMessage.UnchokeMessage;

public class PeerConnection {
	public enum State {
		UNDEFINED(-1), 
		OUT_CONNECTED(0), OUT_HANDSHAKE_MESSAGE_SENT(1), OUT_HANDSHAKE_MESSAGE_RECEIVED(2), OUT_BITFIELD_SENT(3), OUT_EXCHANGE_BITFIELD_COMPLETED(4),
		IN_ACCEPTED(5), IN_HANDSHAKE_MESSAGE_RECEIVED(6), IN_HANDSHAKE_MESSAGE_SENT(7), IN_BITFIELD_RECEIVED(8), IN_EXCHANGE_BITFIELD_COMPLETED(9);

		private int stateId;

		State(int stateId) {
			this.stateId = stateId;
		}
	}

	private State state = State.UNDEFINED;
	
	private final SocketChannel socketChannel;
	private final PeerConnectionManager connectionManager;
	private final DownloadManager downloadManager;
	private final HandshakeHandler handshakeHandler;
	private final PeerMessageHandler peerMessageHandler;
	
	private Peer self;
	private Peer peer;
	private final boolean isOutgoingConnect;

	// Initial status.
	private boolean choking = true;
	private boolean interesting = false;
	private boolean choked = true;
	private boolean interested = false;

	private ArrayBlockingQueue<PeerMessage> messagesToSend = new ArrayBlockingQueue<PeerMessage>(1024, true);
	
	private long timeLastReadMessage;
	private long timeLastWriteMessage;
	
	private int indexPieceDownloading = -1;
	private int requestMessagesSent;
	private int pieceMessageReceived;
	
	public PeerConnection(boolean isOutgoingConnect, SocketChannel socketChannel, PeerConnectionManager connectionManager, DownloadManager downloadManager) {
		this.isOutgoingConnect = isOutgoingConnect;
		this.socketChannel = socketChannel;
		this.connectionManager = connectionManager;
		this.downloadManager = downloadManager;
		this.handshakeHandler = new HandshakeHandler(socketChannel);
		this.peerMessageHandler = new PeerMessageHandler(socketChannel);
	}

	public void readMessage() throws Exception {
		// ACCEPTED -> It's an incoming connection.
		if (State.IN_ACCEPTED == this.state) {
			System.out.println("Status : " + this.state + ", reading handshake message.");
			HandshakeMessage handshakeMessage = this.handshakeHandler.readMessage();
			if (null == handshakeMessage)
				return;
			
			String infoHash = handshakeMessage.getInfoHashString();
			
			this.peer = new Peer();
			this.peer.setAddress((InetSocketAddress) this.socketChannel.socket().getRemoteSocketAddress());
			this.peer.setInfoHash(infoHash);
			this.peer.setPeerId(handshakeMessage.getPeerIdString());
			
			//TODO: DownloadManager should not appear here?
			this.self.setInfoHash(infoHash);
			this.state = State.IN_HANDSHAKE_MESSAGE_RECEIVED;
			System.out.println("Status : " + this.state + ", received handshake message [" + handshakeMessage + "].");			
		}
		
		if (State.IN_HANDSHAKE_MESSAGE_RECEIVED == this.state) {
			writeMessage();
	    }
		
		if (State.IN_HANDSHAKE_MESSAGE_SENT == this.state) {
			System.out.println("Status : " + this.state + ", read bitfield message.");
			PeerMessage peerMessage = this.peerMessageHandler.readMessage();
			if (null == peerMessage)
				return;
			if (peerMessage instanceof PeerMessage.BitfieldMessage) {
				BitSet peerBitfield = ((PeerMessage.BitfieldMessage)peerMessage).getBitfield();
				// TODO: Set the bitfield of this.peer.
				this.peer.setBitfield(peerBitfield);
				
				BitSet selfBitfield = this.downloadManager.getBitfield(this.self.getInfoHash());
				this.interested = PeerConnection.isInterested(selfBitfield, peerBitfield);
				
				this.state = State.IN_BITFIELD_RECEIVED;
				System.out.println("Status : " + this.state + ", received bitfield message [" + peerMessage + "].");
			}
		}
		
		if (State.IN_BITFIELD_RECEIVED == this.state) {
			writeMessage();
		}
		
		if (State.OUT_CONNECTED == this.state) {
			writeMessage();
		}
		
		// It must be an outgoing connection.
		if (State.OUT_HANDSHAKE_MESSAGE_SENT == this.state) {
			System.out.println("Status : " + this.state + ", reading handshake message.");
			HandshakeMessage handshakeMessage = handshakeHandler.readMessage();
			if (null == handshakeMessage)
				return;
			this.peer.setPeerId(handshakeMessage.getPeerIdString());
			this.peer.setInfoHash(handshakeMessage.getInfoHashString());
			this.state = State.OUT_HANDSHAKE_MESSAGE_RECEIVED;
		    System.out.println("Status : " + this.state + ", received handshake message [" + handshakeMessage + "].");
		}
		
		if(State.OUT_HANDSHAKE_MESSAGE_RECEIVED == this.state) {
			writeMessage();
		}
		//TODO: XXXX
		//If it's a outgoing connection, this status is not enough,
		//because the peer may not send back the bitfield yet.
		if(State.OUT_BITFIELD_SENT == this.state) {
			System.out.println("Status : " + this.state + ", reading bitfield message.");
			PeerMessage peerMessage = this.peerMessageHandler.readMessage();
			if (null == peerMessage)
				return;
			if (peerMessage instanceof PeerMessage.BitfieldMessage) {
				// TODO: Set the bitfield of this.peer.
				BitSet peerBitfield = ((PeerMessage.BitfieldMessage)peerMessage).getBitfield();
				this.peer.setBitfield(peerBitfield);
				this.interested = PeerConnection.isInterested(this.downloadManager.getBitfield(self.getInfoHash()), peerBitfield);
				
				this.state = State.OUT_EXCHANGE_BITFIELD_COMPLETED;
				this.connectionManager.addPeerConnection(this.peer.getInfoHash(), this);

				System.out.println("Status : " + this.state + ", completed reading bitfield message : " + peerMessage + ".");
			    if(this.interested) {
				    PeerMessage.InterestedMessage interestedMessage = new PeerMessage.InterestedMessage();
				    this.addMessageToSend(interestedMessage);
			    }
			    // Send the messages that are put in queue during handshaking and bitfield exchanging.
				startSendMessagesInQueue();
			}
		}

		if(State.IN_EXCHANGE_BITFIELD_COMPLETED == this.state || State.OUT_EXCHANGE_BITFIELD_COMPLETED == this.state) {
			System.out.println("Status : " + this.state + ", reading message.");
			PeerMessage peerMessage = this.peerMessageHandler.readMessage();
			if (null == peerMessage)
				return;
			
			this.timeLastReadMessage = System.currentTimeMillis();
			
            if(peerMessage instanceof KeepAliveMessage) {
			}else if(peerMessage instanceof ChokeMessage) {
				this.choked = true;
			}else if(peerMessage instanceof UnchokeMessage) {
				this.choked = false;
				// TODO:
				this.downloadManager.downloadMoreSlices(this.self.getInfoHash(), this);
			}else if(peerMessage instanceof InterestedMessage) {
				this.interested = true;
			}else if(peerMessage instanceof NotInterestedMessage) {
				this.interested = false;
			}else if(peerMessage instanceof HaveMessage) {
				HaveMessage haveMessage = (HaveMessage)peerMessage;
				this.peer.getBitfield().set(haveMessage.getPieceIndex());
				// TODO: XXXX
				if(!this.choked) {
				    this.downloadManager.downloadMoreSlices(self.getInfoHash(), this);
				}
			}else if(peerMessage instanceof BitfieldMessage) {
				// In this status, client should not send/receive bitfield message.
			}else if(peerMessage instanceof RequestMessage) {
				RequestMessage request = (RequestMessage)peerMessage;
				Slice slice = new Slice(request.getIndex(), request.getBegin(), request.getLength());
				ByteBuffer data = this.downloadManager.readSlice(self.getInfoHash(), slice);
				PieceMessage pieceMessage = new PieceMessage(slice.getIndex(), slice.getBegin(), data);
				
				addMessageToSend(pieceMessage);
			}else if(peerMessage instanceof PieceMessage) {
				PieceMessage piece = (PieceMessage)peerMessage;
				// Is it OK to use .limit() to get the length of the ByteBuffer?
				this.downloadManager.writeSlice(self.getInfoHash(), piece.getIndex(), piece.getBegin(), piece.getBlock().remaining(), piece.getBlock());
				this.pieceMessageReceived++;
				if(this.pieceMessageReceived == this.requestMessagesSent) {
					this.pieceMessageReceived = 0;
					this.requestMessagesSent = 0;
				    this.downloadManager.downloadMoreSlices(self.getInfoHash(), this);
				}
			}else if(peerMessage instanceof CancelMessage) {
				CancelMessage cancelMessage = (CancelMessage)peerMessage;
				// TODO: Check the queue of received messages and the queue of outgoing messages, if it's there remove it from the queue.
				Iterator<PeerMessage> it = this.messagesToSend.iterator();
				while(it.hasNext()) {
					PeerMessage message = it.next();
					if(message instanceof PieceMessage) {
						PieceMessage pieceMessage = (PieceMessage)message;
						if((pieceMessage.getIndex() == cancelMessage.getIndex()) && (pieceMessage.getBegin() == cancelMessage.getBegin())) {
							it.remove();
						}
					}
				}
				
			}else if(peerMessage instanceof PortMessage) {

			}
		}
	}

	public void writeMessage() throws Exception {
		if (State.IN_HANDSHAKE_MESSAGE_RECEIVED == this.state) {
			if(!this.handshakeHandler.isSendingMessageInProgress()) {
				HandshakeMessage handshakeMessage = new HandshakeMessage(Utils.hexStringToBytes(self.getInfoHash()), self.getPeerId().getBytes());
				System.out.println("Status : " + this.state + ", writing handshake message to peer [" + handshakeMessage + "].");
		        handshakeHandler.setHandshakeMessageToSend(handshakeMessage);
			}else {
				System.out.println("Status : " + this.state + ", writing remaining part of handshake message to peer.");
			}
			
		    handshakeHandler.writeMessage();
			if(handshakeHandler.isSendingMessageInProgress()) {
				System.out.println("Status : " + this.state + ", only partial handshake message was written to peer.");
				//OP_READ is unregistered.
				this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE, this);
			}else {
				  this.state = State.IN_HANDSHAKE_MESSAGE_SENT;
				  this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
				  System.out.println("Status : " + this.state + ", completed writing handshake message to peer.");
			}
	    }
		
		if (State.IN_BITFIELD_RECEIVED == this.state) {
			if(!peerMessageHandler.isSendingMessageInProgress()) {
				BitSet bitfield  = this.downloadManager.getBitfield(self.getInfoHash());
				System.out.println("Status : " + this.state + ", writing bitfield message to peer [" + bitfield + "].");
				BitfieldMessage bitfieldMessage = new PeerMessage.BitfieldMessage(bitfield);
				peerMessageHandler.setMessageToSend(bitfieldMessage);
			}else {
				System.out.println("Status : " + this.state + ", writing remaining part of bitfield message to peer.");
			}
			peerMessageHandler.writeMessage();
			if(peerMessageHandler.isSendingMessageInProgress()) {
				System.out.println("Status : " + this.state + ", only partial bitfield message was written to peer.");
				// OP_READ is unregistered.
				this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE, this);
			}else {
			    // It should be incoming connection.
			    this.state = State.IN_EXCHANGE_BITFIELD_COMPLETED;
				this.connectionManager.addPeerConnection(this.peer.getInfoHash(), this);
			    //this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
			    System.out.println("Status : " + this.state + ", completed writing bitfield message to peer."); 
			    
			    // TODO: Shall we put this message in the head of the queue?
			    if(this.interested) {
				    PeerMessage.InterestedMessage interestedMessage = new PeerMessage.InterestedMessage();
				    this.addMessageToSend(interestedMessage);
			    }
			    // Send the messages that are put in queue during handshaking and bitfield exchanging.
			    startSendMessagesInQueue();
			}
		}
		
		if (State.OUT_CONNECTED == this.state) {
			//TODO: Use a while to ensure the message is totally written to peer.
			if(!handshakeHandler.isSendingMessageInProgress()) {
				HandshakeMessage handshakeMessage = new HandshakeMessage(Utils.hexStringToBytes(self.getInfoHash()), self.getPeerId().getBytes());
				System.out.println("Status : " + this.state + ", writing handshake message to peer [" + handshakeMessage + "].");
		        handshakeHandler.setHandshakeMessageToSend(handshakeMessage);
			}else {
				System.out.println("Status : " + this.state + ", writing remaining part of handshake message to peer.");
			}
		    handshakeHandler.writeMessage();
			if(handshakeHandler.isSendingMessageInProgress()) {
				// OP_READ is unregistered.
				System.out.println("Status : " + this.state + ", only partial handshake message was written to peer.");
				this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE, this);
			}else {
				  this.state = State.OUT_HANDSHAKE_MESSAGE_SENT;
				  this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
				  System.out.println("Status : " + this.state + ", completed writing handshake message to peer.");
			}
		}
		
		if(State.OUT_HANDSHAKE_MESSAGE_RECEIVED == this.state) {
			if(!peerMessageHandler.isSendingMessageInProgress()) {
				BitSet bitfield = this.downloadManager.getBitfield(self.getInfoHash());
				System.out.println("Status : " + this.state + ", writing bitfield message to peer [" + bitfield + "].");
				BitfieldMessage bitfieldMessage = new PeerMessage.BitfieldMessage(bitfield);
				peerMessageHandler.setMessageToSend(bitfieldMessage);
			}else {
				System.out.println("Status : " + this.state + ", writing remaining part of bitfield message to peer.");
			}
			
			peerMessageHandler.writeMessage();
			if(peerMessageHandler.isSendingMessageInProgress()) {
				// OP_READ is unregistered.
				System.out.println("Status : " + this.state + ", only partial bitfield message was written to peer.");
				this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE, this);
			}else {
				this.state = State.OUT_BITFIELD_SENT;			  
			    this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
			    System.out.println("Status : " + this.state + ", completed writing bitfield message to peer.");
			}
		}
		
		if (State.IN_EXCHANGE_BITFIELD_COMPLETED == this.state || State.OUT_EXCHANGE_BITFIELD_COMPLETED == this.state) {
			this.startSendMessagesInQueue();
		}
	}
	
	public void startSendMessagesInQueue() throws Exception {		
		System.out.println("Status : " + this.state + ", sending messages in queue.");
		
		for(;;) {
			if(!peerMessageHandler.isSendingMessageInProgress()) { // Nothing sent yet or completed sending a message.
			    PeerMessage message = this.messagesToSend.poll();
			    if(null == message) {
			    	this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
					System.out.println("Status : " + this.state + ", no message in the queue, unregistered OP_WRITE for this channel.");
			    	break;
			    }
				System.out.println("Got a message from queue.");
				peerMessageHandler.setMessageToSend(message);
			}else {
				System.out.println("Status : " + this.state + ", continue to send remaining part of current message.");
			}
			peerMessageHandler.writeMessage();
			if(peerMessageHandler.isSendingMessageInProgress()) {
				System.out.println("Status : " + this.state + ", only partialessage was written to peer.");
				this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE | SelectionKey.OP_READ, this);
				break;
			}else {
				this.timeLastWriteMessage = System.currentTimeMillis();
				System.out.println("Status : " + this.state + ", completed writing a message to peer.");
			}
		}
	}
	
	public void addMessageToSend(PeerMessage peerMessage) throws Exception {
		// TODO: Handling exception.
		this.messagesToSend.put(peerMessage);
		System.out.println("Status : " + this.state + ", an outgoing message was added to queue : " + peerMessage + ".");
		
		if (State.IN_EXCHANGE_BITFIELD_COMPLETED == this.state || State.OUT_EXCHANGE_BITFIELD_COMPLETED == this.state) {
			this.startSendMessagesInQueue();
		}
	}
	
	public void addMessageToSend(List<PeerMessage> peerMessages) throws Exception {
		// TODO: Handling exception.
		for(PeerMessage peerMessage : peerMessages) {
		    this.messagesToSend.put(peerMessage);
		}
		System.out.println("Status : " + this.state + ", outgoing messages were added to queue : " + peerMessages + ".");
		
		if (State.IN_EXCHANGE_BITFIELD_COMPLETED == this.state || State.OUT_EXCHANGE_BITFIELD_COMPLETED == this.state) {
			this.startSendMessagesInQueue();
		}
	}
	
	public State getState() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
	}

	public Peer getSelf() {
		return self;
	}

	public void setSelf(Peer self) {
		this.self = self;
	}

	public Peer getPeer() {
		return peer;
	}

	public void setPeer(Peer peer) {
		this.peer = peer;
	}

	public boolean isOutgoingConnect() {
		return isOutgoingConnect;
	}

	public boolean isChoked() {
		return choked;
	}

	public void setChoked(boolean choked) {
		this.choked = choked;
	}

	public boolean isInterested() {
		return interested;
	}

	public void setInterested(boolean interested) {
		this.interested = interested;
	}

	public boolean isChoking() {
		return choking;
	}

	public void setChoking(boolean choking) {
		this.choking = choking;
	}

	public boolean isInteresting() {
		return interesting;
	}

	public void setInteresting(boolean interesting) {
		this.interesting = interesting;
	}

	public PeerMessageHandler getPeerMessageHandler() {
		return peerMessageHandler;
	}

	public int getIndexPieceDownloading() {
		return indexPieceDownloading;
	}

	public void setIndexPieceDownloading(int indexPieceDownloading) {
		this.indexPieceDownloading = indexPieceDownloading;
	}

	public int getRequestMessagesSent() {
		return requestMessagesSent;
	}

	public void setRequestMessagesSent(int requestMessagesSent) {
		this.requestMessagesSent = requestMessagesSent;
	}
	
	public static boolean isInterested(BitSet a, BitSet b) {
		// TODO: validate parameters.
		for(int i = 0; i< a.length(); i++) {
			if(!a.get(i) && b.get(i)) {
				return true;
			}
		}
		return false;
	}
	
	public void checkAliveAndKeepAlive() throws Exception {
		if (State.IN_EXCHANGE_BITFIELD_COMPLETED == this.state || State.OUT_EXCHANGE_BITFIELD_COMPLETED == this.state) {
			long now = System.currentTimeMillis();
			
			if(now - this.timeLastWriteMessage > 45 * 1000) {
				PeerMessage.KeepAliveMessage keepAliveMessage = new PeerMessage.KeepAliveMessage();
				this.addMessageToSend(keepAliveMessage);
			}
			
			if(now - this.timeLastReadMessage > 3 * 60 * 1000) {
				this.close();
			}
		}
	}
	
	public void close() throws Exception {
		
	}
}
