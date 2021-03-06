import java.io.IOException;
import java.util.*;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import java.nio.charset.StandardCharsets;
import java.lang.Math;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;

public class UDPClient {
    public static String routerHost = "localhost";
    public static int routerPort = 3000;
    public static String serverHost = "localhost";
    public static int serverPort = 8007;

    public static final Logger logger = LoggerFactory.getLogger(UDPClient.class);
    public static boolean handshake = false;
    public static ArrayList<Packet> arrayOfSplitPacket = new ArrayList<Packet>();
	
    /**
     * @param routerAddr
     * @param serverAddr
     * @param args
     * @throws IOException
     */
    public static void runClient(SocketAddress routerAddr, InetSocketAddress serverAddr , String[] args) throws IOException {
        try(DatagramChannel channel = DatagramChannel.open()){
            String msg =  createMessage(args);
            long sequenceNumber = threeWayHandShake(channel,serverAddr,routerAddr);
            
            if(handshake) {
	            if(msg.getBytes().length <= Packet.MAX_PAYLOAD) {
	            
	            Packet p = new Packet.Builder()
	                    .setType(0)
	                    .setSequenceNumber(sequenceNumber+1)
	                    .setPortNumber(serverAddr.getPort())
	                    .setPeerAddress(serverAddr.getAddress())
	                    .setPayload(msg.getBytes())
	                    .create();
	            channel.send(p.toBuffer(), routerAddr);
	
	            
	            
	            logger.info("Sending \"{}\" to router at {}", msg, routerAddr);
	            wait(channel,routerAddr,p);
	
	            // We just want a single response.
	            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
	            SocketAddress router = channel.receive(buf);
	            buf.flip();
	            Packet resp = Packet.fromBuffer(buf);
	            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
	            logger.info("Packet: {}", resp);
	            logger.info("Router: {}", router);
	            logger.info("Payload:");
	            System.out.println(payload);
	         
	            
	            //Send FIN to end connection
	            }
	            else {
	            	int count=0;
	      
	               	 count = (int) Math.ceil((double)msg.length()/(double)Packet.MAX_PAYLOAD);
	         
		            Packet p = new Packet.Builder()
		                    .setType(4)
		                    .setSequenceNumber(sequenceNumber)
		                    .setPortNumber(serverAddr.getPort())
		                    .setPeerAddress(serverAddr.getAddress())
		                    .setPayload(msg.getBytes())
		                    .create();
		         
		          
	            	dividePacket(p);
	            	ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
	            	
	            	
	            	Packet packetNumber = new Packet.Builder()
		                    .setType(4)
		                    .setSequenceNumber(sequenceNumber)
		                    .setPortNumber(serverAddr.getPort())
		                    .setPeerAddress(serverAddr.getAddress())
		                    .setPayload(("Message separated in "+count).getBytes())
		                    .create();
	            	
	            	logger.info("Sending number of packet: {}", count);
	 	            channel.send(packetNumber.toBuffer(), routerAddr);
	 	            
	            	for(int i = 0; i < arrayOfSplitPacket.size(); i++) {
	            		
	            		channel.send(arrayOfSplitPacket.get(i).toBuffer(), routerAddr);

	            	}
	            	
	            	
	            		missingPackets(channel,routerAddr);
	            	
	            	
	            	
	            	
	            	    Packet wait = new Packet.Builder()
			                    .setType(4)
			                    .setSequenceNumber(sequenceNumber)
			                    .setPortNumber(serverAddr.getPort())
			                    .setPeerAddress(serverAddr.getAddress())
			                    .setPayload(("Request sent, waiting for response").getBytes())
			                    .create();
	            	    logger.info("Request sent, waiting for response");
	            	    
	            		channel.send(wait.toBuffer(), routerAddr);
	            		// Client starts timer and awaits for response, if timer expires, packet is resent
	                    wait(channel,routerAddr,wait);
	            	
	            	
		                SocketAddress router = channel.receive(buf);
		  	            buf.flip();
		  	            Packet resp = Packet.fromBuffer(buf);
		 
		  	            logger.info("Packet: {}", resp);
		  	            logger.info("Router: {}", router);
		  	            logger.info("Payload:");
	
			            System.out.println(new String(resp.getPayload(), StandardCharsets.UTF_8));
	

	            	    arrayOfSplitPacket.clear();
	            }
            }
        }
    }
    
    private static void missingPackets(DatagramChannel channel,SocketAddress routerAddr) throws IOException {
    	
    	   boolean NoMissing = wait(channel,routerAddr);
 
           if(NoMissing) {
        	   return;
           }
           else {
        	   	
        	   ByteBuffer bufMissingPacket = ByteBuffer.allocate(Packet.MAX_LEN);
        	   SocketAddress router = channel.receive(bufMissingPacket);
        	   bufMissingPacket.flip();
               Packet resp = Packet.fromBuffer(bufMissingPacket);
               String payload =new String(resp.getPayload(), StandardCharsets.UTF_8);
         
               logger.info("Packet: {}", resp);
               logger.info("Router: {}", router);
               logger.info("Payload: {}",payload);
               
        	   int counter=Integer.parseInt(payload.substring(payload.length()-1));
        	   channel.send(arrayOfSplitPacket.get(counter).toBuffer(), routerAddr);
        	   
        	   missingPackets(channel,routerAddr);
        	   
           }
    	
    	
		
	}

