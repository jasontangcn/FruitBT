package com.fruits.bt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class HandshakeHandler {
	private final PeerConnection connection;

	private ByteBuffer readBuffer = ByteBuffer.allocate(HandshakeMessage.HANDSHAKE_MESSAGE_LENGTH);
	private ByteBuffer messageBytesToSend;

	public HandshakeHandler(PeerConnection connection) {
		this.connection = connection;
	}

	public HandshakeMessage readMessage() {
		SocketChannel socketChannel = this.connection.getChannel();
		try {
			if (socketChannel.read(readBuffer) == -1) { // IOException
				// Channel is closed? 
				// To close the channel? 
				// To unregister the channel from the Selector with the cancel() method?
				//
				this.connection.close();
				return null;
			} else {
				if (readBuffer.hasRemaining())
					return null;
				readBuffer.flip();
				return HandshakeMessage.decode(readBuffer);
			}
		} catch (IOException e) {
			e.printStackTrace();
			this.connection.close();
			return null;
		}
	}

	// @return Message has been totally written to peer or not?
	public boolean writeMessage() {
		SocketChannel socketChannel = this.connection.getChannel();
		// TODO: if 0 bytes written, it leads to recreate the message.
		do {
			int n = -1;
			try {
				n = socketChannel.write(messageBytesToSend); // IOException
			} catch (IOException e) {
				e.printStackTrace();
				this.connection.close();
				return false;
			}
			if (n == 0)
				break;
		} while (messageBytesToSend.hasRemaining());

		if (messageBytesToSend.hasRemaining()) {
			return false;
		} else {
			messageBytesToSend = null;
			return true;
		}
	}

	public void setMessageToSend(HandshakeMessage message) {
		this.messageBytesToSend = HandshakeMessage.encode(message);
	}

	public boolean isSendingInProgress() {
		return (messageBytesToSend != null) && messageBytesToSend.hasRemaining();
	}
}
