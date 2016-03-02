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
public class Test3d implements PlugIn {

    /** Global variables */
    boolean showDialog =  false;    // if set, dialog to set parameters is shown at plugin start 

    int nrBands  = 3;		    // #bands (2 - two-beam, 3 - three-beam, ...)
    int nrDirs   = 3;		    // #angles or pattern orientations
    int nrPhases = 5;		    // #phases (at least 2*bands -1 )

    double emWavelen = 560;	    // emission wavelength		    
    double otfNA     = 1.4;	    // NA of objective
    double otfCorr   = 0.31;	    // OTF correction factor
    double pxSize    = 0.080;	    // pixel size (microns)

    double wienParam   = 0.05;	    // Wiener filter parameter

    boolean otfBeforeShift = true;  // multiply the OTF before or after shift to px,py

    boolean findPeak    = true;    // run localization and fit of shfit vector
    boolean refinePhase = false;    // run auto-correlation phase estimation (Wicker et. al)
    boolean doTheReconstruction = true; // if to run the reconstruction (for debug, mostly)
	
    final int visualFeedback = 0;   // amount of intermediate results to create (-1,0,1,2,3)

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
	if ( (inSt.getSize() % nrPhases*nrDirs)!=0 ) {
	    IJ.showMessage("Stack length not multiple of phases*angles: "+inSt.getSize() );
	    return;
	}
	
