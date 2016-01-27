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
along with ESI.  If not, see <http://www.gnu.org/licenses/>
*/

package org.fairsim.utils;


/**
 * Logging and Timers
 * */
public final class Tool {

    /** Forbit the construction of this class */
    private Tool() {}
    /** The tool implementation in use*/
    static private Tool.Logger currentLogger;


    /** Simple logger */
    public interface Logger {
	public void writeTrace(String message); 
	public void writeShortMessage(String message);
    }

    /** Inits a standard tool */
    static {
	// basic logger goes to System.out
	currentLogger = new Tool.Logger() {
	    public void writeTrace(String message) {
		System.out.println( "[fairSIM] "+message);
	    }
    
	    public void writeShortMessage(String message) {
		System.out.println( "-fairSIM- "+message);
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
    
    /** A simple timer */
    public static class Timer {
	long start, stop, runtime, outtime;
	Timer() { start =  System.currentTimeMillis(); }
	/** start the timer */
	public void start() { start = System.currentTimeMillis(); };
	/** stop the timer */
	public void stop() { 
	    stop = System.currentTimeMillis(); 
	    runtime += stop-start;
	    outtime=runtime;
	    runtime=0;
	    }
	/** pause the timer */
	public void hold(){
	    stop = System.currentTimeMillis();
	    runtime += stop-start;
	    outtime  = runtime;
	    start =stop;
	}
	/** output the amount of milliseconds counted */
	@Override public String toString(){ return("ms: "+(outtime));}
    }

}



