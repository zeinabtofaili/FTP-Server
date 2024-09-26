package ftpServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * This class contains various methods to perform JUnit testing on the
 * {@link ClientHandler} class functionalities. It tests functionalities such as
 * user authentication, creating directories, file deletion and renaming,
 * changing working directories, moving to parent directories, and many other
 * methods.
 */
public class ClientHandlerTest {

	private ClientHandler clientHandler;
	private Map<String, String> userCredentials;
	private Socket socket;
	private PrintWriter testWriter;
	private ByteArrayOutputStream baos;
	@TempDir
	Path tempDir;

	/**
	 * This method is called before every test case. It initializes common test resources and
	 * configurations.
	 * 
	 * <p>
	 * It creates mocked versions of {@link Socket}, user credentials, and
	 * {@link ByteArrayOutputStream} to capture the output. It also creates a
	 * {@link ClientHandler} instance with these mocks and defines behavior for
	 * specific user credential checks to help in testing authentication and logging
	 * in.
	 */
	@BeforeEach
	void setUp() {
		socket = mock(Socket.class);
		userCredentials = mock(Map.class);
		baos = new ByteArrayOutputStream();
		testWriter = new PrintWriter(baos, true);
		clientHandler = new ClientHandler(socket, tempDir.toString(), userCredentials);
		clientHandler.setWriter(testWriter);

		when(userCredentials.containsKey("user1")).thenReturn(true);
		when(userCredentials.get("user1")).thenReturn("$2a$10$S1b94/ZBc4fw1iot5kk2aOR22qd2yfI.9BRbV8vvz2nr7vgz0xViy");
		when(userCredentials.containsKey("user2")).thenReturn(false);
	}

