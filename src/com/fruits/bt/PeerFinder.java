package com.fruits.bt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fruits.bt.DownloadTask.DownloadState;
import com.turn.ttorrent.bcodec.BDecoder;
import com.turn.ttorrent.bcodec.BEValue;

// TODO: Communicate with tracker server and get the peers from a torrent seed.
public class PeerFinder {
	static final Logger logger = LoggerFactory.getLogger(PeerFinder.class);
	/*
	 * info_hash
	 * peer_id
	 * port
	 * uploaded
	 * downloaded
	 * left
	 * compact
	 *   Usually it's 1. 
	
	 * event
	 *   started, completed, stopped
	
	 * ip
	 * numwant
	 *  By default it's 50.
	 * key
	 * trackerid
	 * 
	 * */
	public static final String PARAM_INFO_HASH = "info_hash";
	public static final String PARAM_PEER_ID = "peer_id";
	public static final String PARAM_UPLOADED = "uploaded";
	public static final String PARAM_DOWNLOADED = "downloaded";
	public static final String PARAM_LEFT = "left";
	public static final String PARAM_COMPACT = "compact"; // (required?)
	public static final int COMPACT = 1; // by default
	public static final String PARAM_EVENT = "event"; // started��completed��stopped
	public static final String PARAM_NUM_WANT = "numwant"; // (for some tracker servers, it's required?)
	public static final int NUM_WANT = 50; // by default
	public static final String PARAM_KEY = "key"; // random number to identify client (optional)
	public static final String PARAM_TRACKER_ID = "trackerid"; // (optional)
	public static final String PARAM_IP = "ip"; // (optional)
	public static final String PARAM_PORT = "port"; // listener port, (for some tracker servers, it's required?)
	// failure reason
	// warning message
	// incomplete
	// complete
	// interval
	// min interval
	// tracker id
	// peers
	public static final String RESP_FAILURE_REASON = "failure reason";
	public static final String RESP_WARNING_MESSAGE = "warning message";
	public static final String RESP_INCOMPLETE = "incomplete";
	public static final String RESP_COMPLETE = "complete";
	public static final String RESP_INTERVAL = "interval";
	public static final String RESP_MIN_INTERVAL = "min interval";
	public static final String RESP_TRACKER_ID = "tracker id";
	public static final String RESP_PEERS = "peers";

	private final DownloadManager downloadManager;

	private Map<String, ScheduledExecutorService> pingTrackerTasks = new HashMap<String, ScheduledExecutorService>();
	
	private Random random = new Random();
	
	public PeerFinder(DownloadManager downloadManager) {
		this.downloadManager = downloadManager;
	}

