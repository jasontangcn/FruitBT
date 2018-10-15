package com.fruits.bt;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

// TODO: Communicate with tracker server and get the peers from a torrent seed.
public class TrackerManager {
	// By default, 50 peers.
	public List<Peer> getPeers(TorrentSeed seed) {
		List<Peer> peers = new ArrayList<Peer>();
		Peer peer = new Peer();
		peer.setAddress(new InetSocketAddress(Client.REMOTE_DOMAIN, Client.REMOTE_PORT));
		peer.setPeerId(Client.REMOTE_PEER_ID);
		peers.add(peer);
		return peers;
	}
}
