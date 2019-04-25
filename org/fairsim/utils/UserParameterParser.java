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

import java.lang.reflect.Field;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier; 

import java.util.Map;
import java.util.HashMap;

import java.io.PrintStream;

public class UserParameterParser {

    
    static class ParamEntry {
	
	Field	fld;	// the field to work on
	Object	obj;	// the object the field belongs to
	Class	typ;	// type of parameter
	String  key;
	String  des="";
	UserParameter attr;
	boolean hasBeenModified=false;
	int	decimalPlaces=5;

	boolean isValid() {
	    if ( typ.equals(int.class) ) return true;
	    if ( typ.equals(double.class) ) return true;
	    if ( typ.equals(String.class)) return true;
	    return false;
	}

	public static void printParameterHeader(PrintStream out) {

	    out.format("%20s |","Parameter name");
	    out.format("   %12s | %4s |","Value","Type");
	    out.println("  description");
	    out.format("%20s |","----------------");
	    out.format("---%12s-|-%4s-|","------------","----");
	    out.println("--------------------------");
	}

	// provide a formatted output of the parameter (one line)
	public void printParameter(PrintStream out) {
	    if (!isValid()) {
		// TODO: make a custom exception type for this
		throw new RuntimeException("parameter of invalid type");
	    }
	    
	    out.format("%20s |",key);
	    out.format(" %c", (hasBeenModified)?('*'):((attr.mandatory())?('!'):(' ')));

	    int decimalPlaces = 3;
	    if (attr.decimals() >=0 && attr.decimals() <=10) {
		decimalPlaces=attr.decimals();
	    }

	    try {
		if (typ.equals(int.class)){
		    out.format(" %12d | INT  |",fld.getInt(obj));
		}
		if (typ.equals(double.class)){
		    out.format(" %12."+decimalPlaces+(attr.scientific()?("e"):("f"))+" | FLT  |",
			    fld.getDouble(obj));
		}
		if (typ.equals(String.class)){
		    String val = (String)fld.get(obj);
		    out.format(" %12s | TXT  |",(val!=null)?(val):(""));
		}
	    } catch (IllegalAccessException e) {
		throw new RuntimeException(e);
	    }
	    
	    if (des !=null && !des.equals("")) {
		out.println(" "+des);
	    } else {
		out.println("  - n/a -");
	    }
	}

	// set the parameter from a string
	public void parseParameter( String in ) throws NumberFormatException {
	    try {
		if (typ.equals(int.class)){
		    fld.setInt( obj, Integer.parseInt(in));
		}
		if (typ.equals(double.class)){
		    fld.setDouble( obj, Double.parseDouble(in));
		}
		if (typ.equals(String.class)){
		    fld.set(obj,in);
		}
	    } catch (IllegalAccessException e) {
		throw new RuntimeException(e);
	    } catch (NumberFormatException e ) {
		throw e;
	    }
	    hasBeenModified=true;
	}


    }
    
    Map<String,ParamEntry> entries = new HashMap<String,ParamEntry>();


    /** Register non-static and static members of an object instance. */
    public int register( Object obj ) {
	if (obj==null) {
	    throw new NullPointerException();
	}
	return register(obj,obj.getClass());
    }

    /** Register the static members of a class. This will fail with a runtime exception should this class
     *  contain non-static variables annotated as UserParameters */
    public int registerStatic( Class cls ) {
	return register( null, cls);
    }	


    int register( Object obj, Class cls ) {

	int fieldCount =0;
	//System.out.println("-> registering for object: "+obj.toString());

	for ( Field fld :  cls.getDeclaredFields() ) {

	    //System.out.println("-- checking field: "+f.toString());

	    UserParameter up = fld.getAnnotation( UserParameter.class );
	    if (up!=null) {
		
		//System.out.println("** found field annotated ");
		if ( obj==null && !Modifier.isStatic( fld.getModifiers())) {
		    throw new RuntimeException("non-static user-settable variables from a static context: "+fld.getName());
		};
		
		ParamEntry a = new ParamEntry();
		a.fld = fld;
		a.obj = obj;
		a.typ = fld.getType();
		a.key = fld.getName();
		a.des  = (up.desc().equals(""))?(up.value()):(up.desc());
		a.attr = up; 
	    
		if (!a.isValid()) {
		    throw new RuntimeException("invalid field declaration"+fld.toString());
		}

		entries.put(a.key,a);
	    }

	}

	return 0;

    }


