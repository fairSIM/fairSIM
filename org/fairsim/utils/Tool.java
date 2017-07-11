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

import java.io.File;
import java.util.Calendar;
import java.util.logging.Level;
import javax.swing.JOptionPane;

/**
 * Logging and Timers
 * */
public final class Tool {

    /** Forbit the construction of this class */
    private Tool() {}
    /** The tool implementation in use*/
    static private Tool.Logger currentLogger;
    static private boolean errorShown = false;

    /** Simple logger */
    public interface Logger {
	public void writeTrace(String message); 
	public void writeError(String message, boolean fatal);
	public void writeShortMessage(String message);
    }

    /** Inits a standard tool */
    static {
	// basic logger goes to System.out
	currentLogger = new Tool.Logger() {
	    public void writeTrace(String message) {
		System.out.println( "[fairSIM] "+message);
		System.out.flush();
	    }
	    
	    public void writeError(String message, boolean fatal) {
		String prefix = (fatal)?("[fsFATAL]"):("[fsERROR]");
		System.err.println( prefix+" "+message);
		System.err.flush();
	    }
    
	    public void writeShortMessage(String message) {
		System.out.println( "-fairSIM- "+message);
		System.out.flush();
	    }
	};
	// want to catch exceptions
	/*
	Thread.setDefaultUncaughtExceptionHandler( new Thread.UncaughtExceptionHandler () {
	    public void uncaughtException(Thread t, Throwable e) {
		Tool.trace("Problem, caugt exception: "+e);
		e.printStackTrace();
	    }
	});
	*/
    }

    /** Write a trace message */
    static public final void trace(String message) {
	if (currentLogger!=null)
	    currentLogger.writeTrace( message );
    }
    
    /** Output a short status / info message */
    static public final void tell(String message) {
	if (currentLogger!=null)
	    currentLogger.writeShortMessage( message);
    }
   
