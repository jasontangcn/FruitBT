package com.fruits.sample;

import java.nio.ByteBuffer;

import com.turn.ttorrent.bcodec.BDecoder;
import com.turn.ttorrent.bcodec.BEncoder;

public class BDecoderTest {

	public static void main(String[] args) throws Exception {
		byte[] data = "Invalid paramters.".getBytes();
		
		BDecoder.bdecode(ByteBuffer.wrap(data));
	}

}
