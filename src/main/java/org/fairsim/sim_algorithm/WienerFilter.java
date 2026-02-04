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
import org.fairsim.utils.SimpleMT;

/** Wiener filter implementation */
public class WienerFilter {

    // our sim parameters and otf
    private final SimParam sp;

    /** Create a Wiener filter for the parameters and OTF in 'sp' */
    public WienerFilter( SimParam sp )  {
	this.sp = sp;
	if (sp.otf()==null)
	    throw new IllegalArgumentException("No OTF set in SimParam");
	updateCache();
    }

    // cache for denominator values
    private Vec2d.Real wDenom = null;

    /** Add OTF^2, for band and direction, to a vector.
     * @param d Direction
     * @param b Band
     * @param useAtt Include attenuation */
    public void addWienerDenominator( final Vec2d.Real vec, 
	final int d, final int b, final boolean useAtt ) {
	
	// parameters
	final int w = vec.vectorWidth(), h = vec.vectorHeight();
	final SimParam.Dir dir = sp.dir(d);  
	final double cyclMicron = sp.pxlSizeCyclesMicron();

	// loop the vector x,y
	new SimpleMT.PFor(0, h) {
	    public void at(int y) {
		for (int x=0; x<w; x++) {
		    
		    // wrap to coordinates: x in [-w,w], y in [-h, h]
		    double xh = (x<w/2)?( x):(x-w);
		    double yh = (y<h/2)?(-y):(h-y);
		    
		    // from these, calculate distance to +-(kx,ky), convert to cycl/microns
		    double rad1 = MTool.fhypot( xh-dir.px(b), yh-dir.py(b) ) * cyclMicron;
		    double rad2 = MTool.fhypot( xh+dir.px(b), yh+dir.py(b) ) * cyclMicron;
		    
		    // get OTF, at that distance, for that band, un-attenuated
		    float otfVal1 = sp.otf().getOtfVal(b, rad1, false).absSq();
		    float otfVal2 = sp.otf().getOtfVal(b, rad2, false).absSq();
		    
		    // if attenuate, do so
		    if ( useAtt ) {
			otfVal1 *= sp.otf().getAttVal( b, rad1 ) ;
			otfVal2 *= sp.otf().getAttVal( b, rad2 ) ;
		    }
		    
		    // store for Wiener denominator
		    vec.set( x,y, vec.get(x,y) + otfVal1 + otfVal2 );

		}
	    }
	};
    }


    /** Setup the Wiener filter. This (re)initiates the cached
     *  filter values. Has to be called if the OTF or the shift parameters change.*/
    public void updateCache() {

	Tool.Timer t1 = Tool.getTimer();
	final int w = sp.vectorWidth(), h = sp.vectorHeight();

	t1.start();
	wDenom   = Vec2d.createReal(2*w, 2*h); 
	boolean useAtt = sp.otf().isAttenuate();

	// loop directions, bands
	for (int d=0; d<sp.nrDir(); d++) { 
	    for (int b=0; b< sp.dir(d).nrBand(); b++) {
		addWienerDenominator(wDenom, d,b, useAtt);
	    }
	}
	t1.stop();

	Tool.trace("Wiener filter setup complete, took "+t1);
    }
    
    
    // ------ Convenience functions to obtain denominators ------

    /** Returns reciproc Wiener denominator.
     *  'Reciproc' means the vector can directly be multiplied to spectrum. 
     *	@param wParam Wiener filter parameter
     *  */
    public Vec2d.Real getDenominator(double wParam) {
	Vec2d.Real ret = wDenom.duplicate();
	final int w = ret.vectorWidth(), h = ret.vectorHeight();
	for (int y=0; y<h; y++)
	for (int x=0; x<w; x++)
	    ret.set(x, y, 1/( ret.get(x,y) + (float)(wParam*wParam) ) );
	return ret;
    }
    
    
    /** Returns a denominator for filtering the wide-field,
     *  OTF band 0 with no attenuation.
     *	@param wParam Wiener filter parameter
     *  */
    public Vec2d.Real getWidefieldDenominator(double wParam) {

	Vec2d.Real ret = Vec2d.createReal( sp, 2);
	addWienerDenominator( ret, 0,0, false );
	
	final int w = ret.vectorWidth(), h = ret.vectorHeight();
	for (int y=0; y<h; y++)
	for (int x=0; x<w; x++)
	    ret.set(x, y, 1/( ret.get(x,y) + (float)(wParam*wParam) ) );
	return ret;
    }
 
