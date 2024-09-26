package ftpServer;

import java.io.IOException;

/**
 * The {@code Server} interface defines the contract for an FTP server that
 * interacts with an FTP client. It encapsulates all the necessary
 * functionalities to interact with an FTP client.
 *
 * <p>
 * Implementations of this interface should handle various FTP operations such
 * as:
 * <ul>
 * <li>Authenticating an FTP client</li>
 * <li>Listing directory contents</li>
 * <li>Downloading and uploading text files, images, videos, as well as
 * directories</li>
 * <li>Renaming and deleting files and directories on the server</li>
 * <li>Creating files and directories on the server</li>
 * </ul>
 * 
 * @see ClientHandler
 *
 */
public interface Server {

	/**
	 * Handles the USER command sent by the FTP client. It provides a username for
	 * identifying the user. It is usually followed by a PASS command to specify the
	 * password and complete the authentication.
	 * 
	 * @param username the username identifying the user
	 */
	void handleUSERCommand(String username);

	/**
	 * Handles the PASS command sent by the FTP client. It provides the password for
	 * authenticating a user. This command is usually sent after USER command to
	 * authenticate a user.
	 * 
	 * @param password the password provided for the user
	 * @throws IOException if an input/output error occurred
	 */
	void handlePASSCommand(String password) throws IOException;

	/**
	 * Handles the AUTH command sent by the FTP client. It is used to initiate a
	 * secure connection with the FTP server using security mechanisms such as SSL
	 * and TLS.
	 */
	void handleAUTHCommand();

	/**
	 * Handles the PWD command sent by the FTP client. It is used to print the
	 * current working directory.
	 */
	void handlePWDCommand();

	/**
	 * Handles the LIST command sent by the FTP client. It is used to send
	 * information about files and directories through a previously established data
	 * connection.
	 */
	void handleLISTCommand();

	/**
	 * Handles the PASV command sent by the FTP client. It tells the server to open
	 * a passive connection rather than an active one. In this type of connection,
	 * users connecting behind firewalls are able to establish connection with the
	 * server. Furthermore, the server sends the client the IP address and port
	 * number to connect to.
	 * 
	 * @throws IOException if an input/output error occurred
	 */
	void handlePASVCommand() throws IOException;

	/**
	 * Handles the TYPE command sent by the FTP client. It is used to tell the
	 * server the type of data to be transferred (Binary or ASCII). The type code
	 * for Binary mode is 'I' and for ASCII mode is 'A'.
	 * 
	 * @param typeCode the transfer type code which could be either 'A' for ASCII
	 *                 mode or 'I' for binary mode
	 * @throws IOException if an input/output error occurred
	 */
	void handleTYPECommand(String typeCode) throws IOException;

	/**
	 * Handles the EPSV command sent by the FTP client. This command is used to
	 * request the server to enter a passive mode for IPv6 specifically.
	 */
	void handleEPSVCommand();

	/**
	 * Handles the EPRT command sent by the FTP client. This command is used to
	 * request the server to initiate an active data connection for IPv6
	 * specifically.
	 */
	void handleEPRTCommand();

	/**
	 * Handles the CWD command sent by the FTP client. It is used to change the
	 * current working directory to the one provided as an argument.
	 * 
	 * @param clientPath the path of the directory to set as working directory
	 * @throws IOException if an input/output error occurred
	 */
	void handleCWDCommand(String clientPath) throws IOException;

	/**
	 * Handles the CDUP command sent by the FTP client. It is used to change the
	 * current working directory to its immediate parent directory given that it is
	 * a valid one.
	 * 
	 * @throws IOException if an input/output error occurred
	 */
	void handleCDUPCommand() throws IOException;

	/**
	 * Handles the SIZE command sent by the FTP client. It is used to retrieve the
	 * size of a file.
	 * 
	 * @param filename the name of the file to get its size
	 * @throws IOException if an input/output error occurred
	 */
	void handleSIZECommand(String filename) throws IOException;

	/**
	 * Handles the MDTM command sent by the FTP client. It is used to retrieve the
	 * last modification date of the file provided as an argument.
	 * 
	 * @param filename the name of the file to get the last modification date of
	 * @throws IOException if an input/output error occurred
	 */
	void handleMDTMCommand(String filename) throws IOException;

	/**
	 * Handles the RETR command sent by the FTP client. It is used to download files
	 * and directories from the server to the client.
	 * 
	 * @param filename the name of the file/directory to download
	 * @throws IOException if an input/output error occurred
	 */
	void handleRETRCommand(String filename) throws IOException;

	/**
	 * Handles the STOR command sent by the FTP client. It is used to upload files
	 * and directories from the client to the server.
	 * 
	 * @param filename the name of the file/directory to upload
	 */
	void handleSTORCommand(String filename);

	/**
	 * Handles the MKD command sent by the FTP client. It is used to create a new
	 * directory on the server.
	 * 
	 * @param directoryName the name of the directory to be created
	 */
	void handleMKDCommand(String directoryName);

	/**
	 * Handles the DELE command sent by the FTP client. It is used to delete a file
	 * from the server.
	 * 
	 * @param filename the name of the file to be deleted.
	 */
	void handleDELECommand(String filename);

	/**
	 * Handles the RMDC command sent by the FTP client. It is used to remove a
	 * directory from the server.
	 * 
	 * @param dirname the name of the directory to be removed.
	 */
	void handleRMDCommand(String dirname);

	/**
	 * Handles the RNFR command sent by the FTP client. This command is used in
	 * conjunction with RNTO command. It is used when renaming a file or a directory
	 * to provide the original name of the file/directory to be renamed.
	 * 
	 * @param filename the original name of the file/directory to be renamed
	 */
	void handleRNFRCommand(String filename);

	/**
	 * Handles the RNTO command sent by the FTP client. This command is used after
	 * the RNFR command. It is used when renaming a file or a directory to provide
	 * the new name of the file/directory to be renamed.
	 * 
	 * @param newFilename the new name of the file/directory
	 */
	void handleRNTOCommand(String newFilename);
}
