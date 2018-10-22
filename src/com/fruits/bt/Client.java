package com.fruits.bt;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Properties;

public class Client {
	public static String DOWNLOAD_DIR; // "D:\\TorrentDownload";
	public static String DOWNLOAD_TASKS_FILE; // "D:\\TorrentDownload\\downloadTasks.tmp"

	public static byte[] PEER_ID;
	public static String LISTENER_DOMAIN; // "127.0.0.1"
	public static int LISTENER_PORT; // 8888

	public static byte[] REMOTE_PEER_ID;
	public static String REMOTE_DOMAIN; // "127.0.0.1"
	public static int REMOTE_PORT; // 6666

	private PeerConnectionManager connectionManager;
	private DownloadManager downloadManager;

	static {
		Properties props = System.getProperties();
		DOWNLOAD_DIR = props.getProperty("download.dir");
		DOWNLOAD_TASKS_FILE = props.getProperty("download.tasks.file");

		LISTENER_DOMAIN = props.getProperty("listener.domain");
		LISTENER_PORT = Integer.parseInt(props.getProperty("listener.port"));
		PEER_ID = Helper.genClientId(); //props.getProperty("peer.id");

		REMOTE_DOMAIN = props.getProperty("remote.domain");
		REMOTE_PORT = Integer.parseInt(props.getProperty("remote.port"));
		REMOTE_PEER_ID = Helper.genClientId(); //props.getProperty("remote.peer.id");

		System.out.println(DOWNLOAD_DIR + ", " + DOWNLOAD_TASKS_FILE + ", " + new String(PEER_ID) + ", " + LISTENER_PORT + ", " + LISTENER_DOMAIN + ", "
				+ new String(REMOTE_PEER_ID) + ", " + REMOTE_DOMAIN + ", " + REMOTE_PORT + ".");
	}

	public static void main(String[] args) throws IOException {
		Client client = new Client();
		client.start();

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("System is shutting down.");
				long begin = System.currentTimeMillis();
				// This is used to close the channel for the file in FileMetadata.
				client.stop();
				System.out.println("Shutting down system spent " + (System.currentTimeMillis() - begin) + " ms.");
			}
		});
	}

	public void start() throws IOException {
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

		File taskFile = new File(Client.DOWNLOAD_TASKS_FILE);
		if (!taskFile.exists()) {
			taskFile.createNewFile();
		}

		this.connectionManager = new PeerConnectionManager(new InetSocketAddress(Client.LISTENER_DOMAIN, Client.LISTENER_PORT));
		this.downloadManager = new DownloadManager(connectionManager);
		this.connectionManager.setDownloadManager(downloadManager);

		connectionManager.start(new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread thead, Throwable exception) {
				System.out.println("PeerConnectionManager failed, we are going to fail the system.");
				exception.printStackTrace();
				System.exit(0);
			}
		}); // connectionManager is run in a new thread.

		//downloadManager.addDownloadTask("D:\\TorrentDownload2\\Wireshark-win32-1.10.0.exe.torrent");
		//downloadManager.startDownloadTask("b3c8f8e50d3f3f701157f2c2517eee78588b48f2");
	}

	public void stop() {
		this.downloadManager.stopAllDownloadTasks();
		this.connectionManager.stop();
	}
}
