/*
This file is part of Free Analysis and Interactive Reconstruction
for Structured Illumination Microscopy (fairSIM).

fairSIM is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or
(at your option) any later version.

fairSIM is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with fairSIM.  If not, see <http://www.gnu.org/licenses/>
*/

package org.fairsim.utils;

// Parser:
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

// Output:
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.OutputKeys;
import java.io.File;
import java.io.FileInputStream;

// Node manipulation
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

// Data structure
import java.util.Map;
import java.util.Hashtable;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Locale;

// Data conversion
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.DoubleBuffer;
import javax.xml.bind.DatatypeConverter;


/** Wrappers around entries to a configuration file */
public class Conf {

    final String namespace;
    final Folder root;

    /** Create a new configuration with a namespace */
    public Conf(String namespace) {
	this.namespace = namespace;
	root = new Folder(namespace);
    }

    /** Returns the namespace */
    public String getNamespace() {
	return namespace;
    }
    
    /** Returns the root folder  */
    public Folder r() {
	return root;
    }
    /** Returns the root, checking if the namespace matches  */
    public Folder cd(String namespace) 
	throws EntryNotFoundException {
	if (!namespace.equals(this.namespace))
	    throw new EntryNotFoundException(namespace);
	return root;
    }


    // ------ The different entries ------

    /** storing entries */
    static abstract class Entry {
	/** Output name and short description of tag */
	abstract String prettyPrint() ;
	/** Output the type */
	abstract String getType();
	/** Output the content */
	abstract String getText();
	/** Set entry from text */
	abstract void fromText( String t ) throws SomeIOException;
    };


    /** Folder collecting other entries. */
    public static final class Folder extends Entry {
	Map<String,Entry> subEntry = new Hashtable<String,Entry>(); 

	final String ourName;
	private Folder(String name) {
	    ourName = name;
	}

	// ------ General element management ------
	
	/** Add an entry to the folder  */
	public <T extends Entry> T setEntry(String name, T e) {
	    subEntry.put( name, e );
	    return e;
	}

	/** Returns the entry 'name', of type 'type', or null */
	public <T extends Entry> T getEntry(String name, Class<T> type ) {
	    Entry e = subEntry.get(name);
	    if (type.isInstance(e))
		    return type.cast(e);
	    return null;
	}
	
	/** Returns the entry 'name', of type 'type', or raises an exception.
	 *  Exception raised if 'name' does not exist or is of wrong type. */
	public <T extends Entry> T getEntryOrFail(String name, Class<T> type ) 
	    throws EntryNotFoundException {
	    Entry e = subEntry.get(name);
	    if (type.isInstance(e))
		    return type.cast(e);
	    throw new EntryNotFoundException(name);
	}


	/** Returns if an element named 'name' exists in this folder */
	public boolean contains(String name) {
	    return subEntry.containsKey(name);
	}
	
	/** Returns if an element named 'name', of type 'type', exists in this folder */
	public <T extends Entry> boolean contains(String name, Class<T> type) {
	    return (type.isInstance(subEntry.get(name)));
	}
	
	/** Removes element named 'name'.
	 *  @return If the element existed */
	public boolean delete(String name ) {
	    if (!contains(name))
		return false;
	    subEntry.remove(name);
	    return true;
	}

	// ------ Subfolders ------

	/** Returns the sub-folder 'name'. Raises an exception
	 *  if Folder does not exist. */	
	public Folder cd( String name ) 
	    throws EntryNotFoundException {
	    return getEntryOrFail(name,Folder.class);
	}
	
	/** Returns or creates the sub-folder 'name'. */
	public Folder mk( String name ) {
	    Folder f = getEntry(name,Folder.class);
	    if (f==null) {
		f=new Folder(name);
		setEntry(name,f);
	    }
	    return f;
	}

	@Override
	String prettyPrint() {
	    return prettyPrint("");
	}

