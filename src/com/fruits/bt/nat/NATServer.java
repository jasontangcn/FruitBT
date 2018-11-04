package com.fruits.bt.nat;

/*
 * NAT Types
 * 1. Full Cone NAT
 *    Also called one-to-one NAT.
 *    local_IP1 + port1-> NAT -> public_IP2 + port2-> public_IP3 + port3
 *    Any host could access public_IP2 + port2.
 *    
 * 2. Address Restricted Cone NAT
 *    local_IP1 + port1 -> NAT -> pubic_IP2 + port2 -> public_IP3 + port3
 *    Only endpoint with IP = public_IP3 could access public_IP2 + port2.
 * 
 * 3. Port Restricted Cone NAT
 *    local_IP1 + port1 -> NAT -> public_IP2 + port2 -> public_IP3 + port3
 *    Only endpoint with (public_IP3 + port3) could access public_IP2 + port2.
 * 
 * 4.  Symmetric NAT
 *     local_IP1 + port1 -> NAT -> public_IP2 + port2 -> public_IP3 + port3
 *     local_IP4 + port4 -> NAT -> public_IP5 + port5 -> public_IP6 + port6
 *     local_IP7 + port7 -> NAT -> public_IP8 + port8 -> public_IP9 + port9
 *     ...
 *     Only endpoints(public_IP3 + port3) could access public_IP2 + port2.
 *     Only endpoints(public_IP6 + port6) could access public_IP5 + port5.
 *     ...
 * 
 * 
 * Flow for type discovery process with STUN client
 * Client: two ports: P1, P2
 * Sever: 4 endpoints: A1:P3, A1:P4, A2:P3, A2:P4.
 * 1. UDP pass
 *    P1 -> A1:P1 -> <mapped_address1>
 *    No, UDP blocked(by Firewall).
 *    If yes,
 * 2. Binding mapped address
 *    Binding mapped IP with UDP port 0, if failed it is NAT, otherwise it is not NAT,
 *    or just compare the mapped IP with local IP.
 *    If it is NAT,
 * 3.1 P1 -> <mapped_address1>, if received response, it is hairpin, otherwise it is not hairpin.   
 *     If NAT device does not support hairpin, peers behind the same NAT can not communicate with each other.
 * 3.2 P1 -> A2:P2 -> <mapped_address2>
 *     If <mapped_address1> != <mapped_address2>, it is symmetric NAT.
 *     If <mapped_address1> == <mapped_address2>, 
 * 4.  P2 -> A1:P1 and request server to response with A2:P1,
 *     if received response, it is Full Cone NAT,
 *     if can not receive response,
 * 5.  P2 -> A1:P1 and request server to response with A1:P2,
 *     if received response, it is Address Restricted Cone NAT,
 *     if can not received response, it is Port Restricted Cone NAT.
 *    
 * Port keeping: source port is the same with mapped port?
 * 
 * 
 * Hole punching
 * 
 * 
 * 
 */
public class NATServer {

}