	/**
	 * This method is used to test the PASS command in normal conditions when the
	 * user and password match. It makes sure the server replies to the client as
	 * expected.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandlePASSCommandSuccess() throws IOException {

		when(socket.getOutputStream()).thenReturn(baos);
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		testWriter.flush();
		String response = baos.toString();

		assertTrue(response.contains("230 User logged in, proceed"));
	}

	/**
	 * This method is used to test the PASS command when a wrong password is
	 * provided.It makes sure the server does not log the user in replies to the
	 * client with an error message indicating failure.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandlePASSCommandFailure() throws IOException {

		when(socket.getOutputStream()).thenReturn(baos);

		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("incorrectPassword");

		testWriter.flush();
		String response = baos.toString();

		assertTrue(response.contains("530 Not logged in"));
	}

	/**
	 * This method tests {@link ClientHandler#setupUserDirectory()} method. This
	 * test case tests whether a new directory will be created in the temporary
	 * directory created for testing after successful authentication.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testSetupUserDirectoryCreatesDirectory() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		File userDir = tempDir.resolve("user1").toFile();
		assertTrue(userDir.exists() && userDir.isDirectory());
	}

	/**
	 * This method tests {@link ClientHandler#setupUserDirectory()} method. This
	 * test case tests whether a new directory will be created in the temporary
	 * directory created for testing after failed authentication.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testSetupUserDirectoryNoCreatedDirectory() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("wrongPassword");

		File userDir = tempDir.resolve("user1").toFile();
		assertFalse(userDir.exists() && userDir.isDirectory());
	}

	/**
	 * This method tests the {@link ClientHandler#handlePASVCommand()} method. It
	 * makes sure that the server responds as expected in normal scenarios with the
	 * different port parts and the IP address.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandlePASVCommand() throws IOException {

		when(socket.getOutputStream()).thenReturn(baos);

		clientHandler.handlePASVCommand();

		testWriter.flush();

		String response = baos.toString().trim();

		assertTrue(response.matches("227 Entering Passive Mode \\(127,0,0,1,\\d{1,3},\\d{1,3}\\)"));
	}

	/**
	 * This method tests the {@link ClientHandler#handleCWDCommand(String)} method.
	 * In this test case, it checks that directory change is successful when the
	 * directory exists and is indeed a directory.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testChangeToValidDirectory() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		File validDir = tempDir.resolve("user1/someDir").toFile();
		assertTrue(validDir.mkdirs());

		clientHandler.handleCWDCommand("someDir");
		testWriter.flush();
		String response = baos.toString().trim();
		assertTrue(response.contains("250 Directory successfully changed"));
	}

	/**
	 * This method tests the {@link ClientHandler#handleCWDCommand(String)} method.
	 * In this test case, it checks that directory change is not possible when the
	 * directory does not exist.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testChangeToNonExistentDirectory() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		clientHandler.handleCWDCommand("nonExistentDir");
		testWriter.flush();
		String response = baos.toString().trim();
		assertTrue(response.contains("550 Failed to change directory."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleCWDCommand(String)} method.
	 * In this test case, it checks that directory change is not possible when the
	 * directory is outside the root directory.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testChangeToDirectoryOutsideRoot() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		clientHandler.handleCWDCommand("../");
		testWriter.flush();
		String response = baos.toString().trim();
		assertTrue(response.contains("550 Failed to change directory."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleCWDCommand(String)} method.
	 * In this test case, it checks that directory change is not possible when the
	 * directory is a file not a directory.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testChangeToFileInsteadOfDirectory() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		File file = tempDir.resolve("user1/someFile.txt").toFile();
		assertTrue(file.createNewFile());

		clientHandler.handleCWDCommand("someFile.txt");
		testWriter.flush();
		String response = baos.toString().trim();
		assertTrue(response.contains("550 Failed to change directory."));
	}

	/**
	 * This method tests the {@link ClientHandler#isSubdirectory(File, File)}
	 * method. This test case makes sure that a direct subdirectory is recognized as
	 * a subdirectory.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testIsSubdirectoryDirect() throws IOException {
		File parent = tempDir.resolve("parent").toFile();
		assertTrue(parent.mkdirs());
		File child = new File(parent, "child");
		assertTrue(child.mkdirs());

		ClientHandler handler = new ClientHandler(null, tempDir.toString(), null);
		assertTrue(handler.isSubdirectory(child, parent));
	}

	/**
	 * This method tests the {@link ClientHandler#isSubdirectory(File, File)}
	 * method. This test case makes sure that a nested subdirectory is recognized as
	 * a subdirectory.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testIsSubdirectoryNested() throws IOException {
		File parent = tempDir.resolve("parent").toFile();
		assertTrue(parent.mkdirs());
		File child = new File(new File(parent, "nested"), "child");
		assertTrue(child.mkdirs());

		ClientHandler handler = new ClientHandler(null, tempDir.toString(), null);
		assertTrue(handler.isSubdirectory(child, parent));
	}

	/**
	 * This method tests the {@link ClientHandler#isSubdirectory(File, File)}
	 * method. This test case checks that the method does not wrongly flag a
	 * directory that is not a subdirectory as subdirectory.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testIsNotSubdirectory() throws IOException {
		File parent = tempDir.resolve("parent").toFile();
		assertTrue(parent.mkdirs());
		File unrelated = tempDir.resolve("unrelated").toFile();
		assertTrue(unrelated.mkdirs());

		ClientHandler handler = new ClientHandler(null, tempDir.toString(), null);
		assertFalse(handler.isSubdirectory(unrelated, parent));
	}

	/**
	 * This method tests the {@link ClientHandler#isSubdirectory(File, File)}
	 * method. This test case checks that the method does not wrongly flag a
	 * directory as a subdirectory of itself.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testSameDirectoryIsNotSubdirectory() throws IOException {
		File parent = tempDir.resolve("parent").toFile();
		assertTrue(parent.mkdirs());

		ClientHandler handler = new ClientHandler(null, tempDir.toString(), null);
		assertFalse(handler.isSubdirectory(parent, parent));
	}

	/**
	 * This method tests the {@link ClientHandler#handleCDUPCommand()} method. This
	 * test case checks that a subdirectory can successfully call the CDUP command
	 * and the current directory will change accordingly.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testCDUPFromSubdirectory() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		Path subDir = Files.createDirectories(tempDir.resolve("user1/subDir"));
		clientHandler.handleCWDCommand(subDir.getFileName().toString());

		clientHandler.handleCDUPCommand();
		testWriter.flush();

		String response = baos.toString().trim();
		assertTrue(response.contains("250 Directory successfully changed"));

		assertTrue(clientHandler.getCurrentDirectory().equals("/"));
	}

	/**
	 * This method tests the {@link ClientHandler#handleCDUPCommand()} method. This
	 * test case checks that when calling the CDUP command while being in the root,
	 * the directory does not change and a response is sent to the client informing
	 * them that they are already at the root.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testCDUPFromRoot() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		clientHandler.handleCWDCommand("/");
		assertTrue(clientHandler.getCurrentDirectory().equals("/"));

		clientHandler.handleCDUPCommand();
		testWriter.flush();
		String response = baos.toString().trim();
		assertTrue(response.contains("250 Directory already in user root"));
		assertTrue(clientHandler.getCurrentDirectory().equals("/"));

	}

	/**
	 * This method tests the {@link ClientHandler#handleSIZECommand(String)} method.
	 * This test case checks that the SIZE command handler implemented returns the
	 * size of the file when the file exists and is indeed a file and not a
	 * directory.
	 * 
	 * @throws IOException if an input/output error occurred
	 */
	@Test
	void testHandleSIZECommandForExistingFile() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		Path filePath = Files.createFile(tempDir.resolve("user1/existingFile.txt"));

