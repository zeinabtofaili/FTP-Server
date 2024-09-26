package ftpServer;

import java.io.*;
import java.net.*;
import java.util.Map;

/**
 * This class is the driver class of the FTP server. It starts the FTP server on
 * port 21 and listens to incoming connections from clients.
 * 
 * <p>
 * Example command-line usage:
 * 
 * <pre>
 *     java -jar target/FTPServerProj-1.0-SNAPSHOT.jar &lt;server-root-directory&gt;
 * </pre>
 */
public class Main {

	/**
	 * This class is the driver class of the FTP server. It takes as an argument the
	 * root directory of the server and initiates it to listen for incoming client
	 * connections on port 21 which is the default FTP port. It also launches a new
	 * thread for each client connection to ensure the server can manage multiple
	 * clients simultaneously.
	 * <p>
	 * The root directory of the server is provided in the first command-line
	 * argument. User credentials for authenticating users are loaded from a
	 * predetermined file ("credentials.txt").
	 * </p>
	 * <p>
	 * Usage:
	 * 
	 * <pre>
	 *     java -jar target/FTPServerProj-1.0-SNAPSHOT.jar &lt;server-root-directory&gt;
	 * </pre>
	 * </p>
	 * If the server root path is missing, the server will not start, and a message
	 * is displayed informing the user of the proper usage of the program.
	 *
	 * @param args The first command-line argument provided should be the path to
	 *             the server root directory where the server will host and manage
	 *             files and user directories.
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Usage: java -jar target/FTPServerProj-1.0-SNAPSHOT.jar <server-root-directory>");
			System.exit(1);
		}

		String rootDirectory = args[0];
		int port = 21;
		Map<String, String> userCredentials = Utils.loadUserCredentials("credentials.txt");

		try (ServerSocket serverSocket = new ServerSocket(port)) {
			System.out.println("FTP Server is listening on port " + port);

			while (true) {
				Socket socket = serverSocket.accept();
				System.out.println("Client connected: " + socket.getInetAddress());

				ClientHandler session = new ClientHandler(socket, rootDirectory, userCredentials);
				new Thread(session).start();
			}
		} catch (IOException ex) {
			System.out.println("Server exception: " + ex.getMessage());
			ex.printStackTrace();
		}

	}
}
