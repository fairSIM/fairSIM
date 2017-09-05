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

/** Sim parameter estimations through correlations. */
public class Correlation3d {


    /** Fits SIM parameters by cross-correlation of common frequency components.
     *  Correlates band0 vs. band1, with band1 shifted to kx,ky. 
     *	
     *	@param band0 Low freq band to correlate to (that does not change position)
     *	@param band1 High freq band to correlate 
     *	@param bn0  Number of the low band (usually 0)
     *	@param bn1  Number of the high band (usually 1 or 2)
     *	@param otf   Otf to use as weight
     *	@param kx Starting guess kx
     *	@param ky Starting guess ky
     *  @param weightLimit If > 0, consider only freq where both weights are over this limit
     *  @param search kx,ky will be varied +-search
     *  @param projectForSearch if a 2D projection is used for the sub-pixel search
     *	@param cntrl control vector (size 30x10) to be filled with power spectra, may be null
     * */
    public static double [] fitPeak( Vec3d.Cplx band0, Vec3d.Cplx band1,
	int bn0, int bn1, OtfProvider3D otf, 
	double kx, double ky, double weightLimit, 
	double search, final boolean projectForSearch, Vec2d.Real cntrl
	) {

	//Vec2d.failSize( band0, band1 ); // TODO: re-enable
	Tool.Timer t1 = Tool.getTimer();
	double resPhase =0, resMag =0 ;

	// loop iterations, each iteration does closer search
	for ( int iter=0; iter<3; iter++ ) {

	    Tool.tell("fitting peak "+(iter+1)+"/3");

	    // copy input data
	    final Vec3d.Cplx b0 = band0.duplicate();
	    final Vec3d.Cplx b1 = band1.duplicate();

	    // define common region, with current search guess
	    commonRegion( b0, b1, bn0, bn1, otf, kx, ky, 0.15, weightLimit, true);
	    
	    // go to real space
	    b0.fft3d( true );
	    b1.fft3d( true );

	    // store all correlations
	    final Cplx.Double [][] corr = new Cplx.Double[10][10];
	    final Cplx.Double scal = new Cplx.Double(1. /  b0.norm2()  ); // for corr. normalization
	    double max=0,min=Double.MAX_VALUE; 
	    double newKx=0, newKy=0;

	    Tool.trace(String.format("Peak, coarse search %2d: kx [%6.3f -- %6.3f] ky [%6.3f -- %6.3f]",
		iter, (kx - search), (kx + search), (ky - search), (ky + search)));

	    // loop 10x10 points +-search around starting guess
	    // ( good outer loop to do in parallel )
	    final double tkx=kx, tky=ky, ts=search;
	    if ( !projectForSearch ) {
		
		// this version runs on full 3D vectors
		new SimpleMT.PFor(0,10*10) { 
		    public void at(int p) {
		   
			// compute position to shift to
			int xi = p%10;
			int yi = p/10;
			double xpos = tkx + ((xi-4.5)/4.5)*ts;
			double ypos = tky + ((yi-4.5)/4.5)*ts;
	    
			// copy and Fourier-shift band1
			Vec3d.Cplx b1s = b1.duplicate();
			b1s.fourierShift( xpos, -ypos, 0);

			// get correlation by multiplication, summing elements, scaling by b0
			b1s.timesConj( b0 );
			corr[xi][yi] = Cplx.mult( b1s.sumElements(), scal);
			//Tool.trace("3D full: Correlation x "+xi+" y "+yi+" : "+corr[xi][yi].abs());
		    }
		};
	    } else {

		// this version projects down the vectors first
		final Vec2d.Cplx b0proj = Vec2d.createCplx( b0.vectorWidth(), b0.vectorHeight() );
		final Vec2d.Cplx b1proj = Vec2d.createCplx( b1.vectorWidth(), b1.vectorHeight() );
		b0proj.project( b0 );
		b1proj.project( b1 );
	    
		final Cplx.Double scal2d = 
		    new Cplx.Double(1. /  b0proj.norm2()  ); // for corr. normalization
	  
		Tool.trace(" 2d norm: "+ scal2d.abs() );
		
		// this version runs on full 3D vectors
		new SimpleMT.PFor(0,10*10) { 
		    public void at(int p) {
		   
			// compute position to shift to
			int xi = p%10;
			int yi = p/10;
			double xpos = tkx + ((xi-4.5)/4.5)*ts;
			double ypos = tky + ((yi-4.5)/4.5)*ts;
	    
			// copy and Fourier-shift band1
			Vec2d.Cplx b1copy = b1proj.duplicate();
			b1copy.fourierShift( xpos, -ypos );

			// get correlation by multiplication, summing elements, scaling by b0
			b1copy.timesConj( b0proj );
			corr[xi][yi] = Cplx.mult( b1copy.sumElements(), scal2d);
			//Tool.trace("2D proj: Correlation x "+xi+" y "+yi+" : "+corr[xi][yi].abs());
		    }
		};


	    }


	    // find the maximum, set as new starting point
	    for ( int yi=0;yi<10;yi++ )	
	    for ( int xi=0;xi<10;xi++ ) {
		if (corr[xi][yi].hypot() > max ) {
		    max = corr[xi][yi].hypot();
		    newKx = tkx + ((xi-4.5)/4.5)*ts;
		    newKy = tky + ((yi-4.5)/4.5)*ts;
		    resPhase = corr[xi][yi].phase();
		    resMag   = corr[xi][yi].hypot();
		}
	    
		if (corr[xi][yi].abs() < min )
		    min = corr[xi][yi].hypot();
	    
	    };
	    
	    // output to the control vector
	    if (cntrl!=null) 
		for ( int yi=0;yi<10;yi++ )	
		for ( int xi=0;xi<10;xi++ ) {
		    cntrl.set( xi+iter*10, yi, 
			(float) ( (corr[xi][yi].hypot()-min)/(max-min) ) );
	    }

	    // set new guess
	    kx=newKx; ky=newKy;    
	    Tool.trace(String.format("Peak, new kx,ky now: %6.3f %6.3f (%6.3f) pha %6.3f mag %5.4e",
		kx,ky,Math.hypot(ky,kx), resPhase, resMag));
	    search/=5;
	}

	t1.stop();
	Tool.trace("Peak: Correlation fit done: "+t1);

	return new double [] {kx,ky, resPhase, resMag};

    }

    
    /** Locates position, magnitute and phase of the highest peak
     *  in 'vec'.
     *  @param vec input vector (typ. cross-/auto-correlation)
     *  @param kMin Mininum distance from DC component, in pxl
     *  @return px, py, mag, phase */ 
    public static double [] locatePeak( Vec3d.Cplx vec, double kMin ) {
	final int w=vec.vectorWidth(), h=vec.vectorHeight(), d=vec.vectorDepth();
	double xPos=-0, yPos=-1, max = -1, phase=0;

	for (int y=0; y<h; y++)
	for (int x=0; x<w; x++) {
	    // distance to DC component
	    double rad = Math.sqrt( 
		((x<w/2)?(x*x):((x-w)*(x-w))) + ((y<h/2)?(y*y):((y-h)*(y-h)))  );
	    if ((rad>kMin)&&( vec.get(x,y,0).abs() > max )) {
		max = vec.get(x,y,0).abs();
		xPos = x; yPos=y; phase = vec.get(x,y,0).phase();
	    }
	}
	// convert to our coordinate convention
	if (xPos>w/2) xPos=xPos-w;
	if (yPos>h/2) {
	    yPos=h-yPos;
	} else {
	    yPos*=-1;
	}

	return new double [] { xPos, yPos, max, phase };
    }

    
    /** Get the cross-correlation of two bands. This fourier-shifts b1 to kx,ky, and
     *  only takes into account regions where both OTFs are over weightLimit 
     *  (Also, a minimum distance of 0.15*hypot(kx,ky) is ensured to the bands centers).
     *  Result is scaled by |band0|^2, thus might be used as estimate for modulation.
     *  @param band0 The lower band, will not be moved 
     *  @param band1 The higher band, shifted to kx,ky
     *	@param bn0  Number of the low band (usually 0)
     *	@param bn1  Number of the high band (usually 1 or 2)
     *  @param otf The OTF provider
     *	@param kx x-coordinate of shift
     *	@param ky y-coordinate of shift
     *	@param weightLimit Limit for taking into account a certain frequence
     *  */
    public static Cplx.Double getPeak( Vec3d.Cplx band0, Vec3d.Cplx band1, int bn0, int bn1,
	OtfProvider3D otf, double kx, double ky, double weightLimit ) {

	// copy input data
	//Vec2d.failSize( band0, band1 );   // TODO: re-enable
	Vec3d.Cplx b0 = band0.duplicate();
	Vec3d.Cplx b1 = band1.duplicate();
   
	// define common freq. region
	commonRegion( b0, b1, bn0, bn1, otf, kx,ky, 0.15, weightLimit, true );

	// go to real space
	b0.fft3d( true );
	b1.fft3d( true );
    
	// Fourier-shift band1 to correct position
	b1.fourierShift( kx, -ky, 0 ); 
	
	// mult b0 to b1
	b1.timesConj( b0 );

	// scale by |band2|^2
	Cplx.Double scal = new Cplx.Double(1. /  b0.norm2()  );
	Cplx.Double ret = Cplx.mult( b1.sumElements() , scal );

	return ret;
    }
    
    
   
