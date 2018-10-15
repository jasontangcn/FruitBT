package com.fruits.bt;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.BitSet;

public abstract class PeerMessage {
	//  message type id
	//  <length prefix><message ID><payload>
	//     keep_alive : <len=0000>
	//          choke : <len=0001><id=0>
	//        unchoke : <len=0001><id=1>
	//     interested : <len=0001><id=2>
	// not interested : <len=0001><id=3>
	//           have : <len=0005><id=4><piece index>
	//       bitfield : <len=0001+X><id=5><bitfield>
	//        request : <len=0013><id=6><index><begin><length>    // length of slice = 16K, piece = 256K
	//          piece : <len=0009+X><id=7><index><begin><block>    // len = 16K + 9 = 00 00 40 09
	//         cancel : <len=0013><id=8><index><begin><length>
	//           port : <len=0003><id=9><listen-port>
	public static final byte KEEP_ALIVE_MESSAGE_ID = -1;
	public static final byte CHOKE_MESSAGE_ID = 0;
	public static final byte UNCHOKE_MESSAGE_ID = 1;
	public static final byte INTERESTED_MESSAGE_ID = 2;
	public static final byte NOT_INTERESTED_MESSAGE_ID = 3;
	public static final byte HAVE_MESSAGE_ID = 4;
	public static final byte BITFIELD_MESSAGE_ID = 5;
	public static final byte REQUEST_MESSAGE_ID = 6;
	public static final byte PIECE_MESSAGE_ID = 7;
	public static final byte CANCEL_MESSAGE_ID = 8;
	public static final byte PORT_MESSAGE_ID = 9;

	protected int lengthPrefix;
	//message ID
	protected byte id;

	protected ByteBuffer messageBytes;

	public PeerMessage(byte id) {
		this.id = id;
	}

	public PeerMessage(byte id, ByteBuffer messageBytes) {
		this.id = id;
		this.messageBytes = messageBytes;
		if (messageBytes != null) {
			messageBytes.rewind(); // Is it required?
		}
	}

	public abstract ByteBuffer encode();

	public static PeerMessage parseMessage(ByteBuffer messageBytes) {
		int lengthPrefix = messageBytes.getInt();
		PeerMessage peerMessage;

		if (lengthPrefix == 0) {
			peerMessage = KeepAliveMessage.decode(messageBytes);
		} else {
			byte id = messageBytes.get();

			System.out.println("PeerMessage:parseMessage -> id : " + id + ".");

			switch (id) {
			case KEEP_ALIVE_MESSAGE_ID:
				peerMessage = KeepAliveMessage.decode(messageBytes);
				break;
			case CHOKE_MESSAGE_ID:
				peerMessage = ChokeMessage.decode(messageBytes);
				break;
			case UNCHOKE_MESSAGE_ID:
				peerMessage = UnchokeMessage.decode(messageBytes);
				break;
			case INTERESTED_MESSAGE_ID:
				peerMessage = InterestedMessage.decode(messageBytes);
				break;
			case NOT_INTERESTED_MESSAGE_ID:
				peerMessage = NotInterestedMessage.decode(messageBytes);
				break;
			case HAVE_MESSAGE_ID:
				peerMessage = HaveMessage.decode(messageBytes);
				break;
			case BITFIELD_MESSAGE_ID:
				peerMessage = BitfieldMessage.decode(messageBytes);
				break;
			case REQUEST_MESSAGE_ID:
				peerMessage = RequestMessage.decode(messageBytes);
				break;
			case PIECE_MESSAGE_ID:
				peerMessage = PieceMessage.decode(messageBytes);
				break;
			case CANCEL_MESSAGE_ID:
				peerMessage = CancelMessage.decode(messageBytes);
				break;
			case PORT_MESSAGE_ID:
				peerMessage = PortMessage.decode(messageBytes);
				break;
			default:
				throw new RuntimeException("PeerMessage->parseMessage : unrecognized message.");
			}
		}

		peerMessage.setLengthPrefix(lengthPrefix);
		return peerMessage;
	}

	public int getLengthPrefix() {
		return this.lengthPrefix;
	}

	public void setLengthPrefix(int lengthPrefix) {
		this.lengthPrefix = lengthPrefix;
	}

	public byte getId() {
		return this.id;
	}

	public void setId(byte id) {
		this.id = id;
	}

	public ByteBuffer getMessageBytes() {
		return messageBytes;
	}