	/* creates folder tree */
	String prettyPrint(String fn) {
	    String ret="";
	    for ( String n : subEntry.keySet() ) {
		Entry e = subEntry.get(n);
		if (!(e instanceof Folder)) {
		    ret += fn+ourName+":"+n+" "+e.prettyPrint()+"\n";	
		}
		else {
		    ret += ((Folder)e).prettyPrint(fn+ourName+".");
		}
	    }
	    return ret;
	}

	@Override
	String getType() { return "folder"; };
	@Override
	String getText() { return "(--content---)"; };
	@Override
	void fromText(String t) { };


	// ------ Convenient element shortcuts ------

	/** Return the Integer named 'name', raises an exception
	 *  if it does not exist.
	 *  Convenience shortcut to getEntryOrFail(name, IntEntry.class) */
	public IntEntry getInt(String name) throws EntryNotFoundException {
	    return getEntryOrFail(name,IntEntry.class);
	}
	
	/** Create a new Integer 'i' named 'name' (and returns it). */
	public IntEntry newInt(String name) {
	    IntEntry e = new IntEntry();
	    setEntry(name, e);
	    return e;
	}
	
	/** Return the Double named 'name', or null.
	 *  Convenience shortcut to getEntry(name, DoubleEntry.class) */
	public DoubleEntry getDbl(String name) throws EntryNotFoundException {    
	    return getEntryOrFail( name, DoubleEntry.class ); 
	}
	
	/** Create a new Double 'd' named 'name' */
	public DoubleEntry newDbl(String name ) {
	    DoubleEntry e = new DoubleEntry();
	    setEntry(name, e);
	    return e;
	}
	
	/** Return the Double named 'name', or null.
	 *  Convenience shortcut to getEntry(name, DoubleEntry.class) */
	public StringEntry getStr(String name) throws EntryNotFoundException {    
	    return getEntryOrFail( name, StringEntry.class ); 
	}
	
	/** Create a new Double 'd' named 'name' */
	public StringEntry newStr(String name) {
	    StringEntry e = new StringEntry();
	    setEntry(name, e);
	    return e;
	}
	
	/** Return the Double named 'name', or null.
	 *  Convenience shortcut to getEntry(name, DoubleEntry.class) */
	public DataEntry getData(String name) throws EntryNotFoundException {    
	    return getEntryOrFail( name, DataEntry.class ); 
	}
	
	/** Create a new Double 'd' named 'name' */
	public DataEntry newData(String name) {
	    DataEntry e = new DataEntry();
	    setEntry(name, e);
	    return e;
	}
	
	// ------ Convert our content to a DOM node ------ 

	/** create a DOM node, which includes all sub-entries */
	public Element toNode( Document doc ) {
	    
	    // output the folder
	    Element ret = doc.createElement(ourName);
	    //ret.setAttribute("type","folder");

	    // loop sub elements
	    for ( String n : subEntry.keySet() ) {
		Element sub;
		Entry e = subEntry.get(n);
		// data first ...
		if (!(e instanceof Folder )) {
		    
		    sub = doc.createElement(n);
		    sub.setAttribute("type",e.getType());
		    Node text = doc.createTextNode(e.getText()); 
		    sub.appendChild( text );

		} 
		// then, sub-folders
		else {
		    sub = ((Folder)e).toNode( doc );
		}
		ret.appendChild( sub );
	    }
	    return ret;
	}
    
    };


    /** Entry for the common case of storing one or more ints */
    public static class IntEntry extends Entry {
	int [] ourVals=new int[1];

	/** Get all values */
	public int [] vals() {
	    return ourVals;
	}
	/** Get first value */
	public int val() {
	    return ourVals[0];
	}
	/** Set new values */
	public IntEntry setVal(int ... i) {
	    if (i.length==0)
		throw new RuntimeException("Array empty!");
	    ourVals=new int[i.length];
	    System.arraycopy( i, 0, ourVals, 0, i.length);
	    return this;
	}
	
	@Override
	String prettyPrint() {
	    String ret  = "(INT) ";
	    for (int i=0; i<Math.min(5,ourVals.length); i++)
		ret+=" "+ourVals[i];
	    if (ourVals.length>5)
		ret+=" ... ("+ourVals.length+" total)";
	    return ret;
	}
	