    /** Returns a copy of a per-direction Wiener denominator, with all bands. 
     *  This is used mostly for filtering intermediate results. 
     *	@param d Direction
     *	@param wParam Wiener filter parameter
     *  */
    public Vec2d.Real getIntermediateDenominator(int d, double wParam) {

	// get the otf
	Vec2d.Real ret = Vec2d.createReal( sp, 2);
	for (int b=0; b<sp.dir(d).nrBand(); b++)
	    addWienerDenominator( ret, d, b, sp.otf().isAttenuate() );

	// add the wiener parameter
	final int w = ret.vectorWidth(), h = ret.vectorHeight();
	for (int y=0; y<h; y++)
	for (int x=0; x<w; x++)
	    ret.set(x, y, 1/( ret.get(x,y) + (float)(wParam*wParam) ) );
	return ret;
    }

    /** Returns a copy of a per-band, per-direction Wiener denominator.
     *  This is used mostly for filtering intermediate results. 
     *	@param d Direction
     *	@param b Band
     *	@param wParam Wiener filter parameter
     *  */
    public Vec2d.Real getIntermediateDenominator(int d, int b, double wParam) {

	// get the otf
	Vec2d.Real ret = Vec2d.createReal( sp, 2);
	addWienerDenominator( ret, d, b, sp.otf().isAttenuate() );

	// add the wiener parameter
	final int w = ret.vectorWidth(), h = ret.vectorHeight();
	for (int y=0; y<h; y++)
	for (int x=0; x<w; x++)
	    ret.set(x, y, 1/( ret.get(x,y) + (float)(wParam*wParam) ) );
	return ret;
    }

    
    // ------ mask function -----

    /** Set components not covered by OTF to zero.
     *	Cleares "spilled" frequency components after Fourier shift.
     *  @param vec The vector to multiply with the conj. OTF
     *  @param d The pattern direction
     *  @param comp The component, band*2 for pos, band*2-1 for neg shift.
     * */
    @Deprecated
    public void maskOtf(final Vec2d.Cplx vec, int d, int comp) {

	final int w = vec.vectorWidth(), h = vec.vectorHeight();
	final int b = (comp+1)/2; // band from component
	final boolean neg = (comp%2==1); // negative shift
	final SimParam.Dir dir = sp.dir(d);
	
	if (neg)	
	    sp.otf().maskOtf( vec, -dir.px(b), -dir.py(b) );
	else
	    sp.otf().maskOtf( vec,  dir.px(b),  dir.py(b) );
    }


    /** For testing */
    public static void main( String [] args ) {

	OtfProvider otf = OtfProvider.fromEstimate(1.4, 515, 0.35);
	SimParam param   = SimParam.create(3, 3, 5, 512, 0.086, otf);
	SimParam param2  = SimParam.create(3, 5, 5, 1024, 0.086, otf);

	WienerFilter wf = new WienerFilter( param );
	
	Tool.trace("Setup w/o attenuation");
	wf.updateCache();

	Tool.trace("Setup with attenuation");
	otf.setAttenuation( .99, 1.5);
	otf.switchAttenuation(true);
	wf.updateCache();
	
	WienerFilter wf2 = new WienerFilter( param2 );
	Tool.trace("Setup big Zeiss (3x5x5 @ 1024x1024)");
	wf2.updateCache();
	    
	    
	Tool.shutdown();
    
    
    }




}
