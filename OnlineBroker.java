import java.net.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.PatternSyntaxException;
import java.io.*;

/*
 * Class handles online broker server and processes requests from clients and exchanges
 */
public class OnlineBroker {
    public final static Map<String, Integer> table = Collections.synchronizedMap(new HashMap<String, Integer>());
	private static String exchange;
	private static String hostname;
	static ObjectOutputStream toLookupServer;
	static ObjectInputStream fromLookupServer;
	
	/*
	 * 
	 */
	public static void main(String[] args) throws IOException {
    	    	
    	ServerSocket serverSocket = null;
        boolean listening = true;
		Socket lookupSocket = null;		
	
        try {
        	if(args.length == 4) {
        		String lookup_hostname = args[0];
				int lookup_port = Integer.parseInt(args[1]);
				int my_port = Integer.parseInt(args[2]);
				exchange = args[3];

				serverSocket = new ServerSocket(my_port);

				/* Store the hostname to be searched */
				hostname = InetAddress.getLocalHost().getHostName();
				
				/* Connect to the lookup server and try to register */
				lookupSocket = new Socket(lookup_hostname, lookup_port);
				
 				/* create a packet to register with lookup server */
 				BrokerPacket packetToLookupServer= new BrokerPacket();
				BrokerPacket packetFromLookupServer = new BrokerPacket();
				packetToLookupServer.type = BrokerPacket.LOOKUP_REGISTER;
				
				/* Get the hostname of the broker server */
				String hostname = InetAddress.getLocalHost().getHostName();
				
 				String registry_info =exchange + " " + hostname + " " + Integer.toString(my_port);	
				packetToLookupServer.exchange = registry_info;	
 				System.out.println(registry_info);

 				toLookupServer = new ObjectOutputStream(lookupSocket.getOutputStream());
 				fromLookupServer = new ObjectInputStream(lookupSocket.getInputStream());
 				/* send reply back to client */
 				try {
 					toLookupServer.writeObject(packetToLookupServer);
					packetFromLookupServer = (BrokerPacket) fromLookupServer.readObject();
					
					if (packetFromLookupServer.type != BrokerPacket.SUCCESS) {
						System.err.println("Unsuccessful registration!");
						System.exit(-1);
					}
 				} catch (Exception e) {
 					System.err.println("EXCEPTION!");
 					e.printStackTrace();
 				}
        	} else {
        		System.err.println("ERROR: Invalid arguments!");
        		System.exit(-1);
        	}
        } catch (NumberFormatException e) {
			System.err.println("Listening port argument must be an integer");
			System.exit(-1);
		} catch (IOException e) {
            System.err.println("ERROR: Could not listen on port!");
            System.exit(-1);
        }
        
        // Initialize table by reading quote file
        TableInitializer();
        
        // Creating shutdown hook that writes back file and closes socket
    	Runtime.getRuntime().addShutdownHook ( new ShutdownHookThread(table, exchange) );
    	
        System.out.println("listening...");
        try {
        	while (listening) {
            	new OnlineBrokerHandlerThread(serverSocket.accept(), table, 
							hostname, exchange, toLookupServer, fromLookupServer).start();
            }
            
            serverSocket.close();
        } catch (Exception e) {
        	e.printStackTrace();
        }
        
        lookupSocket.close();
        toLookupServer.close();
        fromLookupServer.close();
    }
	
	private static void TableInitializer () {		
		// Open, read file and populate the HashMap
		try {
			// Open quote file
			FileInputStream fstream = new FileInputStream(exchange);
			
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			
			// Read File Line By Line. Parse and populate hashmap
			while ((strLine = br.readLine()) != null) { // Iterate over all lines
				String[] tokens = strLine.split(" "); // Parse quote data
				if (tokens.length == 2 && tokens[0] != "") {
					// Change all the key values that gets placed into the table to uppercase
					table.put(tokens[0].toUpperCase(), Integer.parseInt(tokens[1])); // Store this quote
				}
			}
			
			// Close the input stream
			in.close();
		} catch (NumberFormatException e) {
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
		} catch (PatternSyntaxException e) {
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
		} catch (Exception e) { // Catch exception if any
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
		}		
	}
}