	@Override
	String getText() {
	    String inttext=" ";
	    for (int i : ourVals)
		inttext += i+" ";
	    return inttext;
	}
	
	@Override
	String getType() { return "int"; }
	
	@Override
	void fromText( String text ) {
	    Scanner sc = new Scanner(text);
	    sc.useLocale( Locale.US );
	    
	    ArrayList<Integer> i = new ArrayList<Integer>();
	    while ( sc.hasNextInt() )
		i.add( sc.nextInt() );
	    
	    ourVals = new int [ i.size() ];
	    for (int j=0; j<ourVals.length; j++)
		ourVals[j] = i.get(j);

	}
	
	/* creates folder tree */
	/*
	String outputAll(String fn) {
	    String ret="";
	    for ( String n : subEntry.keySet() ) {
		Entry e = subEntry.get(n);
		if (!(e instanceof Folder)) {
		    ret += fn+ourName+":"+n+"("e.getType()+") "+e.getText()+"\n";	
		}
		else {
		    ret += ((Folder)e).prettyPrint(fn+ourName+".");
		}
	    }
	    return ret;
	} */

    }
   
    /** Entry for the common case of storing one or more Doubles */
    public static class DoubleEntry extends Entry {
	
	boolean exact=false, scientific=true;
	double [] ourVals=new double[1];
	
	/** Get all values */
	public double [] vals() {
	    return ourVals;
	}
	/** Get first value */
	public double val() {
	    return ourVals[0];
	}
	/** Set new values */
	public DoubleEntry setVal(double ... i) {
	    if (i.length==0)
		throw new RuntimeException("Array empty");
	    ourVals=new double[i.length];
	    System.arraycopy( i, 0, ourVals, 0, i.length);
	    return this;
	}

	/** If set, numbers will be stored exact as base64 */
	public void setExactOutput( boolean s) {
	    exact=s;
	}

	@Override
	String prettyPrint() {
	    String ret  = "(DBL)";
	    for (int i=0; i<Math.min(5,ourVals.length); i++)
		ret+=String.format(" %8.4f",ourVals[i]);
	    if (ourVals.length>5)
		ret+=" ... ("+ourVals.length+" total)";
	    return ret;
	}

	@Override
	String getText() {
	    if (! exact  ) {
		String flttext=" ";
		for (double i : ourVals)
		    flttext += String.format("%8.5e ", i);
		return flttext;
	    } else {
		byte [] v = new byte[ 8*ourVals.length ];
		DoubleBuffer db = ByteBuffer.wrap(v).asDoubleBuffer();
		db.put( ourVals );
		String b64 = Base64.encode( v );
		return (" BASE64:"+b64+":END64 ");
	    }
	}
	@Override
	String getType() { return "decimal"; };
	@Override
	void fromText( String text ) 
	    throws SomeIOException {
	    if (text.contains("BASE64:")) {
		String sub = text.substring( 
		    text.indexOf(":")+1, 
		    text.indexOf(":END64")
		    ).trim();
	    
		//System.out.println("[sub] "+sub);

		byte [] v = Base64.decode( sub );
		if (v.length%8!=0)
		    throw new SomeIOException(new Exception("Wrong base64 len for double"));
		int nrDbl = v.length/8;
		DoubleBuffer db = ByteBuffer.wrap(v).asDoubleBuffer();
		ourVals = new double [nrDbl];
		db.get( ourVals );

	    } else {

		Scanner sc = new Scanner(text);
		sc.useLocale( Locale.US );
		ArrayList<Double> i = new ArrayList<Double>();
		while ( sc.hasNextDouble() )
		    i.add( sc.nextDouble() );
		
		ourVals = new double [ i.size() ];
		for (int j=0; j<ourVals.length; j++)
		    ourVals[j] = i.get(j);
	    }
	}
    }


