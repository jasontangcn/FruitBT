package com.fruits.bt;

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
import com.fruits.bt.PeerMessage.HaveMessage;

public class PeerConnectionManager implements Runnable {
	private Thread managerThread = null;
	
	private InetSocketAddress listenEndpoint;
	private Selector selector;
	private ServerSocketChannel serverChannel;
	//TODO: VERY IMPORTANT!
	//This lock is based on thread.
	private final ReentrantLock selectorLock = new ReentrantLock();
	
	private DownloadManager downloadManager;
	// info_hash -> PeerConnection
	private Map<String, List<PeerConnection>> peerConnections = new HashMap<String, List<PeerConnection>>();
	
	private ScheduledExecutorService choker = Executors.newSingleThreadScheduledExecutor();
	
	private ScheduledExecutorService aliveChecker = Executors.newSingleThreadScheduledExecutor();
	
	public PeerConnectionManager(InetSocketAddress listenEndpoint) throws Exception {
		this.listenEndpoint = listenEndpoint;
		this.selector = Selector.open();
		
		choker.scheduleAtFixedRate(new Runnable() {
			public void run() {
				Iterator<String> iterator = peerConnections.keySet().iterator();
				while(iterator.hasNext()) {
					System.out.println("Calculating download/upload rate of connections");
					List<PeerConnection> connections = peerConnections.get(iterator.next());
					for(PeerConnection connection : connections) {
						connection.getPeerMessageHandler().calculateReadWriteRate();
					}
					
					connections.sort(new Comparator<PeerConnection>() {
						public int compare(PeerConnection o1, PeerConnection o2) {
							if(o1.isChoked() == o2.isChoked()) {
								return Float.compare(o2.getPeerMessageHandler().getReadPieceRate(), o1.getPeerMessageHandler().getReadPieceRate());
							}else {
								if(o1.isChoked()) {
									return -1;
								}else {
									return 1;
								}
							}
						}
					});
					
					int n = connections.size();
					if(n > 5) {
						Random random = new Random();
						int j = random.nextInt(n - 4);
						
						if(0 != j) {
							PeerConnection c1 = connections.get(j + 4);
							connections.set(j + 4, connections.get(4));
							connections.set(4, c1);
						}
					}
					
					for(int i = 0; i < connections.size(); i++) {
						PeerConnection connection = connections.get(i);
						if(i < 5) {
							if(connection.isChoking()) {
								connection.setChoking(false);
							    // TODO: Send UnchokeMessage.
							}
						}else{
							if(!connection.isChoking()) {
								connection.setChoking(true);
								// TODO: Send ChokeMessage
							}
						}
					}
				}
			}
		}, 10L, 10L, TimeUnit.SECONDS);
		
		
		aliveChecker.scheduleAtFixedRate(new Runnable() {
			public void run() {
				Iterator<String> iterator = peerConnections.keySet().iterator();
				while(iterator.hasNext()) {
				    List<PeerConnection> connections = peerConnections.get(iterator.next());
				    for(PeerConnection connection : connections) {
				    	try {
					        connection.checkAliveAndKeepAlive();
				    	}catch(Exception e) {
				    		e.printStackTrace();
				    	}
				    }
				}
			}
		}, 0L, 45L, TimeUnit.SECONDS);
	}

	public void setDownloadManager(DownloadManager downloadManager) {
		this.downloadManager = downloadManager;
	}
	
	public void start() {
		managerThread = new Thread(this);
		managerThread.start();
	}
	
