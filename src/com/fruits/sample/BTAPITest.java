package com.fruits.sample;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.List;

import com.fruits.bt.Bitmap;
import com.fruits.bt.FileMetadata;
import com.fruits.bt.Helper;
import com.fruits.bt.Slice;
import com.fruits.bt.TorrentSeed;

public class BTAPITest {
	public static void main(String[] args) throws Exception {
		Bitmap bitmap = new Bitmap(new byte[1]);
		bitmap.set(0);
		bitmap.set(6);
		bitmap.set(7);
		bitmap.clear(6);
		System.out.println(bitmap);

		System.out.println(new String(Helper.genClientId()));
		/*
		byte[] x = "-FRT-".getBytes();
		byte[] a = new byte[20];
		for(int i = 0; i < x.length; i++) {
			a[i] = x[i];
		}
		System.out.println(Utils.bytes2HexString(x));
		*/

		/*
		TorrentSeed seed = TorrentSeed.parseSeedFile("D:\\TorrentDownload3\\Wireshark-win32-1.10.0.exe.torrent");
		System.out.println(seed);
		FileMetadata fileMetadata = new FileMetadata(seed);
		fileMetadata.finalize();
		*/

		// Test download single file.
		/*
		TorrentSeed seed = TorrentSeed.parseSeedFile("D:\\TorrentDownload3\\Wireshark-win32-1.10.0.exe.torrent");
		FileMetadata metadata = new FileMetadata(seed);
		List<Slice> slices = metadata.getIncompletedSlices();
		System.out.println("incompleted slices: " + slices.size());
		
		RandomAccessFile raf = new RandomAccessFile("D:\\TorrentDownload3\\File\\Copy\\Wireshark-win32-1.10.0.exe","rw");
		
		for(Slice slice : slices) {
			int startPos = seed.getPieceLength() * slice.getIndex() + slice.getBegin();
			int length = slice.getLength();
		
			ByteBuffer buffer = ByteBuffer.allocate(length);
			raf.getChannel().read(buffer, startPos);
			buffer.flip();
			metadata.writeSlice(slice.getIndex(), slice.getBegin(), slice.getLength(), buffer);
		}
		
		raf.close();
		*/

		/*
		TorrentSeed seed = TorrentSeed.parseSeedFile("D:\\TorrentDownload3\\BitTorrent-4.0.3-dummy.torrent");
		System.out.println(seed);
		FileMetadata metadata = new FileMetadata(seed, true);
		
		
		TorrentSeed seed2 = TorrentSeed.parseSeedFile("D:\\TorrentDownload3\\BitTorrent-4.0.3-dummy.torrent");
		FileMetadata metadata2 = new FileMetadata(seed2, false);
		
		List<Slice> slices = metadata.getIncompletedSlices();
		System.out.println("slices: " + slices.size());
		for(Slice slice : slices) {
			ByteBuffer data = metadata.readSlice(slice);
			metadata2.writeSlice(slice.getIndex(), slice.getBegin(), slice.getLength(), data);
		}
		*/

	}
}