	// TODO: lots of exception to handle.
	public List<Peer> getPeers(TorrentSeed seed) throws MalformedURLException, IOException {
		List<Peer> peers = new ArrayList<Peer>();
		
		String infoHashString = Utils.bytes2HexString(seed.getInfoHash());
		StringBuffer request = this.createBaseURL(seed);
		if(!this.pingTrackerTasks.containsKey(infoHashString)) {
			request.append("&").append(PeerFinder.PARAM_EVENT).append("=started");
		}

		logger.trace("Tracker request: {}.", request.toString());
		
		URL url = new URL(request.toString());
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

		// If fails, some servers return a plain text and BDecoder will throw InvalidBEncodingException.
		logger.debug("Response from tracker server: {}.", new String(data));
		Map<String, BEValue> resp = BDecoder.bdecode(ByteBuffer.wrap(data)).getMap();
		// failure reason
		// warnging message
		// incomplete
		// complete
		// interval
		// peers
		// min interval
		// tracker id
		// peers
		String failureReason = null;
		BEValue failureReasonValue = resp.get(PeerFinder.RESP_FAILURE_REASON);
		if(failureReasonValue != null) {
			failureReason = failureReasonValue.getString();
			logger.trace("failure reason: " + failureReason + ".");
			throw new RuntimeException("Getting peers from trakcer failed, reason: " + failureReason + ".");
		}
		
		// reponse may have no these fields.
		BEValue incompleteValue = resp.get(PeerFinder.RESP_INCOMPLETE);
		int incomplete = -1;
		if(incompleteValue != null)
			incomplete = incompleteValue.getInt();
		
		BEValue completeValue = resp.get(PeerFinder.RESP_COMPLETE);
		int complete = -1;
		if(completeValue != null)
			complete = completeValue.getInt();
		
		BEValue intervalValue = resp.get(PeerFinder.RESP_INTERVAL);
		int interval = 60;
		if(intervalValue != null)
			interval = intervalValue.getInt();

		Object peersValue = resp.get(PeerFinder.RESP_PEERS).getValue();
		
		// TODO:
		//     No peer: e5:peerslee.
		//    One peer: e5:peers6:括 d醗
		// Multi peers: e5:peers12:��
		byte[] peersByte = null;
		if(peersValue instanceof List) {
			// for no peer, do nothing.
		}else if(peersValue instanceof byte[]){ // for one or multi peers
			peersByte = (byte[])peersValue;
		}

		if (peersByte != null && peersByte.length > 0) {
			for (int i = 0; i < peersByte.length / 6; i++) {
				byte[] ip = Arrays.copyOfRange(peersByte, i * 6, (i * 6 + 4));
				byte[] port = Arrays.copyOfRange(peersByte, i * 6 + 4, (i + 1) * 6);

				logger.trace("bytes of ip: {}.", String.valueOf(ip.length));
				logger.trace("bytes of port: {}.", String.valueOf(port.length));

				InetAddress peerIP = InetAddress.getByAddress(ip);
				ByteArrayInputStream bis = new ByteArrayInputStream(port);
				DataInputStream dis = new DataInputStream(bis);
				int peerPort = dis.readUnsignedShort();

				logger.debug("ip: {}.", peerIP.getHostAddress());
				logger.debug("port: {}.", String.valueOf(peerPort));
				
				// TODO:
				// fliter out myself.
				if(Client.LISTENER_DOMAIN.equals(peerIP.getHostAddress()) && (Client.LISTENER_PORT == peerPort))
					continue;
				Peer peer = new Peer();
				peer.setAddress(new InetSocketAddress(peerIP, peerPort));
				peers.add(peer);
			}
		}
		logger.debug("Got peers, size: {}.", peers.size());
		
		// TODO:
		// Stop the ping task in run method?
		if(!this.pingTrackerTasks.containsKey(infoHashString)) {
			ScheduledExecutorService pingTask = Executors.newSingleThreadScheduledExecutor();
			pingTask.scheduleAtFixedRate(new Runnable() {
				public void run() {
					logger.debug("PingTracker is running.");
					StringBuffer request = createBaseURL(seed);
					DownloadTask task = downloadManager.getDownloadTask(Utils.bytes2HexString(seed.getInfoHash()));
					DownloadState downloadStatus = task.getState();
					switch (downloadStatus) {
					case PENDING:
					case STARTED:
						//sendGetRequest(request.toString());
						break;
					case STOPPED:
						request.append("&").append(PeerFinder.PARAM_EVENT).append("=stopped");
						//sendGetRequest(request.toString());
						break;
					case COMPLETED:
						request.append("&").append(PeerFinder.PARAM_EVENT).append("=completed");
						//sendGetRequest(request.toString());
						break;
					}

				}
			}, interval, interval, TimeUnit.SECONDS);
			this.pingTrackerTasks.put(infoHashString, pingTask);
		}
		
		return peers;
	}
	
	public void removePingTrackerTask(String infoHash) {
		ScheduledExecutorService task = this.pingTrackerTasks.remove(infoHash);
		if(task != null)
			task.shutdown();
	}
	