    /** Write an error message */
    static public final void error(final String message, boolean fatal ) {
        new Thread(new Runnable() {
            public void run() {
                if (!errorShown) {
                    errorShown = true;
                    JOptionPane.showMessageDialog(null, message, "fairSIM Error", JOptionPane.ERROR_MESSAGE);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ex) {
                        Tool.trace("Tool: errorShown sleep interrupted, why?");
                    }
                    errorShown = false;
                }
            }
        }).start();
	if (currentLogger!=null)
	    currentLogger.writeError( message, fatal );
    }

    /** Creates an absolute file from path provided a string.
     *  This especially takes care of "~" to set the users home */
    static public File getFile(String path) {
	if (path.startsWith("~"+File.separator)){
	    path=System.getProperty("user.home")+path.substring(1);
	}
	return new File(path).getAbsoluteFile();
    }


    /** Decode a BCD timestamp (as used by PCO) 
     *	@param BCD input, typically image acquired by camera (first 16 entries used)
     *	@return The timestamp, in microseconds since epoch
     * */
    static public long decodeBcdTimestamp( short [] stamp ) {
	long stampNr= bcdDecode(stamp, 0, 4);

	int year = bcdDecode(stamp, 4, 6);
	int month= bcdDecode(stamp, 6, 7);
	int day  = bcdDecode(stamp, 7, 8);

	int h	  = bcdDecode(stamp,  8, 9);
	int min  = bcdDecode(stamp,  9,10);
	int sec  = bcdDecode(stamp, 10,11);
	int  us  = bcdDecode(stamp, 11,14);

	Calendar cld = Calendar.getInstance();
	cld.set( year, month-1, day, h, min, sec );

	long ret = cld.getTimeInMillis()/1000; 
	// TODO: for some reason, 'getTimeInMillis' is not a multiple of 1000
	
	ret = (ret*1000000) +us;

	return ret;
    }

    /** Decode double-packed (2 digit per byte) BCD-encoded values.
     *  Beware of overflows, even with long
     *  @param arr The input array to decode from 
     *	@param start start of range to decode
     *	@param end end of range to decode
     *	@return the decoded number 
     *  */
    public static int bcdDecode( short [] arr, int start, int end) {
	int ret=0;
	int count=0;
	
	for (int j=end-1; j>=start; j--) {
	    int val1 =  arr[j] & 0x000F ;
	    int val2 = (arr[j] & 0x00F0)>>4 ;
	    int mult = (int)Math.pow(10, count*2);
	    //IJ.log(""+mult);
	    ret += (val1+val2*10)*mult;
	    count++;
	}

	return ret;
    }




    /** Format a 'milliseconds since 1 Jan 1970' timestamp in ISO */ 
    static public String readableTimeStampMillis( long ms , boolean spaces ) {
	java.text.DateFormat df;
	if (!spaces) {
	    df = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
	    df.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
	} else {
	    df = new java.text.SimpleDateFormat("yyyy-MM-dd' T 'HH:mm:ss '('Z')'");
	}

	String nowAsISO = df.format(new java.util.Date(ms));
	return nowAsISO;
    }

    /** Format a 'seconds since 1 Jan 1970' timestamp in ISO */ 
    static public String readableTimeStampSeconds( double seconds , boolean spaces) {
	long val = (long)(seconds*1000);
	return readableTimeStampMillis(val, spaces);
    }

    /** Implement and pass Tool.Logger to redirect log output,
     *  or set null to disable output completely */
    public static void setLogger( Tool.Logger t ) {
	currentLogger = t;
    }

    /** Shuts down all multi-threading pools */
    public static void shutdown() {
	SimpleMT.shutdown();
    }	

    /** Return a Tool.Timer, which is automatically started. */
    static public Timer getTimer() { return new Timer(); };
    
    /** A simple timer. TODO: The meaning of stop, pause, ... could
     *  be much clearer */
    public static class Timer {
	long start, stop, runtime, outtime;
	Timer() { 
	    start();
	}
	/** start the timer */
	public void start() { 
	    //start = System.currentTimeMillis(); 
	    start =  System.nanoTime(); 
	};
	/** stop the timer (next start resets it) */
	public void stop() { 
	    //stop = System.currentTimeMillis(); 
	    stop = System.nanoTime(); 
	    runtime += stop-start;
	    outtime=runtime;
	    runtime=0;
	    }
	/** pause the timer (next start continues) */
	public void hold(){
	    //stop = System.currentTimeMillis();
	    stop = System.nanoTime(); 
	    runtime += stop-start;
	    outtime  = runtime;
	    start =stop;
	}
	/** get the milliseconds on this timer */
	public double msElapsed() {
	    return outtime/1000000.;
	}

	/** output the amount of milliseconds counted */
	@Override public String toString(){ 
	    return String.format("%10.3f ms",(outtime/1000000.));
	}
    }
    
    public static String[] decodeArray(String encodedArray) {
        String[] split = encodedArray.split(";");
        String[] data = new String[split.length - 1];
        for (int i = 0; i < data.length; i++) {
            data[i] = split[i + 1];
        }
        return data;
    }
    
    public static int[] decodeIntArray(String encodedArray) {
        String[] stringArray = decodeArray(encodedArray);
        int len = stringArray.length;
        int[] intArray = new int[len];
        for (int i = 0; i < len; i++) {
            intArray[i] = Integer.parseInt(stringArray[i]);
        }
        return intArray;
    }

    private static String encodeArray(String prefix, String[] array) {
        int len = array.length;
        String output = prefix;
        if (len == 0) {
            return output;
        } else {
            for (int i = 0; i < len; i++) {
                output += ";" + array[i];
            }
            return output;
        }
    }
    
    public static String encodeArray(String prefix, int[] array) {
        int len = array.length;
        String[] sArray = new String[len];
        for (int i = 0; i < len; i++) {
            sArray[i] = Integer.toString(array[i]);
        }
        return encodeArray(prefix, sArray);
    }
    
    public static <T> String encodeArray(String prefix, T[] array) {
        if (array instanceof String[]) {
            return encodeArray(prefix, (String[])array);
        } else {
            int len = array.length;
            String[] sArray = new String[len];
            for (int i = 0; i < len; i++) {
                sArray[i] = array[i].toString();
            }
            return encodeArray(prefix, sArray);
        }
    }

    /** A generic callback interface */
    public static interface Callback<T> {
	public void callback(T a);
    }

    /** A generic tuple */
    public static class Tuple<F,S> {
	public final F first;
	public final S second;

	public Tuple (F first, S second) {
	    this.first  = first;
	    this.second = second;
	}
    }


    /* TODO: compare this to utils.Future and such, and maybe finish it
    public static class Errant<D, Tool.Callback<R>> {
	
	final D val;
	final Tool.Callback<R> iface;

	protected Errant( D val, Tool.Callback<R> iface) {

	}


	public returnResult(R) {

    } */
     

}



