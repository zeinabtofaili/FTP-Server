package ftpServer;

import java.nio.file.*;
import java.io.*;
import java.util.*;

/**
 * This is a helping class that contains some helping methods for specific
 * functionalities in the program.
 */
public class Utils {

	/**
	 * This method is used to load the user credentials from a file on the system to
	 * a hashmap where the keys are the usernames and the values are the passwords.
	 * This map is to be used in user authentication to make sure a username is
	 * associated to a specific password.
	 * 
	 * @param filePath the path to the file where the username-password pairs are
	 *                 saved.
	 * @return a hash map with the user credentials
	 */
	public static Map<String, String> loadUserCredentials(String filePath) {
		Map<String, String> userCredentials = new HashMap<>();
		Path path = Paths.get(filePath);

		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] parts = line.split(",");
				if (parts.length == 2) {
					userCredentials.put(parts[0], parts[1]);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return userCredentials;
	}
	
}
