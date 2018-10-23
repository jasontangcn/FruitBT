package com.fruits.sample;

import com.fruits.bt.Bitmap;
import com.fruits.bt.Utils;

public class BitmapTest {
	
	public static void main(String[] args) {
		byte[] bitfield = Utils.hexStringToBytes("0ffffffffffffffffffff8");
		Bitmap bitmap = new Bitmap(bitfield);
		System.out.println(bitmap);
	}
}