	public void removePingTrackerTasks() {
		Iterator<String> iterator = this.pingTrackerTasks.keySet().iterator();
		while(iterator.hasNext()) {
			String infoHash = iterator.next();
			this.removePingTrackerTask(infoHash);
		}
	}
	
	public void sendGetRequest(String requestURL) {
		try {
			logger.debug("Ping Tracker server, request URL: {}.", requestURL);
			URL url = new URL(requestURL);
			URLConnection conn = url.openConnection();
			conn.setDoOutput(false);
			conn.setDoInput(false);
			conn.connect();
		}catch(IOException e) {
			logger.error("", e);
		}
	}
	
	// By default, 50 peers.
	/*
	public List<Peer> getPeers(TorrentSeed seed) {
		List<Peer> peers = new ArrayList<Peer>();
		Peer peer = new Peer();
		peer.setAddress(new InetSocketAddress(Client.REMOTE_DOMAIN, Client.REMOTE_PORT));
		peer.setPeerId(Client.REMOTE_PEER_ID);
		peers.add(peer);
		return peers;
	}
	*/

	/*
	 * info_hash
	 * peer_id
	 * port
	 * uploaded
	 * downloaded
	 * left
	 * compact
	 *   Usually it's 1. 
	
	 * event
	 *   started, completed, stopped
	
	 * ip
	 * numwant
	 *  By default it's 50.
	 * key
	 * trackerid
	 * 
	 * e.g.
	 * http://192.168.0.100:6969/announce?info_hash=%B3%C8%F8%E5%0D%3F%3Fp%11W%F2%C2Q~%EExX%8BH%F2
	 * &peer_id=-BC0152-%EA%0D%CC%0A%0CF%86%EEl%3D%9A%04&port=25146&natmapped=1&localip=192.168.0.100&port_type=lan
	 * &uploaded=0&downloaded=0&left=0&numwant=200&compact=1&no_peer_id=1&key=16264&event=started
	 * 
	 * Response example:
	 * Succeed:
	 * d8:completei1e10:incompletei1e8:intervali1800e5:peerslee
	 * 
	 * Failed:
	 * 
	 * 
	 */
	private StringBuffer createBaseURL(TorrentSeed seed) {
		String infoHash = "";
		try {
			infoHash = URLEncoder.encode(new String(seed.getInfoHash(), "ISO-8859-1"), "ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
			logger.error("", e);
		}
		String peerId = "";
		try {
			peerId = URLEncoder.encode(new String(Client.PEER_ID, "ISO-8859-1"), "ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
			logger.error("", e);
		}

		DownloadTask task = this.downloadManager.getDownloadTask(Utils.bytes2HexString(seed.getInfoHash()));
		FileMetadata metadata = task.getFileMetadata();
		long downloaded = metadata.getBytesWritten();
		long uploaded = metadata.getBytesRead();

		long left = seed.getRealLength() - downloaded;
		StringBuffer sb = new StringBuffer();
		sb.append(seed.getAnnounce()).append("?")
		    .append(PeerFinder.PARAM_INFO_HASH).append("=").append(infoHash).append("&")
				.append(PeerFinder.PARAM_PEER_ID).append("=").append(peerId).append("&")
				.append(PeerFinder.PARAM_UPLOADED).append("=").append(uploaded).append("&")
				.append(PeerFinder.PARAM_DOWNLOADED).append("=").append(downloaded).append("&")
				.append(PeerFinder.PARAM_LEFT).append("=").append(left).append("&")
				.append(PeerFinder.PARAM_COMPACT).append("=").append(PeerFinder.COMPACT).append("&")
				.append(PeerFinder.PARAM_NUM_WANT).append("=").append(PeerFinder.NUM_WANT).append("&")
				.append(PeerFinder.PARAM_PORT).append("=").append(Client.LISTENER_PORT).append("&")
				.append(PeerFinder.PARAM_KEY).append("=").append(random.nextInt(1000));
		
		
		return sb;
	}
}
