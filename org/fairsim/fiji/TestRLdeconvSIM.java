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

package org.fairsim.fiji;

import org.fairsim.linalg.*;
import org.fairsim.fiji.ImageVector;
import org.fairsim.fiji.DisplayWrapper;
import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;
import org.fairsim.utils.ImageDisplay;
import org.fairsim.sim_algorithm.*;

import ij.plugin.PlugIn;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.process.FloatProcessor;

import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;

/** Small Fiji plugin, running all parameter estimation and reconstruction
 *  steps. Good starting point to look at the code w/o going through all the
 *  GUI components. */
public class TestRLdeconvSIM implements PlugIn {

    /** Global variables */
    boolean showDialog =  false;    // if set, dialog to set parameters is shown at plugin start 
    boolean rlMode     =  true;     // if set, dialog to set parameters is shown at plugin start 

    int nrBands  = 3;		    // #bands (2 - two-beam, 3 - three-beam, ...)
    int nrDirs   = 3;		    // #angles or pattern orientations
    int nrPhases = 5;		    // #phases (at least 2*bands -1 )

    double emWavelen = 560;	    // emission wavelength		    
    double otfNA     = 1.4;	    // NA of objective
    double otfCorr   = 0.31;	    // OTF correction factor
    double pxSize    = 0.080;	    // pixel size (microns)

    double wienParam   = 0.05;	    // Wiener filter parameter
    double attStrength = 0.995;	    // Strength of attenuation
    double attFWHM     = 1.2;	    // FWHM of attenuation (cycles/micron)
    boolean doAttenuation = true;  // use attenuation?

    boolean otfBeforeShift = true;  // multiply the OTF before or after shift to px,py

    boolean findPeak    = true;	    // run localization and fit of shfit vector
    boolean refinePhase = false;    // run auto-correlation phase estimation (Wicker et. al)
	
    final int visualFeedback = 2;   // amount of intermediate results to create (-1,0,1,2,3)
    boolean doFastShift = true;     // use the fast fourier shift impl. or standard

    final double apoB=.9, apoF=2; // Bend and mag. factor of APO

    /** Called by Fiji to start the plugin. 
     *	Uses the currently selected image, does some basic checks
     *	concerning image size.
     * */
    public void run(String arg) {
	// override parameters?
	if (showDialog) {};

	// currently selected stack, some basic checks
	ImageStack inSt = ij.WindowManager.getCurrentImage().getStack();
	final int w=inSt.getWidth(), h=inSt.getHeight();
	if (w!=h) {
	    IJ.showMessage("Image not square (w!=h)");
	    return;
	}
	if (inSt.getSize() != nrPhases*nrDirs ) {
	    IJ.showMessage("Stack length != phases*angles: "+inSt.getSize() );
	    return;
	}
	
	// start the reconstruction
	runReconstruction(inSt);
    }
   

