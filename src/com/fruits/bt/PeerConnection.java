package com.fruits.bt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
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
	private boolean choking = true; // choking peer
	private boolean interesting = false; // interested in peer
	private boolean choked = true; // choked by peer
	private boolean interested = false; // peer interested in me

	private ArrayBlockingQueue<PeerMessage> messagesToSend = new ArrayBlockingQueue<PeerMessage>(1024, true);

	private long timeLastRead = System.currentTimeMillis();
	private long timeLastWrite = System.currentTimeMillis();

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
		// ACCEPTED -> It's an incoming connection.
		if (this.state == State.IN_ACCEPTED) {
			logger.debug("Status : " + this.state + ", reading handshake message.");
			HandshakeMessage message = this.handshakeHandler.readMessage();
			if (message == null)
				return;

			byte[] infoHash = message.getInfoHash();

			this.peer = new Peer();
			this.peer.setAddress((InetSocketAddress) this.socketChannel.socket().getRemoteSocketAddress());
			this.peer.setInfoHash(infoHash);
			this.peer.setPeerId(message.getPeerId());

			//TODO: DownloadManager should not appear here?
			this.self.setInfoHash(infoHash);
			this.state = State.IN_HANDSHAKE_MESSAGE_RECEIVED;
			logger.debug("Status : " + this.state + ", received handshake message [" + message + "].");
		}

		if (this.state == State.IN_HANDSHAKE_MESSAGE_RECEIVED) {
			writeMessage();
		}

		if (this.state == State.IN_HANDSHAKE_MESSAGE_SENT) {
			logger.debug("Status : " + this.state + ", read bitfield message.");
			PeerMessage message = this.messageHandler.readMessage();
			if (message == null)
				return;
			if (message instanceof PeerMessage.BitfieldMessage) {
				Bitmap peerBitfield = ((PeerMessage.BitfieldMessage) message).getBitfield();

				// TODO: Set the bitfield of this.peer.
				this.peer.setBitfield(peerBitfield);

				Bitmap selfBitfield = this.downloadManager.getBitfield(this.self.getInfoHashString());

				// TODO: need to validate it before assigning the length?
				peerBitfield.setLength(selfBitfield.length());

				this.interesting = Helper.isInterested(selfBitfield, peerBitfield);

				this.state = State.IN_BITFIELD_RECEIVED;
				logger.debug("Status : " + this.state + ", received bitfield message [" + message + "].");
			}
		}

		if (this.state == State.IN_BITFIELD_RECEIVED) {
			writeMessage();
		}

		if (this.state == State.OUT_CONNECTED) {
			writeMessage();
		}

		// It must be an outgoing connection.
		if (this.state == State.OUT_HANDSHAKE_MESSAGE_SENT) {
			logger.debug("Status : " + this.state + ", reading handshake message.");
			HandshakeMessage message = handshakeHandler.readMessage();
			if (message == null)
				return;
			this.peer.setPeerId(message.getPeerId());
			this.peer.setInfoHash(message.getInfoHash());
			this.state = State.OUT_HANDSHAKE_MESSAGE_RECEIVED;
			logger.debug("Status : " + this.state + ", received handshake message [" + message + "].");
		}

		if (this.state == State.OUT_HANDSHAKE_MESSAGE_RECEIVED) {
			writeMessage();
		}
		//TODO: XXXX
		//If it's a outgoing connection, this status is not enough,
		//because the peer may not send back the bitfield yet.
		if (this.state == State.OUT_BITFIELD_SENT) {
			logger.debug("Status : " + this.state + ", reading bitfield message.");
			PeerMessage message = this.messageHandler.readMessage();
			if (message == null)
				return;
			if (message instanceof PeerMessage.BitfieldMessage) {
				// TODO: Set the bitfield of this.peer.
				Bitmap peerBitfield = ((PeerMessage.BitfieldMessage) message).getBitfield();
				this.peer.setBitfield(peerBitfield);

				Bitmap selfBitfield = this.downloadManager.getBitfield(self.getInfoHashString());
				peerBitfield.setLength(selfBitfield.length());

				this.interesting = Helper.isInterested(selfBitfield, peerBitfield);

				this.state = State.OUT_EXCHANGE_BITFIELD_COMPLETED;

				this.connectionId = Utils.bytes2HexString(this.peer.getInfoHash()) + "-" + UUID.randomUUID().toString();
				this.connectionManager.addPeerConnection(this.peer.getInfoHashString(), this);

				logger.debug("Status : " + this.state + ", completed reading bitfield message : " + message + ".");
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
			//logger.debug("Status : " + this.state + ", reading message.");
			PeerMessage message = this.messageHandler.readMessage();
			if (message == null)
				return;

			this.timeLastRead = System.currentTimeMillis();

			logger.debug("PeerConnection->readMessage: I got a message from peer [" + message + "].");

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
				/*
				this.indexRequesting = -1;
				this.requestsSent = 0;
				this.piecesReceived = 0;
				*/
			} else if (message instanceof UnchokeMessage) {
				this.choked = false;
				this.downloadManager.getPiecePicker().addConnection(this);
				logger.debug("PeerConnection->readMessage: I got a UnchokeMessage and this.interesting is : " + this.interesting + ".");
				if (this.interesting) {
					this.downloadManager.getPiecePicker().requestMoreSlices(this);
				}
			} else if (message instanceof InterestedMessage) {
				this.interested = true;
				// TODO: If a peer is interested in me, I will unchoke him?
			} else if (message instanceof NotInterestedMessage) {
				this.interested = false;
				// TODO: Cancel the responses for the peer.
				Iterator<PeerMessage> iterator = this.messagesToSend.iterator();
				while (iterator.hasNext()) {
					PeerMessage m = iterator.next();
					if (m instanceof PieceMessage) {
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
						PeerMessage.InterestedMessage interestedMessage = new PeerMessage.InterestedMessage();
						this.addMessageToSend(interestedMessage);
					}
				}

				this.downloadManager.getPiecePicker().peerHaveNewPiece(self.getInfoHashString(), haveMessage.getPieceIndex());
				// TODO: XXXX
				if (!this.choked && interestedNow) {
					if (!this.downloadManager.getPiecePicker().isBatchRequestInProgress(self.getInfoHashString(), connectionId))
						this.downloadManager.getPiecePicker().requestMoreSlices(this);
				}
			} else if (message instanceof BitfieldMessage) {
				// In this status, client should not send/receive bitfield message.
			} else if (message instanceof RequestMessage) {
				logger.debug("PeerConnection->readMessage: Got a RequestMessage " + message + ".");

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
				// Is it OK to use .limit() to get the length of the ByteBuffer?
				// TODO: XXXX
				// Silent but the writing may fail.
				this.downloadManager.writeSlice(self.getInfoHashString(), piece.getIndex(), piece.getBegin(), piece.getBlock().remaining(), piece.getBlock());
				this.downloadManager.getPiecePicker().sliceReceived(this);
				logger.debug("PeerConnection->readMessage: Got a PieceMessage " + message + ".");

			} else if (message instanceof CancelMessage) {
				CancelMessage cancelMessage = (CancelMessage) message;
				// TODO: Check the queue of received messages and the queue of outgoing messages, if it's there remove it from the queue.
				Iterator<PeerMessage> iterator = this.messagesToSend.iterator();
				while (iterator.hasNext()) {
					PeerMessage m = iterator.next();
					if (m instanceof PieceMessage) {
						PieceMessage pieceMessage = (PieceMessage) m;
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
				logger.debug("Status : " + this.state + ", writing handshake message to peer [" + handshakeMessage + "].");
				handshakeHandler.setMessageToSend(handshakeMessage);
			} else {
				logger.debug("Status : " + this.state + ", writing remaining part of handshake message to peer.");
			}

			handshakeHandler.writeMessage();
			if (handshakeHandler.isSendingInProgress()) {
				logger.debug("Status : " + this.state + ", only partial handshake message was written to peer.");
				//OP_READ is unregistered.
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.selfClose();
					return;
				}
			} else {
				this.state = State.IN_HANDSHAKE_MESSAGE_SENT;
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.selfClose();
					return;
				}
				logger.debug("Status : " + this.state + ", completed writing handshake message to peer.");
			}
		}

		if (this.state == State.IN_BITFIELD_RECEIVED) {
			if (!messageHandler.isSendingInProgress()) {
				Bitmap bitfield = this.downloadManager.getBitfield(self.getInfoHashString());
				logger.debug("Status : " + this.state + ", writing bitfield message to peer [" + bitfield + "].");
				BitfieldMessage bitfieldMessage = new PeerMessage.BitfieldMessage(bitfield);
				messageHandler.setMessageToSend(bitfieldMessage);
			} else {
				logger.debug("Status : " + this.state + ", writing remaining part of bitfield message to peer.");
			}
			messageHandler.writeMessage();
			if (messageHandler.isSendingInProgress()) {
				logger.debug("Status : " + this.state + ", only partial bitfield message was written to peer.");
				// OP_READ is unregistered.
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.selfClose();
					return;
				}
			} else {
				// It should be incoming connection.
				this.state = State.IN_EXCHANGE_BITFIELD_COMPLETED;

				this.connectionId = Utils.bytes2HexString(this.peer.getInfoHash()) + "-" + UUID.randomUUID().toString();
				this.connectionManager.addPeerConnection(this.peer.getInfoHashString(), this);
				//this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
				logger.debug("Status : " + this.state + ", completed writing bitfield message to peer.");

				// TODO: Shall we put this message in the head of the queue?
				if (this.interesting) {
					PeerMessage.InterestedMessage interestedMessage = new PeerMessage.InterestedMessage();
					this.addMessageToSend(interestedMessage);
				}
				// Send the messages that are put in queue during handshaking and bitfield exchanging.
				startSendMessages();
			}
		}

		if (this.state == State.OUT_CONNECTED) {
			//TODO: Use a while to ensure the message is totally written to peer.
			if (!handshakeHandler.isSendingInProgress()) {
				HandshakeMessage handshakeMessage = new HandshakeMessage(self.getInfoHash(), self.getPeerId());
				logger.debug("Status : " + this.state + ", writing handshake message to peer [" + handshakeMessage + "].");
				handshakeHandler.setMessageToSend(handshakeMessage);
			} else {
				logger.debug("Status : " + this.state + ", writing remaining part of handshake message to peer.");
			}
			handshakeHandler.writeMessage();
			if (handshakeHandler.isSendingInProgress()) {
				// OP_READ is unregistered.
				logger.debug("Status : " + this.state + ", only partial handshake message was written to peer.");
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.selfClose();
					return;
				}
			} else {
				this.state = State.OUT_HANDSHAKE_MESSAGE_SENT;
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.selfClose();
					return;
				}
				logger.debug("Status : " + this.state + ", completed writing handshake message to peer.");
			}
		}

		if (this.state == State.OUT_HANDSHAKE_MESSAGE_RECEIVED) {
			if (!messageHandler.isSendingInProgress()) {
				Bitmap bitfield = this.downloadManager.getBitfield(self.getInfoHashString());
				logger.debug("Status : " + this.state + ", writing bitfield message to peer [" + bitfield + "].");
				BitfieldMessage bitfieldMessage = new PeerMessage.BitfieldMessage(bitfield);
				messageHandler.setMessageToSend(bitfieldMessage);
			} else {
				logger.debug("Status : " + this.state + ", writing remaining part of bitfield message to peer.");
			}

			messageHandler.writeMessage();
			if (messageHandler.isSendingInProgress()) {
				// OP_READ is unregistered.
				logger.debug("Status : " + this.state + ", only partial bitfield message was written to peer.");
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.selfClose();
					return;
				}
			} else {
				this.state = State.OUT_BITFIELD_SENT;
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.selfClose();
					return;
				}
				logger.debug("Status : " + this.state + ", completed writing bitfield message to peer.");
			}
		}

		if (isHandshakeCompleted()) {
			this.startSendMessages();
		}
	}

	public void startSendMessages() {
		logger.debug("Status : " + this.state + ", sending messages in queue.");
		for (;;) {
			if (!messageHandler.isSendingInProgress()) { // Nothing sent yet or completed sending a message.
				PeerMessage message = this.messagesToSend.poll();
				if (message == null) {
					try {
						this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
					} catch (IOException e) {
						e.printStackTrace();
						this.selfClose();
						return;
					}
					logger.debug("Status : " + this.state + ", no message in the queue, unregistered OP_WRITE for this channel.");
					break;
				}
				logger.debug("Got a message from queue.");
				messageHandler.setMessageToSend(message);
			} else {
				logger.debug("Status : " + this.state + ", continue to send remaining part of current message.");
			}
			messageHandler.writeMessage();
			if (messageHandler.isSendingInProgress()) {
				logger.debug("Status : " + this.state + ", only partial message was written to peer.");
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE | SelectionKey.OP_READ, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.selfClose();
					return;
				}
				break;
			} else {
				this.timeLastWrite = System.currentTimeMillis();
				logger.debug("Status : " + this.state + ", completed writing a message to peer.");
			}
		}
	}

	public void addMessageToSend(PeerMessage message) {
		// TODO: Handling exceptions.
		try {
			this.messagesToSend.put(message);
		} catch (InterruptedException e) {
			logger.debug("Status : " + this.state + ", failed to add outgoing message to queue : " + message + ".");
			e.printStackTrace();
		}
		logger.debug("Status : " + this.state + ", an outgoing message was added to queue : " + message + ".");

		if (isHandshakeCompleted()) {
			this.startSendMessages();
		}
	}

	public void addMessageToSend(List<PeerMessage> messages) {
		// TODO: Handling exceptions.
		try {
			for (PeerMessage message : messages) {
				this.messagesToSend.put(message);
			}
		} catch (InterruptedException e) {
			logger.debug("Status : " + this.state + ", failed to add outgoing messages to queue : " + messages + ".");
			e.printStackTrace();
		}
		logger.debug("Status : " + this.state + ", outgoing messages were added to queue : " + messages + ".");

		if (isHandshakeCompleted()) {
			this.startSendMessages();
		}
	}
	
	// TODO:
	// I need to know how other Clients works.
	// e.g. other Clients count the handshake time in, but I do not, there will be problem.
	// Before handshake has been completed, we should not seed KeepAlive message.
	public void checkAliveAndKeepAlive() {
		logger.debug("Checking alives and keeping alives.");
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
		logger.debug("Closing connection : " + this);
		this.connectionManager.unregister(this.socketChannel);

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
