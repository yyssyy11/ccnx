/**
 * Part of the CCNx Java Library.
 *
 * Copyright (C) 2008, 2009 Palo Alto Research Center, Inc.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation. 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. You should have received
 * a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street,
 * Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.ccnx.ccn.impl.repo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.xml.stream.XMLStreamException;

import org.ccnx.ccn.CCNInterestListener;
import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.impl.CCNFlowControl;
import org.ccnx.ccn.impl.support.Log;
import org.ccnx.ccn.profiles.CommandMarkers;
import org.ccnx.ccn.profiles.nameenum.BasicNameEnumeratorListener;
import org.ccnx.ccn.profiles.nameenum.CCNNameEnumerator;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.SignedInfo.ContentType;


/**
 * Handle repo specialty start/end protocol
 * 
 * Needs to be able to handle multiple clients. Currently due to limitations in close,
 * to do this requires that clients above close their streams in order when multiple
 * streams are using the same FC.
 * 
 * Intended to handle the repo ack protocol. This is currently unused until we find
 * a workable way to do it.
 * 
 * @author smetters, rasmusse
 * @see CCNFlowControl
 * @see RepositoryInterestHandler
 */
public class RepositoryFlowControl extends CCNFlowControl implements CCNInterestListener {

	protected boolean _bestEffort = true;	// Whether to use the ACK protocol (currently disabled)
	
	// Outstanding interests output by this FlowController. Currently includes only the original
	// start write request.
	protected HashSet<Interest> _writeInterests = new HashSet<Interest>();
	
	// The following values are used by the currently disabled ACK protocol
	protected static final int ACK_BLOCK_SIZE = 20;
	protected static final int ACK_INTERVAL = 128;
	protected int _blocksSinceAck = 0;
	protected int _ackInterval = ACK_INTERVAL;
	protected CCNNameEnumerator _ackne;
	protected RepoAckHandler _ackHandler;
	
	// Queue of current clients of this RepositoryFlowController
	// Implemented as a queue so we can decide which one to close on calls to beforeClose/afterClose
	protected Queue<Client> _clients = new ConcurrentLinkedQueue<Client>();

	/**
	 * Handles packets received from the repository after the start write request.  It's looking
	 * for a RepoInfo packet indicating a repository has responded.
	 */
	public Interest handleContent(ArrayList<ContentObject> results,
			Interest interest) {
		
		Interest interestToReturn = null;
		for (ContentObject co : results) {
			Log.info("handleContent: got potential repo message: " + co.name());
			if (co.signedInfo().getType() != ContentType.DATA)
				continue;
			RepositoryInfo repoInfo = new RepositoryInfo();
			try {
				repoInfo.decode(co.content());
				switch (repoInfo.getType()) {
				case INFO:
					for (Client client : _clients) {
						if (client._name.isPrefixOf(co.name()))
							client._initialized = true;
					}
					//_writeInterest = null;
					synchronized (this) {
						notify();
					}
					break;
				default:
					break;
				}
			} catch (XMLStreamException e) {
				Log.info("XMLStreamException parsing RepositoryInfo: " + e.getMessage() + " from content object " + co.name() + ", skipping.");
			}
		}
		// So far, we seem never to have anything to return.
		return interestToReturn;
	}

	/**
	 * The names returned by NameEnumerator are only the 1 level names
	 * without prefix, but the names we are holding contain the basename
	 * so we reconstruct a full name here.
	 *
	 * @author rasmusse
	 *
	 */
	private class RepoAckHandler implements BasicNameEnumeratorListener {

		public int handleNameEnumerator(ContentName prefix, ArrayList<ContentName> names) {
			Log.info("Enumeration response for " + names.size() + " children of " + prefix + ".");
			for (ContentName name : names)
				ack(new ContentName(prefix, name.component(0)));
			return names.size();
		}
	}
	
	/**
	 * Preserves information about our clients
	 * @author rasmusse
	 *
	 */
	protected class Client {
		protected ContentName _name;
		protected Shape _shape;
		protected boolean _initialized = false;
		
		public Client(ContentName name, Shape shape) {
			_name = name;
			_shape = shape;
		}
		
		public ContentName name() { return _name; }
		public Shape shape() { return _shape; }
	}

	/**
	 * 
	 * @param library
	 * @throws IOException if library is null and a new CCNHandle can't be created
	 */
	public RepositoryFlowControl(CCNHandle handle) throws IOException {
		super(handle);
	}

