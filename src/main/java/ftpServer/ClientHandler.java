package ftpServer;

import java.io.*;
import java.net.*;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.mindrot.jbcrypt.BCrypt;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The class {@code ClientHandler} implements the {@link Server} interface and
 * defines all its methods. It provides functionalities to authenticate clients,
 * list the directory tree, upload and download small text files, images,
 * videos, as well as directories. It also allows to create files and
 * directories, delete or rename them directly on the server.
 * 
 */
public class ClientHandler extends Thread implements Server {
	private Socket socket;
	private String rootDirectory;
	private String username;
	private PrintWriter writer;
	private BufferedReader reader;
	private Map<String, String> userCredentials = new HashMap<>();
	private String currentDirectory;
	private ServerSocket pasvServerSocket;
	private Socket dataSocket;
	private String transferType;
	private String renamePath;

	public void setWriter(PrintWriter writer) {
		this.writer = writer;
	}

	public String getCurrentDirectory() {
		return currentDirectory;
	}

	/**
	 * Creates a new object of ClientHandler to manage the interaction between the
	 * server and the clients connected to it.
	 * 
	 * @param socket          the socket connection associated with this FTP client
	 *                        and used for communication between the server and the
	 *                        client
	 * @param rootDirectory   the root directory of the server where all client's
	 *                        directories and files are stored
	 * @param userCredentials a hash map that saves every name and its password;
	 *                        useful for client authentication
	 */
	public ClientHandler(Socket socket, String rootDirectory, Map<String, String> userCredentials) {
		this.socket = socket;
		this.rootDirectory = rootDirectory;
		this.userCredentials = userCredentials;
		this.transferType = "I";
	}

	/**
	 * This method is the entry point of the client handler thread. It initiates
	 * handling of the client socket which receives commands from the FTP server and
	 * responds to them.
	 */
	@Override
	public void run() {
		try {
			handleClientSocket();
		} catch (IOException ex) {
			System.out.println("Server exception: " + ex.getMessage());
		}
	}

	/**
	 * This method initializes the reader and writer for communication with the
	 * client. It then sends a greeting to the client to show that the connection is
	 * successful. It also reads all the commands that are sent by the client to the
	 * server and dispatches them for handling.
	 * 
	 * @throws IOException if an input/output error occurred
	 * 
	 */
	private void handleClientSocket() throws IOException {
		reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		writer = new PrintWriter(socket.getOutputStream(), true);

		sendResponse("220 Connected to the FTP server");

		String command;
		while ((command = reader.readLine()) != null) {
			System.out.println("Received: " + command);
			handleFTPCommands(command);
		}
	}

	/**
	 * This method takes a command as a parameter, extracts the command name from it
	 * and the argument provided by the client for this command then calls the
	 * corresponding method for handling the command.
	 * 
	 * @param command the command to handle that is sent by the client
	 * @throws IOException if an input/output error occurred
	 */
	private void handleFTPCommands(String command) throws IOException {
		String[] tokens = command.split(" ");
		String cmd = tokens[0].toUpperCase();
		String argument = tokens.length > 1 ? tokens[1] : null;

		switch (cmd) {
		case "USER":
			handleUSERCommand(argument);
			break;
		case "PASS":
			handlePASSCommand(argument);
			break;
		case "AUTH":
			handleAUTHCommand();
			break;
		case "PWD":
			handlePWDCommand();
			break;
		case "LIST":
			handleLISTCommand();
			break;
		case "PASV":
			handlePASVCommand();
			break;
		case "TYPE":
			handleTYPECommand(argument);
			break;
		case "EPSV":
			handleEPSVCommand();
			break;
		case "EPRT":
			handleEPRTCommand();
			break;
		case "CWD":
			handleCWDCommand(argument);
			break;
		case "CDUP":
			handleCDUPCommand();
			break;
		case "SIZE":
			handleSIZECommand(argument);
			break;
		case "MDTM":
			handleMDTMCommand(argument);
			break;
		case "RETR":
			handleRETRCommand(argument);
			break;
		case "STOR":
			handleSTORCommand(argument);
			break;
		case "MKD":
			handleMKDCommand(argument);
			break;
		case "DELE":
			handleDELECommand(argument);
			break;
		case "RMD":
			handleRMDCommand(argument);
			break;
		case "RNFR":
			handleRNFRCommand(argument);
			break;
		case "RNTO":
			handleRNTOCommand(argument);
			break;
		default:
			sendResponse("500 command unrecognized");
			break;
		}
	}