    /** Step-by-step reconstruction process. */
    public void runReconstruction( ImageStack inSt ) {
	
	// ----- Parameters -----
	final int w=inSt.getWidth(), h=inSt.getHeight();

	Conf cfg=null;
	OtfProvider otfPr  = null; 

	/*
	try {
	    cfg   = Conf.loadFile("Desktop/Test.xml");
	    otfPr = OtfProvider.loadFromConfig( cfg );
	} catch (Exception e) {
	    Tool.trace(e.toString());
	    return;
	}*/

	// create the OTF (from estimate, could also load from file...)	
	otfPr  = 
	    OtfProvider.fromEstimate( otfNA, emWavelen, otfCorr );
	
	// Reconstruction parameters: #bands, #directions, #phases, size, microns/pxl, the OTF 
	final SimParam param = 
	    SimParam.create(nrBands, nrDirs, nrPhases, w, pxSize, otfPr);

	// Filter settings: Wiener parameter, attenuation parameters
	otfPr.setAttenuation( attStrength, attFWHM );
	otfPr.switchAttenuation( doAttenuation );
	
	
	// ----- Shift vectors for example data -----
	// (used for reconstruction, or as starting guess if 'locatePeak' is off, but 'findPeak' on)
	
	// green
	if (true) {
	    param.dir(0).setPxPy( 137.44, -140.91); 
	    param.dir(1).setPxPy( -52.8,  -189.5);
	    param.dir(2).setPxPy( 190.08,  49.96);
	}
	// red
	if (false) {
	    param.dir(0).setPxPy( 121.303, -118.94 ); 
	    param.dir(1).setPxPy(  -42.04, -164.68 );
	    param.dir(2).setPxPy(  163.05,   46.81 );
	}

	// ---------------------------------------------------------------------
	// Input / Output to/from Fiji

	// Various timers
	Tool.Timer tAll  = Tool.getTimer();	// everything
	Tool.Timer tEst  = Tool.getTimer();	// parameter estimation
	Tool.Timer tPha  = Tool.getTimer();	// phase-by-autocorrelation
	Tool.Timer tWien = Tool.getTimer();	// Wiener filter setup
	Tool.Timer tRec  = Tool.getTimer();	// Reconstruction

	tAll.start();

	ImageDisplay inputImgSt  = new DisplayWrapper(w,h, "Input Images" );
	
	// Copy current stack into vectors, apotize borders 
	    
	Vec2d.Cplx otfVector = Vec2d.createCplx( param );
	otfPr.writeOtfVector( otfVector, 0, 0, 0  );   // currently only band 0
	
	Vec2d.Real [] imgs = new Vec2d.Real[ inSt.getSize() ]; 
	Vec2d.Cplx tmpVec = Vec2d.createCplx( param );

	for (int i=0; i<inSt.getSize();i++) { 
	    imgs[i]  = ImageVector.copy( inSt.getProcessor(i+1) );  
	    //SimUtils.fadeBorderCos( imgs[i] , 10);

	    inputImgSt.addImage( imgs[i], "i: "+i+" (no deconv)");

	    tmpVec.copy(imgs[i]);
	    RLDeconvolution.deconvolve( tmpVec, otfVector, 10 );
	    imgs[i].copy(tmpVec);
	    inputImgSt.addImage( imgs[i], "i: "+i+" (deconv)");

	}
	
	inputImgSt.display();
	//if (true) return;

	// compute the input FFT
	Vec2d.Cplx [][] inFFT = new Vec2d.Cplx[ inSt.getSize()/nrPhases ][nrPhases];
	for (int i=0; i<inSt.getSize();i++) { 
		inFFT[i/nrPhases][i%nrPhases] = Vec2d.createCplx( w, h);
		inFFT[i/nrPhases][i%nrPhases].copy( imgs[i] );
		Transforms.fft2d( inFFT[i/nrPhases][i%nrPhases] , false);
	}
	
	// vectors to store the result
	Vec2d.Cplx fullResult    = Vec2d.createCplx( param, 2);
    
	// Output displays to show the intermediate results
	ImageDisplay pwSt  = new DisplayWrapper(w,h, "Power Spectra" );
	ImageDisplay spSt  = new DisplayWrapper(w,h, "Spatial images");
	ImageDisplay pwSt2 = new DisplayWrapper(2*w,2*h, "Power Spectra" );
	ImageDisplay spSt2 = new DisplayWrapper(2*w,2*h, "Spatial images");



	// ---------------------------------------------------------------------
	// extract the SIM parameters by cross-correlation analysis
	// ---------------------------------------------------------------------
	
	tEst.start();
	if (findPeak) {
	
	    // The attenuation vector helps well to fade out the DC component,
	    // which is uninteresting for the correlation anyway
	    Vec2d.Real otfAtt = Vec2d.createReal( param );
	    otfPr.writeAttenuationVector( otfAtt, .99, 0.15*otfPr.getCutoff(), 0, 0  ); 
	    
	    // loop through pattern directions
	    for (int angIdx=0; angIdx<param.nrDir(); angIdx++) {
	    
		final SimParam.Dir dir = param.dir(angIdx);

		// idx of low band (phase detection) and high band (shift vector detection)
		// will be the same for two-beam
		final int lb = 1;
		final int hb = (param.dir(angIdx).nrBand()==3)?(3):(1);

		// compute band separation
		Vec2d.Cplx [] separate = Vec2d.createArrayCplx( dir.nrComp(), w, h);
		BandSeparation.separateBands( inFFT[angIdx] , separate , 
		    0, dir.nrBand(), null);

		// duplicate vectors, as they are modified for coarse correlation
		Vec2d.Cplx c0 = separate[0].duplicate();
		Vec2d.Cplx c1 = separate[lb].duplicate();
		Vec2d.Cplx c2 = separate[hb].duplicate();

		// dampen region around DC 
		c0.times( otfAtt );
		c1.times( otfAtt );
		c2.times( otfAtt ); 
		
		// compute correlation: ifft, mult. in spatial, fft back
		Transforms.fft2d( c0, true);
		Transforms.fft2d( c1, true);
		Transforms.fft2d( c2, true);
		c1.timesConj( c0 );
		c2.timesConj( c0 );
		Transforms.fft2d( c1, false);
		Transforms.fft2d( c2, false);
	   
		// find the highest peak in corr of band0 to highest band 
		// with min dist 0.5*otfCutoff from origin, store in 'param'
		double minDist = .5 * otfPr.getCutoff() / param.pxlSizeCyclesMicron();
		double [] peak = Correlation.locatePeak(  c2 , minDist );
		
		Tool.trace(String.format("a%1d: LocPeak (min %4.0f) --> Peak at x %5.0f y %5.0f",
		    angIdx, minDist, peak[0], peak[1]));
		
		// fit the peak to sub-pixel precision by cross-correlation of
		// Fourier-shifted components
		ImageVector cntrl    = ImageVector.create(30,10);
		peak = Correlation.fitPeak( separate[0], separate[hb], 0, 2, otfPr,
		    -peak[0], -peak[1], 0.05, 2.5, cntrl );

		// Now, either three beam / 3 bands ...
		if (lb!=hb) {

		    // At the peak position found, extract phase and modulation from band0 <-> band 1
		    Cplx.Double p1 = Correlation.getPeak( separate[0], separate[lb], 
			0, 1, otfPr, peak[0]/2, peak[1]/2, 0.05 );

		    // Extract modulation from band0 <-> band 2
		    Cplx.Double p2 = Correlation.getPeak( separate[0], separate[hb], 
			0, 2, otfPr, peak[0], peak[1], 0.05 );

		    Tool.trace(
			String.format("a%1d: FitPeak --> x %7.3f y %7.3f p %7.3f (m %7.3f, %7.3f)", 
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
		    Cplx.Double p1 = Correlation.getPeak( separate[0], separate[1], 
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
		if (visualFeedback>0) {
		    
		    // mark region excluded from peak finder
		    // output the peaks found, with circles marking them, and the fit result in
		    // the top corner for the correlation band0<->band2
		    ImageDisplay.Marker excludedDC = 
			new ImageDisplay.Marker(w/2,h/2,minDist*2,minDist*2,true);
		    
		    Vec2d.Real fittedPeak = SimUtils.pwSpec( c2 );
		    fittedPeak.paste( cntrl, 0, 0, false );
		    
		    pwSt.addImage( fittedPeak, "dir "+angIdx+" c-corr band 0<>high",
			new ImageDisplay.Marker( w/2-peak[0], h/2+peak[1], 10, 10, true),
			excludedDC);
		    
		    // if there is a low band, also add it
		    if ((visualFeedback>1)&&(lb!=hb))
			pwSt.addImage( SimUtils.pwSpec( c1 ), "dir "+angIdx+" c-corr band 0<>low",
			    new ImageDisplay.Marker( w/2-peak[0]/2, h/2+peak[1]/2, 10, 10, true));
		}
		    

		// --- output visual feedback of overlapping regions (for all bands) ---
		if (visualFeedback>1)  
		for (int b=1; b<param.nrBand(); b++) {	
		
		    SimParam.Dir par = param.dir(angIdx);

		    // find common regions in low and high band
		    Vec2d.Cplx b0 = separate[0  ].duplicate();
		    Vec2d.Cplx b1 = separate[2*b].duplicate();
		
		    Correlation.commonRegion( b0, b1, 0, b, otfPr,  
		        par.px(b), par.py(b), 0.15, (b==1)?(.2):(.05), true);

		    // move the high band to its correct position
		    b1.fft2d( true );
		    b1.fourierShift( par.px(b), -par.py(b) );
		    b1.fft2d( false );
	    
		    // apply phase correction
		    b1.scal( Cplx.Float.fromPhase( param.dir(angIdx).getPhaOff()*b ));
	   
		    // output the full shifted bands
		    if ( visualFeedback>2 )  {
			// only add band0 once	
			if ( b==1 ) {
			    Vec2d.Cplx btmp = separate[0].duplicate();
			    otfPr.maskOtf( btmp, 0, 0);
			    pwSt.addImage(SimUtils.pwSpec( btmp ), String.format(
				"a%1d: full band0", angIdx, b ));
			}

			// add band1, band2, ...
			Vec2d.Cplx btmp = separate[2*b].duplicate();
			btmp.fft2d( true );
			btmp.fourierShift( par.px(b), -par.py(b) );
			btmp.fft2d( false );
			otfPr.maskOtf( btmp, par.px(b), par.py(b));

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
	    
	    }
	
	}
	tEst.stop();


	// ---------------------------------------------------------------------
	// refine phase (by auto-correlation, Wicker et. al) 
	// ---------------------------------------------------------------------
	
	tPha.start();
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
	}
	tPha.stop();


	// ---------------------------------------------------------------------
	// Setup the Wiener filter
	// ---------------------------------------------------------------------
	
	tWien.start();
	WienerFilter wFilter = new WienerFilter( param );
	
	if (visualFeedback>0) {
	    Vec2d.Real wd = wFilter.getDenominator(wienParam);
	    wd.reciproc();
	    wd.normalize();
	    Transforms.swapQuadrant( wd );
	    pwSt2.addImage(wd, "Wiener denominator");
	}
	
	tWien.stop();
	
	
	// ---------------------------------------------------------------------
	// Run the actual reconstruction 
	// ---------------------------------------------------------------------
	
	tRec.start();	
	// loop all pattern directions
	for (int angIdx = 0; angIdx < param.nrDir(); angIdx ++ ) 
	{
	    final SimParam.Dir par = param.dir(angIdx);

	    // ----- Band separation & OTF multiplication (if before shift) -------

	    Vec2d.Cplx [] separate  = Vec2d.createArrayCplx( par.nrComp(), w, h);
	    
	    BandSeparation.separateBands( inFFT[angIdx] , separate , 
		    par.getPhases(), par.nrBand(), par.getModulations());

	    if (otfBeforeShift && !rlMode)
		for (int i=0; i<(par.nrBand()*2-1) ;i++)  
		    otfPr.applyOtf( separate[i], (i+1)/2);

	    // ------- Shifts to correct position ----------

	    Vec2d.Cplx [] shifted		= Vec2d.createArrayCplx(5, 2*w, 2*h);

	    // band 0 is DC, so does not need shifting, only a bigger vector
	    shifted[0].pasteFreq( separate[0] );
	    
	    // higher bands need shifting
	    for ( int b=1; b<par.nrBand(); b++) {
		
		Tool.trace("REC: Dir "+angIdx+": shift band: "+b+" to: "+par.px(b)+" "+par.py(b));
		
		// first, copy to larger vectors
		int pos = b*2, neg = (b*2)-1;	// pos/neg contr. to band
		/*
		SimUtils.placeFreq( separate[pos] , shifted[pos]);
		SimUtils.placeFreq( separate[neg] , shifted[neg]);

		// then, fourier shift
		SimUtils.fourierShift( shifted[pos] ,  par.px(b),  par.py(b) );
		SimUtils.fourierShift( shifted[neg] , -par.px(b), -par.py(b) ); */

		SimUtils.pasteAndFourierShift( separate[pos], shifted[pos], 
		     par.px(b),  par.py(b), doFastShift );
		SimUtils.pasteAndFourierShift( separate[neg], shifted[neg], 
		    -par.px(b), -par.py(b), doFastShift );

	    }
	   
	    // ------ OTF multiplication or masking ------
	    
	    for (int b=0; b<par.nrBand(); b++) {
		int pos = b*2, neg = (b*2)-1;	// pos/neg contr. to band
	
		if (rlMode) {
		    continue;
		}   

		if (!otfBeforeShift) {
		    // multiply with shifted OTF
		    otfPr.applyOtf( shifted[pos], b,  par.px(b),  par.py(b) );
		    if (b>0)
		    otfPr.applyOtf( shifted[neg], b, -par.px(b), -par.py(b) );
		} else {
		    // or mask for OTF support (this should help with noise suppression)
		    otfPr.maskOtf( shifted[pos],  par.px(b),  par.py(b) );
		    if (b>0)
		    otfPr.maskOtf( shifted[neg], -par.px(b), -par.py(b) );
		}
	    }
	    
	    // ------ Sum up result ------
	   
	    shifted[0].scal(1.f/3.f);

	    for (int i=0;i<par.nrBand()*2-1;i++)  
		fullResult.add( shifted[i] ); 
	
	    
	    // ------ Output intermediate results ------
	    
	    if (visualFeedback>0) {
	
		// per-direction results
		Vec2d.Cplx result = Vec2d.createCplx(2*w,2*h);
		for (int i=0;i<par.nrBand()*2-1;i++)  
		    result.add( shifted[i] ); 

		// loop bands in this direction
		for (int i=0;i<par.nrBand();i++) {     

		    // get wiener denominator for (direction, band), add to full denom for this band
		    Vec2d.Real denom = wFilter.getIntermediateDenominator( angIdx, i, wienParam);
		
		    // add up +- shift for this band
		    Vec2d.Cplx thisband   = shifted[i*2].duplicate();
		    if (i!=0)
			thisband.add( shifted[i*2-1] );
	
		    // output the wiener denominator
		    if (visualFeedback>1) {
			Vec2d.Real wd = denom.duplicate();
			wd.reciproc();
			wd.normalize();
			Transforms.swapQuadrant( wd );
			pwSt2.addImage( wd, String.format(
			    "a%1d: OTF/Wiener band %1d",angIdx,(i/2) ));
		    }
		    
		    // apply filter and output result
		    thisband.times( denom );
		    
		    pwSt2.addImage( SimUtils.pwSpec( thisband ) ,String.format(
			"a%1d: band %1d",angIdx,i));
		    spSt2.addImage( SimUtils.spatial( thisband ) ,String.format(
			"a%1d: band %1d",angIdx,i));
		}

		// per direction wiener denominator	
		Vec2d.Real fDenom =  wFilter.getIntermediateDenominator( angIdx, wienParam);	
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
		spSt2.addImage( SimUtils.spatial( result ) ,String.format(
		    "a%1d: all bands",angIdx));
	    
		// power spectra before shift
		if (visualFeedback>2) { 
		    for (int i=0; i<(par.nrBand()*2-1) ;i++)  
		    pwSt.addImage( SimUtils.pwSpec( separate[i] ), String.format(
			"a%1d, sep%1d, seperated band", angIdx, i));
		}
	   
	    }


	}   
	
	// -- done loop all pattern directions, 'fullResult' now holds the image --
	
	// Wiener filtering mode
	if (!rlMode) {

	    // multiply by wiener denominator
	    Vec2d.Real denom = wFilter.getDenominator( wienParam );
	    fullResult.times(denom);
	    
	    if (visualFeedback>0) {
		pwSt2.addImage(  SimUtils.pwSpec( fullResult), "full (w/o APO)");
		spSt2.addImage(  SimUtils.spatial(fullResult), "full (w/o APO)");
	    }

	    // apply apotization filter
	    Vec2d.Cplx apo = Vec2d.createCplx(2*w,2*h);
	    otfPr.writeApoVector( apo, apoB, apoF);
	    fullResult.times(apo);
	    spSt2.addImage( SimUtils.spatial( fullResult), "full result");
	    
	    if (visualFeedback>0) {
		pwSt2.addImage( SimUtils.pwSpec( fullResult), "full result");
	    }

	}

	// Richardson-Lucy mode
	if (rlMode) {

	    

	    Vec2d.Cplx otfSim = Vec2d.createCplx( param, 2);
	    
	    Vec2d.Cplx otfTmpPos = Vec2d.createCplx( param, 2);
	    Vec2d.Cplx otfTmpNeg = Vec2d.createCplx( param, 2);


	    for (int ang = 0; ang<param.nrDir(); ang++) {
	    
		final SimParam.Dir par = param.dir(ang);
		
		for (int b=0; b<param.nrDir(); b++) {
		    
		    otfPr.writeOtfVector( otfTmpPos, b,  par.px(b),  par.py(b));
		    otfPr.writeOtfVector( otfTmpNeg, b, -par.px(b), -par.py(b));
		    
		    if (b==0) {
			otfTmpPos.scal( 1.f/param.nrDir());
			otfSim.add( otfTmpPos );
		    } else {
			otfSim.add( otfTmpPos );
			otfSim.add( otfTmpNeg );
		    }

		}
	    } 

	    // visualize the SIM OTF
	    Vec2d.Real otfVis = Vec2d.createReal( param,2);
	    otfVis.copy( otfSim );
	    Transforms.swapQuadrant( otfVis );
	    pwSt2.addImage( otfVis, "SIM OTF added up");
		
	    pwSt2.addImage(  SimUtils.pwSpec( fullResult), "full unfiltered");
	    spSt2.addImage(  SimUtils.spatial(fullResult), "full unfiltered");
	
	    // filter the output image
	    fullResult.fft2d( true );
	    RLDeconvolution.deconvolve( fullResult, otfSim, 5 );
	    fullResult.fft2d( false );
	    pwSt2.addImage(  SimUtils.pwSpec( fullResult), "full (RL-deconv)");
	    spSt2.addImage(  SimUtils.spatial(fullResult), "full (RL-deconv)");
	    

	}


	// Add wide-field for comparison
	if (visualFeedback>=0) {
	    
	    // obtain the low freq result
	    Vec2d.Cplx lowFreqResult = Vec2d.createCplx( param, 2);
	    
	    // have to do the separation again, result before had the OTF multiplied
	    for (int angIdx = 0; angIdx < param.nrDir(); angIdx ++ ) {
		
		final SimParam.Dir par = param.dir(angIdx);
		
		Vec2d.Cplx [] separate  = Vec2d.createArrayCplx( par.nrComp(), w, h);
		BandSeparation.separateBands( inFFT[angIdx] , separate , 
		    par.getPhases(), par.nrBand(), par.getModulations());

		Vec2d.Cplx tmp  = Vec2d.createCplx( param, 2 );
		SimUtils.placeFreq( separate[0],  tmp);
		lowFreqResult.add( tmp );
	    }	
	    
	    // now, output the widefield
	    if (visualFeedback>0)
		pwSt2.addImage( SimUtils.pwSpec(lowFreqResult), "Widefield" );
	    spSt2.addImage( SimUtils.spatial(lowFreqResult), "Widefield" );
	
	    // otf-multiply and wiener-filter the wide-field
	    otfPr.otfToVector( lowFreqResult, 0, 0, 0, false, false ); 

	    Vec2d.Real lfDenom = wFilter.getWidefieldDenominator( wienParam );
	    lowFreqResult.times( lfDenom );
	    
	    //Vec2d.Cplx apoLowFreq = Vec2d.createCplx(2*w,2*h);
	    //otfPr.writeApoVector( apoLowFreq, 0.4, 1.2);
	    //lowFreqResult.times(apoLowFreq);
	    
	    if (visualFeedback>0)
		pwSt2.addImage( SimUtils.pwSpec( lowFreqResult), "filtered Widefield" );
	    spSt2.addImage( SimUtils.spatial( lowFreqResult), "filtered Widefield" );

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
	if (refinePhase)
	Tool.trace(" Phase refine:                "+tPha);
	Tool.trace(" Wiener filter creation:      "+tWien);
	Tool.trace(" Reconstruction:              "+tRec);
	Tool.trace(" ---");
	Tool.trace(" All:                         "+tAll);

	// DONE, display all results
	pwSt.display();
	pwSt2.display();
	spSt.display();
	spSt2.display();

    }


    /** Start from the command line to run the plugin */
    public static void main( String [] arg ) {

	if (arg.length<1) {
	    System.out.println("TIFF-file");
	    return;
	}
	
	boolean set=false;
  
	new ij.ImageJ( ij.ImageJ.EMBEDDED );
	//SimpleMT.useParallel( false );
	ImagePlus ip = IJ.openImage(arg[0]);
	//ip.show();

	TestRLdeconvSIM tp = new TestRLdeconvSIM();
	tp.runReconstruction( ip.getStack() );
    }


  





}


