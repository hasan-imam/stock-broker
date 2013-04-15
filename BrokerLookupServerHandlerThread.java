import java.net.*;
import java.util.Map;
import java.io.*;

/*
 * Thread handler for each connection to the lookup server
 */
class BrokerLookupServerHandlerThread extends Thread {
	/* Variable stores information about an accepted socket binding request */
	private Socket socket = null;
	
	/*
	 * Stores a list of broker servers that are available at the moment. The string contains a space separated string containing 
	 * the name of the exchange (ie, nasdaq or tse) followed by the host name, the Integer contains the port number for the 
	 * broker server
	 */
	private Map<String, Integer> availableServers;
	
	/*
	 * Constructor for BrokerLookupServerHandlerThread
	 */
	public BrokerLookupServerHandlerThread(Socket socket, Map<String, Integer> servers) {
		super("BrokerLookupServerHandlerThread");
		this.socket = socket;
		this.availableServers = servers;
		System.out.println("Created new Thread to handle client");
	}

	/*
	 * Method stores information about the available servers from the local hashmap "availableServers"
	 * to a BrokerLocation[] array to be attached with the BrokerPacket
	 */
	public BrokerLocation[] updateAvailableServerlist(){
		BrokerLocation locations[] = new BrokerLocation[availableServers.size()];
		
		int i = 0;
		
		// Iterate through each hashmap entry and store it as a BrokerLocation() in the locations array
		synchronized ( availableServers){
			for (Map.Entry<String, Integer> entry : availableServers.entrySet()){
				locations[i++] = new BrokerLocation(entry.getKey(), entry.getValue());
			}
		}
		
		return locations;
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	public void run() {
		String registered_broker = "";
		boolean gotByePacket = false;

		try {
			/* Stream to read from client */
			ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());
			BrokerPacket packetFromClient;
			
			/* Stream to write back to client */
			ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());

			/* Keep accepting BrokerPackets as long as they aren't empty */
			while (( packetFromClient = (BrokerPacket) fromClient.readObject()) != null) {
				/* Create a packet to send reply back to client */
				BrokerPacket packetToClient = new BrokerPacket();

				/* Process a broker lookup request */
				if(packetFromClient.type == BrokerPacket.LOOKUP_REQUEST) {
					packetToClient.type = BrokerPacket.LOOKUP_REPLY;
            
                    packetToClient.exchange = "notFound";

                    /* Try to find a matching broker listed in the availabeServers list  */
                    try {
	                    	synchronized (availableServers){
	                			for (Map.Entry<String, Integer> entry : availableServers.entrySet()){
	                				String tokens[] = entry.getKey().split(" ");
	                
	                				if (packetFromClient.exchange.equals(tokens[0])) 
	                					packetToClient.exchange = tokens[1] + " "+  entry.getValue();
	                			}
	                    	}	
					} catch (NullPointerException e) {
						System.err.println("ERROR: Specified bokerserver is 'null'");
						packetToClient.error_code = BrokerPacket.BROKER_ERROR;
					} catch (ClassCastException e) {
						System.err.println("ERROR: Class casting exception on lookup.");
						packetToClient.error_code = BrokerPacket.BROKER_ERROR;
					}
                    
					/* Send reply back to client */
                    packetToClient.num_locations = availableServers.size();
					packetToClient.locations = updateAvailableServerlist();
					toClient.writeObject(packetToClient);
					
					/* wait for next packet */
					continue;
				}

				/* Process registration request */
				if(packetFromClient.type == BrokerPacket.LOOKUP_REGISTER) {
                    /* Look up which brokers are available and respond connect with it */
					String[] tokens = packetFromClient.exchange.split(" ");
					
					/*
					 * If the correct number of tokens are found, register the broker server and add 
					 * exchange name, hostname and port number on the 'availableServers' list.
					 * Otherwise, check if the BrokerPacket.exchange variable claims its a 'client' or
					 * 'exchange'   
					 */
					if(tokens.length == 3){
						System.out.printf("Registering broker: %s\n", tokens[0]);
						packetToClient.type = BrokerPacket.SUCCESS;
						availableServers.put(tokens[0] + " "+ tokens[1], Integer.parseInt(tokens[2]));
						
						registered_broker = tokens[0] + " " + tokens[1];
						packetToClient.symbol = packetToClient.symbol;
					} else if (packetFromClient.exchange.equals("client")){
						System.out.println("Registering client...");
						packetToClient.type = BrokerPacket.SUCCESS;
					}else if (packetFromClient.exchange.equals("exchange")){
							System.out.println("Registering exchange...");
							packetToClient.type = BrokerPacket.SUCCESS;
					}else {
						packetToClient.type = BrokerPacket.LOOKUP_REPLY;
						packetToClient.error_code = BrokerPacket.ERROR_INVALID_SYMBOL;
						packetToClient.symbol = "Formatting error. Please enter 'broker_name port_name'/n";
						System.out.println("ERROR: Invalid symbol sent from client");
					}

					/* Send reply back to client */
					toClient.writeObject(packetToClient);
					
					/* Wait for next packet */
					continue;
				}
				
				/* Sending an ECHO_NULL || BROKER_BYE means quit */
				if (packetFromClient.type == BrokerPacket.BROKER_NULL || packetFromClient.type == BrokerPacket.BROKER_BYE) {
					gotByePacket = true;
					packetToClient = new BrokerPacket();
					packetToClient.type = BrokerPacket.BROKER_BYE;
					packetToClient.symbol = "Bye!";
					toClient.writeObject(packetToClient);
					break;
				}
				
				/* If code comes here, there is an error in the packet */
				System.err.println("ERROR: Unknown BrokerPacket!!");
				System.exit(-1);
			}
		
			if(socket.isClosed()){
				availableServers.remove(registered_broker);
			}else {
				/* Cleanup when client exits */
				fromClient.close();
				toClient.close();
				socket.close();
			}
		} catch (EOFException e) {
			if(!gotByePacket){
				if(!registered_broker.equals("")){
					availableServers.remove(registered_broker);
					System.out.printf("'%s' server went down!\n", registered_broker);
				}
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			if(!gotByePacket){
				if(!registered_broker.equals("")){
					availableServers.remove(registered_broker);
					System.out.printf("'%s' server went down!\n", registered_broker);
				}
			}
		} 
	}
}