	/**
	 * This method handles the USER command from the FTP client. This command
	 * specifies the username for authentication. It sets the username as part of
	 * the session state and asks the client to provide the password in order to
	 * complete the authentication.
	 * 
	 * @param argument the username of the user to authenticate
	 */
	@Override
	public void handleUSERCommand(String argument) {
		username = argument;
		sendResponse("331 Username okay, need password");
	}

	/**
	 * This method handles the PASS command. This command is used to provide the
	 * password of the client and finish the authentication to login. It uses the
	 * {@link #authenticate(String, String)} method to authenticate the user. If the
	 * authentication is successful, it sets up the user's directory on the server
	 * and replies with 230 code to indicate successful login. Otherwise, it sends
	 * 530 code indicating that logging in was not successful.
	 * 
	 * @param argument the password provided by the client
	 * @throws IOException if an input/output error occurred
	 * 
	 */
	@Override
	public void handlePASSCommand(String argument) throws IOException {
		if (argument != null && authenticate(username, argument)) {
			setupUserDirectory();
			sendResponse("230 User logged in, proceed");
		} else {
			sendResponse("530 Not logged in");
		}
	}

	/**
	 * This method handles the PASV command. It sets up a passive server socket and
	 * prepares to handle incoming data connection. It selects an available port
	 * number and and breaks it into 2 parts and sends it along with the server IP
	 * address to the client.
	 * 
	 * @throws IOException if an input/output error occurred
	 */
	@Override
	public void handlePASVCommand() throws IOException {
		if (pasvServerSocket != null && !pasvServerSocket.isClosed()) {
			pasvServerSocket.close();
		}

		pasvServerSocket = new ServerSocket(0);
		int port = pasvServerSocket.getLocalPort();

		int p1 = port / 256;
		int p2 = port % 256;
		String ipAddress = "127,0,0,1";
		sendResponse(String.format("227 Entering Passive Mode (%s,%d,%d)", ipAddress, p1, p2));
	}

	/**
	 * This methods takes a username and a password and searches for this
	 * combination in the hashmap of user credentials.
	 * 
	 * @param username the username of the user to authenticate
	 * @param password the password provided
	 * @return {@code true} if the username and password are present in the hashmap
	 *         and match, {@code false} otherwise
	 */
	public boolean authenticate(String username, String password) {
		if (userCredentials.containsKey(username)) {
			String storedHashedPassword = userCredentials.get(username);
			boolean equalPasswords = BCrypt.checkpw(password, storedHashedPassword);
			return equalPasswords;
		}
		return false;
	}

	/**
	 * This is a helping method that checks whether a directory is a subdirectory of
	 * another one. This is especially useful when changing the working directory to
	 * make sure the client does not access any directory outside his/her own root
	 * directory on the server.
	 * 
	 * @param child  the child directory to check if belongs to another directory
	 * @param parent the parent directory to check if another directory is inside it
	 * @return {@code true} if the child is a subdirectory of the parent,
	 *         {@code false} otherwise
	 * @throws IOException if an input/output error occurred
	 */
	public boolean isSubdirectory(File child, File parent) throws IOException {
		String childPath = child.getCanonicalPath();
		String parentPath = parent.getCanonicalPath();
		return childPath.startsWith(parentPath) && !childPath.equals(parentPath);
	}

	/**
	 * This method is used to set up the user directory in the server. It is useful
	 * to limit where the user can access folders and files as well as upload and
	 * download them. After the user is authenticated, this method is used to set up
	 * the user space by creating a new directory for them if there is not one
	 * already and sets the current working directory as this directory for the
	 * user.
	 */
	public void setupUserDirectory() {
		File userDir = new File(rootDirectory, username);
		if (!userDir.exists()) {
			userDir.mkdirs();
		}
		currentDirectory = "/";
	}

	/**
	 * This method handles the PWD command. It is used to send the current directory
	 * of the client session to the client but relative the user's root directory
	 * and not the whole path insuring it is easily interpreted by the client.
	 */
	@Override
	public void handlePWDCommand() {
		String userRootPath = new File(rootDirectory, username).getAbsolutePath();

		String relativePath = currentDirectory.replace(userRootPath, "");

		relativePath = relativePath.isEmpty() ? "/" : relativePath.replace("\\", "/");

		sendResponse("257 \"" + relativePath + "\" is the current directory");
	}

