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
	
	private int pieceIndexDownloading = -1;
	private int requestMessageSent;
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
			HandshakeMessage message = this.handshakeHandler.readMessage();
			if (null == message)
				return;
			
			String infoHash = message.getInfoHashString();
			
			this.peer = new Peer();
			this.peer.setAddress((InetSocketAddress) this.socketChannel.socket().getRemoteSocketAddress());
			this.peer.setInfoHash(infoHash);
			this.peer.setPeerId(message.getPeerIdString());
			
			//TODO: DownloadManager should not appear here?
			this.self.setInfoHash(infoHash);
			this.state = State.IN_HANDSHAKE_MESSAGE_RECEIVED;
			System.out.println("Status : " + this.state + ", received handshake message [" + message + "].");			
		}
		
		if (State.IN_HANDSHAKE_MESSAGE_RECEIVED == this.state) {
			writeMessage();
	    }
		
		if (State.IN_HANDSHAKE_MESSAGE_SENT == this.state) {
			System.out.println("Status : " + this.state + ", read bitfield message.");
			PeerMessage message = this.peerMessageHandler.readMessage();
			if (null == message)
				return;
			if (message instanceof PeerMessage.BitfieldMessage) {
				BitSet peerBitfield = ((PeerMessage.BitfieldMessage)message).getBitfield();
				// TODO: Set the bitfield of this.peer.
				this.peer.setBitfield(peerBitfield);
				
				BitSet selfBitfield = this.downloadManager.getBitfield(this.self.getInfoHash());
				this.interesting = PeerConnection.isInterested(selfBitfield, peerBitfield);
				
				this.state = State.IN_BITFIELD_RECEIVED;
				System.out.println("Status : " + this.state + ", received bitfield message [" + message + "].");
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
			HandshakeMessage message = handshakeHandler.readMessage();
			if (null == message)
				return;
			this.peer.setPeerId(message.getPeerIdString());
			this.peer.setInfoHash(message.getInfoHashString());
			this.state = State.OUT_HANDSHAKE_MESSAGE_RECEIVED;
		    System.out.println("Status : " + this.state + ", received handshake message [" + message + "].");
		}
		
		if(State.OUT_HANDSHAKE_MESSAGE_RECEIVED == this.state) {
			writeMessage();
		}
		//TODO: XXXX
		//If it's a outgoing connection, this status is not enough,
		//because the peer may not send back the bitfield yet.
		if(State.OUT_BITFIELD_SENT == this.state) {
			System.out.println("Status : " + this.state + ", reading bitfield message.");
			PeerMessage message = this.peerMessageHandler.readMessage();
			if (null == message)
				return;
			if (message instanceof PeerMessage.BitfieldMessage) {
				// TODO: Set the bitfield of this.peer.
				BitSet peerBitfield = ((PeerMessage.BitfieldMessage)message).getBitfield();
				this.peer.setBitfield(peerBitfield);
				this.interesting = PeerConnection.isInterested(this.downloadManager.getBitfield(self.getInfoHash()), peerBitfield);
				
				this.state = State.OUT_EXCHANGE_BITFIELD_COMPLETED;
				this.connectionManager.addPeerConnection(this.peer.getInfoHash(), this);

				System.out.println("Status : " + this.state + ", completed reading bitfield message : " + message + ".");
			    if(this.interesting) {
				    PeerMessage.InterestedMessage interestedMessage = new PeerMessage.InterestedMessage();
				    this.addMessageToSend(interestedMessage);
			    }
			    // Send the messages that are put in queue during handshaking and bitfield exchanging.
				startSendMessagesInQueue();
			}
		}

		if(State.IN_EXCHANGE_BITFIELD_COMPLETED == this.state || State.OUT_EXCHANGE_BITFIELD_COMPLETED == this.state) {
			System.out.println("Status : " + this.state + ", reading message.");
			PeerMessage message = this.peerMessageHandler.readMessage();
			if (null == message)
				return;
			
			this.timeLastReadMessage = System.currentTimeMillis();
			
            if(message instanceof KeepAliveMessage) {
			}else if(message instanceof ChokeMessage) {
				this.choked = true;
				// TODO: Lot of things to do.
				Iterator<PeerMessage> it = this.messagesToSend.iterator();
				while(it.hasNext()) {
					PeerMessage msg = it.next();
					if(msg instanceof RequestMessage) {
						it.remove();
					}
				}
				
				
			}else if(message instanceof UnchokeMessage) {
				this.choked = false;
				if(this.interesting) {
				    this.downloadManager.downloadMoreSlices(this.self.getInfoHash(), this);
				}
			}else if(message instanceof InterestedMessage) {
				this.interested = true;
				// TODO: If peer is interested in me, I will unchoke him?
			}else if(message instanceof NotInterestedMessage) {
				this.interested = false;
				// TODO: Cancel the response from the peer.
				
				Iterator<PeerMessage> it = this.messagesToSend.iterator();
				while(it.hasNext()) {
					PeerMessage msg = it.next();
					if(msg instanceof PieceMessage) {
						it.remove();
					}
				}
			}else if(message instanceof HaveMessage) {
				HaveMessage haveMessage = (HaveMessage)message;
				this.peer.getBitfield().set(haveMessage.getPieceIndex());
				
				if(!this.interesting) {
					boolean interested = PeerConnection.isInterested(this.downloadManager.getBitfield(self.getInfoHash()), this.peer.getBitfield());
					this.interesting = interested;
					if(interested) {
					    PeerMessage.InterestedMessage interestedMessage = new PeerMessage.InterestedMessage();
					    this.addMessageToSend(interestedMessage);
					}
				}
				
				// TODO: XXXX
				if(!this.choked && this.interesting) {
				    this.downloadManager.downloadMoreSlices(self.getInfoHash(), this);
				}
			}else if(message instanceof BitfieldMessage) {
				// In this status, client should not send/receive bitfield message.
			}else if(message instanceof RequestMessage) {
				RequestMessage request = (RequestMessage)message;
				Slice slice = new Slice(request.getIndex(), request.getBegin(), request.getLength());
				ByteBuffer data = this.downloadManager.readSlice(self.getInfoHash(), slice);
				PieceMessage pieceMessage = new PieceMessage(slice.getIndex(), slice.getBegin(), data);
				
				addMessageToSend(pieceMessage);
			}else if(message instanceof PieceMessage) {
				PieceMessage piece = (PieceMessage)message;
				// Is it OK to use .limit() to get the length of the ByteBuffer?
				this.downloadManager.writeSlice(self.getInfoHash(), piece.getIndex(), piece.getBegin(), piece.getBlock().remaining(), piece.getBlock());
				this.pieceMessageReceived++;
				if(this.pieceMessageReceived == this.requestMessageSent) {
					this.pieceMessageReceived = 0;
					this.requestMessageSent = 0;
				    this.downloadManager.downloadMoreSlices(self.getInfoHash(), this);
				}
			}else if(message instanceof CancelMessage) {
				CancelMessage cancelMessage = (CancelMessage)message;
				// TODO: Check the queue of received messages and the queue of outgoing messages, if it's there remove it from the queue.
				Iterator<PeerMessage> it = this.messagesToSend.iterator();
				while(it.hasNext()) {
					PeerMessage msg = it.next();
					if(message instanceof PieceMessage) {
						PieceMessage pieceMessage = (PieceMessage)message;
						if((pieceMessage.getIndex() == cancelMessage.getIndex()) && (pieceMessage.getBegin() == cancelMessage.getBegin())) {
							it.remove();
						}
					}
				}
				
			}else if(message instanceof PortMessage) {

			}
		}
	}

	public void writeMessage() throws Exception {
		if (State.IN_HANDSHAKE_MESSAGE_RECEIVED == this.state) {
			if(!this.handshakeHandler.isSendingInProgress()) {
				HandshakeMessage handshakeMessage = new HandshakeMessage(Utils.hexStringToBytes(self.getInfoHash()), self.getPeerId().getBytes());
				System.out.println("Status : " + this.state + ", writing handshake message to peer [" + handshakeMessage + "].");
		        handshakeHandler.setMessageToSend(handshakeMessage);
			}else {
				System.out.println("Status : " + this.state + ", writing remaining part of handshake message to peer.");
			}
			
		    handshakeHandler.writeMessage();
			if(handshakeHandler.isSendingInProgress()) {
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
			if(!peerMessageHandler.isSendingInProgress()) {
				BitSet bitfield  = this.downloadManager.getBitfield(self.getInfoHash());
				System.out.println("Status : " + this.state + ", writing bitfield message to peer [" + bitfield + "].");
				BitfieldMessage bitfieldMessage = new PeerMessage.BitfieldMessage(bitfield);
				peerMessageHandler.setMessageToSend(bitfieldMessage);
			}else {
				System.out.println("Status : " + this.state + ", writing remaining part of bitfield message to peer.");
			}
			peerMessageHandler.writeMessage();
			if(peerMessageHandler.isSendingInProgress()) {
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
			    if(this.interesting) {
				    PeerMessage.InterestedMessage interestedMessage = new PeerMessage.InterestedMessage();
				    this.addMessageToSend(interestedMessage);
			    }
			    // Send the messages that are put in queue during handshaking and bitfield exchanging.
			    startSendMessagesInQueue();
			}
		}
		
		if (State.OUT_CONNECTED == this.state) {
			//TODO: Use a while to ensure the message is totally written to peer.
			if(!handshakeHandler.isSendingInProgress()) {
				HandshakeMessage handshakeMessage = new HandshakeMessage(Utils.hexStringToBytes(self.getInfoHash()), self.getPeerId().getBytes());
				System.out.println("Status : " + this.state + ", writing handshake message to peer [" + handshakeMessage + "].");
		        handshakeHandler.setMessageToSend(handshakeMessage);
			}else {
				System.out.println("Status : " + this.state + ", writing remaining part of handshake message to peer.");
			}
		    handshakeHandler.writeMessage();
			if(handshakeHandler.isSendingInProgress()) {
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
			if(!peerMessageHandler.isSendingInProgress()) {
				BitSet bitfield = this.downloadManager.getBitfield(self.getInfoHash());
				System.out.println("Status : " + this.state + ", writing bitfield message to peer [" + bitfield + "].");
				BitfieldMessage bitfieldMessage = new PeerMessage.BitfieldMessage(bitfield);
				peerMessageHandler.setMessageToSend(bitfieldMessage);
			}else {
				System.out.println("Status : " + this.state + ", writing remaining part of bitfield message to peer.");
			}
			
			peerMessageHandler.writeMessage();
			if(peerMessageHandler.isSendingInProgress()) {
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
			if(!peerMessageHandler.isSendingInProgress()) { // Nothing sent yet or completed sending a message.
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
			if(peerMessageHandler.isSendingInProgress()) {
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

	public int getPieceIndexDownloading() {
		return pieceIndexDownloading;
	}

	public void setPieceIndexDownloading(int pieceIndexDownloading) {
		this.pieceIndexDownloading = pieceIndexDownloading;
	}

	public int getRequestMessageSent() {
		return requestMessageSent;
	}

	public void setRequestMessageSent(int requestMessageSent) {
		this.requestMessageSent = requestMessageSent;
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
	
	public void checkAliveKeepAlive() throws Exception {
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
