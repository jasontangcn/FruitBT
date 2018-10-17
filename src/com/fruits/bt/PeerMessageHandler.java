package com.fruits.bt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import com.fruits.bt.PeerMessage.PieceMessage;

/*
 * 1. Message is incomplete, need to read data more.
 * 2. 1+ messages are available.
 */
public class PeerMessageHandler {
	public static final int PEER_MESSAGE_LENGTH_PREFIX = 4;
	private int lengthPrefix = -1;
	private final PeerConnection connection;
	// TODO: Check the definition in TorrentSeed.
	private ByteBuffer readBuffer = ByteBuffer.allocate(4 + 9 + 16 * 1024 * 4); // the longest message is piece
	private PeerMessage messageToSend;
	private ByteBuffer messageBytesToWrite;

	private long bytesRead;
	private long bytesWritten;

	// A separate thread will calculate the download rate and upload rate by every 10 seconds.
	private long bytesReadPrePeriod;
	private long bytesWritePrePeriod;
	private long timePrePeriod = System.currentTimeMillis();
	private float readRate;
	private float writeRate;

	public static enum SendState {
		IDLE(-1), READY(1), SENDING(2), DONE(3);

		private int stateId;

		SendState(int stateId) {
			this.stateId = stateId;
		}
	}

	private SendState state = SendState.IDLE;

	public PeerMessageHandler(PeerConnection connection) {
		this.connection = connection;
	}

	public void calculateReadWriteRate() {
		long time = System.currentTimeMillis() - this.timePrePeriod;
		// bytes read/ms
		this.readRate = ((this.bytesRead - this.bytesReadPrePeriod) / time);
		// bytes write/ms
		this.writeRate = ((this.bytesWritten - this.bytesWritePrePeriod) / time);

		System.out.println("PeerMessageHandler [bytesReadPrePeriod = " + bytesReadPrePeriod + ", bytesRead = " + bytesRead + ", timePreRead = " + timePrePeriod
				+ ",\n" + "bytesWritten = " + bytesWritten + ", bytesWritePrePeriod = " + bytesWritePrePeriod + ",\n"
				+ "readRate = " + readRate + " bytes/ms , writeRate = " + writeRate + " bytes/ms]." + "\n");

		this.bytesReadPrePeriod = this.bytesRead;
		this.bytesWritePrePeriod = this.bytesWritten;
		this.timePrePeriod = System.currentTimeMillis();
	}

	// message type id
	// <length prefix><message ID><payload>
	// keep_alive : <len=0000>
	//      choke : <len=0001><id=0>
	//    unchoke : <len=0001><id=1>
	// interested : <len=0001><id=2>
	// not interested : <len=0001><id=3>
	//           have : <len=0005><id=4><piece index>
	//       bitfield : <len=0001+X><id=5><bitfield>
	//        request : <len=0013><id=6><index><begin><length> // length of slice = 16K, piece = 256K
	//          piece : <len=0009+X><id=7><index><begin><block> // len = 16K + 9 = 00 00 40 09 = 0100 0000 0000 1001
	//         cancel : <len=0013><id=8><index><begin><length>
	//           port : <len=0003><id=9><listen-port>
	public PeerMessage readMessage() {
		SocketChannel socketChannel = this.connection.getChannel();
		try {
			if (lengthPrefix == -1) {
				this.readBuffer.limit(PeerMessageHandler.PEER_MESSAGE_LENGTH_PREFIX);
				// NotYetConnectedException if the channel is not connected.
				int n = socketChannel.read(readBuffer); // IOException
				if(n == -1) {
					this.connection.selfClose();
					return null;
				}
				if (readBuffer.hasRemaining()) {
					return null;
				}
				this.lengthPrefix = readBuffer.getInt(0);
				System.out.println("PeerMessageHandler:readMessage -> lengthPrefix : " + lengthPrefix + ".");
				readBuffer.limit(this.lengthPrefix + PeerMessageHandler.PEER_MESSAGE_LENGTH_PREFIX);
			}
			// NotYetConnectedException if the channel is not connected.
			int n = socketChannel.read(readBuffer); // IOException
			if (n == -1) {
				this.connection.selfClose();
				return null; // TODO: should close the connection.
			}
			if (readBuffer.hasRemaining()) {
				return null;
			} else {
				readBuffer.rewind();
				ByteBuffer copy = ByteBuffer.wrap(Arrays.copyOf(readBuffer.array(), readBuffer.limit()));
				this.lengthPrefix = -1;
				PeerMessage peerMessage = PeerMessage.parseMessage(copy);

				if (peerMessage instanceof PieceMessage) {
					this.bytesRead += ((PieceMessage) peerMessage).getBlock().limit();
				}

				return peerMessage;
			}
		} catch (IOException e) {
			e.printStackTrace();
			this.connection.selfClose();
			return null;
		}
	}

	// @return Message has been totally written to peer or not?
	public boolean writeMessage() {
		SocketChannel socketChannel = this.connection.getChannel();

		if (this.state == SendState.READY) {
			this.state = SendState.SENDING;
		} else if (this.state == SendState.SENDING) {
		} else {
			throw new RuntimeException("PeerMessageHandler is in illegal status.");
		}

		// TODO: if 0 bytes written, it leads to recrete the message.
		do {
			int n = -1;
			try {
				n = socketChannel.write(this.messageBytesToWrite); // NotYetConnectedException
			} catch (IOException e) {
				e.printStackTrace();
				this.connection.selfClose();
				return false;
			}
			if (n == 0)
				break;
		} while (messageBytesToWrite.hasRemaining());

		if (messageBytesToWrite.remaining() == 0) {
			if (this.messageToSend instanceof PieceMessage) {
				this.bytesWritten += ((PieceMessage) messageToSend).getBlock().limit();
			}
		}

		boolean hasRemaining = messageBytesToWrite.hasRemaining();

		if (!hasRemaining)
			this.state = SendState.IDLE;

		return !hasRemaining;
	}

	public void setMessageToSend(PeerMessage messageToSend) {
		if ((this.state == SendState.IDLE) || (this.state == SendState.READY)) {
			this.messageToSend = messageToSend;
			this.messageBytesToWrite = messageToSend.encode();
			this.state = SendState.READY;
		} else {
			throw new RuntimeException("PeerMessageHandler->setMessageToSend: this.state = " + this.state + ", can not set message.");
		}
	}

	public ByteBuffer getMessageBytesToWrite() {
		return messageBytesToWrite;
	}

	public boolean isSendingInProgress() {
		return this.state == SendState.SENDING;
	}

	public float getReadRate() {
		return this.readRate;
	}

	public float getWriteRate() {
		return this.writeRate;
	}
}
