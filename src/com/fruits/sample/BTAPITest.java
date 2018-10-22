package com.fruits.sample;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import com.fruits.bt.Client;
import com.fruits.bt.TorrentSeed;
import com.turn.ttorrent.bcodec.BDecoder;
import com.turn.ttorrent.bcodec.BEValue;

public class BTAPITest {
	public static void main(String[] args) throws Exception {
		/*
		Bitmap bitmap = new Bitmap(new byte[1]);
		bitmap.set(0);
		bitmap.set(6);
		bitmap.set(7);
		bitmap.clear(6);

		
		System.out.println(bitmap);

		System.out.println(new String(Helper.genClientId()));
				*/
		
		
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

		TorrentSeed seed = TorrentSeed.parseSeedFile("D:\\TorrentDownload4\\Wireshark-win32-1.10.0.exe.torrent");
		URL url = new URL(genURL(seed));
		URLConnection conn = url.openConnection();
		conn.setDoOutput(true);
		conn.connect();
		InputStream is = conn.getInputStream();
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		int n;
		byte[] buffer = new byte[1024];
		while (-1 != (n = is.read(buffer))) {
			os.write(buffer, 0, n);
		}
		byte[] data = os.toByteArray();
		
		is.close();
		os.close();
		
		/*
		 * d8:completei100e10:incompletei200e8:intervali1800e5:peers300:
		 */
		
		BEValue result = BDecoder.bdecode(ByteBuffer.wrap(data));
		// failure reason
		// warnging message
		// incomplete
		// complete
		// interval
		// peers
		// min interval
		// tracker id
		// peers
		Map<String, BEValue> map = result.getMap();
		int incomplete = map.get("incomplete").getInt();
		byte[] address = map.get("peers").getBytes();
		System.out.println(incomplete);
		
		if(address != null && address.length >0) {
			for(int i = 0; i < address.length / 6; i++) {
				byte[] ipBytes = Arrays.copyOfRange(address, i * 6, (i * 6 + 4));
				byte[] portBytes = Arrays.copyOfRange(address, i * 6 + 4, (i + 1) * 6);
				
				System.out.println(ipBytes.length);
				System.out.println(portBytes.length);
				
				InetAddress ip = InetAddress.getByAddress(ipBytes);
				ByteArrayInputStream bis = new ByteArrayInputStream(portBytes);
				DataInputStream dis = new DataInputStream(bis);
				int port = dis.read();
				
				System.out.println(ip.getHostAddress());
				System.out.println(port);
			}
		}		
	}
	
	private static String genURL(TorrentSeed seed) {
		String infoHash  = "";
		try{
			infoHash = URLEncoder.encode(new String(seed.getInfoHash(), "ISO-8859-1"), "ISO-8859-1");
		}catch(UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		String peerId = "";
		try{
			peerId = URLEncoder.encode(new String(Client.PEER_ID, "ISO-8859-1"), "ISO-8859-1");
		}catch(UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		long uploaded = 0; //this.downloadManager.getDownloadTask(Utils.bytes2HexString(seed.getInfoHash())).getFileMetadata().getBytesWritten();
		long downloaded = 0; //this.downloadManager.getDownloadTask(Utils.bytes2HexString(seed.getInfoHash())).getFileMetadata().getBytesRead();
		long left = seed.getRealLength() - downloaded;
		StringBuffer sb = new StringBuffer();
		sb.append(seed.getAnnounce()).append("?")
		.append("info_hash=").append(infoHash).append("&")
		.append("peer_id=").append(peerId).append("&")
		.append("uploaded=").append(uploaded).append("&")
		.append("downloaded=").append(downloaded).append("&")
		.append("left=").append(left).append("&")
		.append("compact=1").append("&")
		.append("numwant=50").append("&")
		.append("port=").append(Client.LISTENER_PORT).append("&")
		.append("event=started");
		
		System.out.println("Tracker request: " + sb.toString());
		return sb.toString();
	}
}
