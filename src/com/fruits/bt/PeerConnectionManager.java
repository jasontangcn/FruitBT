package com.fruits.bt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fruits.bt.PeerConnection.State;
import com.fruits.bt.PeerMessage.ChokeMessage;
import com.fruits.bt.PeerMessage.HaveMessage;
import com.fruits.bt.PeerMessage.UnchokeMessage;

/*
 * Regarding the network excepton.
 * 
 * Server->Client
 * Anybody close the channel, this channel will not be selected in the next round.
 * But the peer do not know these, the channel still is considered as open till try to read/write data and it will throw exception.
 * 
 * Exception in thread "main" java.io.IOException: An established connection was aborted by the software in your host machine
 * 				at sun.nio.ch.SocketDispatcher.write0(Native Method)
 * 				at sun.nio.ch.SocketDispatcher.write(Unknown Source)
 * 				at sun.nio.ch.IOUtil.writeFromNativeBuffer(Unknown Source)
 * 				at sun.nio.ch.IOUtil.write(Unknown Source)
 * 				at sun.nio.ch.SocketChannelImpl.write(Unknown Source)
 * 				at com.fruits.sample.NIOClientTest.main(NIOClientTest.java:51)
 */

/*
 * How to manage the system? Client?
 * If component fails, how to deal it? e.g. PeerConnecitonManger fails because of IOException.
 * 
 */
public class PeerConnectionManager implements Runnable {
	static final Logger logger = LoggerFactory.getLogger(PeerConnectionManager.class);
	
	private volatile boolean stopped = false;
	
	private final InetSocketAddress listenEndpoint;
	private final DownloadManager downloadManager;
	private final Thread connectionManagerThread;
	
	private Selector selector;
	private ServerSocketChannel serverChannel; // For accepting incoming connections from peers.
	// TODO: VERY IMPORTANT!
	// This lock is based on thread.
	private final ReentrantLock selectorLock = new ReentrantLock();

	// info_hash -> PeerConnection
	// Incoming or outgoing connection is added to it after bitfield exchanging completed.
	private Map<String, List<PeerConnection>> peerConnections = new HashMap<String, List<PeerConnection>>();

	private ScheduledExecutorService choker = Executors.newSingleThreadScheduledExecutor();
	private ScheduledExecutorService aliveManager = Executors.newSingleThreadScheduledExecutor();

	public PeerConnectionManager(InetSocketAddress listenEndpoint, DownloadManager downloadManager, Thread.UncaughtExceptionHandler handler) throws IOException {
		this.listenEndpoint = listenEndpoint;
		this.downloadManager = downloadManager;
		
		this.connectionManagerThread = new Thread(this);
		this.connectionManagerThread.setUncaughtExceptionHandler(handler);
		this.connectionManagerThread.start();
		
		this.selector = Selector.open(); // This may throw IOException.

		// Choke algorithm
		choker.scheduleAtFixedRate(new Runnable() {
			public void run() {
				logger.debug("Choker is working.");

				Iterator<String> iterator = peerConnections.keySet().iterator();
				while (iterator.hasNext()) {
					logger.debug("Calculating download&upload speed in the past 10 seconds.");
					String infoHash = iterator.next();
					List<PeerConnection> connections = peerConnections.get(infoHash);
					for (PeerConnection conn : connections) {
						conn.getMessageHandler().calculateReadWriteRate();
					}
					connections.sort(new Comparator<PeerConnection>() {
						public int compare(PeerConnection obj1, PeerConnection obj2) {
							if (obj1.isChoked() == obj2.isChoked()) {
								return Float.compare(obj2.getMessageHandler().getReadRate(), obj1.getMessageHandler().getReadRate()); // download speed
							} else {
								if (obj1.isChoked()) {
									return -1;
								} else {
									return 1;
								}
							}
						}
					});
					// Unchoke 4 peers based on download rate, and randomly pick one as 'optimistic unchoking'.
					int size = connections.size();

					logger.debug("Choker-> connections size : " + size + " for " + infoHash + ".");

					if (size > 5) {
						// TODO: do more research on Random to make sure we are using it correctly.
						Random random = new Random();
						int j = random.nextInt(size - 4);

						if (j != 0) {
							PeerConnection c = connections.get(j + 4);
							connections.set(j + 4, connections.get(4));
							connections.set(4, c);
						}
					}

					for (int i = 0; i < connections.size(); i++) {
						PeerConnection conn = connections.get(i);
						if (i < 5) {
							if (conn.isChoking()) {
								logger.debug("Choker-> Unchoking peer " + conn + ".");
								conn.setChoking(false);
								conn.addMessageToSend(new UnchokeMessage());
							}
						} else {
							if (!conn.isChoking()) {
								conn.setChoking(true);
								conn.addMessageToSend(new ChokeMessage());
							}
						}
					}
				}
			}
		}, 10L, 10L, TimeUnit.SECONDS);

		aliveManager.scheduleAtFixedRate(new Runnable() {
			public void run() {
				logger.debug("aliveManager is working.");
				Iterator<String> iterator = peerConnections.keySet().iterator();
				while (iterator.hasNext()) {
					List<PeerConnection> connections = peerConnections.get(iterator.next());
					for (PeerConnection conn : connections) {
						conn.checkAliveAndKeepAlive();
					}
				}
			}
		}, 0L, 45L, TimeUnit.SECONDS);
	}