		String fileContent = "This is a test file.";
		Files.write(filePath, fileContent.getBytes());

		clientHandler.handleSIZECommand("existingFile.txt");
		testWriter.flush();

		String response = baos.toString().trim();
		assertTrue(response.contains("213 " + fileContent.length()));
	}

	/**
	 * This method tests the {@link ClientHandler#handleSIZECommand(String)} method.
	 * This test case checks that the SIZE command handler implemented responds to
	 * the client with the error code 550 when asked to get the size of a
	 * non-existing file.
	 * 
	 * @throws IOException if an input/output error occurred
	 */
	@Test
	void testHandleSIZECommandForNonExistentFile() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		clientHandler.handleSIZECommand("nonExistentFile.txt");
		testWriter.flush();

		String response = baos.toString().trim();
		testWriter.flush();
		assertTrue(response.contains("550 File not found."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleSIZECommand(String)} method.
	 * This test case checks that the SIZE command handler implemented responds to
	 * the client with the error code 550 when asked to get the size of a directory
	 * and not a file.
	 * 
	 * @throws IOException if an input/output error occurred
	 */
	@Test
	void testHandleSIZECommandForDirectory() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		Files.createDirectory(tempDir.resolve("testDir"));

		clientHandler.handleSIZECommand("testDir");
		testWriter.flush();

		String response = baos.toString().trim();
		assertTrue(response.contains("550 File not found."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleMDTMCommand(String)} method.
	 * This test case checks that the MDTM command handler implemented returns the
	 * correct last modification date of the file when the file exists.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleMDTMCommandForExistingFile() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		Path filePath = Files.createFile(tempDir.resolve("user1/testFile.txt"));

		String fileContent = "This is a test file.";
		Files.write(filePath, fileContent.getBytes());

		File file = new File(tempDir.resolve("user1").resolve("testFile.txt").toString());
		long lastModifiedTime = System.currentTimeMillis();
		assertTrue(file.setLastModified(lastModifiedTime));

		clientHandler.handleMDTMCommand("testFile.txt");
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		String expectedResponse = "213 " + dateFormat.format(new Date(lastModifiedTime));
		String response = baos.toString().trim();
		assertTrue(response.contains(expectedResponse));
	}

	/**
	 * This method tests the {@link ClientHandler#handleMDTMCommand(String)} method.
	 * This test case checks that the MDTM command handler implemented responds to
	 * the client with the error code 550 when asked to get the last modification
	 * date of a non-existing file.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleMDTMCommandForNonExistentFile() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		clientHandler.handleMDTMCommand("nonExistentFile.txt");
		String response = baos.toString().trim();
		assertTrue(response.contains("550 File not found."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleMKDCommand(String)} method.
	 * This test case checks that the MKD command successfully creates a new
	 * directory in normal conditions and sends a correct response to the client.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleMKDCommandCreatesNewDirectory() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		String directoryName = "newDirectory";
		clientHandler.handleMKDCommand(directoryName);
		File newDir = new File(tempDir.resolve("user1").resolve(directoryName).toString());

		assertTrue(newDir.exists() && newDir.isDirectory());
		assertTrue(baos.toString().trim().contains("257 \"" + directoryName + "\" created."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleMKDCommand(String)} method.
	 * This test case checks that the MKD command responds with the error code 550
	 * when asked to create a directory with a name of an already existing
	 * directory.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleMKDCommandDirectoryAlreadyExists() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		String directoryName = "existingDirectory";
		Files.createDirectories(tempDir.resolve("user1").resolve(directoryName));

		clientHandler.handleMKDCommand(directoryName);

		assertTrue(baos.toString().trim().contains("550 Directory already exists."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleMKDCommand(String)} method.
	 * This test case checks that the MKD command responds with the error code 501
	 * when asked to create a directory with an invalid name indicating a syntax
	 * error.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleMKDCommandInvalidDirectoryName() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		clientHandler.handleMKDCommand("");
		assertTrue(baos.toString().trim().contains("501 Syntax error"));
	}

	/**
	 * This method tests the {@link ClientHandler#handleMKDCommand(String)} method.
	 * This test case checks that the MKD command responds with the error code 550
	 * when asked to create a directory outside the root directory indicating that
	 * the permission is denied.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleMKDCommandOutsideRoot() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		String directoryName = "../outsideRoot";
		clientHandler.handleMKDCommand(directoryName);

		assertTrue(baos.toString().trim().contains("550 Permission denied."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleDELECommand(String)} method.
	 * This test case checks that the DELE command results in the successful
	 * deletion of the file when the file is indeed a file and not a directory and
	 * when it exits. It also checks that it responds to the client with the success
	 * code 250 informing it of the deletion.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleDELECommandDeletesFile() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		String fileName = "testFile.txt";
		Files.createFile(tempDir.resolve("user1").resolve(fileName));

		clientHandler.handleDELECommand(fileName);
		String response = baos.toString().trim();

		assertFalse(new File(tempDir.resolve("user1").resolve(fileName).toString()).exists());
		assertTrue(response.contains("250 File deleted successfully."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleDELECommand(String)} method.
	 * This test case checks that the DELE command responds with an error code 550
	 * indicating that the file does not exist when prompted to delete a nonexistent
	 * file.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleDELECommandWithNonExistentFile() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		String nonExistentFileName = "nonExistentFile.txt";

		clientHandler.handleDELECommand(nonExistentFileName);
		String response = baos.toString().trim();

		assertTrue(response.contains("550 File not found or is a directory."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleDELECommand(String)} method.
	 * This test case checks that the DELE command responds with an error code 550
	 * when prompted to delete a directory and not a file.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleDELECommandOnDirectory() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		String directoryName = "testDirectory";
		Files.createDirectory(tempDir.resolve("user1").resolve(directoryName));

		clientHandler.handleDELECommand(directoryName);
		String response = baos.toString().trim();

		assertTrue(response.contains("550 File not found or is a directory."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleRMDCommand(String)} method.
	 * This test case checks that the RMD command results in the successful deletion
	 * of the directory when the directory is indeed a directory and not a file, and
	 * when it exits and is empty. It also checks that it responds to the client
	 * with the success code 250 informing it of the deletion.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleRMDCommandDeletesEmptyDirectory() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		String directoryName = "emptyDirectory";
		Files.createDirectories(tempDir.resolve("user1").resolve(directoryName));

		clientHandler.handleRMDCommand(directoryName);
		String response = baos.toString().trim();

		assertFalse(Files.exists(tempDir.resolve("user1").resolve(directoryName)));
		assertTrue(response.contains("250 Directory deleted successfully."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleRMDCommand(String)} method.
	 * This test case checks that the RMD command responds with an error code 550
	 * indicating that the directory does not exist when prompted to remove a
	 * nonexistent directory.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleRMDCommandWithNonExistentDirectory() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		clientHandler.handleRMDCommand("nonExistentDirectory");
		String response = baos.toString().trim();

		assertTrue(response.contains("550 Directory not found or is a file."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleRMDCommand(String)} method.
	 * This test case checks that the RMD command responds with an error code 550
	 * when prompted to delete a file and not a directory.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleRMDCommandOnFile() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		String fileName = "notADirectory.txt";
		Files.createFile(tempDir.resolve("user1").resolve(fileName));

		clientHandler.handleRMDCommand(fileName);
		String response = baos.toString().trim();

		assertTrue(response.contains("550 Directory not found or is a file."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleRMDCommand(String)} method.
	 * This test case checks that the RMD command does not delete a nonempty
	 * directory and responds with an error code 450 when prompted to delete a
	 * nonempty directory.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleRMDCommandDeletesNonEmptyDirectory() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		String directoryName = "nonEmptyDirectory";
		Path directoryPath = tempDir.resolve("user1").resolve(directoryName);
		Files.createDirectories(directoryPath);
		Files.createFile(directoryPath.resolve("file.txt"));

		clientHandler.handleRMDCommand(directoryName);
		String response = baos.toString().trim();

		assertTrue(Files.exists(directoryPath));
		assertTrue(response.contains("450 Directory deletion failed. Directory might not be empty."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleRNFRCommand(String)} method.
	 * It checks that the method replies with the code 350 signaling it is ready to
	 * receive RNTO command to complete the deletion when the file exists in the
	 * user directory.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleRNFRCommandFileExists() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		String fileName = "testFile.txt";
		Files.createFile(tempDir.resolve("user1").resolve(fileName));

		clientHandler.handleRNFRCommand(fileName);
		String response = baos.toString().trim();
		assertTrue(response.contains("350 Ready for RNTO."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleRNFRCommand(String)} method.
	 * It checks that the method replies with the error code 550 signaling that the
	 * file or directory were not found when prompted to rename a non-existing file
	 * or directory.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleRNFRCommandFileDoesNotExist() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		clientHandler.handleRNFRCommand("nonExistentFile.txt");
		String response = baos.toString().trim();
		assertTrue(response.contains("550 File or directory not found."));
	}

	/**
	 * This method tests the {@link ClientHandler#handleRNTOCommand(String)} method.
	 * It checks that the file is successfully renamed when the call to RNTO is
	 * preceded by a call to RNFR and the file indeed exists. It also checks that
	 * the server sends a reply to the client with the code 250 indicating
	 * successful renaming.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleRNTOCommandSuccess() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		String fileName = "testFile.txt";
		Files.createFile(tempDir.resolve("user1").resolve(fileName));
		clientHandler.handleRNFRCommand(fileName);

		String newFileName = "renamedFile.txt";
		clientHandler.handleRNTOCommand(newFileName);
		String response = baos.toString().trim();
		assertTrue(response.contains("250 File or directory renamed successfully."));
		assertFalse(Files.exists(tempDir.resolve("user1").resolve(fileName)));
		assertTrue(Files.exists(tempDir.resolve("user1").resolve(newFileName)));
	}

	/**
	 * This method tests the {@link ClientHandler#handleRNTOCommand(String)} method.
	 * It checks that the server sends the client a message with the error code 503
	 * when the RNTO command is called without a preceding call of RNFR.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleRNTOCommandWithoutRNFR() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");
		clientHandler.handleRNTOCommand("newFileName.txt");
		String response = baos.toString().trim();
		assertTrue(response.contains("503 Bad sequence of commands"));
	}

	/**
	 * This method tests the {@link ClientHandler#handleRNTOCommand(String)} method.
	 * It checks that the server sends the client a message with the error code 553
	 * when the RNTO command is provided a filename which already exists in the
	 * directory.
	 * 
	 * @throws IOException if an input/output error occurs
	 */
	@Test
	void testHandleRNTOCommandTargetExists() throws IOException {
		clientHandler.handleUSERCommand("user1");
		clientHandler.handlePASSCommand("password1");

		String fileName = "testFile.txt";
		String newFileName = "existingFile.txt";
		Files.createFile(tempDir.resolve("user1").resolve(fileName));
		Files.createFile(tempDir.resolve("user1").resolve(newFileName));
		clientHandler.handleRNFRCommand(fileName);

		clientHandler.handleRNTOCommand(newFileName);
		String response = baos.toString().trim();
		assertTrue(response.contains("553 RNTO failed"));
	}

}
