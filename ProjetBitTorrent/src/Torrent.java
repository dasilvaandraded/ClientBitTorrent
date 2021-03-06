/*-------------------------------------------------------------------------
	FILE		: 	Torrent.java
	DESCRIPTION	:	This class is used to create the frame that will be 
					sent to the tracker. It also allows the request to the 
					tracker as well as recover the response from it.
	AUTHORS		:	Magnin Antoine, Da Silva Andrade David
-------------------------------------------------------------------------*/
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Map;
import org.ardverk.coding.BencodingInputStream;

public class Torrent {

	/*----------------
		ATTRIBUTES
	----------------*/
	private Metafile torrent = null;
	private String infoHashEncoded = null;
	private byte[] infoHash = null;
	private String peerID = null;
	private Integer port = null;
	private Integer uploaded = null;
	private Integer downloaded = null;
	private long left;
	private String event = null;

	/*-------------------------------------------------------------------------
		DESCRIPTION	:	This constructor is used to allocate the disk space 
						used by the file to download. It also allows to retrieve
						the sha-1 to be used in the request to the tracker. It 
						also instantiates the various fields to send the request 
						to the tracker.
		PARAMS		:	(Metafile) file
						(Integer) socketPort
						(String) peerID
		RETURN		:	None
	-------------------------------------------------------------------------*/
	@SuppressWarnings("unchecked")
	public Torrent(Metafile file, Integer socketPort, String peerID) {

		// Get the torrent file
		this.torrent = file;

		// Single file torrent
		if (!torrent.isMultiFile()) {

			try {

				// Allocate memory on disk for downloaded file
				RandomAccessFile tmp = new RandomAccessFile(torrent.getName(), "rw");
				tmp.setLength((long) torrent.getLength());
				tmp.close();

			} catch (Exception e) {
				System.err.println("Error while creating tmp file");
			}

			// Multi file torrent
		} else {

			ArrayList<Map<String, ?>> files = torrent.getFiles();

			// Foreach files
			for (Map<String, ?> dict : files) {

				ArrayList<byte[]> path = (ArrayList<byte[]>) dict.get("path");

				String filePath = "." + File.separator + torrent.getName() + File.separator;

				// Get the file path
				for (byte[] data : path) {
					filePath = filePath + File.separator + new String(data);
				}

				try {

					// Allocate memory on disk for downloaded file
					File tmp = new File(filePath);

					if (tmp.getParentFile() != null) {
						tmp.getParentFile().mkdirs();
					}

					RandomAccessFile raf = new RandomAccessFile(tmp, "rw");
					long length = ((BigInteger) dict.get("length")).longValue();
					raf.setLength(length);
					raf.close();

					// Actualise left value
					this.left += length;

				} catch (Exception e) {
					e.printStackTrace();
					System.err.println("Error while creating tmp file");
				}

			}

		}

		// Get SHA-1 infoHash
		try {

			MessageDigest md = MessageDigest.getInstance("SHA-1");
			File f = new File(torrent.getSource());
			InputStream input = null;

			try {

				input = new FileInputStream(f);
				StringBuilder builder = new StringBuilder();

				// Read until "4:info" is found
				while (!builder.toString().endsWith("4:info")) {
					builder.append((char) input.read());
				}

				ByteArrayOutputStream output = new ByteArrayOutputStream();
				int data;

				// Get bytes after "4:info"
				while ((data = input.read()) > -1) {
					output.write(data);
				}

				md.update(output.toByteArray(), 0, output.size() - 1);

			} catch (Exception e) {
				System.err.println("Error while getting info's value");
			}

			// Set infoHash SHA-1 value
			byte[] hash = md.digest();
			this.infoHash = hash;
			this.infoHashEncoded = encodeURL(javax.xml.bind.DatatypeConverter.printHexBinary(hash));

		} catch (Exception e) {
			System.err.println("Error while getting SHA-1 infoHash");
		}

		// Set peerID
		this.peerID = peerID;

		// Set port for listening
		this.port = socketPort;

		// Set downloaded, uploaded values
		this.uploaded = 0;
		this.downloaded = 0;

		// Set left value
		if (!torrent.isMultiFile()) {
			this.left = torrent.getLength();
		}

		// Set event status
		this.event = "started";

	}


	/*-------------------------------------------------------------------------
		DESCRIPTION	:	This method is used to create the request to the tracker
		 				and to send it. Once the response receive, it's debencoded
		 				and returned.
		PARAMS		:	None
		RETURN		:	(Map<String, ?>) response from tracker
	-------------------------------------------------------------------------*/
	@SuppressWarnings("resource")
	public Map<String, ?> request() {
		// Decoder to decode the response from the tracker
		BencodingInputStream bencodeDecoder = null;
		Map<String, ?> responseContent = null;

		try {

			// Initialize http connexion
			URL url = new URL(torrent.getAnnounce() +
					"?info_hash=" + this.infoHashEncoded + 
					"&peer_id=" + this.peerID + 
					"&port=" + this.port + 
					"&uploaded="	+ this.uploaded + 
					"&downloaded=" + this.downloaded + 
					"&left=" + this.left + 
					"&event=" + this.event + 
					"&key=12345" + "&compact=1");

			HttpURLConnection connexion = (HttpURLConnection) url.openConnection();

			// Set properties
			connexion.setRequestMethod("GET");

			connexion.connect();

			// Send request
			connexion.getResponseCode();

			bencodeDecoder = new BencodingInputStream((InputStream) connexion.getInputStream());

			responseContent = bencodeDecoder.readMap();

		} catch (Exception e) {
			System.err.println("Error while sending request to tracker");
		}

		// Return response
		return responseContent;

	}

	/*------------------------------------------------------------------------
		DESCRIPTION	: 	Encodes a String in hexadecimal format to URL format
		PARAMS		:	(String) hexString
		RETURN		:	(String) url encoded
	------------------------------------------------------------------------*/
	public static String encodeURL(String hexString) throws Exception {

		if (hexString == null || hexString.isEmpty()) {
			return "";
		}

		if (hexString.length() % 2 != 0) {
			throw new Exception("String is not hex, length NOT divisible by 2: " + hexString);
		}

		int len = hexString.length();
		char[] output = new char[len + len / 2];
		int i = 0;
		int j = 0;

		while (i < len) {
			output[j++] = '%';
			output[j++] = hexString.charAt(i++);
			output[j++] = hexString.charAt(i++);
		}

		// Return URL encoded value
		return new String(output);

	}
	/*-------------------------------------------------------------------------
		DESCRIPTION	:	Getters to access infohash and client's peerID
	-------------------------------------------------------------------------*/
	public byte[] getInfoHash(){
		return this.infoHash;
	}

	public String getPeerID(){
		return this.peerID;
	}
}
