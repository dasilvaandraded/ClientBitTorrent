/*-------------------------------------------------------------------------
	FILE		: 	PeersManager.java
	DESCRIPTION	:	Manages every peers returned by the tracker, in order
					to download the file.
	AUTHORS		:	Magnin Antoine, Da Silva Andrade David
-------------------------------------------------------------------------*/
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

public class PeersManager {

	
	Peers peers;
	int[] piecesDownloaded; // 0 = non downloaded, 1 = in progress , 2 = download finished
	byte[] infoHash;
	String peerID;
	Metafile metafile;
	ArrayList<PeerConnection> peerConnections;
	int connectionsCount = 0;
	int maxConnections;
	
	/*-------------------------------------------------------------------------
		DESCRIPTION	:	Retrieve all parameters and store it inside the class.
						Create manager and start it.
		PARAMS		: 	(Metafile) metafile
						(byte[]) infohash
						(String) peerID
						(int) maxConnections
		RETURN		: 	None
	-------------------------------------------------------------------------*/
	public PeersManager(Metafile metafile, byte[] infoHash, String peerID, int maxConnections) {

		this.infoHash = infoHash;
		this.peerID = peerID;
		this.piecesDownloaded = new int[metafile.getPieces().length / 20];
		this.peerConnections = new ArrayList<PeerConnection>();
		this.metafile = metafile;
		this.maxConnections = maxConnections;
		
		manager mng = new manager();
		mng.start();

	}

	/*-------------------------------------------------------------------------
		DESCRIPTION	:	Updates the peerConnections array
		PARAMS		: 	(Peers) peers
		RETURN		: 	None
	-------------------------------------------------------------------------*/
	public void updatePeers(Peers peers) {
		
		this.peers = peers;
		
		ArrayList<Peer> listPeers = this.peers.getPeers();

		// Do all handhsakes
		for (Peer peer : listPeers) {
			
			try {
				
				// Throw exception if connection drop out
				PeerConnection peerConnection = new PeerConnection(peer, metafile, piecesDownloaded, infoHash, peerID);
				
				// If the handshake worked as expected, return true.
				if (peerConnection.handshaken) {
					
					synchronized (peerConnections) { 
						// At this point we know that peerConnection is valid because the connection worked and the handshake too
						this.peerConnections.add(peerConnection);
					}
					
				} // else, discard connection
				
			} catch (SocketTimeoutException e) {
				// System.out.print("Time out connection to " + peer.toString() + "." + "\n");
			} catch (ConnectException e) {
				// System.out.print("Connection to " + peer.toString() + " refused." + "\n");
			} catch (Exception e) {
				// System.out.print("Unexpected error while trying to connect to " + peer.toString() + "\n");
			}
			
		}
		
	}

	/*-------------------------------------------------------------------------
		DESCRIPTION	:	Returns true if the download is finished,
						otherwise false
		PARAMS		: 	None
		RETURN		: 	(Boolean)
	-------------------------------------------------------------------------*/
	public Boolean isDownloadFinished() {
		
		synchronized (piecesDownloaded) {
			for (int i = 0; i < piecesDownloaded.length; i++) {
				if (piecesDownloaded[i] == 0 || piecesDownloaded[i] == 1) {
					return false;
				}
			}
		}
		
		return true;
		
	}

	/*-------------------------------------------------------------------------
		DESCRIPTION	:	This manager removes PeerConnection objects with a
						dead state. If required, he starts another one.
	-------------------------------------------------------------------------*/
	public class manager extends Thread {
		
		public void run() {
			
			while (isDownloadFinished() == false) {
				
				ArrayList<PeerConnection> toRemove = new ArrayList<PeerConnection>();

				for (PeerConnection pc : peerConnections) {

					if (pc.isAlive == false) {
						toRemove.add(pc);
						connectionsCount--;
					} else if (connectionsCount < maxConnections && pc.isRunning == false) {
						pc.start();
						connectionsCount++;
					}

				}
				
				peerConnections.removeAll(toRemove);
								
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					System.err.println("Error while trying to sleep");
				}	
				
			}
			
		}
		
	}
	
}