    /** Entry for the common case of storing a String. */
    public static class StringEntry extends Entry {
	String ourVal;
	/** Return the value */
	public String val() {
	    return ourVal;
	}
	/** Set the value. Caution: String should
	 * not contain any XML markup characters, as
	 * it currently does not run through any encoding.
	 * Use 'data' instead. */
	public void val(String v) {
	    ourVal=v;
	}

	@Override
	String prettyPrint() {
	    String ret  = " (STR) "+ourVal+"\n";
	    return ret;
	}
	@Override
	String getText() { return ourVal; }
	@Override
	String getType() { return "string"; }
	@Override
	void fromText( String text ) {
	   ourVal = text; 
	}
    }
   
    /** Entry storing (low to medium amounts) of binary data */
    public static class DataEntry extends Entry {

	byte [] ourData = new byte[0];

	/** Return the currently stored data */
	public byte [] val() {
	    return ourData;
	}

	/** Set the data stored by this node */
	public DataEntry setVal(byte [] b) {
	    ourData = b;
	    return this;
	}

	@Override
	String prettyPrint() {
	    String ret  = " (DAT) "+ourData.length+" bytes ";
	    return ret;
	}
	@Override
	String getText() { 
	    return (" BASE64:"+Base64.encode(ourData)+":END64 ");
	}
	@Override
	String getType() { return "data"; }
	@Override
	void fromText( String text ) {
		
	    String sub = text.substring( 
	        text.indexOf("BASE64:")+7, 
	        text.indexOf(":END64")
	    ).trim();
	    

	    ourData = Base64.decode( sub );
	}


    }


    
    // ========================================================================

    // ------ load / save ------
    
    /** Write the config to an XML file */
    public boolean saveFile( String xmlfile ) 
	throws SomeIOException {
	return saveFile( new File( xmlfile ));
    }

    /** Write the config to an XML file */
    public boolean saveFile( File xmlfile ) 
	throws SomeIOException {
	
	DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	dbf.setNamespaceAware(false);
	dbf.setValidating(false);
	dbf.setIgnoringElementContentWhitespace(true);
	
	DocumentBuilder builder=null; 
	try {
	    builder = dbf.newDocumentBuilder();
	} catch (java.lang.Exception e) {
	    throw new SomeIOException(e);
	}
	
	Document doc = builder.newDocument();
	doc.appendChild( root.toNode(doc) );

	Transformer tf=null;
	try {
	    tf = TransformerFactory.newInstance().newTransformer();
	    tf.setOutputProperty(OutputKeys.INDENT, "yes");
	    tf.setOutputProperty(OutputKeys.METHOD, "xml");
	    tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
	    tf.setOutputProperty(OutputKeys.STANDALONE, "yes");
	    //tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
	    tf.transform( new DOMSource( doc ) , new StreamResult( xmlfile ));
	} catch (java.lang.Exception e) {
	    throw new SomeIOException(e);
	}

	return true;
    }
    
    /** Create a Configuration from an XML file */
    public static Conf loadFile(String xmlfile) 
	throws SomeIOException {
	return loadFile( new File(xmlfile));
    }

    /** Create a Configuration from an XML file */
    public static Conf loadFile( File xmlfile) 
	throws SomeIOException {

	// parse the input
	Document doc=null;
	try {
	    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	    dbf.setNamespaceAware(false);
	    dbf.setValidating(false);
	    dbf.setIgnoringElementContentWhitespace(true);

	    DocumentBuilder builder = dbf.newDocumentBuilder();

	    InputSource is = new InputSource(new FileInputStream(xmlfile));
	    doc = builder.parse( is );
	
	}
	catch ( java.lang.Exception e ) {
	    throw new SomeIOException(e); 
	}
	if (doc==null) {
	    throw new SomeIOException(new Exception("Parsed document is null?")); 
	}

	// loop its nodes
	Element rootNode = doc.getDocumentElement();
	Conf cfg = new Conf(rootNode.getTagName());

	importXmlElement( rootNode, cfg.r() );

	return cfg;
    }

