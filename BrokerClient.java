import java.io.*;
import java.net.*;

/*
 * Class runs the client server and processes requests to be sent to the 
 * online broker
 */
public class BrokerClient {
	/*
	 * Method reads in user input to try to set the local online broker
	 */
	public static String setLocal(){
		BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
		String setLocal = null;
		String command = "dummyCommand";
		String broker = null;

		/* Keep looping until proper broker information has been set */
		while((!command.equals("local")) && (!command.equals("x"))){
			System.out.println("Please set the local broker or enter 'x' to exit");
			System.out.print("> ");
			try {
				setLocal = stdIn.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			String[] tokens = setLocal.split(" ");
			command = tokens[0];
			if(tokens.length == 2)
				broker = tokens[1];
			
			continue;
		}
		
		/* If user choses to exit instead of setting local online broker
		 * return "exit", otherwise return the name of the broker the client is
		 * trying to connect to*/
		if(command.equals("x"))
			return "exit";
		else 
			return broker;
		
		
	}
	
	/*
	 * Main process for processing and routing client requests
	 */
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		
		Socket lookupSocket = null;
		Socket clientSocket = null;
		ObjectOutputStream out = null;
		ObjectInputStream in = null;
		
		/* Variables for hostname and port of the naming service*/
		String hostname = "";
		int port = -1;
		
		try {
			if(args.length == 2 ) {
				hostname = args[0];
				port = Integer.parseInt(args[1]);
			} else {
				System.err.println("ERROR: Invalid arguments!");
				System.exit(-1);
			}
			lookupSocket = new Socket(hostname, port);
		
			out = new ObjectOutputStream(lookupSocket.getOutputStream());
			in = new ObjectInputStream(lookupSocket.getInputStream());
		
		} catch (UnknownHostException e) {
			System.err.println("ERROR: Don't know where to connect!!");
			System.exit(1);
		} catch (IOException e) {
			System.err.println("ERROR: Couldn't get I/O for the connection.");
			System.exit(1);
		}
		
		BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
		String userInput;
		
		BrokerPacket packetToServer = new BrokerPacket();
		BrokerPacket packetFromServer = null;
	
		/*********************** Register client **************************/
		packetToServer.type = BrokerPacket.LOOKUP_REGISTER;
		packetToServer.exchange = "client";
		out.writeObject(packetToServer);
		packetFromServer = (BrokerPacket) in.readObject();
		
		if(packetFromServer.type != BrokerPacket.SUCCESS)
			System.err.println("ERROR: Client could not be registered!");
		else
			System.out.println("Registered client with Lookup Server");
		
		
		/********************* Set Local or Exit **************************/
		/* Trying to set local */
		packetToServer = new BrokerPacket();
		packetToServer.type = BrokerPacket.LOOKUP_REQUEST;
		String setLocal = setLocal();
		
		if(!setLocal.equals("exit")){
			packetToServer.exchange = setLocal;
			//System.out.println (setLocal);
		}else{
			System.out.println("Goodbye!");
			in.close();
			out.close();
			lookupSocket.close();
			return;
		}
		
		out.writeObject(packetToServer);
		packetFromServer = (BrokerPacket) in.readObject();
		
		/* Process reply that has been sent back from the Lookup Server
		 * Keep looping until user has connected to a proper online broker */
		while(packetFromServer.type == BrokerPacket.LOOKUP_REPLY){
			packetToServer = new BrokerPacket();
			packetToServer.type = BrokerPacket.LOOKUP_REQUEST;
			
			if(packetFromServer.exchange.equals("notFound")){
				System.out.println("Broker is not available.");
				setLocal = setLocal();
				
				if(!setLocal.equals("exit"))
					packetToServer.exchange = setLocal;
				else{
					System.out.println("Goodbye!");
					in.close();
					out.close();
					lookupSocket.close();
					return;
				}
				
				out.writeObject(packetToServer);
				packetFromServer = (BrokerPacket) in.readObject();
			}else{
				System.out.println("Broker is online!");
				
				in.close();
				out.close();
				lookupSocket.close();
				
				/* Reopen socket to connect to server */
				String[] tokens = packetFromServer.exchange.split(" ");
				clientSocket = new Socket(tokens[0], Integer.parseInt(tokens[1]));
				out = new ObjectOutputStream(clientSocket.getOutputStream());
				in = new ObjectInputStream(clientSocket.getInputStream());
				
				break;
			}
		}
		
		/******************* Process requests to local broker ****************************/
		System.out.print("> ");
		
		while ((userInput = stdIn.readLine()) != null) {
			packetToServer = new BrokerPacket();
			
			/* Handling (possible) exit case */
			if(userInput.toLowerCase().equals("x"))
			{			
				/* tell server that i'm quitting */
				packetToServer.type = BrokerPacket.BROKER_BYE;
				out.writeObject(packetToServer);
				
				packetFromServer = (BrokerPacket) in.readObject();
				if(packetFromServer.type == BrokerPacket.BROKER_BYE)
					System.out.println(packetFromServer.symbol);
				else
					System.err.println("ERROR: Server didn't acknowledge exit request");
				
				out.close();
				in.close();
				stdIn.close();
				clientSocket.close();
				return;
			}else{
				/*
				 * This scenario handles the case when client tries to change local broker
				 * Client stays connected to current local online broker until another
				 * available online broker information is sent to the naming service
				 * 
				 * Upon finding another available online broker, disconnect from current
				 * online broker and connect to the new online broker
				 */
				String[] tokens = userInput.split(" ");
				
				if(tokens.length == 2){
					if(tokens[0].equals("local")){
						System.out.println("Changing local broker");
						
						/* Reopen clientSocket and connect to the new local broker*/
						lookupSocket = new Socket(hostname, port);
						
						ObjectOutputStream outLookup = new ObjectOutputStream(lookupSocket.getOutputStream());
						ObjectInputStream inLookup = new ObjectInputStream(lookupSocket.getInputStream());
						
						packetToServer = new BrokerPacket();
						packetToServer.type = BrokerPacket.LOOKUP_REQUEST;
						packetToServer.exchange = tokens[1];
						
						/*
						 * Ask lookup server to check if the online broker that the client
						 * is trying to connect to is available. 
						 */
						outLookup.writeObject(packetToServer);
						packetFromServer = (BrokerPacket) inLookup.readObject();
						
						if(packetFromServer.type == BrokerPacket.LOOKUP_REPLY){
							outLookup.close();
							inLookup.close();
							lookupSocket.close();

							if(packetFromServer.exchange.equals("notFound")){
								System.out.println("Broker not found! Try to connect to another local broker");
							}else{
								/* Getting here means exchange was found and the information about hostname
								 *  and port was received */
								String[] token = packetFromServer.exchange.split(" ");

								/* Reopen socket to connect to server */
								packetToServer = new BrokerPacket();
								packetToServer.type = BrokerPacket.BROKER_BYE;

								/* Disconnect from the current local online broker */
								out.writeObject(packetToServer);
								packetFromServer = (BrokerPacket) in.readObject();
								
								if(packetFromServer.type != BrokerPacket.BROKER_BYE)
									System.err.println("ERROR: Server didn't acknowledge client disconnection");

								in.close();
								out.close();
								clientSocket.close();
								
								/* Connect to the new online broker */
								clientSocket = new Socket(token[0], Integer.parseInt(token[1]));
								out = new ObjectOutputStream(clientSocket.getOutputStream());
								in = new ObjectInputStream(clientSocket.getInputStream());
							}
						}
					}else 
						System.out.println("Invalid command");
					
					System.out.print("> ");
					continue;
				}
			}
			
			/* Make a new quote request packet */
			packetToServer.type = BrokerPacket.BROKER_REQUEST;
			
			String companyIdentifier = userInput.toUpperCase();
			packetToServer.symbol = companyIdentifier;
			try {
				out.writeObject(packetToServer);
				
				/* print server reply */
				packetFromServer = (BrokerPacket) in.readObject();
			} catch (IOException e) {
				System.out.println("Exception occured. Aborted.");
				continue;
			}
			
			if (packetFromServer.type == BrokerPacket.BROKER_QUOTE) {
				System.out.println("Quote from broker: " + packetFromServer.quote);
			} else if (packetFromServer.type == BrokerPacket.BROKER_ERROR) {
				if (packetFromServer.error_code == BrokerPacket.ERROR_INVALID_SYMBOL) {
					System.out.println(userInput + " invalid.");
				}else {
					System.out.println("Unknown error code from server.");
				}
			} else {
				System.out.println("Unknown packet type from server.");
			}
				
		
			/* re-print console prompt */
			System.out.print("> ");
		}
		
		out.close();
		in.close();
		stdIn.close();
		clientSocket.close();
	}
}
