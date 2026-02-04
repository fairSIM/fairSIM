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

import org.fairsim.sim_algorithm.OtfProvider;
import org.fairsim.sim_algorithm.SimUtils;

import org.fairsim.utils.Conf;
import org.fairsim.utils.Tool;
import org.fairsim.linalg.Vec2d;
import org.fairsim.linalg.Vec;
import org.fairsim.linalg.Cplx;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.Random;

import ij.plugin.PlugIn;
import ij.ImageStack;
import ij.ImagePlus;
import ij.IJ;
import ij.gui.GenericDialog;
import ij.measure.Calibration;

import org.fairsim.utils.Args;



/** 
 * Small plugin that generate bead surfaces
 * */
public class BeadsCreator implements PlugIn{

    final int width,height,oversample;
    final double pxlSize;

    Vec2d.Real groundTruth;

    Random posPRNG;


    // non-sense constructor only to enable this as a ImageJ plugin
    public BeadsCreator() {
	width=height=oversample=-1;
	pxlSize=-1;
    }

    private BeadsCreator( int w, int h, double pxl, int over, int prngSEED ){
	width  = w*over;
	height = h*over;
	pxlSize = pxl/over;
	oversample = over;
    
	groundTruth = Vec2d.createReal( width, height );
    
	posPRNG = new Random(prngSEED);

    }

    private void addBead( double xPos, double yPos, double rad, boolean hollow ) {

	Vec2d.Real dat = groundTruth;

	int s = (int)(2*rad/pxlSize+2);

	for (int y = (int)yPos-s ; y<(int)yPos+s; y++) {
	    for (int x = (int)xPos-s ; x<(int)xPos+s; x++) {

		double dist = Math.hypot( x-xPos, y-yPos ) / (rad/pxlSize);


		if (y<0 || y>= height || x<0 || x>= height) continue;
		
		if (dist>1) {
		    if (dist<2) {
			dat.set( x,y,dat.get(x,y)+(float)1e-6);
		    }
		    continue;
		}


		if (!hollow) {
		    if (dist<0.5) {
			dat.set(x,y,dat.get(x,y)+1);
		    } else {
			dat.set(x,y,dat.get(x,y) + (float)(Math.cos( ((dist-0.5)*2*Math.PI))+1)*.5f);
		    }
		} else {
		    if (dist>.2)
		    dat.set(x,y,dat.get(x,y) + (float)(Math.sin( (dist-.2)/.8*Math.PI)));
		}

	    }
	}

    }


    private boolean addRandomBead( double rad, boolean hollow ) {

	boolean success = false;

	for (int i=0; i<20; i++) {
	    
	    double x = posPRNG.nextDouble()*width;
	    double y = posPRNG.nextDouble()*height;

	    if ( groundTruth.get((int)x,(int)y)<1e-8 && !success ) {
		addBead( x,y , rad, hollow);
		success = true; // don't return to call PRNG equally often each time
	    }
	}
    
	return success;
    }

    private static void simPattern(Vec2d.Cplx dat,
	    final double kx, final double ky, final double pha ) {

	    final int len = dat.vectorWidth();
	    
	    for (int y=0;y<len; y++)
	    for (int x=0;x<len; x++) {
		double v = Math.cos( 2*Math.PI* (-kx*x + ky*y)/(len) + pha );
		dat.set(x,y,new Cplx.Float( (float)(v+1)*0.3f+.4f, 0f)) ;
	    }
    }



