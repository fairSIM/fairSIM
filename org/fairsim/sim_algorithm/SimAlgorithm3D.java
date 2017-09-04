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
import org.fairsim.fiji.ImageVector;
import org.fairsim.fiji.DisplayWrapper;
import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;
import org.fairsim.utils.ImageDisplay;

public class SimAlgorithm3D {

   /** Step-by-step reconstruction process. */
    public static Vec3d.Cplx runReconstruction( Vec2d.Real [] inSt, 
	final OtfProvider3D otfPr, final SimParam param,
	final int visualFeedback , final double wienParam,
	final int fitLevel ) {

	final boolean disableFiltering = false;
	final boolean   findPeak  = (fitLevel>=3);
	final boolean refinePeak  = (fitLevel>=2);
	final boolean refinePhase = (fitLevel>=1);
	final boolean doTheReconstruction  = true;
	final boolean otfBeforeShift  = true;

	Tool.trace(" find peak: "+findPeak+", refine peak: "+refinePeak+", refine phase: "+refinePhase);

	// ----- Parameters -----
	final int w=inSt[0].vectorWidth(), h=inSt[0].vectorHeight();
	final int d = inSt.length / (param.getImgPerZ() );

	final int nrDirs  =param.nrDir();
	final int nrPhases  =param.dir(0).nrPha();

	// ---------------------------------------------------------------------

	// Various timers
	Tool.Timer tAll  = Tool.getTimer();	// everything
	Tool.Timer tEst  = Tool.getTimer();	// parameter estimation
	Tool.Timer tPha  = Tool.getTimer();	// phase-by-autocorrelation
	Tool.Timer tWien = Tool.getTimer();	// Wiener filter setup
	Tool.Timer tRec  = Tool.getTimer();	// Reconstruction

	tAll.start();
	
	// Output displays to show the intermediate results
	/*
	ImageDisplay pwSt  = new DisplayWrapper(w,h, "Power Spectra" );
	ImageDisplay spSt  = new DisplayWrapper(w,h, "Spatial images");
	ImageDisplay pwSt2 = new DisplayWrapper(2*w,2*h, "Power Spectra" );
	ImageDisplay spSt2 = new DisplayWrapper(2*w,2*h, "Spatial images");
	*/


	// Copy current stack into vectors, apotize borders, run fft 
	Vec3d.Cplx inFFT[][] = new Vec3d.Cplx[ nrDirs ][ nrPhases ];

	Tool.trace(String.format("Running with dimensions %d x %d x %d, a:%d p:%d",
	    w,h,d,nrDirs,nrPhases));

	Tool.trace("FFT'in input data");	
	for (int a=0; a<nrDirs; a++) 
	for (int p=0; p<nrPhases; p++) {
	    
	    inFFT[a][p] = Vec3d.createCplx(w,h,d);

	    for (int z=0; z<d; z++) {
		int pos = p + 5 * z + a * 5 * d; // TODO: Use SimUtils here?
		Vec2d.Real img = inSt[pos].duplicate();
		SimUtils.fadeBorderCos( img , 10);
		inFFT[a][p].setSlice( z, img );
	    }

	    // input FFT
	    inFFT[a][p].fft3d(false);
	    //Tool.trace(String.format("Input FFT a: %d p: %d",a,p));	
	}


	// vectors to store the result
	Vec3d.Cplx fullResult    = Vec3d.createCplx( w*2,h*2,d);


	// ---------------------------------------------------------------------
	// extract the SIM parameters by cross-correlation analysis
	// ---------------------------------------------------------------------
	
	tEst.start();
	if ( refinePhase ) {
	    
	    // loop through pattern directions
	    for (int angIdx=0; angIdx<param.nrDir(); angIdx++) {
	    
		final SimParam.Dir dir = param.dir(angIdx);

		// idx of low band (phase detection) and high band (shift vector detection)
		// will be the same for two-beam TODO: this only works for 3-beam data!
		final int lb = 1;
		final int hb = 3; //(param.dir(angIdx).nrBand()==3)?(3):(1);

		// compute band separation
		Vec3d.Cplx [] separate = Vec3d.createArrayCplx( dir.nrComp(), w, h, d);
		BandSeparation.separateBands( inFFT[angIdx], separate , 
		    0, dir.nrBand(), null);

		// duplicate vectors, as they are modified for coarse correlation
		Vec3d.Cplx c0 = separate[0].duplicate();
		Vec3d.Cplx c1 = separate[lb].duplicate();
		Vec3d.Cplx c2 = separate[hb].duplicate();

		Vec3d.Cplx otf = Vec3d.createCplx( w,h,d);
		
		otfPr.writeOtfVector( otf, 0, 0, 0);
		otf.addConst( new Cplx.Float(0.01f,0));
		otf.reciproc();
		c0.times( otf );
		
		otfPr.writeOtfVector( otf, 1, 0, 0);
		otf.addConst( new Cplx.Float(0.01f,0));
		otf.reciproc();
		c1.times( otf );
		
		otfPr.writeOtfVector( otf, 2, 0, 0);
		otf.addConst( new Cplx.Float(0.01f,0));
		otf.reciproc();
		c2.times( otf );

		// dampen region around DC // TODO: is this needed for 3D?
		//c0.times( otfAtt );
		//c1.times( otfAtt );
		//c2.times( otfAtt ); 
		
		// compute correlation: ifft, mult. in spatial, fft back
		c0.fft3d( true);
		c1.fft3d( true);
		c2.fft3d( true);
		c1.timesConj( c0 );
		c2.timesConj( c0 );
		c1.fft3d( false);
		c2.fft3d( false);
	
		// find the highest peak in corr of band0 to highest band 
		// with min dist 0.5*otfCutoff from origin, store in 'param'
		double minDist = .5 * otfPr.getCutoff() / param.pxlSizeCyclesMicron();
	
		Vec2d.Cplx pl = Vec2d.createCplx(w,h);

		// get preset k0 vector value TODO: this only works for 3-band data
		double [] peak = new double [] { param.dir(angIdx).px(2), param.dir(angIdx).py(2) };
		double [] origPeak = new double [] { param.dir(angIdx).px(2), param.dir(angIdx).py(2) };
		
		// 1 - only run "find peak" if set so
		if (findPeak) {	
		    peak = Correlation3d.locatePeak(  c2 , minDist );
		    Tool.trace(String.format("a%1d: LocatePeak (min %4.0f) --> Peak at x %7.3f y %7.3f",
			angIdx, minDist, peak[0], peak[1]));
		} else {
		    Tool.trace(String.format("a%1d: Using preset coarse peak pos. at x %7.3f y %7.3f",
			angIdx, minDist, peak[0], peak[1]));
		}
		
		// fit the peak to sub-pixel precision by cross-correlation of
		// Fourier-shifted components
		if ( refinePeak ) {
		    ImageVector cntrl    = ImageVector.create(30,10);
		    peak = Correlation3d.fitPeak( separate[0], separate[hb], 0, 2, otfPr,
			-peak[0], -peak[1], 0.005, 2.5, cntrl );
		    Tool.trace(String.format("a%1d: refined peak position to %7.3f y %7.3f",
			angIdx, minDist, peak[0], peak[1]));
		
		    double peakDist = Math.hypot( peak[0] + origPeak[0], peak[1] + origPeak[1]);
		    Tool.trace(String.format("a%1d: peak offset from stores preset: %7.3f ",
			angIdx, peakDist));
		} else {
		    Tool.trace(String.format("a%1d: not refining peak position",angIdx));
		}

		// Now, either three beam / 3 bands ...
		if (lb!=hb) {

		    // At the peak position found, extract phase and modulation from band0 <-> band 1
		    // TODO: Using the band2 OTF is wrong....
		    Cplx.Double p1 = Correlation3d.getPeak( separate[0], separate[lb], 
			0, 2, otfPr, peak[0]/2, peak[1]/2, 0.0075 );

		    // Extract modulation from band0 <-> band 2
		    Cplx.Double p2 = Correlation3d.getPeak( separate[0], separate[hb], 
			0, 2, otfPr, peak[0], peak[1], 0.005 );

		    Tool.trace(
			String.format("a%1d: peak and phase --> x %7.3f y %7.3f p %7.3f (m %7.3f, %7.3f)", 
			angIdx, peak[0], peak[1], p1.phase(), p1.hypot(), p2.hypot() ));
	    
		    // store the result
		    param.dir(angIdx).setPxPy(   -peak[0], -peak[1] );
		    param.dir(angIdx).setPhaOff( p1.phase() );
		    param.dir(angIdx).setModulation( 1, p1.hypot() );
		    param.dir(angIdx).setModulation( 2, p2.hypot() );
		}
		
		// ... or two-beam / 2 bands
		if (lb==hb) {
		    // get everything from one correlation band0 to band1
		    Cplx.Double p1 = Correlation3d.getPeak( separate[0], separate[1], 
			0, 1, otfPr, peak[0], peak[1], 0.05 );

		    Tool.trace(
			String.format("a%1d: FitPeak --> x %7.3f y %7.3f p %7.3f (m %7.3f)", 
			angIdx, peak[0], peak[1], p1.phase(), p1.hypot() ));
	    
		    // store the result
		    param.dir(angIdx).setPxPy(   -peak[0], -peak[1] );
		    param.dir(angIdx).setPhaOff( p1.phase() );
		    param.dir(angIdx).setModulation( 1, p1.hypot() );
		}


		// --- output visual feedback of peak fit ---
		/*
		if (visualFeedback>0) {
		    
		    // mark region excluded from peak finder
		    // output the peaks found, with circles marking them, and the fit result in
		    // the top corner for the correlation band0<->band2
		    ImageDisplay.Marker excludedDC = 
			new ImageDisplay.Marker(w/2,h/2,minDist*2,minDist*2,true);
		    
		    Vec2d.Real fittedPeak = SimUtils.pwSpec( c2 );
		    fittedPeak.paste( cntrl, 0, 0, false );
		   
		    
		    pwSt.addImage( fittedPeak, "dir "+angIdx+" c-corr band 0<>high" );
		    pwSt.addImage( SimUtils.pwSpec( c1 ), "dir "+angIdx+" c-corr band 0<>low");
		    
		    pwSt.addImage( fittedPeak, "dir "+angIdx+" c-corr band 0<>high",
		    	new ImageDisplay.Marker( w/2-peak[0], h/2+peak[1], 10, 10, true),
		    	excludedDC);
		    
		    // if there is a low band, also add it
		    if ((visualFeedback>1)&&(lb!=hb))
			pwSt.addImage( SimUtils.pwSpec( c1 ), "dir "+angIdx+" c-corr band 0<>low",
			    new ImageDisplay.Marker( w/2-peak[0]/2, h/2+peak[1]/2, 10, 10, true));
		} 
		*/  

		// --- output visual feedback of overlapping regions (for all bands) ---
		/*
		if (visualFeedback>1)  
		for (int b=1; b<param.nrBand(); b++) {	
		
		    SimParam.Dir par = param.dir(angIdx);

		    // find common regions in low and high band
		    Vec3d.Cplx b0 = separate[0  ].duplicate();
		    Vec3d.Cplx b1 = separate[2*b].duplicate();
		
		    Correlation3d.commonRegion( b0, b1, 0, b, otfPr,  
		        par.px(b), par.py(b), 0.15, (b==1)?(.2):(.05), true);

		    // move the high band to its correct position
		    b1.fft3d( true );
		    b1.fourierShift( par.px(b), -par.py(b),0 );
		    b1.fft3d( false );
	    
		    // apply phase correction
		    b1.scal( Cplx.Float.fromPhase( param.dir(angIdx).getPhaOff()*b ));
	   
		    // output the full shifted bands
		    if ( visualFeedback>2 )  {
			// only add band0 once	
			if ( b==1 ) {
			    Vec3d.Cplx btmp = separate[0].duplicate();
			    //otfPr2D.maskOtf( btmp, 0, 0);
			    pwSt.addImage(SimUtils.pwSpec( btmp ), String.format(
				"a%1d: full band0", angIdx, b ));
			}

			// add band1, band2, ...
			Vec3d.Cplx btmp = separate[2*b].duplicate();
			btmp.fft3d( true );
			btmp.fourierShift(par.px(b), -par.py(b),0 );
			btmp.fft3d( false );
			//otfPr2D.maskOtf( btmp, par.px(b), par.py(b));

			pwSt.addImage(SimUtils.pwSpec( btmp ), String.format( 
			    "a%1d: full band%1d (shifted %7.3f %7.3f)",
			    angIdx,  b, par.px(b), par.py(b))); 
		    }

		    // output power spectra of common region
		    pwSt.addImage(SimUtils.pwSpec( b0 ), String.format(
			"a%1d: common region b0<>b%1d, band0", angIdx, b )); 
		    pwSt.addImage(SimUtils.pwSpec( b1 ), String.format( 
			"a%1d: common region b0<>b%1d, band%1d",angIdx, b, b)); 

		    // output spatial representation of common region
		    spSt.addImage(SimUtils.spatial( b0 ), String.format(
			"a%1d: common region b0<>b%1d, band0", angIdx, b )); 
		    spSt.addImage(SimUtils.spatial( b1 ), String.format( 
			"a%1d: common region b0<>b%1d, band%1d",angIdx, b, b)); 
		}
		*/
	    }
	
	}
	tEst.stop();


	// ---------------------------------------------------------------------
	// refine phase (by auto-correlation, Wicker et. al) 
	// ---------------------------------------------------------------------
	
	tPha.start();
	// TODO: re-enable this on the z-projected stacks
	/*
	if (refinePhase) {

	    // loop all directions
	    for (int angIdx = 0; angIdx < param.nrDir(); angIdx ++ ) {
		final SimParam.Dir par = param.dir(angIdx);
		
		// run Kai Wicker auto-correlation
		double [] pha = new double[par.nrPha()];
		for (int i=0; i < par.nrPha() ; i++) {
		   
		    // copy input, weight with otf
		    Vec2d.Cplx ac =  inFFT[angIdx][i].duplicate();
		    otfPr.applyOtf( ac, 0); 
		    
		    // compute auto-correlation at px,py (shift of band1)
		    Cplx.Double corr = Correlation.autoCorrelation( 
			ac, par.px(1), par.py(1) );

		    Tool.trace(String.format("a%1d img %1d, Phase(Wicker et. al.) : %5.3f  ",
			angIdx, i, corr.phase()));

		    pha[i] = corr.phase();
		}
		par.setPhases( pha, true );	
		
	    }
	} */
	tPha.stop();


	// ---------------------------------------------------------------------
	// Setup the Wiener filter
	// ---------------------------------------------------------------------
	
	tWien.start();
	WienerFilter3d wFilter = new WienerFilter3d( param );
	/*
	if (visualFeedback>0) {
	    Vec3d.Real wd = wFilter.getDenominator(wienParam);
	    for (int z=0; z<7; z++) {
	    
		Vec2d.Real wdpr = Vec2d.createReal(2*w, 2*h);
		wdpr.slice( wd, z );
		wdpr.reciproc();
		wdpr.normalize();
		Transforms.swapQuadrant( wdpr );
		pwSt2.addImage(wdpr, "Wiener denominator, z: "+z);
	    }
	}
	*/
	tWien.stop();
	
	
	// ---------------------------------------------------------------------
	// Run the actual reconstruction 
	// ---------------------------------------------------------------------
    

	tRec.start();	
	// loop all pattern directions
	if ( doTheReconstruction ) {
	    
	    for (int angIdx = 0; angIdx < param.nrDir(); angIdx ++ ) 
	    {
		final SimParam.Dir par = param.dir(angIdx);

		// ----- Band separation & OTF multiplication (if before shift) -------

		Vec3d.Cplx [] separate  = Vec3d.createArrayCplx( par.nrComp(), w, h, d);
		
		//BandSeparation.separateBands( inFFT[angIdx] , separate , 
		//	par.getPhases(), par.nrBand(), par.getModulations());
		
		BandSeparation.separateBands( inFFT[angIdx] , separate , 
			par.getPhases(), par.nrBand(), new double [] {1, 0.8, 0.8} );

		if ( otfBeforeShift && !disableFiltering )
		    for (int i=0; i<(par.nrBand()*2-1) ;i++)  
			otfPr.applyOtf( separate[i], (i+1)/2);

		// ------- Shifts to correct position ----------

		Vec3d.Cplx [] shifted   = Vec3d.createArrayCplx(5, 2*w, 2*h, d);

		// band 0 is DC, so does not need shifting, only a bigger vector
		shifted[0].pasteFreq( separate[0] );
		
		// higher bands need shifting
		for ( int b=1; b<par.nrBand(); b++) {
		    
		    Tool.trace("REC: Dir "+angIdx+": shift band: "+b+" to: "+par.px(b)+" "+par.py(b));
		    
		    // first, copy to larger vectors
		    int pos = b*2, neg = (b*2)-1;	// pos/neg contr. to band
		    shifted[pos].pasteFreq( separate[pos] );
		    shifted[neg].pasteFreq( separate[neg] );

		    // then, fourier shift
		    shifted[pos].fft3d(true);
		    shifted[pos].fourierShift(  par.px(b), -par.py(b), 0 );
		    shifted[pos].fft3d(false);

		    shifted[neg].fft3d(true);
		    shifted[neg].fourierShift( -par.px(b), par.py(b), 0 );
		    shifted[neg].fft3d(false);
		}
	       
		// ------ OTF multiplication or masking ------
		
		// TODO: re-implement OTF mask support for 3D
		
		if (!otfBeforeShift && !disableFiltering) { 
		    // multiply with shifted OTF
		    for (int b=0; b<par.nrBand(); b++) {
			int pos = b*2, neg = (b*2)-1;	// pos/neg contr. to band
			otfPr.applyOtf( shifted[pos], b,  par.px(b),  par.py(b) );
			if (b>0)
			    otfPr.applyOtf( shifted[neg], b, -par.px(b), -par.py(b) );
		    }
		}
		/*
		else {
		    // or mask for OTF support
		    for (int i=0; i<(par.nrBand()*2-1) ;i++)  
			//wFilter.maskOtf( shifted[i], angIdx, i);
			otfPr.maskOtf( shifted[i], angIdx, i);
		} */
		
		// ------ Sum up result ------
		
		for (int i=0;i<par.nrBand()*2-1;i++) { 
		    //if (( i==1 || i==2 ) && ( angIdx==0)) continue;
		    fullResult.add( shifted[i] ); 
		}
		
		// ------ Output intermediate results ------

		/*
		if (visualFeedback>0) {
	    
		    // per-direction results
		    Vec3d.Cplx result = Vec3d.createCplx(2*w,2*h,d);
		    for (int i=0;i<par.nrBand()*2-1;i++)  
			result.add( shifted[i] ); 

		    // loop bands in this direction
		    for (int i=0;i<par.nrBand();i++) {     

			// TODO: re-implement and re-enable this
			// get wiener denominator for (direction, band), add to full denom for this band
			Vec3d.Real denom = wFilter.getIntermediateDenominator( angIdx, i, wienParam);
		    
			// add up +- shift for this band
			Vec3d.Cplx thisband   = shifted[i*2];
			if (i!=0)
			    thisband.add( shifted[i*2-1] );
	    
			// output the wiener denominator
			if (visualFeedback>1) {
			    Vec3d.Real wd = denom.duplicate();
			    wd.reciproc();
			    wd.normalize();
			    
			    Vec2d.Real tmp = Vec2d.createReal( 2*w, 2*h);
			    tmp.project(wd);
			    Transforms.swapQuadrant( tmp );
			    pwSt2.addImage( tmp, String.format(
				"a%1d: OTF/Wiener band %1d",angIdx,(i/2) ));
			}  
			
			// apply filter and output result
			if (!disableFiltering)
			    thisband.times( denom );
			
			pwSt2.addImage( SimUtils.pwSpec( thisband ) ,String.format(
			    "a%1d: band %1d",angIdx,i));
			
			spSt2.addImage( SimUtils.spatial( thisband,5 ) ,String.format(
			    "a%1d: band %1d (slice 5)",angIdx,i));
		    }

		    // per direction wiener denominator	
		    Vec3d.Real fDenom =  wFilter.getIntermediateDenominator( angIdx, wienParam);
		    if (!disableFiltering)
			result.times( fDenom );
			
		    // output the wiener denominator
		    if (visualFeedback>1) {
			Vec2d.Real wd = fDenom.duplicate();
			wd.reciproc();
			wd.normalize();
			Transforms.swapQuadrant( wd );
			pwSt2.addImage( wd, String.format(
			    "a%1d: OTF/Wiener all bands",angIdx ));
		    } 

		    pwSt2.addImage( SimUtils.pwSpec( result ) ,String.format(
			"a%1d: all bands",angIdx));
		    
		    
		    spSt2.addImage( SimUtils.spatial( result,5 ) ,String.format(
		        "a%1d: all band (slice 5)",angIdx));
		
		    // power spectra before shift
		    if (visualFeedback>2) { 
			for (int i=0; i<(par.nrBand()*2-1) ;i++)  
			pwSt.addImage( SimUtils.pwSpec( separate[i] ), String.format(
			    "a%1d, sep%1d, seperated band", angIdx, i));
		    }
	       
		}
		*/


	    }   
	    
	    // -- done loop all pattern directions, 'fullResult' now holds the image --
	    
	    
	    /*if (visualFeedback>0) {
		pwSt2.addImage(  SimUtils.pwSpec( fullResult), "full (w/o APO, WF)");
		for (int i=0; i<7; i++)
		    spSt2.addImage(  SimUtils.spatial(fullResult,i), "full (w/o APO, WF), pl: "+i);
	    } */
	    
	    // multiply by wiener denominator
	    Vec3d.Real denom = wFilter.getDenominator( wienParam );
	    fullResult.times(denom);

	    // apply apotization filter
	    
	    otfPr.apotize( fullResult, 2.0, 1.4, false );
	    
	    /*
	    for (int z=0; z<fullResult.vectorDepth(); z++)
		spSt2.addImage( SimUtils.spatial( fullResult,z), "full result: z"+z);
	    
	    if (visualFeedback>0) {
		pwSt2.addImage( SimUtils.pwSpec( fullResult), "full result");
	    }
	    */

	    // Add wide-field for comparison
	    /*
	    if (visualFeedback>=0) {
		
		// obtain the low freq result
		Vec3d.Cplx lowFreqResult = Vec3d.createCplx(w*2,h*2,d);
		
		// have to do the separation again, result before had the OTF multiplied
		for (int angIdx = 0; angIdx < param.nrDir(); angIdx ++ ) {
		    
		    final SimParam.Dir par = param.dir(angIdx);
		    
		    Vec3d.Cplx [] separate  = Vec3d.createArrayCplx( par.nrComp(), w, h, d);
		    BandSeparation.separateBands( inFFT[angIdx] , separate , 
			par.getPhases(), par.nrBand(), par.getModulations());

		    Vec3d.Cplx tmp  = Vec3d.createCplx( w*2,h*2,d );
		    tmp.pasteFreq( separate[0] );
		    lowFreqResult.add( tmp );
		}	
		
		// now, output the widefield
		if (visualFeedback>0)
		    pwSt2.addImage( SimUtils.pwSpec(lowFreqResult), "Widefield" );
		spSt2.addImage( SimUtils.spatial(lowFreqResult), "Widefield" );
	    
		// otf-multiply and wiener-filter the wide-field
		//otfPr.otfToVector( lowFreqResult, 0, 0, 0, false, false ); 

		Vec3d.Real lfDenom = wFilter.getWidefieldDenominator( wienParam );
		lowFreqResult.times( lfDenom );
		
		//Vec2d.Cplx apoLowFreq = Vec2d.createCplx(2*w,2*h);
		//otfPr.writeApoVector( apoLowFreq, 0.4, 1.2);
		//lowFreqResult.times(apoLowFreq);
		
		if (visualFeedback>0)
		    pwSt2.addImage( SimUtils.pwSpec( lowFreqResult), "filtered Widefield" );
		spSt2.addImage( SimUtils.spatial( lowFreqResult), "filtered Widefield" );

	    }	
	    */
	}
	// stop timers
	tRec.stop();	
	tAll.stop();

	// output parameters
	Tool.trace( "\n"+param.prettyPrint(true));

	// output timings
	Tool.trace(" ---- Timings ---- ");
	if (findPeak)
	Tool.trace(" Parameter estimation / fit:  "+tEst);
	Tool.trace(" Wiener filter creation:      "+tWien);
	Tool.trace(" Reconstruction:              "+tRec);
	Tool.trace(" ---");
	Tool.trace(" All:                         "+tAll);

	// DONE, display all results
	/*
	pwSt.display();
	pwSt2.display();
	spSt.display();
	spSt2.display();
	*/

	return fullResult;

    }

}