	private static boolean wait(DatagramChannel channel, SocketAddress routerAddr) throws IOException {
		 // Try to receive a packet within timeout.
		channel.configureBlocking(false);
	    Selector selector = Selector.open();
	    channel.register(selector, OP_READ);
	    selector.select(5000); //5000 ms of wait
	
	    Set<SelectionKey> keys = selector.selectedKeys();
	    //resend packet to router after x ms
	    if (keys.isEmpty()) {
	      return true;
	    }
	    keys.clear();
	    return false;
		}
		
	

	private static void dividePacket(Packet p) {
    	long sequenceNumber=0;
       
    	Packet p2;
    	if(p.getPayload().length < p.MAX_PAYLOAD) {
	    	sequenceNumber = p.getSequenceNumber();
	    	sequenceNumber ++;
	    	p2 = new Packet(p.getType(), sequenceNumber, p.getPeerAddress(), p.getPeerPort(), Arrays.copyOf(p.getPayload(), p.MAX_PAYLOAD));
	    
	    	arrayOfSplitPacket.add(p2);
    	}
    	else {
	    	sequenceNumber = p.getSequenceNumber();
	        sequenceNumber ++;
	        p2 = new Packet(p.getType(),  sequenceNumber, p.getPeerAddress(), p.getPeerPort(), Arrays.copyOf(p.getPayload(), p.MAX_PAYLOAD));
	       
	        arrayOfSplitPacket.add(p2);
	    	sequenceNumber ++;
	    	p2 = new Packet(p.getType(),  sequenceNumber, p.getPeerAddress(), p.getPeerPort(), Arrays.copyOfRange(p.getPayload(),p.MAX_PAYLOAD, p.getPayload().length));
	    
	    	dividePacket(p2);
    	}
    
    }
    

	private static String createMessage(String[] args) {
		String message=args[1]+" "+args[2]+" ";
	
		for(int i=1;i<args.length;i++) {	
			if(args[i].equals("-h")) {
				message=message+args[i]+" "+args[i+1]+" ";
			}
			if(args[i].equals("-d")) {				
				for(int j = i ; j < args.length-1; j++) {
						message=message+args[j]+" ";
				}				
			}
		}
		message=message+args[args.length-1];
		return message;
	}
	
	
	
	private static long threeWayHandShake(DatagramChannel datagramChannel,InetSocketAddress serverAddress, SocketAddress routerAddr) throws IOException {

	        String connectionString = "Client : Asking Server for connection #SYN";
	        
	        Packet connect = new Packet.Builder()
	                .setType(1) // Ask for SYN
	                .setSequenceNumber(1L)
	                .setPortNumber(serverAddress.getPort())
	                .setPeerAddress(serverAddress.getAddress())
	                .setPayload(connectionString.getBytes())
	                .create();
	        datagramChannel.send(connect.toBuffer(), routerAddr);
	        
	        logger.info("Client : Packet SYN has been sent out");
		      
	        wait(datagramChannel,routerAddr,connect);



	        ByteBuffer byteBuffer = ByteBuffer.allocate(Packet.MAX_LEN);
	        byteBuffer.clear();
	        datagramChannel.receive(byteBuffer);
	        byteBuffer.flip();
	        
	        Packet packet = Packet.fromBuffer(byteBuffer);
	        long awkSeqNumber = packet.getSequenceNumber();
	        connectionString = "Client AWK connection";
	        logger.info("Message : {} " , new String(packet.getPayload(),UTF_8));
	   
	        
	        if(packet.getType()==Packet.SYN_ACK) {
	        	logger.info("Connection AWK by client, Three-way handshake is complete");
	        	logger.info("Going forward with request");
	     	   
	        	connect = new Packet.Builder()
		                .setType(3) // Send AWK and will send a request afterward
		                .setSequenceNumber(awkSeqNumber+1)
		                .setPortNumber(serverAddress.getPort())
		                .setPeerAddress(serverAddress.getAddress())
		                .setPayload(connectionString.getBytes())
		                .create();
		        datagramChannel.send(connect.toBuffer(), routerAddr);
		        

	        }
	        
	     
	        handshake = true;
	        return connect.getSequenceNumber();
	    }
	
	  
	private static void wait(DatagramChannel channel,SocketAddress routerAddr,Packet packet) throws IOException {
		    // Try to receive a packet within timeout.
			channel.configureBlocking(false);
		    Selector selector = Selector.open();
		    channel.register(selector, OP_READ);
		    selector.select(5000);
		
		    Set<SelectionKey> keys = selector.selectedKeys();
		    //resend packet to router after x ms
		    if (keys.isEmpty()) {
		        logger.info("No answer from server , sending again Message : {}",new String(packet.getPayload(),UTF_8));
		    	channel.send(packet.toBuffer(), routerAddr);
		    	wait(channel,routerAddr,packet);
		    }
		    keys.clear();
		    return;
			}
		}

	
	
	
	
	
	