    public void run(String arg)  {

	GenericDialog gd = new GenericDialog("Bead surface simulator");

	gd.addNumericField("#beads" , 300, 0);
	gd.addNumericField("image size (pxl)" , 256, 0);
	gd.addNumericField("oversample" , 4, 0);
	gd.addNumericField("pxl size (nm)" , 80, 0);
	gd.addNumericField("min. bead size (nm)" , 200, 0);
	gd.addNumericField("max. bead size (nm)" , 220, 0);
	gd.addNumericField("doughnut fraction", 0.5,2);
	gd.addNumericField("PRNG seed" , 42, 0);


	gd.showDialog();
	if (gd.wasCanceled()) {
	    return;
	}

	final int nrBeads		= (int)gd.getNextNumber();
	final int imgSize		= (int)gd.getNextNumber();
	final int overSample		= (int)gd.getNextNumber();
	final double pxlSize		= gd.getNextNumber();
	final double minBeadSize	= gd.getNextNumber();
	final double maxBeadSize	= gd.getNextNumber();
	final double doughnutFraction   = gd.getNextNumber();
	final int prngSeed		= (int)gd.getNextNumber();

	BeadsCreator bc = new BeadsCreator(imgSize,imgSize,pxlSize/1000.,overSample,prngSeed);

	Random hollowPRNG = new Random(prngSeed*123+3);
	Random noisePRNG  = new Random(prngSeed*3+123);
	Random sizePRNG   = new Random(prngSeed*5+123);

	for (int i=0; i<nrBeads; i++) {

	    double beadSize = minBeadSize + (maxBeadSize-minBeadSize)*sizePRNG.nextDouble();
	    bc.addRandomBead(beadSize/1000., (hollowPRNG.nextDouble()<doughnutFraction));
	    IJ.showProgress(i,nrBeads-1);
	}
	 
	
	ImageVector img = ImageVector.create( imgSize*overSample, imgSize*overSample );
	img.copy( bc.groundTruth );
	img.scal(1000.f);


	ImagePlus ip = new ImagePlus("bead_surface", img.img());
	Calibration cb = new Calibration();
	cb.setUnit("um");
	cb.pixelWidth = pxlSize / overSample / 1000;
	cb.pixelHeight = pxlSize / overSample /1000;
	ip.setCalibration(cb);

	ip.show();
    }


    
    public static void main(String [] attr) { 

	// parse the parameters
	// TODO: This could be done with less code using reflections, but...

	Args arg = new Args();
	
	arg.addInt("outputSize",	256,	"Size of the output image (pxls)");
	arg.addDbl("pxlSize",	0.08,	"Size of the pixels (micron)");
	arg.addInt("angles",	3,	"Number of angles");
	arg.addInt("phases",	3,	"Number of phases");
	arg.addInt("phaseMult",	1,	"Phase rotation speed multiplier");
    	arg.addInt("nrBeads",	300,	"Number of beads to simulate");
	arg.addInt("photons",	50,	"Number of photons");
	arg.addDbl("simResImpr",  0.9,	"Resolution improvement");
	arg.addInt("prngSEED",	1337,	"Seed for the random number generators");

	if (arg.parseArgs(attr)<0) System.exit(-1);

	int    outputSize = arg.getInt("outputSize");
	double pxlSize    = arg.getDbl("pxlSize");
	int angles	  = arg.getInt("angles");
	int phases	  = arg.getInt("phases");
	int phaseMult	  = arg.getInt("phaseMult");
	int nrBeads	  = arg.getInt("nrBeads");
	int photons	  = arg.getInt("photons");
	double simResImpr = arg.getDbl("simResImpr");
	int prngSEED	  = arg.getInt("prngSEED") ;
	double closedBeadsFraction = 0.5;
	int oversample = 8;

	arg.printParams();
	
	if ( phaseMult!=1 && phases % phaseMult == 0 ) {
	    System.err.println("!! #phases is a multiple of phaseMult, not good");
	}

	// run the simulation
	
	ij.ImageJ inst = new ij.ImageJ( ij.ImageJ.EMBEDDED);


	BeadsCreator bc = new BeadsCreator(outputSize,outputSize,pxlSize,oversample,prngSEED);
	DisplayWrapper dwSI = new DisplayWrapper( outputSize, outputSize,"Simulated data");
	DisplayWrapper dwGT = new DisplayWrapper( bc.width, bc.height,"Bead Simulation: full res");

	Random hollowPRNG = new Random(prngSEED*123+2);
	Random noisePRNG  = new Random(prngSEED*2+123);

	for (int i=0; i<nrBeads; i++) {
	    bc.addRandomBead(.2, (hollowPRNG.nextDouble()>closedBeadsFraction));
	}
	   
	dwGT.addImage( bc.groundTruth, "Ground truth");
	
	// initialize OTF
	final double cyclPerMicron = 1./(bc.pxlSize * bc.width);
	OtfProvider otf = OtfProvider.fromEstimate(1.4, 525, .7);
	otf.setPixelSize( cyclPerMicron );


	// create wide-field
	Vec2d.Cplx img = Vec2d.createCplx( bc.width, bc.height );

	img.copy( bc.groundTruth );
	img.fft2d(false);
	otf.applyOtf( img,0 );

	dwGT.addImage( SimUtils.spatial( img ), "Widefield");
	dwGT.display();


	// create SIM
	Vec2d.Cplx sim  = Vec2d.createCplx( bc.width, bc.height );
	Vec2d.Real tmp  = Vec2d.createReal( bc.width, bc.height );
	
	Vec2d.Real tmp2 = Vec2d.createReal( outputSize, outputSize );

	final double abbeLimit = 1. / otf.getCutoff();
	final double simPttrLen = abbeLimit / simResImpr ;

	final double k0 = otf.getCutoff()*simResImpr / cyclPerMicron;

	Tool.trace(" abbe: "+abbeLimit+" sim: "+simPttrLen+" k0: "+k0);


	for ( int a=0; a<angles; a++) { 
	    
	    double kx = Math.sin( (a/3.+.2)*Math.PI*2 )*k0;
	    double ky = Math.cos( (a/3.+.2)*Math.PI*2 )*k0;
	   
	    Tool.trace("kx "+kx+" ky "+ky);
    

	    for ( int p=0; p<phases; p++) {
		
		simPattern( sim, kx, ky, p*Math.PI * 2 *phaseMult / (double)phases);
		tmp.copy( sim );

		dwGT.addImage(  tmp , "SIM field  a:"+a+" p:"+p);
		
		img.copy( bc.groundTruth );
		img.times( sim );
		tmp.copy( img );
		dwGT.addImage( tmp , "SIM signal a:"+a+" p:"+p);
		img.fft2d( false );
		otf.applyOtf( img,0 );
		img.fft2d( true );
		tmp.copy( img );
		dwGT.addImage( tmp, "SIM folded a:"+a+" p:"+p);

		// convert to photons
		tmp2.zero();

		for ( int y=0;y<outputSize;y++) {
		    for ( int x=0;x<outputSize;x++) {
    
			// down-sample
			float acc = 0;
			
			for ( int y1 = 0; y1< bc.oversample; y1++) {
			    for ( int x1 = 0; x1< bc.oversample; x1++) {
				acc += tmp.get( x*bc.oversample + x1, y*bc.oversample+y1); 
			    }
			}

			acc *= photons;
			acc += (float)(noisePRNG.nextGaussian() * Math.sqrt( acc )); 

			acc += (float)(98 + noisePRNG.nextGaussian()*25);

			tmp2.set( x,y,acc);
		    }
		}

		dwSI.addImage( tmp2, "meas TIRF SIM a:"+a+" p:"+p);
	    }
	}

	dwSI.display();
    }




}
