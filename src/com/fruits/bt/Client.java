package com.fruits.bt;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.Properties;

public class Client {
	public static String DOWNLOAD_TASKS_TEMP_FILE; // = "D:\\TorrentDownload\\downloadTasks.tmp";
	public static String DOWNLOAD_TEMP_DIR; // = "D:\\TorrentDownload";

	public static String LISTENER_DOMAIN; // = "127.0.0.1";
	public static int LISTENER_PORT; // = 8888;

	public static String PEER_ID;

	public static String REMOTE_DOMAIN; // = "127.0.0.1";
	public static int REMOTE_PORT; // = 8888;

	public static String REMOTE_PEER_ID;

	private PeerConnectionManager connectionManager;
	private DownloadManager downloadManager;

	static {
		Properties props = System.getProperties();
		DOWNLOAD_TASKS_TEMP_FILE = props.getProperty("download.tasks.tmp.file");
		DOWNLOAD_TEMP_DIR = props.getProperty("download.tmp.dir");

		LISTENER_DOMAIN = props.getProperty("listener.domain");
		LISTENER_PORT = Integer.parseInt(props.getProperty("listener.port"));
		PEER_ID = props.getProperty("peer.id");

		REMOTE_DOMAIN = props.getProperty("remote.domain");
		REMOTE_PORT = Integer.parseInt(props.getProperty("remote.port"));
		REMOTE_PEER_ID = props.getProperty("remote.peer.id");

		System.out.println(DOWNLOAD_TASKS_TEMP_FILE + "," + DOWNLOAD_TEMP_DIR + "," + LISTENER_DOMAIN + "," + LISTENER_PORT + "," 
		                   + PEER_ID + "," + REMOTE_DOMAIN + "," + REMOTE_PORT + "," + REMOTE_PEER_ID);
	}

	public static void main(String[] args) {
		Client client = new Client();
		client.start();
	}

	public void start() {
		// 1. Load metadata for the files downloading/downloaded.
		// 2. Start listener for other peers.
		// 3. Start to download the files.
		//    (1) Get peers from tracker.
		//    (2) Connect to peers and get bitfield.
		//    (3) Download pieces(slices) from peers based on specific strategies.
		//        which peer is the fast?
		//        which piece is rare?
		//    (4) keep_alive, choke, unchoke, interested, not_interested, port
		//        bitfield, request, have, cancel
		//        Most import messages: bitfield, request, have

		// TODO: Its better to bind the socket with a IP instead of a domain.

		File tasksFile = new File(Client.DOWNLOAD_TASKS_TEMP_FILE);
		if (!tasksFile.exists()) {
			tasksFile.createNewFile();
		}

		this.connectionManager = new PeerConnectionManager(new InetSocketAddress(Client.LISTENER_DOMAIN, Client.LISTENER_PORT));
		this.downloadManager = new DownloadManager(connectionManager);
		this.connectionManager.setDownloadManager(downloadManager);

		connectionManager.start();

		//downloadManager.addDownloadTask("doc\\\\Wireshark-win32-1.10.0.exe.torrent");
		//downloadManager.addDownloadTask("D:\\\\TorrentDownload2\\\\Wireshark-win32-1.10.0.exe.torrent");
		//downloadManager.startDownloadFile("b3c8f8e50d3f3f701157f2c2517eee78588b48f2");
	}
}
