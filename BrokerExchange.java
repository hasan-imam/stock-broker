import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.regex.PatternSyntaxException;

public class BrokerExchange {
	private static String exchange = "";

	public static void main(String[] args) throws IOException, ClassNotFoundException {

		Socket exchangeSocket = null;
		ObjectOutputStream out = null;
		ObjectInputStream in = null;

		/* Parsing inputs and creating sockets etc. */
		try {
			/* variables for hostname/port */
			String hostname = "";
			int port = -1;

			if(args.length == 3 ) {
				hostname = args[0];
				port = Integer.parseInt(args[1]);
			} else {
				System.err.println("ERROR: Invalid arguments!");
				System.exit(-1);
			}
			exchangeSocket = new Socket(hostname, port);

			out = new ObjectOutputStream(exchangeSocket.getOutputStream());
			in = new ObjectInputStream(exchangeSocket.getInputStream());

		} catch (UnknownHostException e) {
			System.err.println("ERROR: Don't know where to connect!!");
			System.exit(1);
		} catch (IOException e) {
			System.err.println("ERROR: Couldn't get I/O for the connection.");
			System.exit(1);
		}

		BrokerPacket packetToServer = new BrokerPacket();
		BrokerPacket packetFromServer = null;

		/*********************** Register client **************************/
		packetToServer.type = BrokerPacket.LOOKUP_REGISTER;
		packetToServer.exchange = "exchange";
		out.writeObject(packetToServer);
		packetFromServer = (BrokerPacket) in.readObject();

		if(packetFromServer.type != BrokerPacket.SUCCESS)
			System.err.println("ERROR: Exchange could not be registered!");
		else
			System.out.println("Registered exchange with Lookup Server");


		/********************* Set Local or Exit **************************/
		/* Trying to set local */
		packetToServer = new BrokerPacket();
		packetToServer.type = BrokerPacket.LOOKUP_REQUEST;

		packetToServer.exchange = args[2];

		out.writeObject(packetToServer);
		packetFromServer = (BrokerPacket) in.readObject();

		if(packetFromServer.exchange.equals("notFound")){
			System.out.println("Broker " + args[2] + " is not available.");

			System.out.println("Goodbye!");
			in.close();
			out.close();
			exchangeSocket.close();
			return;
		} else{
			System.out.println("Broker is online!");

			in.close();
			out.close();
			exchangeSocket.close();

			/* Reopen socket to connect to server */
			String[] tokens = packetFromServer.exchange.split(" ");
			exchangeSocket = new Socket(tokens[0], Integer.parseInt(tokens[1]));
			out = new ObjectOutputStream(exchangeSocket.getOutputStream());
			in = new ObjectInputStream(exchangeSocket.getInputStream());

		}


		BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
		String userInput;

		/* Command line interaction begins */
		for (System.out.print("> "); (userInput = stdIn.readLine()) != null; System.out.print("> ")) { // Reading from commandline
			String[] tokens;
			int quote = 0;

			/* Parse input */
			try {
				tokens = userInput.split(" ");
			} catch (PatternSyntaxException e) {
				System.err.println("ERROR: Parsing failed.");
				e.printStackTrace();
				continue;
			}

			/* Handling exit case */
			if (tokens[0].compareToIgnoreCase("x") == 0) {
				break;
			} 
			/* Empty line case */
			else if (tokens[0].compareToIgnoreCase("") == 0 || tokens.length == 0) {
				continue;
			}
			/* Invalid input */
			else if (tokens.length < 2) {
				System.err.println("Invalid command.");
				continue;
			}

			/* Make a new packet to send */
			packetFromServer = null;
			packetToServer = new BrokerPacket();
			packetToServer.symbol = tokens[1].toUpperCase(); // Second argument is always the symbol. Converted to uppercase
			packetToServer.exchange = exchange;

			/* Handle user commands. Building packet accordingly */
			if (tokens[0].equalsIgnoreCase("add") && tokens.length == 2) {
				packetToServer.type = BrokerPacket.EXCHANGE_ADD;
			} else if (tokens[0].equalsIgnoreCase("update") && tokens.length == 3) {
				try {
					packetToServer.type = BrokerPacket.EXCHANGE_UPDATE;
					quote = Integer.parseInt(tokens[2]);
					packetToServer.quote = (long) quote;
					if (packetToServer.quote == null || quote > 300 || quote < 1) {
						System.err.println(tokens[1] + " out of range.");
						continue;
					}
				} catch (NumberFormatException e) {
					System.err.println(tokens[1] + " out of range.");
					continue;
				} catch (Exception e) {
					System.err.println("Exception!");
					continue;
				}

			} else if (tokens[0].equalsIgnoreCase("remove") && tokens.length == 2) {
				packetToServer.type = BrokerPacket.EXCHANGE_REMOVE;
			} else {
				System.err.println("Invalid command.");
				continue;
			}

			try {
				/* Sending packet */
				out.writeObject(packetToServer);

				/* Get server reply */
				packetFromServer = (BrokerPacket) in.readObject();
			} catch (IOException e) {
				System.err.println("Exception occured in send/recieve through socket. Operation aborted.");
				if (exchangeSocket.isConnected() == false) {
					System.err.println("Lost connection to host. Exiting.");
					break;
				}
				continue;
			}

			/* Processing reply from server */
			if (packetFromServer.type == BrokerPacket.EXCHANGE_REPLY) {
				if (packetFromServer.error_code == BrokerPacket.SUCCESS) { // Successful operation on server side
					if (tokens[0].equalsIgnoreCase("add")) {
						System.out.println(tokens[1] + " added.");
					} else if (tokens[0].equalsIgnoreCase("remove")) {
						System.out.println(tokens[1] + " removed.");
					} else if (tokens[0].equalsIgnoreCase("update")) {
						System.out.println(tokens[1] + " updated to " + packetToServer.quote + ".");
					} else {
						System.err.println("Logic error in program.");
					}
				} else if (packetFromServer.error_code == BrokerPacket.ERROR_SYMBOL_EXISTS) {
					System.out.println(tokens[1] + " exists.");
				} else if (packetFromServer.error_code == BrokerPacket.ERROR_INVALID_SYMBOL) {
					System.out.println(tokens[1] + " invalid.");
				}
			} else if (packetFromServer.type == BrokerPacket.BROKER_ERROR) {
				System.err.println("Wrong packet type from server.");
			} else {
				System.err.println("Unknown packet type from server.");
			}
		}

		if (exchangeSocket.isConnected() == true) {
			/* tell server that i'm quitting */
			packetToServer = new BrokerPacket();
			packetToServer.type = BrokerPacket.BROKER_BYE;
			out.writeObject(packetToServer);

			out.close();
			in.close();
			stdIn.close();
			exchangeSocket.close();
		} else {
			out.close();
			in.close();
			stdIn.close();
		}
	}
}