	/**
	 * This method is used to send a response to the client by using the Print
	 * Writer. It is used to communicate with the client the command outcomes,
	 * errors, and statuses.
	 * 
	 * @param response The message to send to the FTP client
	 */
	private void sendResponse(String response) {
		writer.println(response);

	}

	/**
	 * This method is used to translate a path relative to the client to a path
	 * relative to the server. This is done by getting the absolute path that can be
	 * used by the server later to upload files or create subdirectories or perform
	 * any action in the correct directory context.
	 * 
	 * @param clientPath the path relative to the client
	 * @return the full path relative to the server file system corresponding to the
	 *         provided client path
	 */
	private String translateClientPathToServerPath(String clientPath) {
		clientPath = clientPath != null ? clientPath : "";

		if ("/".equals(clientPath)) {
			return new File(rootDirectory, username).getAbsolutePath();
		}

		Path fullPath;
		if (clientPath.startsWith("/")) {
			fullPath = Paths.get(rootDirectory, username).resolve(clientPath.substring(1)).normalize();
		} else {
			fullPath = Paths.get(rootDirectory, username, currentDirectory).resolve(clientPath).normalize();
		}
		return fullPath.toString();
	}

	/**
	 * This method handles the LIST command. It is used to list the contents of the
	 * current directory relative to the user's root directory to maintain session
	 * isolation and security. It initiates a new thread to handle the passive
	 * connection setup for listing the contents of the directory. It sends
	 * file/folder names along with their sizes, latest modification date,
	 * permissions and other relevant information.
	 * 
	 * <p>
	 * It is noteworthy that this method does not send the real linkcount, owner,
	 * and group of the file/directory for simplicity.
	 */
	@Override
	public void handleLISTCommand() {
		new Thread(() -> {
			try {
				Socket dataConnection = pasvServerSocket.accept();
				PrintWriter dataOut = new PrintWriter(dataConnection.getOutputStream(), true);

				String directoryPath = currentDirectory.equals("/")
						? new File(rootDirectory, username).getAbsolutePath()
						: translateClientPathToServerPath(currentDirectory);

				File directory = new File(directoryPath);
				File[] files = directory.listFiles();

				if (files != null) {
					for (File file : files) {
						String permissions = file.isDirectory() ? "d" : "-";
						permissions += file.canRead() ? "r" : "-";
						permissions += file.canWrite() ? "w" : "-";
						permissions += file.canExecute() ? "x" : "-";

						int linkCount = 1;

						String owner = "owner";
						String group = "group";

						long fileSize = file.length();

						SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd yyyy");
						String modifiedDate = dateFormat.format(new Date(file.lastModified()));

						String fileInfo = String.format("%s%s%s%s %d %s %s %8d %s %s", permissions, "rwx", "rwx", "rwx",

								linkCount, owner, group, fileSize, modifiedDate, file.getName());

						dataOut.println(fileInfo);
					}
				}

				dataOut.close();
				dataConnection.close();

				sendResponse("226 Transfer complete.");
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if (pasvServerSocket != null && !pasvServerSocket.isClosed()) {
						pasvServerSocket.close();
					}
					pasvServerSocket = null;
				} catch (IOException e) {
					e.printStackTrace();
				}
				dataSocket = null;
			}
		}).start();
	}

	/**
	 * This method handles the CWD command. It changes the client's session current
	 * working directory. It makes sure that the path provided is indeed a directory
	 * and lies within the client's root directory to maintain the security and
	 * isolation of the session. If the path is valid, it responds by 250 code and
	 * changes the current directory. Otherwise it replies by an error code 500 and
	 * the current directory remains unchanged.
	 * 
	 * @param clientPath the new working directory within the client's session
	 * @throws IOException if an input/output error occurred
	 */
	@Override
	public void handleCWDCommand(String clientPath) throws IOException {

		String serverPath = translateClientPathToServerPath(clientPath);
		File newDir = new File(serverPath);
		File userRootDir = new File(translateClientPathToServerPath("/"));

		if (!newDir.exists() || !newDir.isDirectory() || !isSubdirectory(newDir, userRootDir)) {
			sendResponse("550 Failed to change directory.");
			return;
		}

		this.currentDirectory = clientPath.startsWith("/") ? clientPath : "/" + clientPath;
		sendResponse("250 Directory successfully changed.");
	}

	/**
	 * This method handles the CDUP command. It changes the current directory to its
	 * parent directory as long as it is still a subdirectory of the client's root.
	 * If the client is already in their root, the responds by the code 250 but
	 * informs the client through the message that they are already in the root. If
	 * the parent directory is invalid, it replies by an error.
	 * 
	 * @throws IOException if an input/output error occurred
	 */
	@Override
	public void handleCDUPCommand() throws IOException {
		Path currentDirPath = Paths.get(translateClientPathToServerPath(currentDirectory));
		Path userRootDirPath = Paths.get(translateClientPathToServerPath("/"));

		if (currentDirPath.equals(userRootDirPath)) {
			sendResponse("250 Directory already in user root.");
			return;
		}

		Path parentDirPath = currentDirPath.getParent();

		if (parentDirPath == null || !parentDirPath.startsWith(userRootDirPath)) {
			sendResponse("550 Cannot change to directory above root.");
			return;
		}

		if (parentDirPath.equals(userRootDirPath)) {
			currentDirectory = "/";
		} else {
			currentDirectory = "/" + userRootDirPath.relativize(parentDirPath).toString().replace("\\", "/");
		}

		sendResponse("250 Directory successfully changed.");
	}

	/**
	 * This method handles the SIZE command. It takes as an argument the filename to
	 * get its size and sends the files's length through the response.
	 * 
	 * @param filename the relative path to the file from the client
	 * @throws IOException if an input/output error occurred
	 */
	@Override
	public void handleSIZECommand(String filename) throws IOException {
		File file = new File(translateClientPathToServerPath(filename));
		if (file.exists() && file.isFile()) {
			sendResponse("213 " + file.length());
		} else {
			sendResponse("550 File not found.");
		}
	}

	/**
	 * This method handles the MDTM command. It sends to the client the last
	 * modification date of a specific file. In case the filename provided does not
	 * exist or is a directory, the server sends a 550 error code to the client.
	 * 
	 * @param filename the relative path to the file from the client
	 * @throws IOException if an input/output error occurred
	 */
	@Override
	public void handleMDTMCommand(String filename) throws IOException {
		File file = new File(translateClientPathToServerPath(filename));
		if (file.exists() && file.isFile()) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
			sendResponse("213 " + dateFormat.format(new Date(file.lastModified())));
		} else {
			sendResponse("550 File not found.");
		}
	}

	/**
	 * This method handles the TYPE command. It sets the transfer type to either
	 * ASCII ('A') when dealing with text files or Binary ('I') when dealing with
	 * binary files such as images and videos.
	 * 
	 * @param typeCode the transfer type code which could be either 'A' for ASCII
	 *                 mode or 'I' for binary mode
	 * @throws IOException if an input/output error occurred
	 */
	@Override
	public void handleTYPECommand(String typeCode) throws IOException {
		switch (typeCode) {
		case "A":
			transferType = "A";
			sendResponse("200 Switching to ASCII mode.");
			break;
		case "I":
			transferType = "I";
			sendResponse("200 Switching to Binary mode.");
			break;
		default:
			sendResponse("504 Command not implemented for that parameter.");
			break;
		}
	}

	/**
	 * This method handles the RETR command. It is used to download a file or
	 * directory. This method can download different types of files such as text
	 * files, images, and videos. It can also download directories and their
	 * contents. For files, the content is sent directly over the data connection.
	 * For directories, they are first compressed into a ZIP file and then
	 * transmitted.
	 * <p>
	 * This method starts a new thread when transmitting files and folders to make
	 * sure the server continues receiving other commands and is not busy. When the
	 * connection is initiated, it sends a code 150. When the transfer is
	 * successful, it sends the code 226. If the file transfer failed, it sends an
	 * error code 426.
	 * 
	 * @param filename the relative path to the file to retrieve
	 * @throws IOException if an input/output error occurred
	 */
	@Override
	public void handleRETRCommand(String filename) throws IOException {
		new Thread(() -> {
			try (Socket dataConnection = pasvServerSocket.accept();
					BufferedOutputStream dataOut = new BufferedOutputStream(dataConnection.getOutputStream())) {

				File target = new File(translateClientPathToServerPath(filename));
				if (!target.exists()) {
					sendResponse("550 File not found.");
					return;
				}

				sendResponse("150 Opening " + transferType + " mode data connection for " + filename);

				if (target.isDirectory()) {
					try (ZipOutputStream zipOut = new ZipOutputStream(dataOut)) {
						Path basePath = Paths.get(target.getAbsolutePath());
						Files.walk(basePath).forEach(path -> {
							String zipEntryName = basePath.relativize(path).toString();
							if (Files.isDirectory(path)) {
								zipEntryName = zipEntryName.endsWith("/") ? zipEntryName : zipEntryName + "/";
								try {
									zipOut.putNextEntry(new ZipEntry(zipEntryName));
									zipOut.closeEntry();
								} catch (IOException e) {
									e.printStackTrace();
								}
							} else {
								try (InputStream inputStream = new BufferedInputStream(
										new FileInputStream(path.toFile()))) {
									zipOut.putNextEntry(new ZipEntry(zipEntryName));
									byte[] bytes = new byte[4096];
									int length;
									while ((length = inputStream.read(bytes)) >= 0) {
										zipOut.write(bytes, 0, length);
									}
									zipOut.closeEntry();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						});
					}
				} else {
					try (InputStream fileInput = new BufferedInputStream(new FileInputStream(target))) {
						byte[] buffer = new byte[4096];
						int bytesRead;
						while ((bytesRead = fileInput.read(buffer)) != -1) {
							dataOut.write(buffer, 0, bytesRead);
						}
					}
				}

				sendResponse("226 Transfer complete.");
			} catch (IOException e) {
				sendResponse("426 Connection closed; transfer aborted.");
				e.printStackTrace();
			} finally {
				try {
					if (pasvServerSocket != null && !pasvServerSocket.isClosed()) {
						pasvServerSocket.close();
					}
					pasvServerSocket = null;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	/**
	 * This method handles the STOR command. It is used to upload files and folders
	 * to the server. It can handle storing different types of files such as text
	 * files, images, and videos. It can also store directories and their contents
	 * with the help of the method {@link #handleMKDCommand(String)} that creates
	 * new directories while storing as needed.
	 * <p>
	 * This method starts a new thread when receiving files and folders to make sure
	 * the server continues receiving other commands and is not busy. When the
	 * connection is initiated, it sends a code 150. When the transfer is
	 * successful, it sends the code 226. If the file transfer failed, it sends an
	 * error code 552.
	 * 
	 * 
	 * @param filename the relative path to the file to store
	 *
	 */
	@Override
	public void handleSTORCommand(String filename) {
		new Thread(() -> {
			try {
				Socket dataConnection = pasvServerSocket.accept();
				BufferedInputStream dataInput = new BufferedInputStream(dataConnection.getInputStream());

				String relativePath = currentDirectory.equals("/") ? filename : currentDirectory + "/" + filename;
				String serverPath = translateClientPathToServerPath(relativePath);
				File file = new File(serverPath);

				try (FileOutputStream fileOut = new FileOutputStream(file);
						BufferedOutputStream fileBuffer = new BufferedOutputStream(fileOut)) {

					sendResponse("150 Ok to send data.");

					byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = dataInput.read(buffer)) != -1) {
						fileBuffer.write(buffer, 0, bytesRead);
					}
					fileBuffer.flush();

					sendResponse("226 Transfer complete.");
				} catch (IOException e) {
					sendResponse("552 Requested file action aborted.");
				} finally {
					if (dataConnection != null && !dataConnection.isClosed()) {
						dataConnection.close();
					}
				}
			} catch (IOException e) {
				sendResponse("425 Can't open data connection.");
			} finally {
				try {
					if (pasvServerSocket != null && !pasvServerSocket.isClosed()) {
						pasvServerSocket.close();
					}
					pasvServerSocket = null;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	/**
	 * This method handles the MKD command. It is used to create a new directory in
	 * the current directory in the user's space on the server. It checks if the
	 * directory is being created in a valid location or if it already exits. If the
	 * path provided for the directory is valid, it replies with 257 code for
	 * successful creation.
	 * 
	 * @param directoryName the name of the directory to be created
	 */
	@Override
	public void handleMKDCommand(String directoryName) {
		if (directoryName == null || directoryName.trim().isEmpty()) {
			sendResponse("501 Syntax error in parameters or arguments.");
			return;
		}

		String relativePath = currentDirectory.equals("/") ? directoryName : currentDirectory + "/" + directoryName;
		String path = translateClientPathToServerPath(relativePath);
		File newDir = new File(path);

		if (!path.startsWith(new File(rootDirectory, username).getAbsolutePath())) {
			sendResponse("550 Permission denied.");
			return;
		}

		if (newDir.exists()) {
			sendResponse("550 Directory already exists.");
		} else {
			boolean created = newDir.mkdirs();
			if (created) {
				sendResponse("257 \"" + directoryName + "\" created.");
			} else {
				sendResponse("550 Failed to create directory.");
			}
		}
	}

	/**
	 * This method handles the DELE command. It is used to delete a file in the
	 * current working directory. Before deleting the specified file, it makes sure
	 * it is not a directory and that the file indeed exists.
	 * 
	 * @param filename the name of the file to be deleted
	 */
	@Override
	public void handleDELECommand(String filename) {
		String relativePath = currentDirectory.equals("/") ? filename : currentDirectory + "/" + filename;
		String serverPath = translateClientPathToServerPath(relativePath);
		File file = new File(serverPath);

		if (!file.exists() || file.isDirectory()) {
			sendResponse("550 File not found or is a directory.");
			return;
		}

		if (file.delete()) {
			sendResponse("250 File deleted successfully.");
		} else {
			sendResponse("450 File deletion failed.");
		}
	}

	/**
	 * This method handles the RMD command. It is used to delete a directory in the
	 * current working space. Before deleting the directory, it ensures that the
	 * directory exists and is not empty.
	 * 
	 * @param dirname the name of the directory to be removed
	 * 
	 */
	@Override
	public void handleRMDCommand(String dirname) {
		String relativePath = currentDirectory.equals("/") ? dirname : currentDirectory + "/" + dirname;
		String serverPath = translateClientPathToServerPath(relativePath);

		File directory = new File(serverPath);

		if (!directory.exists() || !directory.isDirectory()) {
			sendResponse("550 Directory not found or is a file.");
			return;
		}

		if (directory.delete()) {
			sendResponse("250 Directory deleted successfully.");
		} else {
			sendResponse("450 Directory deletion failed. Directory might not be empty.");
		}
	}

	/**
	 * This method handles the RNFR command. It specifies the file or directory to
	 * be renamed. It is used in conjunction with the
	 * {@link #handleRNTOCommand(String)}. It verifies the existence of the file or
	 * the directory to be renamed and saves its path to be used subsequently by
	 * RNTO command.
	 * 
	 * @param filename the name of the file or directory to be renamed.
	 */
	@Override
	public void handleRNFRCommand(String filename) {
		String relativePath = currentDirectory.equals("/") ? filename : currentDirectory + "/" + filename;

		String serverPath = translateClientPathToServerPath(relativePath);
		File file = new File(serverPath);

		if (!file.exists()) {
			sendResponse("550 File or directory not found.");
			return;
		}

		renamePath = serverPath;
		sendResponse("350 Ready for RNTO.");
	}

	/**
	 * This method handles the RNTO command. It shall be preceded by a call to
	 * {@link #handleRNFRCommand(String)}. It specifies the name of the file or the
	 * folder to the name provided as an argument.
	 * 
	 * @param newFilename the new name of the file
	 */
	@Override
	public void handleRNTOCommand(String newFilename) {
		if (renamePath == null || renamePath.isEmpty()) {
			sendResponse("503 Bad sequence of commands: Use RNFR before RNTO.");
			return;
		}

		String newServerPath = translateClientPathToServerPath(newFilename);
		File newFile = new File(newServerPath);

		if (newFile.exists()) {
			sendResponse("553 RNTO failed: File or directory already exists.");
			return;
		}

		File oldFile = new File(renamePath);
		if (oldFile.renameTo(newFile)) {
			sendResponse("250 File or directory renamed successfully.");
		} else {
			sendResponse("550 Rename failed.");
		}

		renamePath = null;
	}

	/**
	 * This method handles the AUTH command from the FTP client. This command is
	 * used to initiate a secure communication. This server does not support secure
	 * communication and thus replies with 502 code informing the client that it is
	 * not implemented.
	 */
	@Override
	public void handleAUTHCommand() {
		sendResponse("502 Command not implemented");
	}

	/**
	 * This method handles the EPSV command from the FTP client. This command is
	 * used to request the server to enter a passive mode for IPv6 specifically.
	 * This server does not support extended passive mode and thus replies with 502
	 * code informing the client that it is not implemented.
	 */
	@Override
	public void handleEPSVCommand() {
		sendResponse("502 Command not implemented");
	}

	/**
	 * This method handles the EPRT command from the FTP client. This command is
	 * used to request the server to initiate an active data connection for IPv6
	 * specifically. This server does not support this feature and thus replies with
	 * 502 code informing the client that it is not implemented.
	 */
	@Override
	public void handleEPRTCommand() {
		sendResponse("502 Command not implemented");
	}

}
