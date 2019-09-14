package com.fruits.bt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	static final Logger logger = LoggerFactory.getLogger(PeerConnection.class);
	
	// TODO:
	public enum State {
		UNDEFINED(-1), OUT_CONNECTED(0), OUT_HANDSHAKE_MESSAGE_SENT(1), OUT_HANDSHAKE_MESSAGE_RECEIVED(2), OUT_BITFIELD_SENT(3), OUT_EXCHANGE_BITFIELD_COMPLETED(4),
		IN_ACCEPTED(5), IN_HANDSHAKE_MESSAGE_RECEIVED(6), IN_HANDSHAKE_MESSAGE_SENT(7), IN_BITFIELD_RECEIVED(8), IN_EXCHANGE_BITFIELD_COMPLETED(9);

		private int stateId;

		State(int stateId) {
			this.stateId = stateId;
		}
	}

	private State state = State.UNDEFINED;
	private String connectionId;

	private final boolean isOutgoingConnection;
	private final SocketChannel socketChannel;
	private final PeerConnectionManager connectionManager;
	private final DownloadManager downloadManager;
	private final HandshakeHandler handshakeHandler;
	private final PeerMessageHandler messageHandler;

	private Peer self;
	private Peer peer;

	// Initial status.
	private volatile boolean choking = true; // choking peer
	private volatile boolean interesting = false; // interested in peer
	private volatile boolean choked = true; // choked by peer
	private volatile boolean interested = false; // peer interested in me

	private ArrayBlockingQueue<PeerMessage> messagesToSend = new ArrayBlockingQueue<PeerMessage>(1024, true);

	private volatile long timeLastRead = System.currentTimeMillis();
	private volatile long timeLastWrite = System.currentTimeMillis();

	// TODO: XXXX
	// Batch send/receive may not work well because of unexpected communication.

	public PeerConnection(boolean isOutgoingConnection, SocketChannel socketChannel, PeerConnectionManager connectionManager, DownloadManager downloadManager) {
		this.isOutgoingConnection = isOutgoingConnection;
		this.socketChannel = socketChannel;
		this.connectionManager = connectionManager;
		this.downloadManager = downloadManager;
		this.handshakeHandler = new HandshakeHandler(this);
		this.messageHandler = new PeerMessageHandler(this);
	}

	public void readMessage() {
		if (this.state == State.IN_ACCEPTED) {
			logger.debug("Status : {}, reading handshake message.", this.state);
			HandshakeMessage message = this.handshakeHandler.readMessage();
			if (message == null) // Did not get a complete HandshakeMessage.
				return;

			byte[] infoHash = message.getInfoHash();

			this.peer = new Peer();
			this.peer.setAddress((InetSocketAddress) this.socketChannel.socket().getRemoteSocketAddress());
			this.peer.setInfoHash(infoHash);
			this.peer.setPeerId(message.getPeerId());

			//TODO: DownloadManager should not appear here?
			this.self.setInfoHash(infoHash);
			this.state = State.IN_HANDSHAKE_MESSAGE_RECEIVED;
			logger.debug("Status : {}, received handshake message [{}].", this.state, message);
		}

		if (this.state == State.IN_HANDSHAKE_MESSAGE_RECEIVED) {
			writeMessage();
		}
		
		if (this.state == State.IN_HANDSHAKE_MESSAGE_SENT) {
			logger.debug("Status : {}, read bitfield message.", this.state);
			PeerMessage message = this.messageHandler.readMessage();
			if (message == null)
				return;
			if (message instanceof BitfieldMessage) {
				Bitmap peerBitfield = ((BitfieldMessage) message).getBitfield();

				this.peer.setBitfield(peerBitfield);

				Bitmap selfBitfield = this.downloadManager.getBitfield(this.self.getInfoHashString());
				// TODO: need to validate it before assigning the length?
				peerBitfield.setLength(selfBitfield.length());

				this.interesting = Helper.isInterested(selfBitfield, peerBitfield);

				this.state = State.IN_BITFIELD_RECEIVED;
				logger.debug("Status : {}, received bitfield message [{}].", this.state, message);
			}
		}
		
		if (this.state == State.IN_BITFIELD_RECEIVED) {
			writeMessage();
		}
		
		if (this.state == State.OUT_CONNECTED) {
			writeMessage();
		}

		if (this.state == State.OUT_HANDSHAKE_MESSAGE_SENT) {
			logger.debug("Status : {}, reading handshake message.", this.state);
			HandshakeMessage message = handshakeHandler.readMessage();
			if (message == null)
				return; // if reading handshake message is not finished, go on reading.
			this.peer.setPeerId(message.getPeerId());
			this.peer.setInfoHash(message.getInfoHash());
			this.state = State.OUT_HANDSHAKE_MESSAGE_RECEIVED;
			logger.debug("Status : {}, received handshake message [{}].", this.state, message);
		}
		
		if (this.state == State.OUT_HANDSHAKE_MESSAGE_RECEIVED) {
			writeMessage();
		}

		if (this.state == State.OUT_BITFIELD_SENT) {
			logger.debug("Status : {}, reading bitfield message.", this.state);
			PeerMessage message = this.messageHandler.readMessage();
			if (message == null)
				return;
			if (message instanceof BitfieldMessage) {
				Bitmap peerBitfield = ((BitfieldMessage) message).getBitfield();
				this.peer.setBitfield(peerBitfield);

				Bitmap selfBitfield = this.downloadManager.getBitfield(self.getInfoHashString());
				peerBitfield.setLength(selfBitfield.length()); // TODO: XXX

				this.interesting = Helper.isInterested(selfBitfield, peerBitfield);

				this.state = State.OUT_EXCHANGE_BITFIELD_COMPLETED;

				//this.connectionId = Utils.bytes2HexString(this.peer.getInfoHash()) + "-" + UUID.randomUUID().toString();
				this.connectionId = UUID.randomUUID().toString();
				this.connectionManager.addPeerConnection(this.peer.getInfoHashString(), this);

				logger.debug("Status : {}, completed reading bitfield message : {}.", this.state, message);
				logger.info("Completed handshake with peer.");
				
				if (this.interesting) {
					PeerMessage.InterestedMessage interestedMessage = new PeerMessage.InterestedMessage();
					this.addMessageToSend(interestedMessage);
				}
				// Send the messages that are put in queue during handshaking and bitfield exchanging.
				startSendMessages();
			}
		}

		//if (this.state == State.IN_EXCHANGE_BITFIELD_COMPLETED || this.state == State.OUT_EXCHANGE_BITFIELD_COMPLETED) {
		if (isHandshakeCompleted()) {
			//logger.trace("Status : " + this.state + ", reading message.");
			PeerMessage message = this.messageHandler.readMessage();
			if (message == null)
				return;

			this.timeLastRead = System.currentTimeMillis();

			logger.debug("PeerConnection->readMessage: Got a message [{}] from peer {}.", message, this.peer.getAddress());

			if (message instanceof KeepAliveMessage) {
			} else if (message instanceof ChokeMessage) {
				this.choked = true;
				// TODO: Lot of things to do.
				Iterator<PeerMessage> it = this.messagesToSend.iterator();
				while (it.hasNext()) {
					PeerMessage msg = it.next();
					if (msg instanceof RequestMessage) {
						it.remove();
					}
				}
				this.downloadManager.getPiecePicker().removeConnection(this);
			} else if (message instanceof UnchokeMessage) {
				this.choked = false;
				this.downloadManager.getPiecePicker().addConnection(this);
				logger.debug("PeerConnection->readMessage: Got a UnchokeMessage and this.interesting is : {}.", this.interesting);
				if (this.interesting) {
					this.downloadManager.getPiecePicker().requestMoreSlices(this);
				}
			} else if (message instanceof InterestedMessage) {
				this.interested = true;
				// TODO: If a peer is interested in me, I will unchoke him?
			} else if (message instanceof NotInterestedMessage) {
				this.interested = false;
				// TODO: Cancel the piece messages for the peer, any more to do?
				Iterator<PeerMessage> iterator = this.messagesToSend.iterator();
				while (iterator.hasNext()) {
					PeerMessage msg = iterator.next();
					if (msg instanceof PieceMessage) {
						iterator.remove();
					}
				}
			} else if (message instanceof HaveMessage) {
				HaveMessage haveMessage = (HaveMessage) message;
				this.peer.getBitfield().set(haveMessage.getPieceIndex());

				boolean interestedNow = false;
				if (!this.interesting) {
					boolean interested = Helper.isInterested(this.downloadManager.getBitfield(self.getInfoHashString()), this.peer.getBitfield());
					this.interesting = interested;
					if (interested) {
						interestedNow = true;
						InterestedMessage interestedMessage = new InterestedMessage();
						this.addMessageToSend(interestedMessage);
					}
				}

				this.downloadManager.getPiecePicker().peerHaveNewPiece(self.getInfoHashString(), haveMessage.getPieceIndex());
				// TODO: XXXX
				if (!this.choked && interestedNow) {
					// TODO: isBatchRequestInProgress?
					if (!this.downloadManager.getPiecePicker().isBatchRequestInProgress(self.getInfoHashString(), connectionId))
						this.downloadManager.getPiecePicker().requestMoreSlices(this);
				}
			} else if (message instanceof BitfieldMessage) {
				// In this status, client should not send/receive bitfield message.
				logger.warn("Status : {}, in this status client should not receive bitfield message.", this.state);
			} else if (message instanceof RequestMessage) {
				logger.trace("PeerConnection->readMessage: Got a RequestMessage {}.", message);

				RequestMessage request = (RequestMessage) message;
				Slice slice = new Slice(request.getIndex(), request.getBegin(), request.getLength());
				// TODO: data may null if failed to read slice.
				ByteBuffer data = this.downloadManager.readSlice(self.getInfoHashString(), slice);
				if (data != null) {
					PieceMessage pieceMessage = new PieceMessage(slice.getIndex(), slice.getBegin(), data);
					addMessageToSend(pieceMessage);
				}
			} else if (message instanceof PieceMessage) {
				PieceMessage piece = (PieceMessage) message;
				// TODO: Silent but the writing may fail.
				logger.trace("PeerConnection->readMessage: Got a PieceMessage {}.", message);
				this.downloadManager.writeSlice(self.getInfoHashString(), piece.getIndex(), piece.getBegin(), piece.getBlock().remaining(), piece.getBlock());
				this.downloadManager.getPiecePicker().sliceReceived(this);

			} else if (message instanceof CancelMessage) {
				CancelMessage cancelMessage = (CancelMessage) message;
				// TODO: Check the queue of received messages and the queue of outgoing messages, if it's there remove it from the queue.
				Iterator<PeerMessage> iterator = this.messagesToSend.iterator();
				while (iterator.hasNext()) {
					PeerMessage msg = iterator.next();
					if (msg instanceof PieceMessage) {
						PieceMessage pieceMessage = (PieceMessage) msg;
						if ((pieceMessage.getIndex() == cancelMessage.getIndex()) && (pieceMessage.getBegin() == cancelMessage.getBegin())) {
							iterator.remove();
						}
					}
				}
			} else if (message instanceof PortMessage) {

			}
		}
	}

	public void writeMessage() { // No exception is thrown, the exceptions are handled in where they appear.
		if (this.state == State.IN_HANDSHAKE_MESSAGE_RECEIVED) {
			if (!this.handshakeHandler.isSendingInProgress()) {
				HandshakeMessage handshakeMessage = new HandshakeMessage(self.getInfoHash(), self.getPeerId());
				logger.debug("Status : {}, writing handshake message to peer [{}].", this.state, handshakeMessage);
				handshakeHandler.setMessageToSend(handshakeMessage);
			} else {
				logger.debug("Status : {}, writing remaining part of handshake message to peer.", this.state);
			}

			handshakeHandler.writeMessage();
			if (handshakeHandler.isSendingInProgress()) {
				logger.trace("Status : {}, only partial handshake message was written to peer.", this.state);
				//OP_READ is unregistered.
				try {
					this.connectionManager.registerChannel(this.socketChannel, SelectionKey.OP_WRITE, this);
				} catch (IOException e) {
					logger.error("", e);
					this.selfClose();
					return;
				}
			} else {
				this.state = State.IN_HANDSHAKE_MESSAGE_SENT;
				try {
					this.connectionManager.registerChannel(this.socketChannel, SelectionKey.OP_READ, this);
				} catch (IOException e) {
					logger.error("", e);
					this.selfClose();
					return;
				}
				logger.debug("Status : {}, completed writing handshake message to peer.", this.state);
			}
		}else if (this.state == State.IN_BITFIELD_RECEIVED) {
			if (!messageHandler.isSendingInProgress()) {
				Bitmap bitfield = this.downloadManager.getBitfield(self.getInfoHashString());
				logger.debug("Status : {}, writing bitfield message to peer [{}].", this.state, bitfield);
				BitfieldMessage bitfieldMessage = new BitfieldMessage(bitfield);
				messageHandler.setMessageToSend(bitfieldMessage);
			} else {
				logger.debug("Status : {}, writing remaining part of bitfield message to peer.", this.state);
			}
			messageHandler.writeMessage();
			if (messageHandler.isSendingInProgress()) {
				logger.debug("Status : {}, only part of the bitfield message was written to peer.", this.state);
				// OP_READ is unregistered.
				try {
					this.connectionManager.registerChannel(this.socketChannel, SelectionKey.OP_WRITE, this);
				} catch (IOException e) {
					logger.error("", e);
					this.selfClose();
					return;
				}
			} else {
				// It should be incoming connection.
				this.state = State.IN_EXCHANGE_BITFIELD_COMPLETED;

				//this.connectionId = Utils.bytes2HexString(this.peer.getInfoHash()) + "-" + UUID.randomUUID().toString();
				this.connectionId = UUID.randomUUID().toString();
				this.connectionManager.addPeerConnection(this.peer.getInfoHashString(), this);
				//this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
				logger.debug("Status : {}, completed writing bitfield message to peer.", this.state);
				logger.info("Completed handshake with peer.");
				// TODO: Shall we put this message in the head of the queue?
				if (this.interesting) {
					InterestedMessage interestedMessage = new InterestedMessage();
					this.addMessageToSend(interestedMessage);
				}
				// Send the messages that are put in queue during handshaking and bitfield exchanging.
				//startSendMessages();
			}
		}else if (this.state == State.OUT_CONNECTED) {
			//TODO: Use a while to ensure the message is totally written to peer.
			if (!handshakeHandler.isSendingInProgress()) {
				HandshakeMessage handshakeMessage = new HandshakeMessage(self.getInfoHash(), self.getPeerId());
				logger.debug("Status : {}, writing handshake message to peer [{}].", this.state, handshakeMessage);
				handshakeHandler.setMessageToSend(handshakeMessage);
			} else {
				logger.debug("Status : {}, writing remaining part of handshake message to peer.", this.state);
			}
			handshakeHandler.writeMessage();
			if (handshakeHandler.isSendingInProgress()) {
				// OP_READ is unregistered.
				logger.debug("Status : {}, only part of the handshake message was written to peer.", this.state);
				try {
					this.connectionManager.registerChannel(this.socketChannel, SelectionKey.OP_WRITE, this);
				} catch (IOException e) {
					logger.error("", e);
					this.selfClose(); // TODO: check all of the selfClose carefully to make sure it works as expected.
					return;
				}
			} else {
				this.state = State.OUT_HANDSHAKE_MESSAGE_SENT;
				try {
					this.connectionManager.registerChannel(this.socketChannel, SelectionKey.OP_READ, this);
				} catch (IOException e) {
					logger.error("", e);
					this.selfClose();
					return;
				}
				logger.debug("Status : {}, completed writing handshake message to peer.", this.state);
			}
		}else if (this.state == State.OUT_HANDSHAKE_MESSAGE_RECEIVED) { // start to send bitfield to peer.
			if (!messageHandler.isSendingInProgress()) {
				Bitmap bitfield = this.downloadManager.getBitfield(self.getInfoHashString());
				logger.debug("Status : {}, writing bitfield message to peer [{}].", this.state, bitfield);
				BitfieldMessage bitfieldMessage = new BitfieldMessage(bitfield);
				messageHandler.setMessageToSend(bitfieldMessage);
			} else {
				logger.debug("Status : {}, writing remaining part of bitfield message to peer.", this.state);
			}

			messageHandler.writeMessage();
			if (messageHandler.isSendingInProgress()) {
				// OP_READ is unregistered.
				logger.debug("Status : {}, only part of the bitfield message was written to peer.", this.state);
				try {
					this.connectionManager.registerChannel(this.socketChannel, SelectionKey.OP_WRITE, this);
				} catch (IOException e) {
					logger.error("", e);
					this.selfClose();
					return;
				}
			} else {
				this.state = State.OUT_BITFIELD_SENT;
				try {
					this.connectionManager.registerChannel(this.socketChannel, SelectionKey.OP_READ, this); // XXX: Overwrite the OP_WRITE. 
				} catch (IOException e) {
					logger.error("", e);
					this.selfClose();
					return;
				}
				logger.trace("Status : {}, completed writing bitfield message to peer.", this.state);
			}
		}
		
		if (isHandshakeCompleted()) {
			this.startSendMessages();
		}
	}

	public void startSendMessages() {
		logger.trace("Status : {}, sending messages in queue.", this.state);
		for (;;) {
			if (!messageHandler.isSendingInProgress()) { // Nothing sent yet or completed sending a message.
				PeerMessage message = this.messagesToSend.poll();
				if (message == null) {
					logger.trace("Status : {}, there is no message in queue, unregistered OP_WRITE for this channel.", this.state);
					try {
						this.connectionManager.registerChannel(this.socketChannel, SelectionKey.OP_READ, this);
						break;
					} catch (IOException e) {
						logger.error("", e);
						this.selfClose();
						return;
					}
				}
				logger.trace("Got a message from queue {}.", message);
				messageHandler.setMessageToSend(message);
			} else {
				logger.warn("Status : {}, send remaining part of current message {}.", this.state, messageHandler.getMessageToSend());
			}
			messageHandler.writeMessage();
			if (messageHandler.isSendingInProgress()) {
				logger.warn("Status : {}, only part of the message {} was written to peer.", this.state, messageHandler.getMessageToSend());
				try {
					this.connectionManager.registerChannel(this.socketChannel, SelectionKey.OP_WRITE | SelectionKey.OP_READ, this);
					break;
				} catch (IOException e) {
					logger.error("", e);
					this.selfClose();
					return;
				}
			} else {
				this.timeLastWrite = System.currentTimeMillis();
				logger.debug("Status : {}, completed writing a message [{}] to peer.", this.state, messageHandler.getMessageToSend());
			}
		}
	}

	public void addMessageToSend(PeerMessage message) {
		List<PeerMessage> messages = new ArrayList<PeerMessage>();
		messages.add(message);
		this.addMessageToSend(messages);
	}

	public void addMessageToSend(List<PeerMessage> messages) {
		// TODO: Handling exceptions.
		try {
			for (PeerMessage message : messages) {
				this.messagesToSend.put(message);
			}
		} catch (InterruptedException e) {
			logger.warn("Status : {}, failed to add outgoing messages to queue : {}.", this.state, messages);
			logger.error("", e);
		}
		logger.debug("Status : {}, outgoing messages for {} added to queue {}.", this.state, this.peer.getAddress(),  messages);

		if (isHandshakeCompleted()) {
			this.startSendMessages();
		}
	}
	
	// TODO:
	// I need to know how other Clients works.
	// e.g. other Clients count the handshake time in, but I do not, there will be problem.
	// Before handshake has been completed, we should not seed KeepAlive message.
	public void checkAliveAndKeepAlive() {
		logger.trace("Checking alives and keeping alives.");
		if (isHandshakeCompleted()) {
			long now = System.currentTimeMillis();
			
			if (now - this.timeLastRead > 3 * 60 * 1000) {
				this.selfClose();
				return;
			}
			
			if (now - this.timeLastWrite > 45 * 1000) {
				this.addMessageToSend(new KeepAliveMessage());
			}
		}
	}

	public void selfClose() {
		this.close();

		if (this.state == State.IN_EXCHANGE_BITFIELD_COMPLETED || this.state == State.OUT_EXCHANGE_BITFIELD_COMPLETED) {
			this.connectionManager.removePeerConnection(this.self.getInfoHashString(), this.connectionId);
		}
	}

	// TODO: Connection managing.
	// Is it enough?
	// TODO: Carefully! Close may be called during the handshake stage, so self.getInfoHash may return null?
	public void close() {
		// 1. Cancel the key.
		// 2. Close the channel.
		// 3. Cancel the indexes requesting in DownloadManager.
		// 4. Remove this connection from the peerConnections in PeerConnecionManager.
		logger.warn("Closing connection : {}.", this);
		this.connectionManager.unregisterChannel(this.socketChannel);

		Helper.closeChannel(socketChannel);
		if (isHandshakeCompleted()) {
		  this.downloadManager.getPiecePicker().removeConnection(this);
		}
	}

	public boolean isHandshakeCompleted() {
		return this.state == State.IN_EXCHANGE_BITFIELD_COMPLETED || this.state == State.OUT_EXCHANGE_BITFIELD_COMPLETED;
	}
	
	public State getState() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
	}

	public String getConnectionId() {
		return connectionId;
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

	public boolean isOutgoingConnection() {
		return isOutgoingConnection;
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

	public PeerMessageHandler getMessageHandler() {
		return messageHandler;
	}

	public SocketChannel getChannel() {
		return this.socketChannel;
	}

	@Override
	public String toString() {
		return "PeerConnection [state=" + state + ", connectionId=" + connectionId + ", isOutgoingConnection=" + isOutgoingConnection + ", self=" + self + ", peer="
				+ peer + ", choking=" + choking + ", interesting=" + interesting + ", choked=" + choked + ", interested=" + interested + "]";
	}
}
