package com.fruits.bt;

import java.net.InetSocketAddress;
import java.util.Arrays;

public class Peer {
	private InetSocketAddress address;

	private byte[] peerId; // same with the peerId passed to Tracker
	private byte[] infoHash;
	private String infoHashString;
	private Bitmap bitfield; // self should not use this field, it should be null.

	public Peer() {
	}

	public InetSocketAddress getAddress() {
		return address;
	}

	public void setAddress(InetSocketAddress address) {
		this.address = address;
	}

	public byte[] getPeerId() {
		return peerId;
	}

	public void setPeerId(byte[] peerId) {
		this.peerId = peerId;
	}
	
	public byte[] getInfoHash() {
		return this.infoHash;
	}

	public void setInfoHash(byte[] infoHash) {
		this.infoHash = infoHash;
		this.infoHashString = Utils.bytes2HexString(infoHash);
	}
	
	public void setInfoHashString(String infoHashString) {
		this.infoHashString = infoHashString;
		this.infoHash = Utils.hexStringToBytes(infoHashString);
	}

	public String getInfoHashString() {
		return this.infoHashString;
	}

	public Bitmap getBitfield() {
		return bitfield;
	}

	public void setBitfield(Bitmap bitfield) {
		this.bitfield = bitfield;
	}

	@Override
	public String toString() {
		return "Peer [address=" + address + ", peerId=" + Arrays.toString(peerId) + ", infoHash=" + Arrays.toString(infoHash) + ", infoHashString=" + infoHashString
				+ ", bitfield=" + bitfield + "]";
	}
}
