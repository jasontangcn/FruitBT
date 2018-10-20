package com.fruits.bt;

import java.net.InetSocketAddress;

public class Peer {
	private InetSocketAddress address;

	private String peerId; // same with the peerId passed to Tracker
	private String infoHash;
	private Bitmap bitfield; // self should not use this field, it should be null.

	public Peer() {
	}

	public InetSocketAddress getAddress() {
		return address;
	}

	public void setAddress(InetSocketAddress address) {
		this.address = address;
	}

	public String getPeerId() {
		return peerId;
	}

	public void setPeerId(String peerId) {
		this.peerId = peerId;
	}

	public String getInfoHash() {
		return infoHash;
	}

	public void setInfoHash(String infoHash) {
		this.infoHash = infoHash;
	}

	public Bitmap getBitfield() {
		return bitfield;
	}

	public void setBitfield(Bitmap bitfield) {
		this.bitfield = bitfield;
	}

	@Override
	public String toString() {
		return "Peer [address=" + address + ", peerId=" + peerId + ", infoHash=" + infoHash + ", bitfield=" + bitfield + "].\n";
	}
}
