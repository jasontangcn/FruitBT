package com.fruits.bt;

import java.io.IOException;
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
		UNDEFINED(-1), OUT_CONNECTED(0), OUT_HANDSHAKE_MESSAGE_SENT(1), OUT_HANDSHAKE_MESSAGE_RECEIVED(2), OUT_BITFIELD_SENT(3), OUT_EXCHANGE_BITFIELD_COMPLETED(4),
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
	private final PeerMessageHandler messageHandler;

	private Peer self;
	private Peer peer;
	private final boolean isOutgoingConnect;

	// Initial status.
	private boolean choking = true; // choking peer
	private boolean interesting = false; // interested in peer
	private boolean choked = true; // choked by peer
	private boolean interested = false; // peer interested in me

	private ArrayBlockingQueue<PeerMessage> messagesToSend = new ArrayBlockingQueue<PeerMessage>(1024, true);

	private long timeLastRead;
	private long timeLastWrite;

	private int indexRequesting = -1;
	private int requestsSent;
	private int piecesReceived;
	private volatile boolean closing = false;

	public PeerConnection(boolean isOutgoingConnect, SocketChannel socketChannel, PeerConnectionManager connectionManager, DownloadManager downloadManager) {
		this.isOutgoingConnect = isOutgoingConnect;
		this.socketChannel = socketChannel;
		this.connectionManager = connectionManager;
		this.downloadManager = downloadManager;
		this.handshakeHandler = new HandshakeHandler(this);
		this.messageHandler = new PeerMessageHandler(this);
	}

	public void readMessage() {
		// ACCEPTED -> It's an incoming connection.
		if (this.state == State.IN_ACCEPTED) {
			System.out.println("Status : " + this.state + ", reading handshake message.");
			HandshakeMessage message = this.handshakeHandler.readMessage();
			if (message == null)
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

		if (this.state == State.IN_HANDSHAKE_MESSAGE_RECEIVED) {
			writeMessage();
		}

		if (this.state == State.IN_HANDSHAKE_MESSAGE_SENT) {
			System.out.println("Status : " + this.state + ", read bitfield message.");
			PeerMessage message = this.messageHandler.readMessage();
			if (message == null)
				return;
			if (message instanceof PeerMessage.BitfieldMessage) {
				BitSet peerBitfield = ((PeerMessage.BitfieldMessage) message).getBitfield();
				// TODO: Set the bitfield of this.peer.
				this.peer.setBitfield(peerBitfield);

				BitSet selfBitfield = this.downloadManager.getBitfield(this.self.getInfoHash());
				this.interesting = PeerConnection.isInterested(selfBitfield, peerBitfield);

				this.state = State.IN_BITFIELD_RECEIVED;
				System.out.println("Status : " + this.state + ", received bitfield message [" + message + "].");
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
			System.out.println("Status : " + this.state + ", reading handshake message.");
			HandshakeMessage message = handshakeHandler.readMessage();
			if (message == null)
				return;
			this.peer.setPeerId(message.getPeerIdString());
			this.peer.setInfoHash(message.getInfoHashString());
			this.state = State.OUT_HANDSHAKE_MESSAGE_RECEIVED;
			System.out.println("Status : " + this.state + ", received handshake message [" + message + "].");
		}

		if (this.state == State.OUT_HANDSHAKE_MESSAGE_RECEIVED) {
			writeMessage();
		}
		//TODO: XXXX
		//If it's a outgoing connection, this status is not enough,
		//because the peer may not send back the bitfield yet.
		if (this.state == State.OUT_BITFIELD_SENT) {
			System.out.println("Status : " + this.state + ", reading bitfield message.");
			PeerMessage message = this.messageHandler.readMessage();
			if (message == null)
				return;
			if (message instanceof PeerMessage.BitfieldMessage) {
				// TODO: Set the bitfield of this.peer.
				BitSet peerBitfield = ((PeerMessage.BitfieldMessage) message).getBitfield();
				this.peer.setBitfield(peerBitfield);

				this.interesting = PeerConnection.isInterested(this.downloadManager.getBitfield(self.getInfoHash()), peerBitfield);

				this.state = State.OUT_EXCHANGE_BITFIELD_COMPLETED;
				this.connectionManager.addPeerConnection(this.peer.getInfoHash(), this);

				System.out.println("Status : " + this.state + ", completed reading bitfield message : " + message + ".");
				if (this.interesting) {
					PeerMessage.InterestedMessage interestedMessage = new PeerMessage.InterestedMessage();
					this.addMessageToSend(interestedMessage);
				}
				// Send the messages that are put in queue during handshaking and bitfield exchanging.
				startSendMessages();
			}
		}

		if (this.state == State.IN_EXCHANGE_BITFIELD_COMPLETED || this.state == State.OUT_EXCHANGE_BITFIELD_COMPLETED) {
			System.out.println("Status : " + this.state + ", reading message.");
			PeerMessage message = this.messageHandler.readMessage();
			if (message == null)
				return;

			this.timeLastRead = System.currentTimeMillis();

			System.out.println("PeerConnection->readMessage: I got a message from peer [" + message + "].");

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

				this.downloadManager.cancelRequestPiece(self.getInfoHash(), this);

				this.indexRequesting = -1;
				this.requestsSent = 0;
				this.piecesReceived = 0;

			} else if (message instanceof UnchokeMessage) {
				this.choked = false;
				System.out.println("PeerConnection->readMessage: I got a UnchokeMessage and this.interesting is : " + this.interesting + ".");
				if (this.interesting) {
					this.downloadManager.requestMoreSlices(this.self.getInfoHash(), this);
				}
			} else if (message instanceof InterestedMessage) {
				this.interested = true;
				// TODO: If peer is interested in me, I will unchoke him?
			} else if (message instanceof NotInterestedMessage) {
				this.interested = false;
				// TODO: Cancel the response from the peer.

				Iterator<PeerMessage> it = this.messagesToSend.iterator();
				while (it.hasNext()) {
					PeerMessage msg = it.next();
					if (msg instanceof PieceMessage) {
						it.remove();
					}
				}
			} else if (message instanceof HaveMessage) {
				HaveMessage haveMessage = (HaveMessage) message;
				this.peer.getBitfield().set(haveMessage.getPieceIndex());

				if (!this.interesting) {
					boolean interested = PeerConnection.isInterested(this.downloadManager.getBitfield(self.getInfoHash()), this.peer.getBitfield());
					this.interesting = interested;
					if (interested) {
						PeerMessage.InterestedMessage interestedMessage = new PeerMessage.InterestedMessage();
						this.addMessageToSend(interestedMessage);
					}
				}

				// TODO: XXXX
				if (!this.choked && this.interesting) {
					this.downloadManager.requestMoreSlices(self.getInfoHash(), this);
				}
			} else if (message instanceof BitfieldMessage) {
				// In this status, client should not send/receive bitfield message.
			} else if (message instanceof RequestMessage) {
				RequestMessage request = (RequestMessage) message;
				Slice slice = new Slice(request.getIndex(), request.getBegin(), request.getLength());
				ByteBuffer data = this.downloadManager.readSlice(self.getInfoHash(), slice);
				if (data != null) {
					PieceMessage pieceMessage = new PieceMessage(slice.getIndex(), slice.getBegin(), data);
					addMessageToSend(pieceMessage);
				}
			} else if (message instanceof PieceMessage) {
				PieceMessage piece = (PieceMessage) message;
				// Is it OK to use .limit() to get the length of the ByteBuffer?
				// TODO: XXXX
				// Slient but the write may fail.
				this.downloadManager.writeSlice(self.getInfoHash(), piece.getIndex(), piece.getBegin(), piece.getBlock().remaining(), piece.getBlock());
				this.piecesReceived++;

				System.out.println("PeerConnection->readMessage: Got a PieceMessage, pieceMessageReceived = " + this.piecesReceived + ", and expected count is : "
						+ this.requestsSent + ".");

				if (this.piecesReceived == this.requestsSent) {
					this.piecesReceived = 0;
					this.requestsSent = 0;
					this.downloadManager.requestMoreSlices(self.getInfoHash(), this);
				}
			} else if (message instanceof CancelMessage) {
				CancelMessage cancelMessage = (CancelMessage) message;
				// TODO: Check the queue of received messages and the queue of outgoing messages, if it's there remove it from the queue.
				Iterator<PeerMessage> it = this.messagesToSend.iterator();
				while (it.hasNext()) {
					PeerMessage msg = it.next();
					if (message instanceof PieceMessage) {
						PieceMessage pieceMessage = (PieceMessage) message;
						if ((pieceMessage.getIndex() == cancelMessage.getIndex()) && (pieceMessage.getBegin() == cancelMessage.getBegin())) {
							it.remove();
						}
					}
				}

			} else if (message instanceof PortMessage) {

			}
		}
	}

	public void writeMessage() {
		if (this.state == State.IN_HANDSHAKE_MESSAGE_RECEIVED) {
			if (!this.handshakeHandler.isSendingInProgress()) {
				HandshakeMessage handshakeMessage = new HandshakeMessage(Utils.hexStringToBytes(self.getInfoHash()), self.getPeerId().getBytes());
				System.out.println("Status : " + this.state + ", writing handshake message to peer [" + handshakeMessage + "].");
				handshakeHandler.setMessageToSend(handshakeMessage);
			} else {
				System.out.println("Status : " + this.state + ", writing remaining part of handshake message to peer.");
			}

			handshakeHandler.writeMessage();
			if (handshakeHandler.isSendingInProgress()) {
				System.out.println("Status : " + this.state + ", only partial handshake message was written to peer.");
				//OP_READ is unregistered.
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.close();
					return;
				}
			} else {
				this.state = State.IN_HANDSHAKE_MESSAGE_SENT;
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.close();
					return;
				}
				System.out.println("Status : " + this.state + ", completed writing handshake message to peer.");
			}
		}

		if (this.state == State.IN_BITFIELD_RECEIVED) {
			if (!messageHandler.isSendingInProgress()) {
				BitSet bitfield = this.downloadManager.getBitfield(self.getInfoHash());
				System.out.println("Status : " + this.state + ", writing bitfield message to peer [" + bitfield + "].");
				BitfieldMessage bitfieldMessage = new PeerMessage.BitfieldMessage(bitfield);
				messageHandler.setMessageToSend(bitfieldMessage);
			} else {
				System.out.println("Status : " + this.state + ", writing remaining part of bitfield message to peer.");
			}
			messageHandler.writeMessage();
			if (messageHandler.isSendingInProgress()) {
				System.out.println("Status : " + this.state + ", only partial bitfield message was written to peer.");
				// OP_READ is unregistered.
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.close();
					return;
				}
			} else {
				// It should be incoming connection.
				this.state = State.IN_EXCHANGE_BITFIELD_COMPLETED;
				this.connectionManager.addPeerConnection(this.peer.getInfoHash(), this);
				//this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
				System.out.println("Status : " + this.state + ", completed writing bitfield message to peer.");

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
				HandshakeMessage handshakeMessage = new HandshakeMessage(Utils.hexStringToBytes(self.getInfoHash()), self.getPeerId().getBytes());
				System.out.println("Status : " + this.state + ", writing handshake message to peer [" + handshakeMessage + "].");
				handshakeHandler.setMessageToSend(handshakeMessage);
			} else {
				System.out.println("Status : " + this.state + ", writing remaining part of handshake message to peer.");
			}
			handshakeHandler.writeMessage();
			if (handshakeHandler.isSendingInProgress()) {
				// OP_READ is unregistered.
				System.out.println("Status : " + this.state + ", only partial handshake message was written to peer.");
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.close();
					return;
				}
			} else {
				this.state = State.OUT_HANDSHAKE_MESSAGE_SENT;
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.close();
					return;
				}
				System.out.println("Status : " + this.state + ", completed writing handshake message to peer.");
			}
		}

		if (this.state == State.OUT_HANDSHAKE_MESSAGE_RECEIVED) {
			if (!messageHandler.isSendingInProgress()) {
				BitSet bitfield = this.downloadManager.getBitfield(self.getInfoHash());
				System.out.println("Status : " + this.state + ", writing bitfield message to peer [" + bitfield + "].");
				BitfieldMessage bitfieldMessage = new PeerMessage.BitfieldMessage(bitfield);
				messageHandler.setMessageToSend(bitfieldMessage);
			} else {
				System.out.println("Status : " + this.state + ", writing remaining part of bitfield message to peer.");
			}

			messageHandler.writeMessage();
			if (messageHandler.isSendingInProgress()) {
				// OP_READ is unregistered.
				System.out.println("Status : " + this.state + ", only partial bitfield message was written to peer.");
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.close();
					return;
				}
			} else {
				this.state = State.OUT_BITFIELD_SENT;
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.close();
					return;
				}
				System.out.println("Status : " + this.state + ", completed writing bitfield message to peer.");
			}
		}

		if (this.state == State.IN_EXCHANGE_BITFIELD_COMPLETED || this.state == State.OUT_EXCHANGE_BITFIELD_COMPLETED) {
			this.startSendMessages();
		}
	}

	public void startSendMessages() {
		System.out.println("Status : " + this.state + ", sending messages in queue.");
		for (;;) {
			if (!messageHandler.isSendingInProgress()) { // Nothing sent yet or completed sending a message.
				PeerMessage message = this.messagesToSend.poll();
				if (message == null) {
					try {
						this.connectionManager.register(this.socketChannel, SelectionKey.OP_READ, this);
					} catch (IOException e) {
						e.printStackTrace();
						this.close();
						return;
					}
					System.out.println("Status : " + this.state + ", no message in the queue, unregistered OP_WRITE for this channel.");
					break;
				}
				System.out.println("Got a message from queue.");
				messageHandler.setMessageToSend(message);
			} else {
				System.out.println("Status : " + this.state + ", continue to send remaining part of current message.");
			}
			messageHandler.writeMessage();
			if (messageHandler.isSendingInProgress()) {
				System.out.println("Status : " + this.state + ", only partialessage was written to peer.");
				try {
					this.connectionManager.register(this.socketChannel, SelectionKey.OP_WRITE | SelectionKey.OP_READ, this);
				} catch (IOException e) {
					e.printStackTrace();
					this.close();
					return;
				}
				break;
			} else {
				this.timeLastWrite = System.currentTimeMillis();
				System.out.println("Status : " + this.state + ", completed writing a message to peer.");
			}
		}
	}

	public void addMessageToSend(PeerMessage message) {
		// TODO: Handling exception.
		try {
			this.messagesToSend.put(message);
		} catch (InterruptedException e) {
			System.out.println("Status : " + this.state + ", failed to add outgoing message to queue : " + message + ".");
			e.printStackTrace();
			return;
		}
		System.out.println("Status : " + this.state + ", an outgoing message was added to queue : " + message + ".");

		if (this.state == State.IN_EXCHANGE_BITFIELD_COMPLETED || this.state == State.OUT_EXCHANGE_BITFIELD_COMPLETED) {
			this.startSendMessages();
		}
	}

	public void addMessageToSend(List<PeerMessage> messages) {
		// TODO: Handling exception.
		try {
			for (PeerMessage message : messages) {
				this.messagesToSend.put(message);
			}
		} catch (InterruptedException e) {
			System.out.println("Status : " + this.state + ", failed to add outgoing messages to queue : " + messages + ".");
			e.printStackTrace();
			return;
		}
		System.out.println("Status : " + this.state + ", outgoing messages were added to queue : " + messages + ".");

		if (this.state == State.IN_EXCHANGE_BITFIELD_COMPLETED || this.state == State.OUT_EXCHANGE_BITFIELD_COMPLETED) {
			this.startSendMessages();
		}
	}

	public static boolean isInterested(BitSet a, BitSet b) {
		System.out
				.println("a-> " + a + " [length = " + a.length() + ", size = " + a.size() + "], b-> " + b + " [length = " + b.length() + ", size = " + b.size() + "].");
		int n = Math.max(a.size(), b.size());
		// TODO: validate parameters.
		boolean interested = false;
		for (int i = 0; i < n; i++) {
			if (!a.get(i) && b.get(i)) {
				interested = true;
			}
		}
		System.out.println("a is interested in b? " + interested);
		return interested;
	}

	public void checkAliveAndKeepAlive() {
		System.out.println("Checking alives and keeping alives.");
		if (this.state == State.IN_EXCHANGE_BITFIELD_COMPLETED || this.state == State.OUT_EXCHANGE_BITFIELD_COMPLETED) {
			long now = System.currentTimeMillis();

			if (now - this.timeLastWrite > 45 * 1000) {
				PeerMessage.KeepAliveMessage keepAliveMessage = new PeerMessage.KeepAliveMessage();
				this.addMessageToSend(keepAliveMessage);
			}

			if (now - this.timeLastRead > 3 * 60 * 1000) {
				this.close();
			}
		}
	}

	// TODO: Connection managing.
	// Is it enough?
	public void close() {
		// 2. Cancel the indexes requesting in DownloadManager.
		// 3. Remove this connection from the peerConnections in PeerConnecionManager.
		// 1. Cancel the key.
		// 4. Close the channel.
		String infoHash = self.getInfoHash();
		this.downloadManager.cancelRequestPiece(infoHash, this);
		this.closing = true;
		this.connectionManager.removeClosedConnections(infoHash);

		this.connectionManager.unregister(this.socketChannel);

		if (socketChannel.isOpen()) {
			try {
				socketChannel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
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

	public PeerMessageHandler getMessageHandler() {
		return messageHandler;
	}

	public int getIndexRequesting() {
		return indexRequesting;
	}

	public void setIndexRequesting(int indexRequesting) {
		this.indexRequesting = indexRequesting;
	}

	public int getRequestsSent() {
		return requestsSent;
	}

	public void setRequestsSent(int requestsSent) {
		this.requestsSent = requestsSent;
	}

	public boolean isClosing() {
		return this.closing;
	}

	public SocketChannel getChannel() {
		return this.socketChannel;
	}
}