	public void setMessageBytes(ByteBuffer messageBytes) {
		this.messageBytes = messageBytes;
	}

	public static class KeepAliveMessage extends PeerMessage {
		public static final int BASE_SIZE = 0;

		public KeepAliveMessage() {
			super(PeerMessage.KEEP_ALIVE_MESSAGE_ID);
		}

		public KeepAliveMessage(ByteBuffer messageBytes) {
			super(PeerMessage.KEEP_ALIVE_MESSAGE_ID, messageBytes);
		}

		public ByteBuffer encode() {
			ByteBuffer messageBytes = ByteBuffer.allocate(4 + KeepAliveMessage.BASE_SIZE);
			messageBytes.putInt(KeepAliveMessage.BASE_SIZE);
			messageBytes.flip();
			return messageBytes;
		}

		public static KeepAliveMessage decode(ByteBuffer messageBytes) {
			return new KeepAliveMessage(messageBytes);
		}
	}

	public static class ChokeMessage extends PeerMessage {
		public static final int BASE_SIZE = 1;

		public ChokeMessage() {
			super(PeerMessage.CHOKE_MESSAGE_ID);
		}

		public ChokeMessage(ByteBuffer messageBytes) {
			super(PeerMessage.CHOKE_MESSAGE_ID, messageBytes);
		}

		public ByteBuffer encode() {
			ByteBuffer messageBytes = ByteBuffer.allocate(4 + ChokeMessage.BASE_SIZE);
			messageBytes.putInt(ChokeMessage.BASE_SIZE);
			messageBytes.put(PeerMessage.CHOKE_MESSAGE_ID);
			messageBytes.flip();
			return messageBytes;
		}

		public static ChokeMessage decode(ByteBuffer messageBytes) {
			return new ChokeMessage(messageBytes);
		}
	}

	public static class UnchokeMessage extends PeerMessage {
		public static final int BASE_SIZE = 1;

		public UnchokeMessage() {
			super(PeerMessage.UNCHOKE_MESSAGE_ID);
		}

		public UnchokeMessage(ByteBuffer messageBytes) {
			super(PeerMessage.UNCHOKE_MESSAGE_ID, messageBytes);
		}

		public ByteBuffer encode() {
			ByteBuffer messageBytes = ByteBuffer.allocate(4 + UnchokeMessage.BASE_SIZE);
			messageBytes.putInt(UnchokeMessage.BASE_SIZE);
			messageBytes.put(PeerMessage.UNCHOKE_MESSAGE_ID);
			messageBytes.flip();
			return messageBytes;
		}

		public static UnchokeMessage decode(ByteBuffer messageBytes) {
			return new UnchokeMessage(messageBytes);
		}
	}

	public static class InterestedMessage extends PeerMessage {
		public static final int BASE_SIZE = 1;

		public InterestedMessage() {
			super(PeerMessage.INTERESTED_MESSAGE_ID);
		}

		public InterestedMessage(ByteBuffer messageBytes) {
			super(PeerMessage.INTERESTED_MESSAGE_ID, messageBytes);
		}

		public ByteBuffer encode() {
			ByteBuffer messageBytes = ByteBuffer.allocate(4 + InterestedMessage.BASE_SIZE);
			messageBytes.putInt(InterestedMessage.BASE_SIZE);
			messageBytes.put(PeerMessage.INTERESTED_MESSAGE_ID);
			messageBytes.flip();
			return messageBytes;
		}

		public static InterestedMessage decode(ByteBuffer messageBytes) {
			return new InterestedMessage(messageBytes);
		}
	}

	public static class NotInterestedMessage extends PeerMessage {
		public static final int BASE_SIZE = 1;

		public NotInterestedMessage() {
			super(PeerMessage.NOT_INTERESTED_MESSAGE_ID);
		}

		public NotInterestedMessage(ByteBuffer messageBytes) {
			super(PeerMessage.NOT_INTERESTED_MESSAGE_ID, messageBytes);
		}

		public ByteBuffer encode() {
			ByteBuffer messageBytes = ByteBuffer.allocate(4 + NotInterestedMessage.BASE_SIZE);
			messageBytes.putInt(HaveMessage.BASE_SIZE);
			messageBytes.put(PeerMessage.NOT_INTERESTED_MESSAGE_ID);
			messageBytes.flip();
			return messageBytes;
		}

		public static NotInterestedMessage decode(ByteBuffer messageBytes) {
			return new NotInterestedMessage(messageBytes);
		}
	}