    /** Determines a common freq region of band0, band1.
     *  All frequencies where either OTF is below threshhold 'weightLimit'
     *  are set to zero in both bands. All others are divided by weight (if switch is set). 
     *  @param band0 The lower band, will not be moved 
     *  @param band1 The higher band, shifted to kx,ky
     *	@param bn0  Number of the low band (usually 0)
     *	@param bn1  Number of the high band (usually 1 or 2)
     *  @param otf The OTF provider
     *	@param kx x-coordinate of shift
     *	@param ky y-coordinate of shift
     *	@param dist Minimal distance (as fraction of hypot(kx,ky)) to band centers (std use 0.15)
     *	@param weightLimit Components where either OTF is below this limit are set to 0
     *	@param divideByOtf If set, all components over weightLimit are divided by OTF weight
     *  */
    public static void commonRegion( Vec3d.Cplx band0, Vec3d.Cplx band1,
	int bn0, int bn1, OtfProvider3D otf, final double kx, final double ky,
	double dist, double weightLimit, boolean divideByOtf ) {

	// TODO: re-enable next line
	Vec3d.failSize( band0, band1 );
	final int w = band0.vectorWidth(), h = band0.vectorHeight(), d=band0.vectorDepth();

	// retrive the OTFs as vectors
	Vec3d.Cplx weight0 = Vec3d.createCplx(w,h,d);
	Vec3d.Cplx weight1 = Vec3d.createCplx(w,h,d);
	Vec3d.Cplx wt0 = Vec3d.createCplx(w,h,d);	// transposed OTFs
	Vec3d.Cplx wt1 = Vec3d.createCplx(w,h,d);
	otf.writeOtfVector( weight0, bn0, 0, 0);
	otf.writeOtfVector( weight1, bn1, 0, 0);
	otf.writeOtfVector( wt0, bn0, kx, ky);
	otf.writeOtfVector( wt1, bn1,-kx,-ky);

	int cutCount =0;

	// place the weight1 at its presumed position (currently, to pxl. prec)
	for (int z=0; z<d; z++)
	for (int y=0; y<h; y++)
	for (int x=0; x<w; x++) {
	    
	    // distance to DC component // TOOO: This really needs to be in 3d :)
	    double rad = Math.sqrt( 
		  ((x<w/2)?(x*x):((x-w)*(x-w))) 
		+ ((y<h/2)?(y*y):((y-h)*(y-h)))  );
	    
	    double max = Math.sqrt( kx*kx+ ky*ky ); 

	    double ratio = rad / max;

	    // set zero if minimal weight not reached in one or both OTFs
	    if (    (weight0.get(x,y,z).abs() < weightLimit) 
		    || ( wt0.get(x,y,z).abs() < weightLimit) ) {
		cutCount++;
		band0.set(x,y,z, Cplx.Float.zero());
	    } else {
		if (divideByOtf)
		band0.set(x,y,z, band0.get(x,y,z).div( weight0.get(x,y,z)));
	    }
	    if (    (weight1.get(x,y,z).abs() < weightLimit ) 
		    || ( wt1.get(x,y,z).abs() < weightLimit)) { 
		band1.set(x,y,z, Cplx.Float.zero());
	    } else {
		if (divideByOtf)
		band1.set(x,y,z, band1.get(x,y,z).div( weight1.get(x,y,z)));
	    }
	   
	    /*
	    // set zero around DC component
	    // TODO: the 'z>2' avoids all axial contribution (fine for band 0<>2, but has to be fixed!)
	    if ((ratio<dist )||(ratio>(1-dist)|| (z>2))) {
		band0.set(x,y,z, Cplx.Float.zero());
		band1.set(
		    (x-(int)kx+w)%w,
		    (y+(int)ky+h)%h,
		    z,
		    Cplx.Float.zero());
		cutCount++;
	    } */
	}
	
	Tool.trace("Cuts: "+cutCount+"/"+(w*h*d)+" --> "+(float)cutCount/(w*h*d));

    }