	/**
	 * @param name		an initial namespace for this stream
	 * @param library
	 * @throws IOException if library is null and a new CCNHandle can't be created
	 */
	public RepositoryFlowControl(ContentName name, CCNHandle handle) throws IOException {
		this(handle);
		addNameSpace(name);
	}
	
	/**
	 * @param name		an intial namespace for this stream
	 * @param library
	 * @param shape		shapes are not currently implemented and may be deprecated. The only currently defined
	 * 					shape is "Shape.STREAM"
	 * @throws IOException	if library is null and a new CCNHandle can't be created
	 * @see	CCNFlowControl
	 */
	public RepositoryFlowControl(ContentName name, CCNHandle handle, Shape shape) throws IOException {
		this(handle);
		addNameSpace(name);
	}

	@Override
	/**
	 * Send out a start write request to any listening repositories and wait for a response.
	 * 
	 * @param name	the basename of the stream to start
	 * @param shape currently ignored - can only be Shape.STREAM
	 * @throws IOException if there is no response from a repository
	 */
	public void startWrite(ContentName name, Shape shape) throws IOException {
		
		Client client = new Client(name, shape);
		_clients.add(client);
		clearUnmatchedInterests();	// Remove possible leftover interests from "getLatestVersion"
		
		// A nonce is used because if we tried to write data with the same name more than once, we could retrieve the
		// the previous answer from the cache, and the repo would never be informed of our start write.
		ContentName repoWriteName = new ContentName(name, CommandMarkers.COMMAND_MARKER_REPO_START_WRITE, Interest.generateNonce());

		Interest writeInterest = new Interest(repoWriteName);
		_handle.expressInterest(writeInterest, this);
		_writeInterests.add(writeInterest);
		if (! _bestEffort) {
			_ackHandler = new RepoAckHandler();
			_ackne = new CCNNameEnumerator(_handle, _ackHandler);
		}

		//Wait for information to be returned from a repo
		synchronized (this) {
			boolean interrupted;
			long startTime = System.currentTimeMillis();
			do {
				try {
					interrupted = false;
					long timeout = getTimeout() - (System.currentTimeMillis() - startTime);
					if (timeout <= 0)
						break;
					wait(timeout);
				} catch (InterruptedException e) {
					interrupted = true;
				}
			} while (interrupted || (!client._initialized && ((getTimeout() + startTime) > System.currentTimeMillis())));
		}
		if (!client._initialized) {
			Log.finest("No response from a repository, cannot add name space : " + name);
			throw new IOException("No response from a repository for " + name);
		}
	}

	/**
	 * Handle acknowledgement packet from the repo
	 * @param co
	 */
	public void ack(ContentName name) {
		synchronized (_holdingArea) {
			Log.fine("Handling ACK " + name);
			if (_holdingArea.get(name) != null) {
				ContentObject co = _holdingArea.get(name);
				Log.fine("CO " + co.name() + " acked");
				_holdingArea.remove(co.name());
				if (_holdingArea.size() < _highwater)
					_holdingArea.notify();
			}
		}
	}

	public void setBestEffort(boolean flag) {
		_bestEffort = flag;
	}

	public boolean flushComplete() {
		return _bestEffort ? true : _holdingArea.size() == 0;
	}

	public void afterPutAction(ContentObject co) throws IOException {
		if (! _bestEffort) {
			if (_holdingArea.size() > _ackInterval) {
				ContentName prefix = getNameSpace(co.name());
				_ackne.cancelPrefix(prefix);
				_ackne.registerPrefix(prefix);
			}
		} else {
			super.afterPutAction(co);
		}
	}

	/**
	 * Called after close() is called but before close attempts to flush its output data
	 */
	@Override
	public void beforeClose() throws IOException {
		_ackInterval = 0;
	}

	/**
	 * Called after close has completed a flush
	 */
	@Override
	public void afterClose() throws IOException {
		try {
			_clients.remove();
		} catch (NoSuchElementException nse) {}
		
		// super.afterClose() calls waitForPutDrain.
		super.afterClose();
		// DKS don't actually want to cancel all the interests, only the
		// ones relevant to the data we've finished writing.
		cancelInterests();
		if (!flushComplete()) {
			throw new IOException("Unable to confirm writes are stable: timed out waiting ack for " + _holdingArea.firstKey());
		}
	}

	public void cancelInterests() {
		if (! _bestEffort) {
			for (ContentName prefix : _filteredNames) {
				_ackne.cancelPrefix(prefix);
			}
		}
		for (Interest writeInterest : _writeInterests){
			_handle.cancelInterest(writeInterest, this);
		}
	}
}