	// start the reconstruction
	runReconstruction(inSt, "~/Desktop/green-otf.xml");
    }
   

    /** Step-by-step reconstruction process. */
    public void runReconstruction( ImageStack inSt, String cfgfile ) {
	
	// ----- Parameters -----
	final int w=inSt.getWidth(), h=inSt.getHeight();
	final int d = inSt.getSize() / (nrPhases*nrDirs);
	//final int d = 6;

	Conf cfg=null;
	OtfProvider3D otfPr    = null; 
	OtfProvider   otfPr2D  = null;	// for parameter estimation

	try {
	    cfg	    = Conf.loadFile(cfgfile);
	    otfPr   = OtfProvider3D.loadFromConfig( cfg );
	    otfPr2D = OtfProvider.loadFromConfig( cfg );
	} catch (Exception e) {
	    Tool.trace(e.toString());
	    return;
	}

	Tool.trace("successfully loaded OTF");

	
	// Reconstruction parameters: #bands, #directions, #phases, size, microns/pxl, the OTF 
	final SimParam param = 
	    SimParam.create3d(nrBands, nrDirs, nrPhases, w, d, 0.08, 0.125, otfPr2D, otfPr );
	
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
	
	// Output displays to show the intermediate results
	ImageDisplay pwSt  = new DisplayWrapper(w,h, "Power Spectra" );
	ImageDisplay spSt  = new DisplayWrapper(w,h, "Spatial images");
	ImageDisplay pwSt2 = new DisplayWrapper(2*w,2*h, "Power Spectra" );
	ImageDisplay spSt2 = new DisplayWrapper(2*w,2*h, "Spatial images");

	// Do some OTF testing for 3D OTF
	if (false) {
	    int otfdepth=128;
	    otfPr.setPixelSize(0.0244,0.052);
	    Vec3d.Cplx otfVec = Vec3d.createCplx(512,512,otfdepth);
	    Vec2d.Cplx tmp  = Vec2d.createCplx(512,512);
	    Vec2d.Real tmp2 = Vec2d.createReal(512,512);

	    for (int b=0; b<=2; b++) {
		ImageDisplay otfSt  = new DisplayWrapper(w,h, "OTF band"+b );
		otfPr.writeOtfVector( otfVec, b, 0, 0 );
		for (int z=0; z<otfdepth; z++) {
		    tmp.slice(otfVec, z);
		    tmp2.copy(tmp);
		    otfSt.addImage(tmp2, "b:"+b+" z:"+z);
		}
		otfSt.display();
	    }
	    return;
	}




	// Copy current stack into vectors, apotize borders, run fft 
	Vec3d.Cplx inFFT[][] = new Vec3d.Cplx[ nrDirs ][ nrPhases ];

	Tool.trace(String.format("Running with dimensions %d x %d x %d, a:%d p:%d",
	    w,h,d,nrDirs,nrPhases));

	for (int a=0; a<nrDirs; a++) 
	for (int p=0; p<nrPhases; p++) {
	    
	    inFFT[a][p] = Vec3d.createCplx(w,h,d);

	    for (int z=0; z<d; z++) {
		
		int pos = 1 + p + 5 * z + a * 5 * d; // TODO: Use SimUtils here?
		Vec2d.Real img = ImageVector.copy( inSt.getProcessor(pos) );
		SimUtils.fadeBorderCos( img , 10);
		inFFT[a][p].setSlice( z, img );
	    }

	    // input FFT
	    inFFT[a][p].fft3d(false);
	    Tool.trace(String.format("Input FFT a: %d p: %d",a,p));	
	    
	    // DEBUG output
	    Vec2d.Cplx tmp = Vec2d.createCplx(w,h);
	    tmp.slice( inFFT[a][p],0 );
	    spSt.addImage( SimUtils.spatial(tmp), "IN: a"+a+" p"+p);
	    pwSt.addImage( SimUtils.pwSpec( inFFT[a][p] ) , "IN: a"+a+" p"+p);
	}


	// vectors to store the result
	Vec3d.Cplx fullResult    = Vec3d.createCplx( w*2,h*2,d);


	// ---------------------------------------------------------------------
	// extract the SIM parameters by cross-correlation analysis
	// ---------------------------------------------------------------------
	
	tEst.start();
	if (findPeak) {

	    // TODO: This currently just uses the 2D algorithm on the projection
	    // For good modulation estimate, however the full 3D OTF should be used

	    // The attenuation vector helps well to fade out the DC component,
	    // which is uninteresting for the correlation anyway
	    Vec2d.Real otfAtt = Vec2d.createReal( param );
	    otfPr2D.writeAttenuationVector( otfAtt, .99, 0.15*otfPr.getCutoff(), 0, 0  ); 
	    
	    // loop through pattern directions
	    for (int angIdx=0; angIdx<param.nrDir(); angIdx++) {
	    
		final SimParam.Dir dir = param.dir(angIdx);

		// idx of low band (phase detection) and high band (shift vector detection)
		// will be the same for two-beam
		final int lb = 1;
		final int hb = (param.dir(angIdx).nrBand()==3)?(3):(1);

		// compute z-projection
		Vec2d.Cplx [] inFFT2d = Vec2d.createArrayCplx( nrPhases, w, h);
		for (int p=0; p<nrPhases; p++) {
		    inFFT2d[p].slice( inFFT[angIdx][p], 0);
		    // DEBUG: Output the z-projection
		    pwSt.addImage( SimUtils.pwSpec( inFFT2d[p]), "a"+angIdx+" p"+p);
		    spSt.addImage( SimUtils.spatial( inFFT2d[p]), "a"+angIdx+" p"+p);
		}


		// compute band separation
		Vec2d.Cplx [] separate = Vec2d.createArrayCplx( dir.nrComp(), w, h);
		
		BandSeparation.separateBands( inFFT2d, separate , 
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
		peak = Correlation.fitPeak( separate[0], separate[hb], 0, 2, otfPr2D,
		    -peak[0], -peak[1], 0.05, 2.5, cntrl );

		// Now, either three beam / 3 bands ...
		if (lb!=hb) {

		    // At the peak position found, extract phase and modulation from band0 <-> band 1
		    Cplx.Double p1 = Correlation.getPeak( separate[0], separate[lb], 
			0, 1, otfPr2D, peak[0]/2, peak[1]/2, 0.05 );

		    // Extract modulation from band0 <-> band 2
		    Cplx.Double p2 = Correlation.getPeak( separate[0], separate[hb], 
			0, 2, otfPr2D, peak[0], peak[1], 0.05 );

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
			0, 1, otfPr2D, peak[0], peak[1], 0.05 );

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
		
		    Correlation.commonRegion( b0, b1, 0, b, otfPr2D,  
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
			    otfPr2D.maskOtf( btmp, 0, 0);
			    pwSt.addImage(SimUtils.pwSpec( btmp ), String.format(
				"a%1d: full band0", angIdx, b ));
			}

			// add band1, band2, ...
			Vec2d.Cplx btmp = separate[2*b].duplicate();
			btmp.fft2d( true );
			btmp.fourierShift(par.px(b), -par.py(b) );
			btmp.fft2d( false );
			otfPr2D.maskOtf( btmp, par.px(b), par.py(b));

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
	
	if (visualFeedback>0) {
	    Vec3d.Real wd = wFilter.getDenominator(wienParam);
	    Vec2d.Real wdpr = Vec2d.createReal(2*w, 2*h);
	    wdpr.project( wd );
	    wdpr.reciproc();
	    wdpr.normalize();
	    Transforms.swapQuadrant( wdpr );
	    pwSt2.addImage(wdpr, "Wiener denominator");
	}
	
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
		
		BandSeparation.separateBands( inFFT[angIdx] , separate , 
			par.getPhases(), par.nrBand(), par.getModulations());

		if (otfBeforeShift)
		    for (int i=0; i<(par.nrBand()*2-1) ;i++)  
			otfPr.applyOtf( separate[i], (i+1)/2);

		// ------- Shifts to correct position ----------

		Vec3d.Cplx [] shifted   = Vec3d.createArrayCplx(5, 2*w, 2*h, d);

		// band 0 is DC, so does not need shifting, only a bigger vector
		shifted[0].pasteFreq( separate[0] );
		//SimUtils.placeFreq( separate[0],  shifted[0]);
		
		// higher bands need shifting
		for ( int b=1; b<par.nrBand(); b++) {
		    
		    Tool.trace("REC: Dir "+angIdx+": shift band: "+b+" to: "+par.px(b)+" "+par.py(b));
		    
		    // first, copy to larger vectors
		    int pos = b*2, neg = (b*2)-1;	// pos/neg contr. to band
		    shifted[pos].pasteFreq( separate[pos] );
		    shifted[neg].pasteFreq( separate[neg] );

		    // then, fourier shift
		    shifted[pos].fourierShift(  par.px(b),  par.py(b), 0 );
		    shifted[neg].fourierShift( -par.px(b), -par.py(b), 0 );
		}
	       
		// ------ OTF multiplication or masking ------
		
		if (!otfBeforeShift) { 
		    // multiply with shifted OTF
		    for (int b=0; b<par.nrBand(); b++) {
			int pos = b*2, neg = (b*2)-1;	// pos/neg contr. to band
			otfPr.applyOtf( shifted[pos], b,  par.px(b),  par.py(b) );
			otfPr.applyOtf( shifted[neg], b, -par.px(b), -par.py(b) );
		    }
		} 
		// TODO: re-implement OTF mask support for 3D
		/*
		else {
		    // or mask for OTF support
		    for (int i=0; i<(par.nrBand()*2-1) ;i++)  
			//wFilter.maskOtf( shifted[i], angIdx, i);
			otfPr.maskOtf( shifted[i], angIdx, i);
		} */
		
		// ------ Sum up result ------
		
		for (int i=0;i<par.nrBand()*2-1;i++)  
		    fullResult.add( shifted[i] ); 
	    
		
		// ------ Output intermediate results ------
		
		if (visualFeedback>0) {
	    
		    // per-direction results
		    Vec2d.Cplx result = Vec2d.createCplx(2*w,2*h);
		    Vec2d.Cplx tmp = Vec2d.createCplx(2*w,2*h);
		    for (int i=0;i<par.nrBand()*2-1;i++) { 
			tmp.slice( shifted[i], 0) ;
			result.add( tmp ); 
		    }

		    // loop bands in this direction
		    for (int i=0;i<par.nrBand();i++) {     

			// TODO: re-implement and re-enable this
			// get wiener denominator for (direction, band), add to full denom for this band
			//Vec2d.Real denom = wFilter.getIntermediateDenominator( angIdx, i, wienParam);
		    
			// add up +- shift for this band
			Vec3d.Cplx thisband   = shifted[i*2];
			if (i!=0)
			    thisband.add( shifted[i*2-1] );
	    
			// output the wiener denominator
			/*
			if (visualFeedback>1) {
			    Vec2d.Real wd = denom.duplicate();
			    wd.reciproc();
			    wd.normalize();
			    Transforms.swapQuadrant( wd );
			    pwSt2.addImage( wd, String.format(
				"a%1d: OTF/Wiener band %1d",angIdx,(i/2) ));
			} */
			
			// apply filter and output result
			//thisband.times( denom );
			
			pwSt2.addImage( SimUtils.pwSpec( thisband ) ,String.format(
			    "a%1d: band %1d",angIdx,(i/2)));
			spSt2.addImage( SimUtils.spatial( thisband ) ,String.format(
			    "a%1d: band %1d",angIdx,(i/2)));
		    }

		    // per direction wiener denominator	
		    /*
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
		    } */
		    
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
	    
	    // multiply by wiener denominator
	    Vec3d.Real denom = wFilter.getDenominator( wienParam );
	    fullResult.times(denom);
	    
	    if (visualFeedback>0) {
		pwSt2.addImage(  SimUtils.pwSpec( fullResult), "full (w/o APO)");
		spSt2.addImage(  SimUtils.spatial(fullResult), "full (w/o APO)");
	    }

	    // apply apotization filter
	    Vec2d.Cplx apo = Vec2d.createCplx(2*w,2*h);
	    //otfPr.writeApoVector( apo, apoB, apoF);
	    //fullResult.times(apo);
	    spSt2.addImage( SimUtils.spatial( fullResult), "full result");
	    
	    if (visualFeedback>0) {
		pwSt2.addImage( SimUtils.pwSpec( fullResult), "full result");
	    }

	    // Add wide-field for comparison
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

	if (arg.length<2) {
	    System.out.println("TIFF-file config-otf.xml");
	    return;
	}
	
	boolean set=false;
  
	new ij.ImageJ( ij.ImageJ.EMBEDDED );
	//SimpleMT.useParallel( false );
	ImagePlus ip = IJ.openImage(arg[0]);
	//ip.show();

	Test3d tp = new Test3d();
	tp.runReconstruction( ip.getStack(), arg[1] );
    }




}