    /** parses an XML element to our data structure */
    static void importXmlElement( Element xml, Folder fdl ) 
	throws SomeIOException {
	
	// loop nodes
	NodeList cld = xml.getChildNodes();
	for (int i = 0; i<cld.getLength(); i++) {

	    // first, check only elements
	    Node n = cld.item(i);
	    if (n.getNodeType() != Node.ELEMENT_NODE)
		continue;
	    
	    Element e = (Element)n;
	    String name = e.getTagName();

	    // now, if no type is set, assume it is a folder, recurse into to
	    if (e.getAttribute("type").equals("")) {
		Folder nf = fdl.mk( name );
		importXmlElement( e, nf );
	    }

	    // otherwise, see if we can import it
	    String t = e.getAttribute("type");
	    String c = e.getTextContent();

	    /*
	    System.out.print("t: "+t+" val: ");
	    for ( String j : c.trim().split("\\s+") )
		System.out.print("["+j+"]");
	    System.out.println(""); */


	    try {

		if ( t.equals( "int" )) 
		    fdl.newInt( name ).fromText( c );
		
		if ( t.equals( "decimal" )) 
		    fdl.newDbl( name ).fromText( c );
		
		if ( t.equals( "string" )) 
		    fdl.newStr( name ).fromText( c );
		
		if ( t.equals( "data" )) 
		    fdl.newData( name ).fromText( c );
	    
	    
	    } catch ( Exception ex ) {
		throw new SomeIOException(ex);
	    }
	
	
	}

    }

    // ========================================================================

    // ------ Exception ------

    /** Raised if an entry is not found in a folder */
    public static class EntryNotFoundException extends Exception {
	EntryNotFoundException(String n) {
	    super(n);
	}
    }

    /** Raised if there are IO or XML-parser problems */
    public static class SomeIOException extends Exception {
	/** The original exception */
	public final Exception original;
	SomeIOException(Exception e) {
	    super("IO Problem: "+e.toString());
	    original = e;
	}
    }

    /** convert a byte [] to float [] */
    public static float [] fromByte(byte [] in) {
    
	if (in.length%4!=0)
	    throw new RuntimeException("Array size not a multiple of 4");
	
	float [] ret = new float[ in.length /4 ];

	FloatBuffer fb = ByteBuffer.wrap(in).asFloatBuffer();
	fb.get(ret);
	return ret;
    }

    /** convert a byte [] to float [] */
    public static byte [] toByte(float [] in) {
	byte [] ret = new byte[ in.length *4 ];
	FloatBuffer fb = ByteBuffer.wrap(ret).asFloatBuffer();
	fb.put(in);
	return ret;
    }


    // ========================================================================
  
    // ------ Test function ------
    
    /** Read in and display an config file */
    static public void  main(String [] args) throws Exception {

	if (args.length==0) {
	    System.out.println("Usage [file] [-w]");
	    System.out.println("Use -w to write an example file");
	    return;
	}

	if ((args.length<2) || (!args[1].equals("-w"))) {
	    System.out.println("# Reading: "+args[0]);

	    Conf cfg2 = Conf.loadFile( args[0] );
	    System.out.println(cfg2.r().prettyPrint());
	    
	    return;
	}

	if ((args.length>=2) && (args[1].equals("-w"))) {
	    
	    System.out.println("# Writing example: "+args[0]);

	    Conf cfg = new Conf("fairsim");

	    cfg.r().mk("sim-parameter").newInt("nrBands").setVal(3);
	    cfg.r().mk("sim-parameter").mk("dir1").newDbl("px").setVal(6.5);
	    cfg.r().mk("sim-parameter").mk("dir1").newDbl("py").setVal(6.5);

	    cfg.r().mk("exaple-dbl").newDbl("array").setVal(1.2, 3.4, 5.6, 6.5);
	    cfg.r().mk("exaple-dbl").newDbl("exact").setVal(1.6).setExactOutput(true);

	    byte [] test = new byte[40];
	    for (int i=0; i<40; i++) test[i]=(byte)(Math.random()*255);

	    cfg.r().mk("otf").newData("values").setVal( test );


	    cfg.saveFile( args[0] );
	}


    }

}