	public static class HaveMessage extends PeerMessage {
		public static final int BASE_SIZE = 5;

		private int pieceIndex;

		public HaveMessage(int pieceIndex) {
			super(PeerMessage.HAVE_MESSAGE_ID);
			this.pieceIndex = pieceIndex;
		}

		public HaveMessage(ByteBuffer messageBytes, int pieceIndex) {
			super(PeerMessage.HAVE_MESSAGE_ID, messageBytes);
			this.pieceIndex = pieceIndex;
		}

		public int getPieceIndex() {
			return pieceIndex;
		}

		public void setPieceIndex(int pieceIndex) {
			this.pieceIndex = pieceIndex;
		}

		public ByteBuffer encode() {
			ByteBuffer messageBytes = ByteBuffer.allocate(4 + HaveMessage.BASE_SIZE);
			messageBytes.putInt(HaveMessage.BASE_SIZE);
			messageBytes.put(PeerMessage.HAVE_MESSAGE_ID);
			messageBytes.putInt(this.pieceIndex);
			messageBytes.flip();
			return messageBytes;
		}

		public static HaveMessage decode(ByteBuffer messageBytes) {
			return new HaveMessage(messageBytes, messageBytes.getInt());
		}
	}

	public static class BitfieldMessage extends PeerMessage {
		public static final int BASE_SIZE = 1;

		private BitSet bitfield;

		public BitfieldMessage(BitSet bitfield) {
			super(PeerMessage.BITFIELD_MESSAGE_ID);
			this.bitfield = bitfield;
		}

		public BitfieldMessage(ByteBuffer messageBytes, BitSet bitfield) {
			super(PeerMessage.BITFIELD_MESSAGE_ID, messageBytes);
			this.bitfield = bitfield;
		}

		public BitSet getBitfield() {
			return bitfield;
		}

		public void setBitfield(BitSet bitfield) {
			this.bitfield = bitfield;
		}

		public String toString() {
			return this.bitfield.toString();
		}

		public ByteBuffer encode() {
			byte[] bits = bitfield.toByteArray();
			ByteBuffer messageBytes = ByteBuffer.allocate(4 + BitfieldMessage.BASE_SIZE + bits.length);
			messageBytes.putInt(BitfieldMessage.BASE_SIZE + bits.length);
			messageBytes.put(PeerMessage.BITFIELD_MESSAGE_ID);
			messageBytes.put(bits);
			messageBytes.flip();
			return messageBytes;
		}

		public static BitfieldMessage decode(ByteBuffer messageBytes) {
			//TODO: Design the bitfiled
			return new BitfieldMessage(messageBytes, BitSet.valueOf(messageBytes.slice()));
		}
	}

	public static class RequestMessage extends PeerMessage {
		public static final int BASE_SIZE = 13;

		private int index;
		private int begin;
		private int length;

		public RequestMessage(int index, int begin, int length) {
			super(PeerMessage.REQUEST_MESSAGE_ID);
			this.index = index;
			this.begin = begin;
			this.length = length;
		}

		public RequestMessage(ByteBuffer messageBytes, int index, int begin, int length) {
			super(PeerMessage.REQUEST_MESSAGE_ID, messageBytes);
			this.index = index;
			this.begin = begin;
			this.length = length;
		}

		public int getIndex() {
			return index;
		}

		public void setIndex(int index) {
			this.index = index;
		}

		public int getBegin() {
			return begin;
		}

		public void setBegin(int begin) {
			this.begin = begin;
		}

		public int getLength() {
			return length;
		}

		public void setLength(int length) {
			this.length = length;
		}

		public ByteBuffer encode() {
			ByteBuffer messageBytes = ByteBuffer.allocate(4 + RequestMessage.BASE_SIZE);
			messageBytes.putInt(RequestMessage.BASE_SIZE);
			messageBytes.put(PeerMessage.REQUEST_MESSAGE_ID);
			messageBytes.putInt(this.index);
			messageBytes.putInt(this.begin);
			messageBytes.putInt(this.length);
			messageBytes.flip();
			return messageBytes;
		}

		public static RequestMessage decode(ByteBuffer messageBytes) {
			return new RequestMessage(messageBytes, messageBytes.getInt(), messageBytes.getInt(), messageBytes.getInt());
		}
	}

	public static class PieceMessage extends PeerMessage {
		public static final int BASE_SIZE = 9;

