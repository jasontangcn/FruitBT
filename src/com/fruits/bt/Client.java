package com.fruits.bt;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client {
	static final Logger logger = LoggerFactory.getLogger(Client.class);
	
	public static String DOWNLOAD_DIR; // "D:\\TorrentDownload";
	public static String DOWNLOAD_TASKS_FILE; // "D:\\TorrentDownload\\downloadTasks.tmp"

	public static byte[] PEER_ID;
	public static String LISTENER_DOMAIN; // "127.0.0.1"
	public static int LISTENER_PORT; // 8888

	//public static byte[] REMOTE_PEER_ID;
	//public static String REMOTE_DOMAIN; // "127.0.0.1"
	//public static int REMOTE_PORT; // 6666

	private PeerConnectionManager connectionManager;
	private DownloadManager downloadManager;

	static {
		// Initialize it by JVM options
		Properties props = System.getProperties();
		DOWNLOAD_DIR = props.getProperty("download.dir");
		DOWNLOAD_TASKS_FILE = props.getProperty("download.tasks.file");

		LISTENER_DOMAIN = props.getProperty("listener.domain");
		LISTENER_PORT = Integer.parseInt(props.getProperty("listener.port"));
		PEER_ID = Peer.createPeerId(); //props.getProperty("peer.id");

		//REMOTE_DOMAIN = props.getProperty("remote.domain");
		//REMOTE_PORT = Integer.parseInt(props.getProperty("remote.port"));
		//REMOTE_PEER_ID = Helper.genClientId(); //props.getProperty("remote.peer.id");
		
		/*
		logger.trace(DOWNLOAD_DIR + ", " + DOWNLOAD_TASKS_FILE + ", " + new String(PEER_ID) + ", " + LISTENER_PORT + ", " + LISTENER_DOMAIN + ", "
				+ new String(REMOTE_PEER_ID) + ", " + REMOTE_DOMAIN + ", " + REMOTE_PORT + ".");
				*/
		logger.debug("{}, {}, {}, {}, {}.", DOWNLOAD_DIR, DOWNLOAD_TASKS_FILE, new String(PEER_ID), LISTENER_PORT, LISTENER_DOMAIN);
	}

	public static void main(String[] args) throws IOException {
		Client system = new Client();
		system.start();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				logger.error("Shutting down the system.");
				long begin = System.currentTimeMillis();
				// This is used to close the channel for the file in FileMetadata.
				system.stop();
				logger.error("Shutting down the system spent " + (System.currentTimeMillis() - begin) + " ms.");
			}
		});
	}

	public void start() throws IOException {
		// 1. Load metadata for the files downloading/downloaded.
		// 2. Start listener for other peers.
		// 3. Start to download the files.
		//    (1) Get peers from tracker.
		//    (2) Connect to peers and get bitfield.
		//    (3) Download pieces(slices) from peers based on strategies.
		//        Random, Rarest First, Sequential
		//    (4) keep_alive, choke, unchoke, interested, not_interested
		//        bitfield, request, piece, have, cancel, port
		//        Most import messages: bitfield, request, piece, have
		// TODO: It is better to bind the socket with a IP instead of a domain.

		File taskFile = new File(Client.DOWNLOAD_TASKS_FILE);
		if (!taskFile.exists()) {
			taskFile.createNewFile();
		}

		/*
		 * This class implements an IP Socket Address (IP address + port number)
     * It can also be a pair (hostname + port number), in which case an attempt
     * will be made to resolve the hostname. If resolution fails then the address
     * is said to be <I>unresolved</I> but can still be used on some circumstances
     * like connecting through a proxy.
		 */
		this.downloadManager = new DownloadManager();
		this.connectionManager = new PeerConnectionManager(new InetSocketAddress(Client.LISTENER_DOMAIN, Client.LISTENER_PORT), downloadManager,
				new Thread.UncaughtExceptionHandler() {
					public void uncaughtException(Thread t, Throwable e) {
						logger.error("Connection manager failed, it is failing the system.", e);
						System.exit(0);
					}
				});
		downloadManager.setConnectionManager(connectionManager);

		
		//downloadManager.addDownloadTask("D:\\TorrentDownload5\\129952FBCED69192FB110391D6AA20F7E7AFAA80.torrent");
		downloadManager.addDownloadTask("D:\\TorrentDownload4\\Wireshark-win32-1.10.0.exe.torrent");
		//downloadManager.startDownloadTask("b3c8f8e50d3f3f701157f2c2517eee78588b48f2");
	}

	public void stop() {
		this.downloadManager.stopAllDownloadTasks();
		this.connectionManager.stop();
	}
}