	public void run() {	
		try {
			this.serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(false);
			serverChannel.socket().setReuseAddress(true);
			serverChannel.socket().bind(listenEndpoint);
			System.out.println("Listening at : " + listenEndpoint + ".");
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
	
			for (;;) {
				if(Thread.interrupted())
					break;
				
				System.out.println("Acquiring selector lock.");
			    selectorLock.lock();
			    System.out.println("Acquired selector lock.");
	            selectorLock.unlock();
	
	            System.out.println("Selecting keys.");
	            // Select blocks until at least one key is selected.
				selector.select();
				System.out.println("Some keys got selected.");
				
				Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
				while (selectedKeys.hasNext()) {
					SelectionKey key = (SelectionKey) selectedKeys.next();
					//Is it required?
					selectedKeys.remove();
	
					if (!key.isValid())
						continue;
	
					if (key.isAcceptable()) {
						ServerSocketChannel serverSocket = (ServerSocketChannel) key.channel();
						SocketChannel socketChannel = serverSocket.accept();
						socketChannel.configureBlocking(false);
						System.out.println("[In Selector] Accepted : " + socketChannel.socket().getRemoteSocketAddress());
	
						PeerConnection peerConnection = new PeerConnection(false, socketChannel, this, downloadManager);
						Peer self = new Peer();
						//TODO: peerId managing.
						self.setPeerId(Client.PEER_ID);
						self.setAddress((InetSocketAddress)socketChannel.getLocalAddress());
						// self.setLocal((InetSocketAddress)serverSocket.getLocalAddress());
						peerConnection.setSelf(self);
						peerConnection.setState(State.IN_ACCEPTED);
						
						socketChannel.register(selector, SelectionKey.OP_READ, peerConnection);
					} else if (key.isReadable()) {
						System.out.println("[In Selector] Ready to read.");
						((PeerConnection) key.attachment()).readMessage();
					} else if (key.isWritable()) {
						System.out.println("[In Selector] Ready to write.");
						((PeerConnection) key.attachment()).writeMessage();
					} else if (key.isConnectable()) {						
						System.out.println("[In Selector] Ready to connect to remote peer.");
						SocketChannel socketChannel = (SocketChannel) key.channel();
						PeerConnection peerConnection = (PeerConnection) key.attachment();
						if (socketChannel.finishConnect()) {
							System.out.println("[In Selector] Completed outgoing connecting to peer.");
							peerConnection.setState(State.OUT_CONNECTED);
							peerConnection.writeMessage();
						}
					}
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public PeerConnection createOutgoingConnection(Peer self, Peer peer) throws Exception {
		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);
		socketChannel.socket().setReuseAddress(true);
		// socketChannel.bind(new InetSocketAddress("127.0.0.1", 6666));
		// TODO: Random binding to local address?
		socketChannel.connect(peer.getAddress());
		System.out.println("Connection to " + peer.getAddress() + ".");
		PeerConnection peerConnection = new PeerConnection(true, socketChannel, this, downloadManager);

		self.setAddress((InetSocketAddress) socketChannel.socket().getLocalSocketAddress());
		peerConnection.setSelf(self);
		peerConnection.setPeer(peer);
		
		// TODO: VERY IMPORTANT!
		// selector.select() is blocking, and register() is blocking too.
		register(socketChannel, SelectionKey.OP_CONNECT, peerConnection);
		        
        return peerConnection;
	}

	public void stop() throws Exception {
		try {
			managerThread.interrupt();
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			if ((null != selector) && (selector.isOpen())) {
				selector.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			if ((null != serverChannel) && (serverChannel.isOpen())) {
				serverChannel.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void addPeerConnection(String infoHash, PeerConnection peerConnection) {
		System.out.println("New PeerConnection is created, is it outgoing connection? [" + peerConnection.isOutgoingConnect() + "], infoHash = " + infoHash + ", [" + peerConnection + "]");
		List<PeerConnection> connections = this.peerConnections.get(infoHash);
		if (null == connections) {
			connections = new ArrayList<PeerConnection>();
		}
		connections.add(peerConnection);
		this.peerConnections.put(infoHash, connections);
	}

	public List<PeerConnection> getPeerConnections(String infoHash) {
		return this.peerConnections.get(infoHash);
	}
	
	public SelectionKey register(SelectableChannel socketChannel, int ops, Object attachment) throws Exception {
        try {
    		selectorLock.lock();
            System.out.println("Wakening the selector.");
            selector.wakeup();
    		System.out.println("Registering channel : " + socketChannel + ", OPS = " + interestOps(ops) + ", attachment = " + attachment + ".");
    		return socketChannel.register(selector, ops, attachment);
        } finally {
            selectorLock.unlock();
    		System.out.println("Completed registering channel.");
        }
	}
	
	private String interestOps(int ops) {
		if(ops == SelectionKey.OP_READ) {
			return "OP_READ";
		}else if(ops == SelectionKey.OP_WRITE) {
			return "OP_WRITE";
		}else if(ops == (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) {
			return "OP_READ | OP_WRITE";
		}
		return "Unexpected : " + ops + ".";
	}
	
	public void notifyPeersIHavePiece(String infoHash, int pieceIndex) throws Exception {
		BitSet selfBitfield = this.downloadManager.getBitfield(infoHash);
		
		List<PeerConnection> peerConnections = this.peerConnections.get(infoHash);
		for(PeerConnection peerConnection : peerConnections) {
			BitSet peerBitfield = peerConnection.getPeer().getBitfield();
			boolean interesting = peerConnection.isInteresting();
			boolean interestingNew = PeerConnection.isInterested(selfBitfield, peerBitfield);
			if(interesting && !interestingNew) {
			    PeerMessage.NotInterestedMessage notInterestedMessage = new PeerMessage.NotInterestedMessage();
			    peerConnection.addMessageToSend(notInterestedMessage);
			}
			peerConnection.setInteresting(interestingNew);
			
			if(!peerConnection.isInterested())
				break;
			peerConnection.addMessageToSend(new HaveMessage(pieceIndex));
		}
	}
	
	public PeerConnection canIDownloadThisSlice(String infoHash, Slice slice) {
		List<PeerConnection> connections = this.peerConnections.get(infoHash);
		for(PeerConnection connection : connections) {
			if(!connection.isChoked()) {
				BitSet bitfield = connection.getPeer().getBitfield();
				if(bitfield.get(slice.getIndex()))
					return connection;
			}
		}
		return null;
	}
}