		private int index;
		private int begin;
		private ByteBuffer block;

		public PieceMessage(int index, int begin, ByteBuffer block) {
			super(PeerMessage.PIECE_MESSAGE_ID);
			this.index = index;
			this.begin = begin;
			this.block = block;
		}

		public PieceMessage(ByteBuffer messageBytes, int index, int begin, ByteBuffer block) {
			super(PeerMessage.PIECE_MESSAGE_ID, messageBytes);
			this.index = index;
			this.begin = begin;
			this.block = block;
		}

		public int getIndex() {
			return index;
		}

		public void setIndex(int index) {
			this.index = index;
		}

		public int getBegin() {
			return begin;
		}

		public void setBegin(int begin) {
			this.begin = begin;
		}

		public ByteBuffer getBlock() {
			return block;
		}

		public void setBlock(ByteBuffer block) {
			this.block = block;
		}

		public ByteBuffer encode() {
			byte[] bits = this.block.array();
			ByteBuffer messageBytes = ByteBuffer.allocate(4 + PieceMessage.BASE_SIZE + bits.length);
			System.out.println("PieceMessage prefixLength : " + (PieceMessage.BASE_SIZE + bits.length));
			messageBytes.putInt(PieceMessage.BASE_SIZE + bits.length);
			messageBytes.put(PeerMessage.PIECE_MESSAGE_ID);
			messageBytes.putInt(this.index);
			messageBytes.putInt(this.begin);
			messageBytes.put(bits);
			messageBytes.flip();
			return messageBytes;
		}

		public static PieceMessage decode(ByteBuffer messageBytes) {
			return new PieceMessage(messageBytes, messageBytes.getInt(), messageBytes.getInt(), messageBytes.slice());
		}
	}

	public static class CancelMessage extends PeerMessage {
		public static final int BASE_SIZE = 13;

		private int index;
		private int begin;
		private int length;

		public CancelMessage(int index, int begin, int length) {
			super(PeerMessage.CANCEL_MESSAGE_ID);
			this.index = index;
			this.begin = begin;
			this.length = length;
		}

		public CancelMessage(ByteBuffer messageBytes, int index, int begin, int length) {
			super(PeerMessage.CANCEL_MESSAGE_ID, messageBytes);
			this.index = index;
			this.begin = begin;
			this.length = length;
		}

		public int getIndex() {
			return index;
		}

		public void setIndex(int index) {
			this.index = index;
		}

		public int getBegin() {
			return begin;
		}

		public void setBegin(int begin) {
			this.begin = begin;
		}

		public int getLength() {
			return length;
		}

		public void setLength(int length) {
			this.length = length;
		}

		public ByteBuffer encode() {
			ByteBuffer messageBytes = ByteBuffer.allocate(4 + CancelMessage.BASE_SIZE);
			messageBytes.putInt(CancelMessage.BASE_SIZE);
			messageBytes.put(PeerMessage.CANCEL_MESSAGE_ID);
			messageBytes.putInt(this.index);
			messageBytes.putInt(this.begin);
			messageBytes.putInt(this.length);
			messageBytes.flip();
			return messageBytes;
		}

		public static CancelMessage decode(ByteBuffer messageBytes) {
			return new CancelMessage(messageBytes, messageBytes.getInt(), messageBytes.getInt(), messageBytes.getInt());
		}
	}

	public static class PortMessage extends PeerMessage {
		public static final int BASE_SIZE = 3;

		private short port;

		public PortMessage(short port) {
			super(PeerMessage.PORT_MESSAGE_ID);
			this.port = port;
		}

		public PortMessage(ByteBuffer messageBytes, short port) {
			super(PeerMessage.PORT_MESSAGE_ID, messageBytes);
			this.port = port;
		}

		public short getPort() {
			return port;
		}

		public void setPort(short port) {
			this.port = port;
		}

		public ByteBuffer encode() {
			ByteBuffer messageBytes = ByteBuffer.allocate(4 + PortMessage.BASE_SIZE);
			messageBytes.putInt(PortMessage.BASE_SIZE);
			messageBytes.put(PeerMessage.PORT_MESSAGE_ID);
			messageBytes.putShort(this.port);
			messageBytes.flip();
			return messageBytes;
		}

		public static PortMessage decode(ByteBuffer messageBytes) {
			return new PortMessage(messageBytes, messageBytes.getShort());
		}
	}
}
