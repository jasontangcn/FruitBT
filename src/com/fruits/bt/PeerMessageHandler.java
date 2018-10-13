package com.fruits.bt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
	private ByteBuffer messageBytes = ByteBuffer.allocate(4 + 9 + 16 * 1024 * 4); // the longest message is piece
	private PeerMessage messageToSend;
	private ByteBuffer messageBytesOut;

	private long pieceMessageBytesRead;
	private long pieceMessageBytesWrite;
	
	// A separate thread will calculate the download rate and upload rate by every 10 seconds.
	private long pieceMessageBytesReadLastPeriod;
	private long timeLastReadPiece = System.currentTimeMillis();
	private long timeLastWritePiece = System.currentTimeMillis();
	private long pieceMessageBytesWriteLastPeriod;
	private float readPieceRate;
	private float writePieceRate;

	
	public PeerMessageHandler(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}
	
	public void calculateReadWriteRate() {
		// bytes read/MS
		this.readPieceRate = ((this.pieceMessageBytesRead - this.pieceMessageBytesReadLastPeriod) / (System.currentTimeMillis() - this.timeLastReadPiece));
		// bytes write/MS
		this.writePieceRate = ((this.pieceMessageBytesWrite - this.pieceMessageBytesWriteLastPeriod) / (System.currentTimeMillis() - this.timeLastWritePiece));
		
		System.out.println("PeerMessageHandler [pieceMessageBytesReadLastPeriod = " + pieceMessageBytesReadLastPeriod
				 + ", pieceMessageBytesRead = " + pieceMessageBytesRead + ", timeLastReadPiece = " + timeLastWritePiece + 
				 ",\n" + "pieceMessageBytesWrite = " + pieceMessageBytesWrite + ", pieceMessageBytesWriteLastPeriod = " + pieceMessageBytesWriteLastPeriod 
		 + ", timeLastWritePiece = " + timeLastReadPiece +
		 ",\n" + "readPieceRate=" + readPieceRate + " byte/ms , writePieceRate=" + writePieceRate + " bytes/ms]." + "\n");
		
		this.pieceMessageBytesReadLastPeriod = this.pieceMessageBytesRead;
		this.timeLastReadPiece = System.currentTimeMillis();
		this.pieceMessageBytesWriteLastPeriod = this.pieceMessageBytesWrite;
		this.timeLastWritePiece = System.currentTimeMillis();
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
			this.messageBytes.limit(PeerMessageHandler.PEER_MESSAGE_LENGTH_PREFIX);
			socketChannel.read(messageBytes);
			if (messageBytes.hasRemaining()) {
				return null;
			}
			this.lengthPrefix = messageBytes.getInt(0);
			System.out.println("PeerMessageHandler:readMessage -> lengthPrefix : " + lengthPrefix + ".");
			messageBytes.limit(this.lengthPrefix + PeerMessageHandler.PEER_MESSAGE_LENGTH_PREFIX);
		}

		int count = socketChannel.read(messageBytes);
		if(-1 == count)
			return null; // TODO: should close the connection.
		if (messageBytes.hasRemaining()) {
			return null;
		} else {
			messageBytes.rewind();
			ByteBuffer copy = ByteBuffer.wrap(Arrays.copyOf(messageBytes.array(), messageBytes.limit()));
			this.lengthPrefix = -1;
	        PeerMessage peerMessage = PeerMessage.parseMessage(copy);
	        
	        if(peerMessage instanceof PieceMessage) {
	        	int n = ((PieceMessage)peerMessage).getBlock().limit();
				this.pieceMessageBytesRead += n;
	        }
	        
	        return peerMessage;
		}
	}
	
	// @return Message has been totally written to peer or not?
	public boolean writeMessage() throws IOException {
		// TODO: if 0 bytes written, it leads to recrete the message.
		do {
			int n = socketChannel.write(this.messageBytesOut);
			if(0 == n)
				break;
		}while(messageBytesOut.hasRemaining());
		
		if(0 == messageBytesOut.remaining()) {
			if(this.messageToSend instanceof PieceMessage) {
				int n = ((PieceMessage)messageToSend).getBlock().limit();
				this.pieceMessageBytesWrite += n;
			}
		}
		
		return !messageBytesOut.hasRemaining();
	}
	
	public void setMessageToSend(PeerMessage messageToSend) {
		this.messageToSend = messageToSend;
		this.messageBytesOut =  messageToSend.encode();
	}

	public ByteBuffer getMessageBytesOut() {
		return messageBytesOut;
	}

	public void setMessageBytesOut(ByteBuffer messageBytesOut) {
		this.messageBytesOut = messageBytesOut;
	}

	public boolean isSendingMessageInProgress() {
		return (null != this.messageBytesOut) && this.messageBytesOut.hasRemaining();
	}

	public float getReadPieceRate() {
		calculateReadWriteRate();
		return this.readPieceRate;
	}

	public float getWritePieceRate() {
		calculateReadWriteRate();
		return this.writePieceRate;
	}
}