    /** Computes the autocorrelation of inV at kx, ky. Used for Wickers 
     *  non-iterative phase determination. 
     *  */
    public static Cplx.Double autoCorrelation( Vec2d.Cplx inV, 
	double kx, double ky ) {

	final int N = Vec2d.checkSquare(inV) *2; 

	// double the vector size to allow a good shift
	Vec2d.Cplx aV = Vec2d.createCplx(inV,2);
	Vec2d.Cplx bV = Vec2d.createCplx(inV,2);
	SimUtils.placeFreq( inV, aV);
	SimUtils.placeFreq( inV, bV);

	// move one copy to its new position kx, ky
	Transforms.fft2d( bV, true  );
	Transforms.timesShiftVector( bV, kx, -ky);
	Transforms.fft2d( bV, false );

	// compute the auto-correlation
	Cplx.Double ret = Cplx.Double.zero();
	for (int y=0; y<N; y++)
	for (int x=0; x<N; x++) {
	    Cplx.Double a = aV.get( x,y ).toDbl(); 
	    Cplx.Double b = bV.get( x,y ).toDbl();
	    ret = Cplx.add( ret, Cplx.mult( a , b.conj() ));
	}

	// normalize (?)
	//Tool.trace("Norm1 "+ret.hypot());
	ret = ret.div( aV.norm2());
	//Tool.trace("Norm2 "+ret.hypot());

	return ret;
    }


}

