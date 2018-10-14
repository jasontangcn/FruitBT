package com.fruits.bt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class HandshakeHandler {
	private final SocketChannel socketChannel;
	
	private ByteBuffer readBuffer = ByteBuffer.allocate(HandshakeMessage.HANDSHAKE_MESSAGE_LENGTH);
	private ByteBuffer messageBytesToSend;
			
	public HandshakeHandler(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}
	
	public HandshakeMessage readMessage() throws IOException {
		if (-1 == socketChannel.read(readBuffer)) {
			// Channel is closed? 
			// To close the channel? 
			// To unregister the channel from the Selector with the cancel() method?
			//
			this.socketChannel.close();
			return null;
		} else {
			if (readBuffer.hasRemaining())
				return null;
			readBuffer.flip();
			return HandshakeMessage.decode(readBuffer);
		}
	}

	// @return Message has been totally written to peer or not?
	public boolean writeMessage() throws IOException {
		// TODO: if 0 bytes written, it leads to recrete the message.
		do {
			int n = socketChannel.write(messageBytesToSend);
			if(0 == n)
				break;
		}while(messageBytesToSend.hasRemaining());
		
		
		if(messageBytesToSend.hasRemaining()) {
			return false;
		}else {
			messageBytesToSend = null;
			return true;
		}
	}
	
	public void setMessageToSend(HandshakeMessage message) {
		this.messageBytesToSend =  HandshakeMessage.encode(message);
	}

	public boolean isSendingInProgress() {
		return (null != messageBytesToSend) && messageBytesToSend.hasRemaining();
	}
}
