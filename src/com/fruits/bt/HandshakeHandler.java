package com.fruits.bt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class HandshakeHandler {
	private final SocketChannel socketChannel;
	
	private ByteBuffer messageBytesIn = ByteBuffer.allocate(HandshakeMessage.HANDSHAKE_MESSAGE_LENGTH);
	private ByteBuffer messageBytesToSend;
			
	public HandshakeHandler(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}
	
	public HandshakeMessage readMessage() throws IOException {
		if (-1 == socketChannel.read(messageBytesIn)) {
			// Channel is closed? 
			// To close the channel? 
			// To unregister the channel from the Selector with the cancel() method?
			//
			this.socketChannel.close();
			return null;
		} else {
			if (messageBytesIn.hasRemaining())
				return null;
			messageBytesIn.flip();
			return HandshakeMessage.decode(messageBytesIn);
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
	
	public void setHandshakeMessageToSend(HandshakeMessage handshakeMessage) {
		this.messageBytesToSend =  HandshakeMessage.encode(handshakeMessage);
	}

	public boolean isSendingMessageInProgress() {
		return (null != messageBytesToSend) && messageBytesToSend.hasRemaining();
	}
}
