import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Map;

class FileManager {
	/*
	 * Reads in stock values from the file 
	 * @param file File to open and read from
	 * @return Returns a HashMap<symbol, quote> of the values 
	 */
	public Map<String, Integer> reader (String file, Map<String, Integer> m) {
		//Map<String, Integer> m = Collections.synchronizedMap(new HashMap<String, Integer>());
		
		// Open, read file and populate the HashMap
		try {
			// Open quote file
			FileInputStream fstream = new FileInputStream("nasdaq");
			
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			
			// Read File Line By Line. Parse and populate hashmap
			while ((strLine = br.readLine()) != null) {
				String[] tokens = strLine.split(" ");
				m.put(tokens[0], Integer.parseInt(tokens[1]));
				
				
				System.out.println (strLine + ". Looked up: " + m.get(tokens[0])); ///temp
			}
			
			// Close the input stream
			in.close();
		} catch (Exception e) { //Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}
		
		return m;
	}
}