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

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException ;

import java.util.List;
import java.util.ArrayList;


/** A utility class to help multi-threading loops.
 *  Tried to make multi-threaded loops as convenient
 *  to write as possible. Example:
 *  <code>

// len, has to be final to be accesible in inner class
final int len = 1024*1024; 

// single-threaded
final double [] v1 = new double [len];
for (int i=0; i<len; i++)
    v1[i] += Math.sin(2*Math.PI*i/len);

// multi-threaded, 'at' gets calls for every
// iteration of the loop
final double [] v2 = new double [len];
new SimpleMT.PFor(0,len) {
    public void at(int i) {
	v2[i] += Math.sin(2*Math.PI*i/len);
    }
};

 *  </code>
 *
 * */
public final class SimpleMT {

    private static int nrThreads = Runtime.getRuntime().availableProcessors();
    static {
	Tool.trace("SimpleMT: Init to "+nrThreads+" threads ");
    }
    private static ExecutorService ex = 
	Executors.newFixedThreadPool(nrThreads);

    static private boolean doParallel = true;
    static private boolean parallelInProgress = false;


    /*
    public interface SubLoop {
	public void pfor(int s, int e); 

    } */
    
    /** Helpfull class to run parallel loops */
    public static abstract class PFor {
	private final int start, end, inc;
	
	/** Like for(int i=s; i<e; i++) */
	protected PFor(int s, int e) {
	    start=s; end=e; inc=1;
	    SimpleMT.execute(this);
	}
	/** Called for every index in loop **/
	protected abstract void at(int pos) ;
    }
    
    /** Helpfull class to run parallel loops */
    public static abstract class StrPFor {
	private final int start, end;
	
	/** Like for(int i=s; i<e; i++) */
	protected StrPFor(int s, int e) {
	    start=s; end=e;
	    SimpleMT.execute(this);
	}
	/** Called for every index in loop **/
	protected abstract void at(int pos) ;
    }
    
    // -----------------------------------------------------

    public static void shutdown() {
	ex.shutdown();
    }
    
    /** Execute a parallel loop, called by the constructor */
    private static void execute(final PFor loop){
	if (doParallel&&(!parallelInProgress)) {
	    // only run the outermost loop in parallel
	    parallelInProgress=true;
	    
	    // split the loop into sub-loop
	    final int [][] sp = split( nrThreads, loop.start, loop.end );
	    List<Calls> cb = new ArrayList<Calls>(nrThreads);
	    for (int i=0; i<nrThreads; i++) {
		final int j=i;
		cb.add( new Calls() {
		    final int s = sp[j][0], e = sp[j][1];
		    public Object call() {
			for(int i=s;i<e;i+=loop.inc)
			    loop.at(i);
			return null;
		    }
		} );
	    }
	    execute( cb );
	    parallelInProgress=false;
	
	} else {
	    // run in serial if already in parallel loop, or parallel is turned off
	    for (int i=loop.start; i<loop.end; i+=loop.inc)
		loop.at(i);
	}
    }

    /** Execute a parallel loop, called by the constructor */
    private static void execute(final StrPFor loop){
	if (doParallel&&(!parallelInProgress)) {
	    // only run the outermost loop in parallel
	    parallelInProgress=true;
	    
	    // split the loop into sub-loop
	    List<Calls> cb = new ArrayList<Calls>(nrThreads);
	    final int nr = nrThreads;

	    for (int i=0; i<nr; i++) {
		final int j=i;
		cb.add( new Calls() {
		    public Object call() {
			for(int i=loop.start+j; i<loop.end; i+=nr)
			    loop.at(i);
			return null;
		    }
		} );
	    }
	    execute( cb );
	    parallelInProgress=false;
	
	} else {
	    // run in serial if already in parallel loop, or parallel is turned off
	    for (int i=loop.start; i<loop.end; i++)
		loop.at(i);
	}
    }


    /** Abbreviation */
    private interface Calls extends Callable<Object> {} ;
    
    /** execute and wait for all callables */
    static void execute( List<Calls> jobs ) {
	try {
	    List<Future<Object>> fut = ex.invokeAll( jobs );
	    for (Future<Object> t : fut )
		t.get();
	} catch (InterruptedException e) {
	    System.err.println("ERR: "+e);	
	} catch ( ExecutionException e) {
	    System.err.println("ERR: "+e);	
	}
    }


    /** split a range into n parts */
    static int [][] split( int pr, int start, int end ) {
	int [][] sp = new int[pr][2];
	int inc = (end-start)/pr;

	for (int i=0;i<pr;i++) {
	    sp[i][0] = (inc*(i+0))+start;
	    sp[i][1] = (inc*(i+1))+start;
	}
	sp[pr-1][1]=end;
	return sp;
    }

    /** Switch parallel implementation on/off.
     *  Used mostly for benchmarking, if 'value' is false,
     *  all calls will run in standard, serial mode. */
    public static void useParallel(boolean value) {
	Tool.trace("SimpleMT: parallel mode switched "+((value)?("ON"):("OFF")));
	doParallel = value;
    }


    /** For testing */
    public static void main( String [] args ) {

	int [][] s = split( 4, 2, 20 );
	for (int i=0;i<4;i++)
	    System.out.println( "->"+s[i][0]+" "+s[i][1]);

	Tool.Timer t1 = Tool.getTimer();
	Tool.Timer t2 = Tool.getTimer();
	Tool.Timer t3 = Tool.getTimer();

	for (int loop=0;loop<10;loop++) {

	    // len, has to be final to be accesible in inner class
	    final int len = 1024*1024; 
	    
	    // single-threaded
	    t1.start();
	    final double [] v1 = new double [len];
	    for (int i=0; i<len; i++)
		v1[i] += Math.sin(2*Math.PI*i/len);
	    t1.stop();
	  
	    // multi-threaded, 'at' gets calls for every
	    // iteration of the loop
	    t2.start();
	    final double [] v2 = new double [len];
	    new SimpleMT.PFor(0,len) {
		public void at(int i) {
		    v2[i] += Math.sin(2*Math.PI*i/len);
		}
	    };
	    t2.stop();
	    
	    // multi-threaded, 'at' gets calls for every
	    // iteration of the loop
	    t3.start();
	    final double [] v3 = new double [len];
	    new SimpleMT.StrPFor(0,len) {
		public void at(int i) {
		    v3[i] += Math.sin(2*Math.PI*i/len);
		}
	    };
	    t3.stop();

	    Tool.trace("    std: "+t1);
	    Tool.trace("   PFor: "+t2);
	    Tool.trace("StrPFor: "+t3);
	    Tool.trace("---");

	}
	// shutdown the executer
	SimpleMT.shutdown();
    
    }



}
