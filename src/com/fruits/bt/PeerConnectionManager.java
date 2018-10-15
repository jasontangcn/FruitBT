package com.fruits.bt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.BitSet;
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

import com.fruits.bt.PeerConnection.State;
import com.fruits.bt.PeerMessage.ChokeMessage;
import com.fruits.bt.PeerMessage.HaveMessage;
import com.fruits.bt.PeerMessage.UnchokeMessage;

public class PeerConnectionManager implements Runnable {
	private Thread connectionManagerThread = null;

	private InetSocketAddress listenEndpoint;
	private Selector selector;
	private ServerSocketChannel serverChannel;
	// TODO: VERY IMPORTANT!
	// This lock is based on thread.
	private final ReentrantLock selectorLock = new ReentrantLock();

	private DownloadManager downloadManager;
	// info_hash -> PeerConnection
	private Map<String, List<PeerConnection>> peerConnections = new HashMap<String, List<PeerConnection>>();

	private ScheduledExecutorService choker = Executors.newSingleThreadScheduledExecutor();
	private ScheduledExecutorService aliveManager = Executors.newSingleThreadScheduledExecutor();

	public PeerConnectionManager(InetSocketAddress listenEndpoint) throws IOException {
		this.listenEndpoint = listenEndpoint;
		this.selector = Selector.open(); // This may throw IOException.

		choker.scheduleAtFixedRate(new Runnable() {
			public void run() {
				System.out.println("Choker is working.");

				Iterator<String> iterator = peerConnections.keySet().iterator();
				while (iterator.hasNext()) {
					System.out.println("Calculating download/upload rate in the past 10 seconds.");
					String infoHash = iterator.next();
					List<PeerConnection> connections = peerConnections.get(infoHash);
					for (PeerConnection conn : connections) {
						conn.getMessageHandler().calculateReadWriteRate();
					}
					connections.sort(new Comparator<PeerConnection>() {
						public int compare(PeerConnection a, PeerConnection b) {
							if (a.isChoked() == b.isChoked()) {
								return Float.compare(b.getMessageHandler().getReadRate(), a.getMessageHandler().getReadRate());
							} else {
								if (a.isChoked()) {
									return -1;
								} else {
									return 1;
								}
							}
						}
					});
					// Unchoke 4 peers based on download rate, and randomly pick one as 'optimistic unchoking'.
					int n = connections.size();

					System.out.println("Choker-> connections size : " + n + " for " + infoHash + ".");

					if (n > 5) {
						// TODO: do more research on Random to make sure we are using it correctly.
						Random random = new Random();
						int j = random.nextInt(n - 4);

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
								System.out.println("Choker-> I am going to unchoke connecton " + conn + ".");
								conn.setChoking(false);
								UnchokeMessage unchokeMessage = new UnchokeMessage();
								// try {
								conn.addMessageToSend(unchokeMessage);
								// }catch(Exception e) {
								// e.printStackTrace();
								// }
							}
						} else {
							if (!conn.isChoking()) {
								conn.setChoking(true);

								ChokeMessage chokeMessage = new ChokeMessage();
								// try {
								conn.addMessageToSend(chokeMessage);
								// }catch(Exception e) {
								// e.printStackTrace();
								// }
							}
						}
					}
				}
			}
		}, 10L, 5L, TimeUnit.SECONDS);

		aliveManager.scheduleAtFixedRate(new Runnable() {
			public void run() {
				System.out.println("aliveManager is working.");
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

	public void setDownloadManager(DownloadManager downloadManager) {
		this.downloadManager = downloadManager;
	}

	public void start() {
		connectionManagerThread = new Thread(this);
		connectionManagerThread.start();
	}

	public void run() {
		/*
		 * try {
		 */
		this.serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);
		serverChannel.socket().setReuseAddress(true);
		serverChannel.socket().bind(listenEndpoint);
		System.out.println("Listening at : " + listenEndpoint + ".");
		serverChannel.register(selector, SelectionKey.OP_ACCEPT);

		for (;;) {
			if (Thread.interrupted())
				break;

			System.out.println("Acquiring selector lock.");
			selectorLock.lock();
			System.out.println("Acquired selector lock.");
			selectorLock.unlock();

			System.out.println("Selecting keys.");
			// Select blocks until at least one key is selected.
			selector.select(); // IOException, ClosedSelectorException
			System.out.println("Some keys got selected.");

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
					socketChannel.configureBlocking(false);
					System.out.println("[In Selector] Accepted : " + socketChannel.socket().getRemoteSocketAddress());

					PeerConnection conn = new PeerConnection(false, socketChannel, this, downloadManager);
					Peer self = new Peer();
					// TODO: peerId managing.
					self.setPeerId(Client.PEER_ID);
					self.setAddress((InetSocketAddress) socketChannel.getLocalAddress()); // IOException, ClosedChannelException
					// self.setLocal((InetSocketAddress)serverSocket.getLocalAddress());
					conn.setSelf(self);
					conn.setState(State.IN_ACCEPTED);

					socketChannel.register(selector, SelectionKey.OP_READ, conn); // Lots of exceptions may be thrown.
				} else if (key.isReadable()) {
					System.out.println("[In Selector] Ready to read.");
					((PeerConnection) key.attachment()).readMessage();
				} else if (key.isWritable()) {
					System.out.println("[In Selector] Ready to write.");
					((PeerConnection) key.attachment()).writeMessage();
				} else if (key.isConnectable()) {
					System.out.println("[In Selector] Ready to connect to remote peer.");
					SocketChannel socketChannel = (SocketChannel) key.channel();
					PeerConnection conn = (PeerConnection) key.attachment();
					if (socketChannel.finishConnect()) { // Lots of exceptions may be thrown.
						System.out.println("[In Selector] Completed outgoing connecting to peer.");
						conn.setState(State.OUT_CONNECTED);
						conn.writeMessage();
					}
				}
			}
		}
		/**
		 * } catch (Exception e) { throw new RuntimeException(e); }
		 */
	}

	public PeerConnection createOutgoingConnection(Peer self, Peer peer) {
		SocketChannel socketChannel = SocketChannel.open(); // IOException
		socketChannel.configureBlocking(false);
		socketChannel.socket().setReuseAddress(true); // SocketException
		// socketChannel.bind(new InetSocketAddress("127.0.0.1", 6666));
		// TODO: Random binding to local address?
		socketChannel.connect(peer.getAddress()); // connect-> lots of exception may be thrown.
		System.out.println("Connection to " + peer.getAddress() + ".");
		PeerConnection conn = new PeerConnection(true, socketChannel, this, downloadManager);

		self.setAddress((InetSocketAddress) socketChannel.socket().getLocalSocketAddress());
		conn.setSelf(self);
		conn.setPeer(peer);

		// TODO: VERY IMPORTANT!
		// selector.select() is blocking, and register() is blocking too.
		register(socketChannel, SelectionKey.OP_CONNECT, conn);

		return conn;
	}

	public void stop() {
		this.choker.shutdown();

		this.aliveManager.shutdown();

		this.connectionManagerThread.interrupt();

		try {
			if ((this.serverChannel != null) && (this.serverChannel.isOpen())) {
				this.serverChannel.close(); // IOException
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			if ((this.selector != null) && (this.selector.isOpen())) {
				this.selector.close(); // IOException
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void addPeerConnection(String infoHash, PeerConnection connection) {
		System.out.println("New PeerConnection is created, is it outgoing connection? [" + connection.isOutgoingConnect() + "], infoHash = " + infoHash + ", ["
				+ connection + "]");
		List<PeerConnection> connections = this.peerConnections.get(infoHash);
		if (connections == null) {
			connections = new ArrayList<PeerConnection>();
		}
		connections.add(connection);
		this.peerConnections.put(infoHash, connections);
	}

	public List<PeerConnection> getPeerConnections(String infoHash) {
		return this.peerConnections.get(infoHash);
	}

	public SelectionKey register(SelectableChannel socketChannel, int ops, Object attachment) {
		try {
			selectorLock.lock();
			System.out.println("Wakening the selector.");
			selector.wakeup();
			System.out.println("Registering channel : " + socketChannel + ", OPS = " + interestOps(ops) + ", attachment = " + attachment + ".");
			return socketChannel.register(selector, ops, attachment); // lots of exception may be thrown.
		} finally {
			selectorLock.unlock();
			System.out.println("Completed registering channel.");
		}
	}

	private String interestOps(int ops) {
		if (ops == SelectionKey.OP_READ) {
			return "OP_READ";
		} else if (ops == SelectionKey.OP_WRITE) {
			return "OP_WRITE";
		} else if (ops == (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) {
			return "OP_READ | OP_WRITE";
		}
		return "Unexpected : " + ops + ".";
	}

	public void notifyPeersIHavePiece(String infoHash, int pieceIndex) {
		BitSet selfBitfield = this.downloadManager.getBitfield(infoHash);

		List<PeerConnection> connections = this.peerConnections.get(infoHash);
		for (PeerConnection conn : connections) {
			BitSet peerBitfield = conn.getPeer().getBitfield();
			boolean interestingNew = PeerConnection.isInterested(selfBitfield, peerBitfield);
			if (conn.isInteresting() && !interestingNew) {
				PeerMessage.NotInterestedMessage notInterestedMessage = new PeerMessage.NotInterestedMessage();
				conn.addMessageToSend(notInterestedMessage);
			}
			conn.setInteresting(interestingNew);

			if (!conn.isInterested())
				break;
			conn.addMessageToSend(new HaveMessage(pieceIndex));
		}
	}

	public PeerConnection canIDownloadSlice(String infoHash, Slice slice) {
		List<PeerConnection> connections = this.peerConnections.get(infoHash);
		for (PeerConnection conn : connections) {
			if (!conn.isChoked()) {
				BitSet bitfield = conn.getPeer().getBitfield();
				if (bitfield.get(slice.getIndex()))
					return conn;
			}
		}
		return null;
	}
}
