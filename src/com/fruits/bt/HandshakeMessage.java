package com.fruits.bt;

import java.nio.ByteBuffer;

public class HandshakeMessage {
	public static final int HANDSHAKE_MESSAGE_LENGTH = 68; // in bytes
	public static final byte HANDSHAKE_MESSAGE_PSTRLEN = 19;
	private static final String HANDSHAKE_MESSAGE_PSTR = "BitTorrent protocol";

	private byte pstrLen = HandshakeMessage.HANDSHAKE_MESSAGE_PSTRLEN;
	private byte[] pstr = HandshakeMessage.HANDSHAKE_MESSAGE_PSTR.getBytes(); // byte[19] = "BitTorrent protocol"
	private byte[] reserved = new byte[8];
	private byte[] infoHash; // byte[20];
	private byte[] peerId; // byte[20];

	public HandshakeMessage(byte[] infoHash, byte[] peerId) {
		this.infoHash = infoHash;
		this.peerId = peerId;
	}

	public static HandshakeMessage decode(ByteBuffer messageBytes) {
		messageBytes.get();
		byte[] pstr = new byte[19];
		byte[] reserved = new byte[8];
		byte[] infoHash = new byte[20];
		byte[] peerId = new byte[20];

		messageBytes.get(pstr).get(reserved).get(infoHash).get(peerId);
		// TODO: validate the data.
		return new HandshakeMessage(infoHash, peerId);
	}

	public static ByteBuffer encode(HandshakeMessage message) {
		ByteBuffer messageBytes = ByteBuffer.allocate(HandshakeMessage.HANDSHAKE_MESSAGE_LENGTH);
		messageBytes.put(message.getPstrLen());
		messageBytes.put(message.getPstr());
		messageBytes.put(message.getReserved());
		messageBytes.put(message.getInfoHash());
		messageBytes.put(message.getPeerId());
		messageBytes.flip();
		return messageBytes;
	}

	public byte getPstrLen() {
		return pstrLen;
	}

	public byte[] getPstr() {
		return pstr;
	}

	public void setPstr(byte[] pstr) {
		this.pstr = pstr;
	}

	public byte[] getReserved() {
		return reserved;
	}

	public void setReserved(byte[] reserved) {
		this.reserved = reserved;
	}

	public byte[] getInfoHash() {
		return infoHash;
	}

	public void setInfoHash(byte[] infoHash) {
		this.infoHash = infoHash;
	}

	public String getInfoHashString() {
		return Utils.bytes2HexString(this.infoHash);
	}

	public byte[] getPeerId() {
		return peerId;
	}

	public String getPeerIdString() {
		return Utils.bytes2HexString(this.peerId);
	}

	public void setPeerId(byte[] peerId) {
		this.peerId = peerId;
	}

	public String toString() {
		return "infoHash = " + this.getInfoHashString() + ", peerId = " + this.getPeerIdString() + ".";
	}
}