    /** Prints the parameters managed by the parser */
    public void printParameters( PrintStream out ) {
	ParamEntry.printParameterHeader(out);
	for (ParamEntry p : entries.values() ) {
	    p.printParameter(out);
	}
    }

    /** parses parametes */
    public boolean parseParameter( String key, String val) {
	ParamEntry e = entries.get(key);
	if (e==null) {
	    return false;
	}
	e.parseParameter(val);
	return true;
    }

    /** parse key=value style from command line */
    public int parseParameters( String [] args ) {

	int count=0;
	for ( String i : args ) {
	    String [] a = i.split("=");
	    if ( parseParameter(a[0],a[1]) ) {
		count++;
	    }
	}

	return count;
    }
    

   /** main() command line parser, with default set.
    *  
    *  This will set all parameters, print a summary to System.out()
    *  
    *  It will fail
    *  - when mandatory parameters are not set
    *  - when parameters could not be parsed (typos and such)
    *  - when the user requests help (--help, -h, help)
    *
    *  This static version also requires all fields annotated 'UserParameter'
    *  in the given class to be static. Typical use case are scripts run from the command line.
    *
    * @param arg The String [] arg of the main method
    * @param cls The class to modify the arguments of
    * @param summary If set, print a summary to System.out
    * @return number of parameters set */
   public static int staticCmdLineParser( String [] arg, Class cls, boolean summary ) {
	return cmdLineParser( arg,null, cls,true,true, (summary)?(2):(1));
   }

   /** main() command line parser, with default set.
    *  
    *  This will set all parameters, print a summary to System.out()
    *  
    *  It will fail
    *  - when mandatory parameters are not set
    *  - when parameters could not be parsed (typos and such)
    *  - when the user requests help (--help, -h, help)
    * 
    *  This version updates both static and non-static members of
    *  the given object.
    *
    *
    * @param arg The String [] arg of the main method
    * @param cls The class to modify the arguments of
    * @return number of parameters set */
   public static int cmdLineParser( String [] arg, Object obj, boolean summary ) {
	return cmdLineParser( arg,obj, null,true,true,(summary)?(2):(1));
   }


    /** main() command line parser, high level.
     *
     * Note: if one of the arguments is 'help', '-h' or '--help' an error (-1) is returned.
     * A help message will be printed (if verbosity is not -1) and the programm will
     * exit with error code 1 (if exitOnFail is set).
     *
     * @param arg The String [] arg of the main method
     * @param obj The object to modifiy the argument of (set to null for static version)
     * @param cls The class to modify the arguments of (only used when obj==null)
     * @param failOnNonMatched If to fail if parameters could not be parsed (typos, etc.)
     * @param exitOnFail If to call System.exit() if parameters could not be parsed
     * @param verbose -1 no output, 0 only on errors + help, 1 summary
     * @return number of parameters succesfully matched, or -1 on fails */
    public static int cmdLineParser( String [] arg, Object obj, Class cls,
	boolean failOnNonMatched, boolean exitOnFail, int verbose ) {

	UserParameterParser upp = new UserParameterParser();

	if ( obj != null) {
	    upp.register( obj, obj.getClass() );
	} else {
	    upp.register(null, cls);
	}

	// check for help
	for ( String i: arg) {
	    if (i.equals("help") || i.equals("-h") || i.equals("--help")) {
		if (verbose>=0) {
		    System.err.println("Usage: set parameters as key=value. \nParameters marked '!' are mandatory");
		    upp.printParameters( System.err );	
		}
		if (exitOnFail) {
		    System.exit(1);
		}
		return -1;
	    }
	}

	// parse parameters
	int count=0;
	for ( String i : arg ) {
	    String [] a = i.split("=");
	    if ( upp.parseParameter(a[0],a[1]) ) {
		count++;
	    } else if (failOnNonMatched) {
		if (verbose>=0) {
		    System.err.println("Parameter not found: "+a[0]);
		    System.err.println("Use 'help' to obtain a list");
		}
		if (exitOnFail) {
		    System.exit(2);
		}
		return -2;
	    }
	}

	// check if all mandatories have been set
	boolean failed =false;
	for ( ParamEntry e: upp.entries.values()) {
	    if (e.attr.mandatory() && !e.hasBeenModified) {
		if (verbose>=0) {
		    System.err.println("Mandatory parameter not set: "+e.key);
		}
		failed=true;
	    }
	}
    
	if (failed) {
	    if (exitOnFail) {
		System.exit(1);
	    }
	    return -1;
	}

	if (verbose >=2) {
	    upp.printParameters(System.out);
	}

	return count;

    }
    	


}

