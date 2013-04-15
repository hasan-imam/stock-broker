import java.net.*;
import java.util.Map;
import java.io.*;

/*
 * Handler of threads created by the OnlineBroker upon client or exchange connection
 */
class OnlineBrokerHandlerThread extends Thread {
	private Socket socket = null;
	private Map<String, Integer> table;
	private ObjectOutputStream toLookupServer;
	private ObjectInputStream fromLookupServer;
	private String hostname;
	private String exchange;

	/*
	 * Constructor for the OnlineBrokerHandlerThread
	 */
	public OnlineBrokerHandlerThread(Socket socket, Map<String, Integer> table, String hostname, String exchange, ObjectOutputStream toLookupServer, ObjectInputStream fromLookupServer) {
		super("OnlineBrokerHandlerThread"); // Superclass constructor
		this.socket = socket;
		this.table = table;
		this.toLookupServer = toLookupServer;
		this.fromLookupServer = fromLookupServer;
		this.hostname = hostname;
		this.exchange = exchange;
		System.out.println("Created new Thread: " + this.getId() + " to handle client");
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	public void run() {
		System.out.println("Running thread: " + this.getId());
		boolean gotByePacket = false;
		
		try {
			/* stream to read from client */
			ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());
			BrokerPacket packetFromClient;
			
			/* stream to write back to client */
			ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());

			while (( packetFromClient = (BrokerPacket) fromClient.readObject()) != null) {
				/* create a packet to send reply back to client */
				BrokerPacket packetToClient = new BrokerPacket();
				
				/* BROKER HANDLERS */
				/* Handle request for quote */
				if(packetFromClient.type == BrokerPacket.BROKER_REQUEST) {
					/* Preparing the packet to be sent */
					packetToClient.type = BrokerPacket.BROKER_QUOTE;
					
					/* Look into the hashmap to find the quote associated with the company code
					 * if quote is not found, try to forward the search request to another broker
					 * else set quote to 0 */
					try {
						if (table.get(packetFromClient.symbol.toUpperCase()) != null) {							
							synchronized (table) {
								packetToClient.quote = (long) table.get(packetFromClient.symbol);
							}
						} else {
							packetToClient.quote = (long) forwardRequest(packetFromClient.symbol);
						}
					} catch (ClassCastException e) {
						System.err.println("ERROR: Class casting exception on lookup.");
					}
					
					packetToClient.symbol = packetFromClient.symbol;
					
					if (packetToClient.quote == 0) {
						packetToClient.error_code = BrokerPacket.ERROR_INVALID_SYMBOL; // Since no quote for this symbol found
					} else {
						packetToClient.error_code = BrokerPacket.SUCCESS;
					}
					
					/* send reply back to client */
					try {
						toClient.writeObject(packetToClient);
					} catch (Exception e) {
						System.err.println("EXCEPTION!");
						e.printStackTrace();
					}
					
					/* wait for next packet */
					continue;
				}
				
				if(packetFromClient.type == BrokerPacket.BROKER_FORWARD) {
					/* Preparing the packet to be sent */
					packetToClient.type = BrokerPacket.BROKER_QUOTE;
					
					try {
						if (table.get(packetFromClient.symbol.toUpperCase()) != null) {							
							synchronized (table) {
								packetToClient.quote = (long) table.get(packetFromClient.symbol);
							}
						} else {
							packetToClient.quote = (long) 0;
						}
					} catch (ClassCastException e) {
						System.err.println("ERROR: Class casting exception on lookup.");
					}
					
					packetToClient.symbol = packetFromClient.symbol;
					if (packetToClient.quote == 0) {
						// packetToClient.type = BrokerPacket.BROKER_ERROR;
						packetToClient.error_code = BrokerPacket.ERROR_INVALID_SYMBOL; // Since no quote for this symbol found
					} else {
						// packetToClient.type = BrokerPacket.BROKER_QUOTE;
						packetToClient.error_code = 0;
					}
					
					/* send reply back to client */
					try {
						toClient.writeObject(packetToClient);
					} catch (Exception e) {
						System.err.println("EXCEPTION!");
						e.printStackTrace();
					}
					
					/* wait for next packet */
					continue;
				}
				
				/* EXCHANGE HANDLERS */
				/* Handle request for ADD */
				if(packetFromClient.type == BrokerPacket.EXCHANGE_ADD) {
					packetToClient.type = BrokerPacket.EXCHANGE_REPLY;
					try {
						synchronized (table) {
							if (table.containsKey(packetFromClient.symbol.toUpperCase()) == false) {
								table.put(packetFromClient.symbol.toUpperCase(), 0);
								packetToClient.error_code = BrokerPacket.SUCCESS;
							} else { // Symbol already exists
								packetToClient.error_code = BrokerPacket.ERROR_SYMBOL_EXISTS;
							}
						}
					} catch (Exception e) {
						System.err.println("EXCEPTION!");
						e.printStackTrace();
					}
					
					/* send reply back to client */
					try {
						toClient.writeObject(packetToClient);
					} catch (Exception e) {
						System.err.println("EXCEPTION!");
						e.printStackTrace();
					}
					
					/* wait for next packet */
					continue;
				}
				
				/* Handle request for REMOVE */
				if(packetFromClient.type == BrokerPacket.EXCHANGE_REMOVE) {
					packetToClient.type = BrokerPacket.EXCHANGE_REPLY;
					try {
						synchronized (table) {
							if (table.containsKey(packetFromClient.symbol.toUpperCase()) == true) {
								table.remove(packetFromClient.symbol.toUpperCase());
								packetToClient.error_code = BrokerPacket.SUCCESS;
							} else { // Symbol doesn't exists
								packetToClient.error_code = BrokerPacket.ERROR_INVALID_SYMBOL;
							}
						}
					} catch (Exception e) {
						System.err.println("EXCEPTION!");
						e.printStackTrace();
					}
					
					/* send reply back to client */
					try {
						toClient.writeObject(packetToClient);
					} catch (Exception e) {
						System.err.println("EXCEPTION!");
						e.printStackTrace();
					}
					
					/* wait for next packet */
					continue;
				}
				
				/* Handle request for UPDATE */
				if(packetFromClient.type == BrokerPacket.EXCHANGE_UPDATE) {
					packetToClient.type = BrokerPacket.EXCHANGE_REPLY;
					try {
						synchronized (table) {
							if (table.containsKey(packetFromClient.symbol.toUpperCase()) == true) {
								table.put(packetFromClient.symbol.toUpperCase(), packetFromClient.quote.intValue());
								packetToClient.error_code = BrokerPacket.SUCCESS;
							} else { // Symbol doesn't exists
								packetToClient.error_code = BrokerPacket.ERROR_INVALID_SYMBOL;
							}
						}
					} catch (Exception e) {
						System.err.println("EXCEPTION!");
						e.printStackTrace();
					}
					
					/* send reply back to client */
					try {
						toClient.writeObject(packetToClient);
					} catch (Exception e) {
						System.err.println("EXCEPTION!");
						e.printStackTrace();
					}
					
					/* wait for next packet */
					continue;
				}
				
				/* LOOKUP HANDLERS */
				/* Handle request for LOOKUP_REQUEST */
				if(packetFromClient.type == BrokerPacket.LOOKUP_REQUEST) {
					
				}
				
				/* Sending a BROKER_BYE means quit */
				if(packetFromClient.type == BrokerPacket.BROKER_BYE) {
					packetToClient.type = BrokerPacket.BROKER_BYE;
					packetToClient.symbol = "Bye!";
					gotByePacket = true;
					
					/* send bye back to client */
					toClient.writeObject(packetToClient);
					//System.err.println("breaking out of while loop");
					/* No more packet expected from this client */
					break;
				}
				
				/* If code comes here, there is an error in the packet */
				System.err.println("ERROR: Unknown BROKER_* packet!!");
				System.exit(-1);
			}
			
			/* Cleanup when client exits */
			System.out.println("Exiting thread: " + this.getId());
			fromClient.close();
			toClient.close();
			socket.close();
			
		} catch (IOException e) {
			if(!gotByePacket){
				if(socket.isClosed())
					System.out.println("Client went down!");
				//else
					//e.printStackTrace();
			}
		} catch (ClassNotFoundException e) {
			if(!gotByePacket)
				e.printStackTrace();
		}
	}

	/*
	 * 
	 */
	private long forwardRequest(String company) {
		long quote = 0;
		
		/* Preparing packets to be used for communication with the lookup server */
		BrokerPacket packetToLookupServer = new BrokerPacket();
		packetToLookupServer.type = BrokerPacket.LOOKUP_REQUEST;
		packetToLookupServer.exchange = this.hostname;
		
		BrokerPacket packetFromLookupServer = null;
		Socket forwardSocket = null;
		
		try {
			toLookupServer.writeObject(packetToLookupServer);
			packetFromLookupServer = (BrokerPacket) fromLookupServer.readObject();
			
			if(packetFromLookupServer.type == BrokerPacket.LOOKUP_REPLY){
				for(int i =0; i < packetFromLookupServer.num_locations; i++){
					if(!packetFromLookupServer.locations[i].broker_host.equals(this.exchange + " " + this.hostname)){
						/* Send forward request to another host */
						System.out.println("Forwarding request ...");
						String[] tokens = packetFromLookupServer.locations[i].broker_host.split(" ");
						forwardSocket = new Socket(tokens[1], packetFromLookupServer.locations[i].broker_port);
						
						ObjectOutputStream out = new ObjectOutputStream(forwardSocket.getOutputStream());
						ObjectInputStream in = new ObjectInputStream(forwardSocket.getInputStream());
					
						BrokerPacket packetToServer = new BrokerPacket();
						BrokerPacket packetFromServer = null;
						
						/* Prepare a bye packet */
						BrokerPacket byePacket = new BrokerPacket();
						byePacket.type = BrokerPacket.BROKER_BYE;
						
						/* make a new quote request packet */
						packetToServer.type = BrokerPacket.BROKER_FORWARD;
						packetToServer.symbol = company.toUpperCase();
						
						try {
							out.writeObject(packetToServer);
							
							/* print server reply */
							packetFromServer = (BrokerPacket) in.readObject();
							quote = packetFromServer.quote;
							
							out.writeObject(byePacket);
							packetFromServer = (BrokerPacket) in.readObject();
							
						} catch (IOException e) {
							System.out.println("Exception occured. Aborted.");
							continue;
						}
						
						
						out.close();
						in.close();
						forwardSocket.close();
						break;
					}
				}
			}else
				System.err.println("ERROR: This reson");
				
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		
		return quote;
	}
}
