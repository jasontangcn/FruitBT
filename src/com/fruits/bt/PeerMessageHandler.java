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
	private final SocketChannel socketChannel;
	// TODO: Check the definition in TorrentSeed.
	private ByteBuffer readBuffer = ByteBuffer.allocate(4 + 9 + 16 * 1024 * 4); // the longest message is piece
	private PeerMessage messageToSend;
	private ByteBuffer messageBytesToWrite;

	private long bytesRead;
	private long bytesWritten;
	
	// A separate thread will calculate the download rate and upload rate by every 10 seconds.
	private long bytesReadPrePeriod;
	private long timePreRead = System.currentTimeMillis();
	private long timePreWrite = System.currentTimeMillis();
	private long bytesWritePrePeriod;
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
	
	public PeerMessageHandler(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}
	
	public void calculateReadWriteRate() {
		// bytes read/ms
		this.readRate = ((this.bytesRead - this.bytesReadPrePeriod) / (System.currentTimeMillis() - this.timePreRead));
		// bytes write/ms
		this.writeRate = ((this.bytesWritten - this.bytesWritePrePeriod) / (System.currentTimeMillis() - this.timePreWrite));
		
		System.out.println("PeerMessageHandler [bytesReadPrePeriod = " + bytesReadPrePeriod + ", bytesRead = " + bytesRead + ", timePreRead = " + timePreRead + ",\n"
				+ "bytesWritten = " + bytesWritten + ", bytesWritePrePeriod = " + bytesWritePrePeriod + ", timePreWrite = " + timePreWrite + ",\n" 
				+ "readRate = " + readRate + " bytes/ms , writeRate = " + writeRate + " bytes/ms]." + "\n");
		
		this.bytesReadPrePeriod = this.bytesRead;
		this.timePreRead = System.currentTimeMillis();
		this.bytesWritePrePeriod = this.bytesWritten;
		this.timePreWrite = System.currentTimeMillis();
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
	public PeerMessage readMessage() throws IOException {
		if (-1 == lengthPrefix) {
			this.readBuffer.limit(PeerMessageHandler.PEER_MESSAGE_LENGTH_PREFIX);
			socketChannel.read(readBuffer);
			if (readBuffer.hasRemaining()) {
				return null;
			}
			this.lengthPrefix = readBuffer.getInt(0);
			System.out.println("PeerMessageHandler:readMessage -> lengthPrefix : " + lengthPrefix + ".");
			readBuffer.limit(this.lengthPrefix + PeerMessageHandler.PEER_MESSAGE_LENGTH_PREFIX);
		}

		int count = socketChannel.read(readBuffer);
		if(-1 == count)
			return null; // TODO: should close the connection.
		if (readBuffer.hasRemaining()) {
			return null;
		} else {
			readBuffer.rewind();
			ByteBuffer copy = ByteBuffer.wrap(Arrays.copyOf(readBuffer.array(), readBuffer.limit()));
			this.lengthPrefix = -1;
	        PeerMessage peerMessage = PeerMessage.parseMessage(copy);
	        
	        if(peerMessage instanceof PieceMessage) {
				this.bytesRead += ((PieceMessage)peerMessage).getBlock().limit();
	        }
	        
	        return peerMessage;
		}
	}
	
	// @return Message has been totally written to peer or not?
	public boolean writeMessage() throws IOException {
<<<<<<< HEAD
		if(SendState.READY == this.state) {
			this.state = SendState.SENDING;
		}else if(SendState.SENDING == this.state) {
		}else {
			throw new RuntimeException("PeerMessageHandler is in illegal status.");
		}
		
=======
		if(SendState.READY != this.state) {
			throw new RuntimeException("PeerMessageHandler is in illegal status.");
		}
		
		this.state = SendState.SENDING;
>>>>>>> refs/remotes/origin/master
		// TODO: if 0 bytes written, it leads to recrete the message.
		do {
			int n = socketChannel.write(this.messageBytesToWrite);
			if(0 == n)
				break;
		}while(messageBytesToWrite.hasRemaining());
		
		if(0 == messageBytesToWrite.remaining()) {
			if(this.messageToSend instanceof PieceMessage) {
				this.bytesWritten += ((PieceMessage)messageToSend).getBlock().limit();
			}
		}
		
		boolean hasRemaining = messageBytesToWrite.hasRemaining();
		
		if(!hasRemaining)
			this.state = SendState.IDLE;
	
		
		return !hasRemaining;
	}
	
	public void setMessageToSend(PeerMessage messageToSend) {
<<<<<<< HEAD
		if(SendState.IDLE == this.state || SendState.READY == this.state) {
		    this.messageToSend = messageToSend;
		    this.messageBytesToWrite =  messageToSend.encode();
		    this.state = SendState.READY;
		}else {
			throw new RuntimeException("PeerMessageHandler->setMessageToSend: this.state = " + this.state + ", can not set message.");
		}
=======
		this.messageToSend = messageToSend;
		this.messageBytesToWrite =  messageToSend.encode();
		this.state = SendState.READY;
>>>>>>> refs/remotes/origin/master
	}

	public ByteBuffer getMessageBytesToWrite() {
		return messageBytesToWrite;
	}

	public boolean isSendingInProgress() {
		return SendState.SENDING == this.state;
	}

	public float getReadRate() {
		calculateReadWriteRate();
		return this.readRate;
	}

	public float getWriteRate() {
		calculateReadWriteRate();
		return this.writeRate;
	}
}
