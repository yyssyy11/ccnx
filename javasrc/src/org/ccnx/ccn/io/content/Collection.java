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

package org.ccnx.ccn.io.content;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import javax.xml.stream.XMLStreamException;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.impl.encoding.GenericXMLEncodable;
import org.ccnx.ccn.impl.encoding.XMLDecoder;
import org.ccnx.ccn.impl.encoding.XMLEncodable;
import org.ccnx.ccn.impl.encoding.XMLEncoder;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;


/**
 * Mapping from a collection to the underlying XML representation.
 * Basically a collection is a content object containing
 * a List of links -- names and optionally some content authentication
 * information to specify whose value for each name
 * to take.
 * @author smetters
 *
 */
public class Collection extends GenericXMLEncodable implements XMLEncodable, Iterable<Link> {
	
	/**
	 * This should eventually be called Collection, and the Collection class deleted.
	 */
	public static class CollectionObject extends CCNEncodableObject<Collection> {
		
		/**
		 * Write constructor. Doesn't save until you call save, in case you want to tweak things first.
		 * @param name
		 * @param data
		 * @param handle
		 * @throws ConfigurationException
		 * @throws IOException
		 */
		public CollectionObject(ContentName name, Collection data, CCNHandle handle) throws IOException {
			super(Collection.class, name, data, handle);
		}
		
		public CollectionObject(ContentName name, java.util.Collection<Link> contents, CCNHandle handle) throws IOException {
			this(name, new Collection(contents), handle);
		}
		
		public CollectionObject(ContentName name, Link [] contents, CCNHandle handle) throws IOException {
			this(name, new Collection(contents), handle);			
		}

		public CollectionObject(ContentName name, Collection data, PublisherPublicKeyDigest publisher, KeyLocator keyLocator, CCNHandle handle) throws IOException {
			super(Collection.class, name, data, publisher, keyLocator, handle);
		}

		public CollectionObject(ContentName name, java.util.Collection<Link> contents, PublisherPublicKeyDigest publisher, KeyLocator keyLocator, CCNHandle handle) throws IOException {
			this(name, new Collection(contents), publisher, keyLocator, handle);
		}
		
		public CollectionObject(ContentName name, Link [] contents, PublisherPublicKeyDigest publisher, KeyLocator keyLocator, CCNHandle handle) throws IOException {
			this(name, new Collection(contents), publisher, keyLocator, handle);			
		}

		/**
		 * Read constructor -- opens existing object.
		 * @param name
		 * @param handle
		 * @throws XMLStreamException
		 * @throws IOException
		 * @throws ClassNotFoundException 
		 */
		public CollectionObject(ContentName name, PublisherPublicKeyDigest publisher, CCNHandle handle) throws IOException, XMLStreamException {
			super(Collection.class, name, publisher, handle);
		}
		
		public CollectionObject(ContentName name, CCNHandle handle) throws IOException, XMLStreamException {
			super(Collection.class, name, (PublisherPublicKeyDigest)null, handle);
		}
		
		public CollectionObject(ContentObject firstBlock, CCNHandle handle) throws IOException, XMLStreamException {
			super(Collection.class, firstBlock, handle);
		}
		
		public Collection collection() throws ContentNotReadyException, ContentGoneException {
			return data();
		}
		
		public LinkedList<Link> contents() throws ContentNotReadyException, ContentGoneException { 
			if (null == data())
				return null;
			return data().contents(); 
		}
	}
	
	protected static final String COLLECTION_ELEMENT = "Collection";

	protected LinkedList<Link> _contents = new LinkedList<Link>();
	
	public Collection() {
	}
	
	public Collection clone() {
		return new Collection(_contents);
	}
	
	public Collection(java.util.Collection<Link> contents) {
		_contents.addAll(contents); // should we clone each?
	}
	
	public Collection(Link [] contents) {
		if (contents != null) {
			for (int i=0; i < contents.length; ++i) {
				_contents.add(contents[i]);
			}
		}
	}
	
	public LinkedList<Link> contents() { 
		return _contents; 
	}
		
	public Link get(int i) {
		return contents().get(i);
	}
	
	public void add(Link content) {
		_contents.add(content);
	}
	
	public void add(ArrayList<Link> contents) {
		_contents.addAll(contents);
	}
	
	public Link remove(int i) {
		return _contents.remove(i);
	}
	
	public boolean remove(Link content) {
		return _contents.remove(content);
	}

	public void removeAll() {
		_contents.clear();
	}
	
	public int size() { return _contents.size(); }
	
	/**
	 * XML format:
	 * @throws XMLStreamException 
	 * 
	 */
	public void decode(XMLDecoder decoder) throws XMLStreamException {
		_contents.clear();
		
		decoder.readStartElement(getElementLabel());

		Link link = null;
		while (decoder.peekStartElement(Link.LINK_ELEMENT)) {
			link = new Link();
			link.decode(decoder);
			add(link);
		}
		decoder.readEndElement();
	}

	public void encode(XMLEncoder encoder) throws XMLStreamException {
		if (!validate()) {
			throw new XMLStreamException("Cannot encode " + this.getClass().getName() + ": field values missing.");
		}
		encoder.writeStartElement(getElementLabel());
		Iterator<Link> linkIt = contents().iterator();
		while (linkIt.hasNext()) {
			Link link = linkIt.next();
			link.encode(encoder);
		}
		encoder.writeEndElement();   		
	}
	
	public boolean validate() { 
		return (null != contents());
	}
	
	@Override
	public String getElementLabel() { return COLLECTION_ELEMENT; }

	@Override
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = PRIME * result + ((_contents == null) ? 0 : _contents.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final Collection other = (Collection) obj;
		if (_contents == null) {
			if (other._contents != null)
				return false;
		} else if (!_contents.equals(other._contents))
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	public Iterator<Link> iterator() {
		return _contents.iterator();
	}
}