	public void run() {
		try {
			this.serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(false);
			serverChannel.socket().setReuseAddress(true);
			serverChannel.socket().bind(listenEndpoint);
			logger.debug("Listening at : " + listenEndpoint + ".");
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);

			for (;;) {
				if (Thread.interrupted())
					break;

				//logger.debug("Acquiring selector lock.");
				selectorLock.lock();
				//logger.debug("Acquired selector lock.");
				selectorLock.unlock();

				//logger.debug("Selecting keys.");
				// Select blocks until at least one key is selected.
				selector.select(); // IOException, ClosedSelectorException
				//logger.debug("Some keys got selected.");

				Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
				while (selectedKeys.hasNext()) {
					SelectionKey key = (SelectionKey) selectedKeys.next();
					// Is it required?
					selectedKeys.remove();

					if (!key.isValid())
						continue;

					if (key.isAcceptable()) {
						ServerSocketChannel serverSocket = (ServerSocketChannel) key.channel();
						SocketChannel socketChannel = serverSocket.accept(); // Lots of exceptions may be thrown.

						try {
							socketChannel.configureBlocking(false);
							logger.debug("[In Selector] Accepted : " + socketChannel.socket().getRemoteSocketAddress());
							PeerConnection conn = new PeerConnection(false, socketChannel, this, downloadManager);
							Peer self = new Peer();
							// TODO: peerId managing.
							self.setPeerId(Client.PEER_ID);
							self.setAddress((InetSocketAddress) socketChannel.getLocalAddress()); // IOException, ClosedChannelException
							// self.setLocal((InetSocketAddress)serverSocket.getLocalAddress());
							conn.setSelf(self);
							conn.setState(State.IN_ACCEPTED);

							socketChannel.register(selector, SelectionKey.OP_READ, conn); // Lots of exceptions may be thrown.
						} catch (IOException e) {
							e.printStackTrace();
						}
					} else if (key.isReadable()) {
						//logger.debug("[In Selector] Ready to read.");
						((PeerConnection) key.attachment()).readMessage();
					} else if (key.isWritable()) {
						logger.debug("[In Selector] Ready to write.");
						((PeerConnection) key.attachment()).writeMessage();
					} else if (key.isConnectable()) {
						logger.debug("[In Selector] Ready to connect to remote peer.");
						SocketChannel socketChannel = (SocketChannel) key.channel();
						PeerConnection conn = (PeerConnection) key.attachment();
						try {
							if (socketChannel.finishConnect()) { // Lots of exceptions may be thrown.
								logger.debug("[In Selector] Completed outgoing connecting to peer.");
								conn.setState(State.OUT_CONNECTED);
								conn.writeMessage();
							}
						} catch (IOException e) {
							e.printStackTrace();
							Helper.closeChannel(socketChannel);
							// This channel may be closed, just cancel the key because this connection is not yet managed by PeerConnectionManager.
							key.cancel();
						}
					}
				}
			}
		} catch (Exception e) { // This exception may be thrown because of :
														// (1) ServerSocketChannel does not work.
														// (2) Selector does not work.
														// TODO: Shall I call it or the Client call it?
			this.stop();

			throw new RuntimeException(e); // How to propagate this exception from child thread to parent thread?
		}
	}

	public PeerConnection createOutgoingConnection(Peer self, Peer peer) {
		SocketChannel socketChannel = null;
		try {
			socketChannel = SocketChannel.open(); // IOException
			socketChannel.configureBlocking(false);
			socketChannel.socket().setReuseAddress(true); // SocketException
			// socketChannel.bind(new InetSocketAddress("127.0.0.1", 6666));
			// TODO: Randomly bind to local address.
			socketChannel.connect(peer.getAddress()); // connect-> lots of exception may be thrown.
			logger.debug("Connecting to peer: " + peer.getAddress() + ".");
			PeerConnection conn = new PeerConnection(true, socketChannel, this, downloadManager);

			self.setAddress((InetSocketAddress) socketChannel.socket().getLocalSocketAddress());
			conn.setSelf(self);
			conn.setPeer(peer);

			// TODO: VERY IMPORTANT!
			// selector.select() is blocking, and register() is blocking too.
			register(socketChannel, SelectionKey.OP_CONNECT, conn);

			return conn;
		} catch (IOException e) {
			logger.debug("Failed to create connection -> self : " + self + ", peer : " + peer + ".");
			e.printStackTrace();

			Helper.closeChannel(socketChannel);
			return null;
		}
	}

	public void stop() {
		logger.debug("PeerConnectionManager is stopping.");

		if (this.stopped)
			return;
		this.stopped = true;

		this.choker.shutdown();
		this.aliveManager.shutdown();
		this.connectionManagerThread.interrupt();

		Helper.closeServerChannel(this.serverChannel);
		Helper.closeSelector(this.selector);
	}

	// For outgoing connection: the connection may not finish connection yet.
	// For incoming connection: the connection at least has been accepted.
	public void addPeerConnection(String infoHash, PeerConnection connection) {
		logger.debug(
				"New connection is created, outgoing connection? [" + connection.isOutgoingConnection() + "], infoHash = " + infoHash + ", [" + connection + "]");
		List<PeerConnection> connections = this.peerConnections.get(infoHash);
		if (connections == null) {
			connections = new ArrayList<PeerConnection>();
			this.peerConnections.put(infoHash, connections);
		}
		connections.add(connection);
	}

	public List<PeerConnection> getPeerConnections(String infoHash) {
		return this.peerConnections.get(infoHash);
	}

	public void removePeerConnection(String infoHash, String connectionId) {
		List<PeerConnection> connections = this.peerConnections.get(infoHash);

		Iterator<PeerConnection> iterator = connections.iterator();
		while (iterator.hasNext()) {
			PeerConnection conn = iterator.next();
			if (conn.getConnectionId().equalsIgnoreCase(connectionId)) {
				iterator.remove();
				break;
			}
		}
	}

	public void closePeerConnections(String infoHash) {
		List<PeerConnection> connections = this.peerConnections.get(infoHash);

		Iterator<PeerConnection> iterator = connections.iterator();
		while (iterator.hasNext()) {
			PeerConnection conn = iterator.next();
			iterator.remove();
			conn.close();
		}
	}

	public SelectionKey register(SelectableChannel socketChannel, int ops, Object attachment) throws IOException {
		try {
			selectorLock.lock();
			logger.debug("Wakening the selector.");
			selector.wakeup();
			logger.debug("Registering channel : " + socketChannel + ", ops = " + interestOps(ops) + ", attachment = " + attachment + ".");
			return socketChannel.register(selector, ops, attachment); // lots of exception may be thrown.
		} finally {
			selectorLock.unlock();
			logger.debug("Completed registering channel.");
		}
	}

	private String interestOps(int ops) {
		if (ops == SelectionKey.OP_ACCEPT) {
			return "OP_ACCEPT";
		}else if (ops == SelectionKey.OP_CONNECT) {
			return "OP_CONNECT";
		}else if (ops == SelectionKey.OP_READ) {
			return "OP_READ";
		} else if (ops == SelectionKey.OP_WRITE) {
			return "OP_WRITE";
		} else if (ops == (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) {
			return "OP_READ | OP_WRITE";
		}
		return "Unexpected : " + ops;
	}

	public void unregister(SocketChannel socketChannel) {
		if (socketChannel.isRegistered()) // it may be registered in multiple selectors.
			socketChannel.keyFor(this.selector).cancel();
		selector.wakeup();
	}

	public void notifyPeersIHavePiece(String infoHash, int pieceIndex) {
		Bitmap selfBitfield = this.downloadManager.getBitfield(infoHash);

		List<PeerConnection> connections = this.peerConnections.get(infoHash);
		for (PeerConnection conn : connections) {
			Bitmap peerBitfield = conn.getPeer().getBitfield();
			boolean interestingNew = Helper.isInterested(selfBitfield, peerBitfield);
			if (conn.isInteresting() && !interestingNew) { // I have a new piece, so I am not interested in the peer.
				PeerMessage.NotInterestedMessage notInterestedMessage = new PeerMessage.NotInterestedMessage();
				conn.addMessageToSend(notInterestedMessage);
			}
			conn.setInteresting(interestingNew);
			conn.addMessageToSend(new HaveMessage(pieceIndex));
		}
	}
}
