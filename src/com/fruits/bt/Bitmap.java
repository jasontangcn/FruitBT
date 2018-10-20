package com.fruits.bt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

// TODO:
// 1. Not allowed zero length.
// 2. ByteOrder.LITTLE_ENDIAN or ByteOrder.BIG_ENDIAN

public class Bitmap {
	private final byte[] bits;
	private int length = -1; // This used a 'real length'.
                           // size is used a real length of internal byte array.
	// I can not expand automatically, so length must be > 1.
	// otherwise if the bits.length == 0, it does not make sense.
	public Bitmap(int length) {
		if(length < 0)
			throw new RuntimeException("length must be > 1.");
		this.length = length;
		this.bits = new byte[(length >> 3) + 1];
	}

	public Bitmap(byte[] bits) {
		if(bits == null || bits.length == 0)
			throw new NullPointerException("bits can not be null or zero length.");
		this.bits = bits;
	}

	public int length() {
		return this.length;
	}

	public void setLength(int length) {
		this.length = length;
	}

	public int size() {
		return this.bits.length * 8;
	}
	
	public void set(int index) {
		this.bits[index >> 3] |= (1 << (index & 0x07));
	}

	public boolean get(int index) {
		return (this.bits[index >> 3] & (1 << (index & 0x07))) != 0;
	}

	public void clear(int index) {
		this.bits[index >> 3] &= ~(1 << (index & 0x07));
	}

	public byte[] toByteArray() {
		return Arrays.copyOf(this.bits, bits.length);
	}

	public static Bitmap valueOf(ByteBuffer bytes) {
		byte[] bits = new byte[bytes.remaining()];
		bytes.get(bits);
		return new Bitmap(bits);
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[(length=").append(this.length).append(")(");
		int n;
		if (length == -1) {
			n = this.size();
		} else {
			n = length;
		}

		for (int i = 0; i < n; i++) {
			if (this.get(i)) {
				sb.append("{").append(i).append("}");
			}
		}

		sb.append(")]");
		return sb.toString();
	}
	
	public static void main(String[] args) {
		Bitmap bitmap = new Bitmap(new byte[1]);
		bitmap.set(0);
		bitmap.set(6);
		bitmap.set(7);
		bitmap.clear(6);
		System.out.println(bitmap);
	}
}
