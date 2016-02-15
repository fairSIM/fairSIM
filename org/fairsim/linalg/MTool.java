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

package org.fairsim.linalg;

import org.fairsim.utils.Tool;

/** Collection of (additional) math subroutines.
 *  Routines which names start with 'f' are faster than their
 *  counterpart in java.lang.Math (with drawbacks mentioned
 *  in their comment).
 *
 *  */
public final class MTool {



    /** Computes sin(x), precision 5.4e-9, approx 3x faster than Math.sin. */
    public static double fsin(double x) {
	x  = x/(Math.PI/2);	    // 0..2pi -> 0..4
	x -= Math.floor(0.25*x)*4;  // x now in 0..4

	// check which quadrat to return
	if (x>3) return -aproxSin01(4-x);
	if (x>1) return  aproxSin01(2-x);
	return aproxSin01(x);
    }
    
    /** Computes cos(x), precision 5.4e-9, approx 3x faster than Math.sin */
    public static double fcos(double x) {
	return fsin(x+Math.PI/2);
    }
    
    // http://stackoverflow.com/questions/523531/fast-transcendent-trigonometric-functions-for-java
    private static double aproxSin01(double x) {
  	double x2 = x * x;
  	return ((((.00015148419 * x2
		- .00467376557) * x2
            	+ .07968967928) * x2
           	- .64596371106) * x2
          	+ 1.57079631847) * x;
    }	

    /** Computes sqrt(x^2+y^2), without the overflow handling of Math.hypot(x,y).
     *  Math.hypot ensures correct results even if x^2 would overflow a double.
     *  If neither x^2 nor y^2 overflows, this will be 30x faster */
    public static double fhypot(double x, double y) {
	return Math.sqrt(x*x+y*y);
    }
    




    /** Tests against java.lang.Math, Timings */
    public static void main(String [] args ) {

	// check approx
	double sindiv = 0;
	for (double r=-6*Math.PI;r<6*Math.PI;r+=0.01) {
	    double ds = Math.abs( fsin(r) - Math.sin(r) );
	    double dc = Math.abs( fcos(r) - Math.cos(r) );
	    //System.out.println(fsin(r)+" "+Math.sin(r));
	    if (ds>sindiv) sindiv=ds;
	    if (dc>sindiv) sindiv=ds;
	}
	System.out.println(String.format("sin/cos: Largest abs. error: %5.3e",sindiv));

	// timing
	double [] v1, v2;
	float [] v2f ;	
	v1 = new double[ 1000000 ];
	v2 = new double[ 1000000 ];
	Tool.Timer t1 = Tool.getTimer();
	Tool.Timer t2 = Tool.getTimer();
  
	// sin, cos
	for (int loop=0; loop<4; loop++) {
	    t1.start();
	    for ( int i=0;i< v1.length; i++)
		v1[i] = Math.sin( (i/100.) );
	    t1.stop();
	    
	    t2.start();
	    for ( int i=0;i< v2.length; i++)
		v2[i] = fsin( (i/100.) );
	    t2.stop();

	    System.out.println("Timing,   Math.sin vs fsin:   "+t1+" "+t2);
	}   
	
	// hypot
	for (int loop=0; loop<4; loop++) {
	    t1.start();
	    for ( int i=0;i< v1.length; i++)
		v1[i] = Math.hypot( (i/100.), (i%100) );
	    t1.stop();
	    
	    t2.start();
	    for ( int i=0;i< v2.length; i++)
		v2[i] = fhypot( (i/100.), (i%100) );
	    t2.stop();

	    System.out.println("Timing, Math.hypot vs fhypot: "+t1+" "+t2);
	}   
	
	
    }



}
