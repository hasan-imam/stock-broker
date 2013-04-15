import java.net.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.io.*;

/*
 * Class handles lookup server that keeps track of online brokers and provides
 * information about available online brokers to the clients and exchanges
 */
public class BrokerLookupServer {
	/* Variable stores a list of broker servers that are currently up and running */
	public final static Map<String, Integer> availableServers = Collections.synchronizedMap(new HashMap<String, Integer>());
	
	/*
	 * Method creates a server socket and listens for incoming connection requests
	 */
    public static void main(String[] args) throws IOException {
		// Creating shutdown hook that writes back file and closes socket

        ServerSocket serverSocket = null;
        boolean listening = true;

        try {
        	if(args.length == 1) {
        		serverSocket = new ServerSocket(Integer.parseInt(args[0]));
        	} else {
        		System.err.println("ERROR: Invalid arguments!");
        		System.exit(-1);
        	}
        } catch (IOException e) {
            System.err.println("ERROR: Could not listen on port!");
            System.exit(-1);
        }
    	
        /* Listen for connection request */
		System.out.println("listening...");
        while (listening) {
        	new BrokerLookupServerHandlerThread(serverSocket.accept(), availableServers).start();
        }

		System.out.println("Shutting down Lookup Server\n");
        serverSocket.close();
    }
}
