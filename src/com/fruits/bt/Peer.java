package com.fruits.bt;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

public class Peer {
	private InetSocketAddress address;

	private byte[] peerId; // same with the peerId passed to Tracker
	private byte[] infoHash;
	private String infoHashString;
	private Bitmap bitfield; // self should not use this field, it should be null.

	public Peer() {
	}

	public static byte[] createPeerId() {
		ByteBuffer buffer = ByteBuffer.allocate(20);
		buffer.put("-FRT-".getBytes());

		String ip = "";
		try {
			ip = InetAddress.getLocalHost().getHostAddress();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Map<String, String> env = System.getenv();
		String userDomain = env.get("USERDOMAIN");
		String computerName = env.get("COMPUTERNAME");
		String userName = env.get("USERNAME");

		StringBuffer sb = new StringBuffer();

		if (userDomain != null && userDomain.length() > 0) {
			sb.append(userDomain).append("-");
		}

		if (computerName != null && computerName.length() > 0) {
			sb.append(computerName).append("-");
		}

		if (userName != null && userName.length() > 0) {
			sb.append(userName);
		}

		byte[] bytes = sb.toString().getBytes();
		for (byte bt : bytes) {
			if (buffer.hasRemaining()) {
				buffer.put(bt);
			} else {
				break;
			}
		}

		while (buffer.hasRemaining()) {
			buffer.putInt((byte) '-');
		}

		return buffer.array();
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
