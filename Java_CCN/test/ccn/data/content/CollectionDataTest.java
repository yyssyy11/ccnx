package test.ccn.data.content;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import test.ccn.data.util.XMLEncodableTester;

import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.content.CollectionData;
import com.parc.ccn.data.content.LinkReference;
import com.parc.ccn.data.security.LinkAuthenticator;
import com.parc.ccn.data.security.PublisherID;
import com.parc.ccn.data.security.SignedInfo;
import com.parc.ccn.data.security.PublisherID.PublisherType;

public class CollectionDataTest {

	static final  String baseName = "test";
	static final  String subName = "smetters";
	static final  String document1 = "intro.html";	
	static final  String document2 = "key";	
	static final String document3 = "cv.txt";
	static ContentName name = null;
	static ContentName name2 = null; 
	static ContentName name3 = null;
	static ContentName name4 = null;
	static ContentName [] ns = null;
	static public byte [] contenthash1 = new byte[32];
	static public byte [] contenthash2 = new byte[32];
	static public byte [] publisherid1 = new byte[32];
	static public byte [] publisherid2 = new byte[32];
	static PublisherID pubID1 = null;	
	static PublisherID pubID2 = null;	
	static LinkAuthenticator [] las = new LinkAuthenticator[4];
	static LinkReference [] lrs = null;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		name = ContentName.fromURI(new String[]{baseName, subName, document1});
		name2 = ContentName.fromURI(new String[]{baseName, subName, document2});
		name3 = ContentName.fromURI(new String[]{baseName, subName, document3});
		name4 = ContentName.fromURI("/parc/home/briggs/collaborators.txt");
		ns = new ContentName[]{name,name2,name3,name4};
		Arrays.fill(contenthash1, (byte)2);
		Arrays.fill(contenthash2, (byte)4);
		Arrays.fill(publisherid1, (byte)6);
		Arrays.fill(publisherid2, (byte)3);

		pubID1 = new PublisherID(publisherid1, PublisherType.KEY);
		pubID2 = new PublisherID(publisherid2, PublisherType.ISSUER_KEY);

		las[0] = new LinkAuthenticator(pubID1);
		las[1] = null;
		las[2] = new LinkAuthenticator(pubID2, null,
				SignedInfo.ContentType.LEAF, contenthash1);
		las[3] = new LinkAuthenticator(pubID1, new Timestamp(System.currentTimeMillis()),
				null, contenthash1);


		lrs = new LinkReference[4];
		for (int i=0; i < lrs.length; ++i) {
			lrs[i] = new LinkReference(ns[i],las[i]);
		}
	}

	@Test
	public void testValidate() {
		CollectionData cd = new CollectionData();
		Assert.assertTrue(cd.validate());
		cd.add(lrs[0]);
		Assert.assertTrue(cd.validate());
		cd.remove(0);
		Assert.assertTrue(cd.validate());		
	}

	@Test
	public void testCollectionData() {
		CollectionData cd = new CollectionData();
		Assert.assertNotNull(cd);
		Assert.assertTrue(cd.validate());
	}

	@Test
	public void testContents() {
		CollectionData cd = new CollectionData();
		Assert.assertTrue(cd.validate());
		for (int i=0; i < lrs.length; ++i) {
			cd.add(lrs[i]);
		}
		ArrayList<LinkReference> c = cd.contents();
		Assert.assertNotNull(c);
		Assert.assertTrue(c.size() == lrs.length);
		for (int i=0; i < lrs.length; ++i) {
			Assert.assertEquals(c.get(i), lrs[i]);
		}
	}

	@Test
	public void testAddGet() {
		CollectionData cd = new CollectionData();
		for (int i=0; i < lrs.length; ++i) {
			cd.add(lrs[i]);
		}
		for (int i=0; i < lrs.length; ++i) {
			Assert.assertEquals(cd.get(i), lrs[i]);
		}
	}

	@Test
	public void testRemoveInt() {
		CollectionData cd = new CollectionData();
		for (int i=0; i < lrs.length; ++i) {
			cd.add(lrs[i]);
		}
		cd.remove(0);
		Assert.assertEquals(cd.get(0), lrs[1]);
	}

	@Test
	public void testRemoveLink() {
		CollectionData cd = new CollectionData();
		for (int i=0; i < lrs.length; ++i) {
			cd.add(lrs[i]);
		}
		cd.remove(lrs[0]);
		Assert.assertEquals(cd.get(0), lrs[1]);
		LinkAuthenticator la2alt = new LinkAuthenticator(pubID2, null,
				SignedInfo.ContentType.LEAF, contenthash1);

		LinkReference lr2alt = new LinkReference(name3.clone(), la2alt);
		cd.remove(lr2alt);
		Assert.assertEquals(cd.get(1), lrs[3]);
	}

	@Test
	public void testSize() {
		CollectionData cd = new CollectionData();
		for (int i=0; i < lrs.length; ++i) {
			cd.add(lrs[i]);
		}
		Assert.assertTrue(cd.size() == lrs.length);
	}

	@Test
	public void testEqualsObject() {
		CollectionData cd = new CollectionData();
		CollectionData cd2 = new CollectionData();
		CollectionData cd3 = new CollectionData();

		for (int i=0; i < lrs.length; ++i) {
			cd.add(lrs[i]);
			cd2.add(lrs[i]);
			cd3.add(lrs[lrs.length-i-1]);
		}
		Assert.assertEquals(cd, cd2);
		Assert.assertFalse(cd.equals(cd3));
		cd.remove(2);
		CollectionData cd4 = cd2.clone();
		Assert.assertFalse(cd.equals(cd2));
		Assert.assertEquals(cd4, cd2);
		cd2.remove(2);
		Assert.assertEquals(cd, cd2);

		cd2.remove(2); // remove last entry
		cd2.add(new LinkReference(name3, las[2]));
		cd2.add(new LinkReference(name4, las[3]));
		Assert.assertEquals(cd2, cd4);
	}

	@Test
	public void testEncodeDecodeStream() {
		CollectionData cd = new CollectionData();
		CollectionData cdec = new CollectionData();
		CollectionData bdec = new CollectionData();

		for (int i=0; i < lrs.length; ++i) {
			cd.add(lrs[i]);
		}
		XMLEncodableTester.encodeDecodeTest("CollectionData", cd, cdec, bdec);
	}

	@Test
	public void testEncodeDecodeByteArray() {
		CollectionData cd = new CollectionData();
		CollectionData cdec = new CollectionData();
		CollectionData bdec = new CollectionData();

		for (int i=0; i < lrs.length; ++i) {
			cd.add(lrs[i]);
		}
		XMLEncodableTester.encodeDecodeByteArrayTest("CollectionData", cd, cdec, bdec);
	}
}
