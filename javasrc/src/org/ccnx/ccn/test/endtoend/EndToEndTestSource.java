/**
 * A CCNx library test.
 *
 * Copyright (C) 2008, 2009 Palo Alto Research Center, Inc.
 *
 * This work is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation. 
 * This work is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details. You should have received a copy of the GNU General Public
 * License along with this program; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301, USA.
 */

package org.ccnx.ccn.test.endtoend;


import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.ccnx.ccn.CCNFilterListener;
import org.ccnx.ccn.io.CCNWriter;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.Interest;
import org.junit.Test;


//NOTE: This test requires ccnd to be running and complementary sink process 

public class EndToEndTestSource extends BaseLibrarySource implements CCNFilterListener {
	protected CCNWriter _writer;
	
	@Test
	public void puts() throws Throwable {
		assert(count <= Byte.MAX_VALUE);
		System.out.println("Put sequence started");
		CCNWriter writer = new CCNWriter("/BaseLibraryTest", handle);
		writer.setTimeout(5000);
		for (int i = 0; i < count; i++) {
			Thread.sleep(rand.nextInt(50));
			byte[] content = getRandomContent(i);
			ContentName putResult = writer.put(ContentName.fromNative("/BaseLibraryTest/gets/" + new Integer(i).toString()), content);
			System.out.println("Put " + i + " done: " + content.length + " content bytes");
			checkPutResults(putResult);
		}
		writer.close();
		System.out.println("Put sequence finished");
	}
	
	@Test
	public void server() throws Throwable {
		System.out.println("PutServer started");
		name = ContentName.fromNative("/BaseLibraryTest/");
		_writer = new CCNWriter(name, handle);
		_writer.setTimeout(5000);
		handle.registerFilter(name, this);
		// Block on semaphore until enough data has been received
		sema.acquire();
		handle.unregisterFilter(name, this);
		if (null != error) {
			throw error;
		}
	}

	public synchronized int handleInterests(ArrayList<Interest> interests) {
		try {
			if (next >= count) {
				return 0;
			}
			for (Interest interest : interests) {
				assertTrue(name.isPrefixOf(interest.name()));
				byte[] content = getRandomContent(next);
				ContentName putResult = _writer.put(ContentName.fromNative("/BaseLibraryTest/server/" + new Integer(next).toString()), content);
				System.out.println("Put " + next + " done: " + content.length + " content bytes");
				checkPutResults(putResult);
				next++;
			}
			if (next >= count) {
				sema.release();
			}
		} catch (Throwable e) {
			error = e;
		}
		return 0;
	}
}
