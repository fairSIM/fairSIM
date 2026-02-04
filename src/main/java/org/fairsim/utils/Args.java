package org.fairsim.utils;


import java.util.Map;
import java.util.HashMap;


public class Args {

    enum ArgType {
	INT, DBL, STR;
    }


    class ArgEntry {
	ArgType type;
	String key;
	String strValue;
	int intValue;
	double dblValue;
	String description;
	boolean hasBeenModified=false;
    }

    Map<String,ArgEntry> entries = new HashMap<String,ArgEntry>();


    public int addInt( String k, int v, String des ) {
	ArgEntry a = new ArgEntry();
	a.key = k; a.intValue = v; a.description=des; a.type = ArgType.INT;
	entries.put(k,a);
	return v;
    }

    public double addDbl( String k, double v, String des ) {
	ArgEntry a = new ArgEntry();
	a.key = k; a.dblValue = v; a.description=des; a.type = ArgType.DBL;
	entries.put(k,a);
	return v;
    }

    public String addStr( String k, String v, String des ) {
	ArgEntry a = new ArgEntry();
	a.key = k; a.strValue = v; a.description=des; a.type = ArgType.DBL;
	entries.put(k,a);
	return v;
    }


    public int getInt(String k ) {
	ArgEntry v = entries.get(k);
	if (v==null || v.type != ArgType.INT) 
	    throw new RuntimeException("entry not found or not int: "+k);
	return v.intValue;
    }

    public double getDbl(String k ) {
	ArgEntry v = entries.get(k);
	if (v==null || v.type != ArgType.DBL) 
	    throw new RuntimeException("entry not found or not double: "+k);
	return v.dblValue;
    }

    public String getStr(String k ) {
	ArgEntry v = entries.get(k);
	if (v==null || v.type != ArgType.STR) 
	    throw new RuntimeException("entry not found or not double: "+k);
	return v.strValue;
    }


    public boolean parseArg(String in) {
	
	String [] v = in.split("=");

	ArgEntry arg = entries.get(v[0]);
	if (arg==null) {
	    return false;
	}

	if (arg.type == ArgType.INT) {
	    arg.intValue = Integer.parseInt(v[1]);
	}
	if (arg.type == ArgType.DBL) {
	    arg.dblValue = Double.parseDouble(v[1]);
	}

	arg.strValue=v[1];
	arg.hasBeenModified=true;

	return true;	

    }

    public int parseArgs(String [] in) {

	for (String i: in) {
	    if ( i.startsWith("-h") || i.startsWith("--help") || i.startsWith("help")) {
		printParams();
		return -1;
	    }
	}




	int count=0;
	for (String i: in) {
	    if (parseArg(i)) count++;
	}
    
	return count;
    }

    public void printParams() {
	
	for (ArgEntry i: entries.values()) {

	    String val="";
	    if ( i.type == ArgType.INT) val=String.format("%12d",i.intValue);
	    if ( i.type == ArgType.DBL) val=String.format("%12.5f",i.dblValue);
	    if ( i.type == ArgType.STR) val=String.format("%12s",i.strValue);

	    String def = (i.hasBeenModified)?("[SET]"):("(def)");

	    String out = String.format("%20s %s %s %s",i.key,val,def,i.description);
	    System.out.println(out);
	}


    }



}

