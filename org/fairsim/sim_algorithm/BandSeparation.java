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

package org.fairsim.sim_algorithm;

import org.fairsim.linalg.*;
import org.fairsim.utils.Tool;

/**
 * SIM band separation methods.
 */
public class BandSeparation {

    /** Obtain a separation matrix for arbitrary amount 
     *  of bands. Note: Not really tested with more than
     *  3 bands yet. <br> If #phases > #bands*2-1, pseudo-inverse
     *  will be used. <br>
     *
     *  @param phases Phases 0..2pi, order: phases[band-1][#phase]
     *  @param bands  Number bands (2 for 2beam, 3 for 3beam data, ..)
     *  @param fac    Scaling factors, one for each band (may be 'null', then all factors are 1,
     *   only use that for testing!)
     *  @return Inverted band separation matrix
     */
    public static MatrixComplex createSeparationMatrix(
	double [][] phases, final int bands, double [] fac ) {

	// phase matrix and its inverse
	MatrixComplex M;
	MatrixComplex invM;
	
	// check some inputs
	if (bands<2)
	    throw new RuntimeException("#bands < 2, not useful");
	if ((phases.length != bands-1) || ((fac!=null)&&(fac.length != bands)))
	    throw new RuntimeException("#factors or #phases+1 != #bands");
	
	final int nrPhases = phases[0].length;
	if (nrPhases < (bands*2)-1 )
		throw new RuntimeException("not enough phases for #bands");
	
	for (int i=0; i<bands-1; i++) 
	    if (phases[i].length != nrPhases)
		throw new RuntimeException("phase array length mismatch");
	
	// debug ...
	if (false) {	
	    Tool.trace("Sep. Matrix: "+phases.length+" phases, "+bands+" bands, "+
		nrPhases+" phases per band");
	    String tmp="";
	    for (int j=0;j<bands-1;j++) 
	    for (int i=0;i<nrPhases;i++) 
		tmp+=String.format("%5.3f ",phases[j][i]);
	    Tool.trace(tmp);
	}

	// set scaling factors (if they are null)
	if ( fac == null ) {
	    fac = new double[ bands ];
	    for (int i=0;i<bands; i++)
		fac[i]=(i==0)?(1.0):(.5);
	} else {
	    for (int i=1;i<bands; i++)
		fac[i]*=0.5;
	}

	// create the matrix
	M = new MatrixComplex(nrPhases,(bands*2)-1);

	// loop phases
	for (int i=0; i<nrPhases; i++) {
		
	    // band zero is one, DC component (col 0)
	    M.set(i,0, new Cplx.Double(fac[0]));
    
	    // all other bands: two phases per band (2 dirac peaks)
	    for (int j=0; j<bands-1; j++) {

		// pos. dirac peak (cols 1,3,5..)
		M.set(i,2*j+1, new Cplx.Double( 
			fac[j+1]*Math.cos(phases[j][i]) , 
			fac[j+1]*Math.sin(phases[j][i])) );
		
		// neg. dirac peak (cols 2,4,6..)
		M.set(i,2*j+2, new Cplx.Double( 
			fac[j+1]*Math.cos(-phases[j][i]) , 
			fac[j+1]*Math.sin(-phases[j][i])) );
	    }
	}

	// invert the shift matrix
	if ( nrPhases == (bands*2)-1)
	    invM = M.inverse();
	else
	    invM = M.pseudoInverse();

	return invM;

    }


    /** Compute the spectral separation. This creates the matrix
     *  first (see {@link #createSeparationMatrix}),
     *  then multiplies the spectra. <br> Supports arbitrary #phases,
     *  if (#phases > bands*2-1), pseudo-inverse matrix is used. <br>
     *  Phase input array should hold all (absolute) phases for 1. band, 
     *  higher band phases are set to multiplies of base. <br >
     *
     *	@param in     FFT'd input images, array size: #images = #phases/#bands
     *	@param out    Band-separated output
     *  @param phases Phases 0..2pi, length >= (bands*2)-1
     *  @param bands  Number of bands, convention: 2 for 2beam, 3 for 3beam data
     *  @param fac    Scaling factors, one for each band 
     *
     *  */
    static public void separateBands( Vec.Cplx [] in, Vec.Cplx [] out, 
	double [] phases, final int bands, double [] fac ) {
    
	// check length
	if (in.length!=phases.length)
	    throw new IndexOutOfBoundsException("#input images != phases per band");
	if (out.length!=(bands*2)-1) 
	    throw new IndexOutOfBoundsException("#output images != #bands*2-1");

	// compute phases for higher bands
	double [][] pha = new double[bands-1][phases.length];
	for (int b=1; b<bands; b++)
	for (int p=0; p<phases.length; p++)
	    pha[b-1][p] = phases[p]*b;

	// create separation matrix
	MatrixComplex  SpM = createSeparationMatrix( pha, bands, fac );

	// some debug output
	String lg = "Band sep. with phases";
	for (double i: phases) lg+=String.format(" %3.2f", i/Math.PI*180);
	Tool.trace(lg);

	// zero output vector
	for ( int b=0; b<(bands*2-1); b++)
	     out[b].zero();

	// multiply, output 0 .. bands*2-1
	for ( int p=0; p<phases.length; p++) 
	for ( int b=0; b<(bands*2-1); b++)
	    out[b].axpy( SpM.get(b,p).toFlt() , in[p] );

    }


    /** Compute the spectral separation. This creates the matrix
     *  first (see {@link #createSeparationMatrix}),
     *  then multiplies the spectra. Phases assumed equi-distant,
     *  with a global offset 
     *  <p>
     *  Assumes as many phases as there are entries in 'in',
     *  sets these phases equidistant
     *
     *	@param in     FFT'd input images, array size: #images = #phases/#bands
     *	@param out    Band-separated output
     *  @param phaOff Per-band phase offset
     *  @param bands  Number of bands, convention: 2 for 2beam, 3 for 3beam data
     *  @param fac    Scaling factors, one for each band 
     *
     *  */
    static public void separateBands( Vec.Cplx [] in, Vec.Cplx [] out, 
	final double  phaOff, final int bands, double [] fac ) {

	// construct phases
	//int phaPerBand =  (bands*2)-1;
	int phaPerBand =  in.length;
	double [] phases = new double[ phaPerBand ];
	
	for (int p=0;p<phaPerBand;p++)
	    phases[p] = (2*Math.PI*p)/phaPerBand + phaOff;

	// call into more general function
	separateBands( in, out, phases, bands, fac );

    }
}
