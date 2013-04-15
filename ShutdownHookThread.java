import java.util.Map;
import java.io.*;

/*
 * Class handles database changes when online broker exits by storing cached changes 
 * to the exchange database onto the database file
 */
public class ShutdownHookThread extends Thread {

	protected String message;
	private String exchange;
	private Map<String, Integer> table;
	
	public ShutdownHookThread (Map<String, Integer> table, String exchange) {
		this.message = "Shutting down...";
		this.exchange = exchange;
		this.table = table;
	}
	
	public void run ( ) {
		System.out.println ( );
		System.out.println ( message );
		message = "";
		
		if (table.isEmpty()) {
			// No clean up required
			return;
		}
		
		/* Write back to file */
		synchronized (table) {  // Synchronizing on table (a reference of the original one)
			for (Map.Entry<String, Integer> entry : table.entrySet()) {
			    message += entry.getKey() + " " + entry.getValue().toString() + "\n";
			}
		}
		
		PrintWriter out = null;
		try {
			out = new PrintWriter(exchange);
			out.println(message);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			if (out != null) out.close();
		}
		System.out.println("File: " + exchange + " updated.");
	}
} 

