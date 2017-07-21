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
import org.fairsim.utils.ImageStackOutput;

import org.fairsim.fiji.DisplayWrapper5D;


public class OtfCreator {


    // the full OTF creation
    public Vec2d.Cplx createOtf( Vec3d.Cplx [][] inputImgs ) {

	final int nrBands = 3;

	final int nrAngle = inputImgs.length;
	final int width =inputImgs[0][0].vectorWidth();
	final int height =inputImgs[0][0].vectorHeight();
	final int depth =inputImgs[0][0].vectorDepth();



	Tool.trace("OTF creating for "+nrAngle+" angles");

	ImageStackOutput iso1 = new DisplayWrapper5D( width, height, depth, 2,nrBands,"smoothed pseudo-widefield");
	//ImageStackOutput iso2 = new DisplayWrapper5D( width, height, depth, 2,nrBands*2,"full OTFs");

	Tool.trace("--- running fit ---");

	// run for every angle
	for (int ang = 0 ; ang < nrAngle; ang++ ){

	    if (nrBands != 3) {
		Tool.error("currently supports exactly bands == 3",true);
		return null;
	    }

	    // band-separate the input
	    Vec3d.Cplx [] separate = Vec3d.createArrayCplx( 2*nrBands-1, width, height, depth); 
	    BandSeparation.separateBands( inputImgs[ang], separate, 0, nrBands, null ); 
	    Vec3d.Cplx [] bands = new Vec3d.Cplx[nrBands];
	    bands[0] = separate[0].duplicate();	
	    bands[1] = separate[2].duplicate();	
	    bands[2] = separate[4].duplicate();

	    // output the band-separated input data
	    for (int z=0;z<depth;z++) {
		for (int b=0;b<nrBands;b++) {
		    Vec2d.Real res = Vec2d.createReal( width, height );
		    res.slice( bands[b] , z);
		    iso1.setImage( res , z, 0 ,b, "");
		}
	    }

	    // locate the bead in the pseudo-widefield 
	    Tool.trace("--- Fitting bead position ---");
	    double [] pos = getBeadPos( bands[0] );
	    Tool.trace(String.format(" bead found at: %7.3f %7.3f %7.3f ", pos[0],pos[1],pos[2]));
		    
    
	    // fft the otfs, apply the correct phase offset 
	    for (int b=0; b<nrBands; b++) {
		bands[b].fft3d(false);
	    }
    
	    // for debugging: output the centered bead
	    {
		ImageStackOutput isoOtf = new DisplayWrapper5D( width, height, depth, 2,3,"centered OTFs");
		for (int b=0; b<nrBands; b++) {
		    
		    Vec3d.Cplx cpyBands = bands[b].duplicate();
		    Vec3d.Cplx tmp = bands[b].duplicate();
		    cpyBands.fourierShift(pos[0],pos[1],pos[2]); 
		    cpyBands.fft3d(true);
		    Transforms.swapQuadrant(cpyBands, tmp);
		    for (int z=0;z<depth;z++) {
			Vec2d.Cplx res  = Vec2d.createCplx( width, height );
			Vec2d.Real img1 = Vec2d.createReal( width, height );
			Vec2d.Real img2 = Vec2d.createReal( width, height );
			res.slice(tmp,z);
			img1.copyMagnitude( res );
			img2.copyPhase( res );
			isoOtf.setImage(img1,z,0,b,"");
			isoOtf.setImage(img2,z,1,b,"");
		    }
		
		}	
		isoOtf.update();
	    }

	    // for demonstration: scan the phase range
	    if (false) {
		// for x/y
		ImageStackOutput isoMag = 
		    new DisplayWrapper5D( width, height, depth, 10,30,"xy pha scan full OTFs - mag");
		ImageStackOutput isoPha = 
		    new DisplayWrapper5D( width, height, depth, 10,30,"xy pha scan full OTFs - pha");
		
		Tool.trace("computing phase scan, this might take a while...");
   
		for (int phaShiftX=0; phaShiftX<1; phaShiftX++) {
		    for (int phaShiftY=0; phaShiftY<10; phaShiftY++) {
		

			double phaOffX=-.15+.03*phaShiftX;
			double phaOffY=-.15+.03*phaShiftY;

			// compensate bead position
			Vec3d.Cplx [] cpyBands = new Vec3d.Cplx[nrBands];
			for (int b=0; b<nrBands; b++) {
			    cpyBands[b] = bands[b].duplicate();
			    cpyBands[b].fourierShift(pos[0]+phaOffX,pos[1]+phaOffY,pos[2]); 
			}

			

			// visualize result
			for (int b=0;b<nrBands;b++) {
			    Vec3d.Cplx tmp = cpyBands[b].duplicate();
			    Transforms.swapQuadrant(cpyBands[b], tmp);

			    for (int z=0;z<depth;z++) {
				Vec2d.Cplx res  = Vec2d.createCplx( width, height );
				Vec2d.Real img1 = Vec2d.createReal( width, height );
				Vec2d.Real img2 = Vec2d.createReal( width, height );
				res.slice( tmp , z);
				img1.copyMagnitude( res );
				img2.copyPhase( res );

				isoMag.setImage( img1 , z,phaShiftY,phaShiftX+b*10,"mag");
				isoPha.setImage( img2 , z,phaShiftY,phaShiftX+b*10,"pha");
			    }
			}
		    }
		}
		


		isoMag.update();
		isoPha.update();
	    }
	    
	    if (false) {
		// for z
		ImageStackOutput isoMag = 
		    new DisplayWrapper5D( width, height, depth, 10,3,"z pha scan full OTFs - mag");
		ImageStackOutput isoPha = 
		    new DisplayWrapper5D( width, height, depth, 10,3,"z pha full OTFs - pha");
		
		Tool.trace("computing phase scan, this might take a while...");
   
		for (int phaShiftZ=0; phaShiftZ<10; phaShiftZ++) {
		
			double phaOffZ=-.15+.03*phaShiftZ;

			// compensate bead position
			Vec3d.Cplx [] cpyBands = new Vec3d.Cplx[nrBands];
			for (int b=0; b<nrBands; b++) {
			    cpyBands[b] = bands[b].duplicate();
			    cpyBands[b].fourierShift(pos[0],pos[1],pos[2]+phaOffZ); 
			}
			
			Tool.trace( "corr z "+phaOffZ+" "+crossCorrelateZ( cpyBands[0]));

			// visualize result
			for (int b=0;b<nrBands;b++) {
			    Vec3d.Cplx tmp = cpyBands[b].duplicate();
			    Transforms.swapQuadrant(cpyBands[b], tmp);

			    for (int z=0;z<depth;z++) {
				Vec2d.Cplx res  = Vec2d.createCplx( width, height );
				Vec2d.Real img1 = Vec2d.createReal( width, height );
				Vec2d.Real img2 = Vec2d.createReal( width, height );
				res.slice( tmp , z);
				img1.copyMagnitude( res );
				img2.copyPhase( res );

				isoMag.setImage( img1 , z,phaShiftZ,b,"mag");
				isoPha.setImage( img2 , z,phaShiftZ,b,"pha");
			    }
			}
		}

		isoMag.update();
		isoPha.update();
	    }
	
	

	    // run a phase optimization scan
	    double [] posComp = new double [] {0,0,0 }; 
	    
	    if (true) {
		
		Tool.trace("computing phase scan for Y:");
   
		double min = 1<<24, minPos=0;
		for (double phaShiftY=-.7; phaShiftY<=.7; phaShiftY+=0.01) {
			// compensate bead position
			Vec3d.Cplx cpyBands = bands[0].duplicate();
			cpyBands.fourierShift(pos[0],pos[1]+phaShiftY,pos[2]); 
			double corr = crossCorrelateX( cpyBands);
			//Tool.trace("phase pos "+phaShiftY+" "+corr);  
			if (corr<min) {
			    min = corr;
			    minPos = phaShiftY;
			}
		}
		posComp[1] = minPos + pos[1];
		Tool.trace("Y Phase offset "+minPos+" as "+min);
		
		min = 1<<24; minPos=0;
		for (double phaShiftX=-.7; phaShiftX<=.7; phaShiftX+=0.01) {
			// compensate bead position
			Vec3d.Cplx cpyBands = bands[0].duplicate();
			cpyBands.fourierShift(pos[0]+phaShiftX,pos[1],pos[2]); 
			double corr = crossCorrelateY( cpyBands);
			//Tool.trace("phase pos "+phaShiftY+" "+corr);  
			if (corr<min) {
			    min = corr;
			    minPos = phaShiftX;
			}
		}
		posComp[0] = minPos + pos[0];
		Tool.trace("X Phase offset "+minPos+" as "+min);
		
		min = 1<<24; minPos=0;
		for (double phaShiftZ=-1.5; phaShiftZ<=1.5; phaShiftZ+=0.025) {
			// compensate bead position
			Vec3d.Cplx cpyBands = bands[0].duplicate();
			cpyBands.fourierShift(posComp[0],posComp[1],pos[2]+phaShiftZ); 
			double corr = crossCorrelateZ( cpyBands);
			//Tool.trace("phase pos "+phaShiftZ+" "+corr);  
			if (corr<min) {
			    min = corr;
			    minPos = phaShiftZ;
			}
		}
		posComp[2] = minPos + pos[2];
		Tool.trace("Z Phase offset "+minPos+" as "+min);


	    }
	
	    // output results		
	    {
		ImageStackOutput isoMag = 
		    new DisplayWrapper5D( width, height, depth, 3,3,"full comp. OTFs - mag");
		ImageStackOutput isoPha = 
		    new DisplayWrapper5D( width, height, depth, 3,3,"full comp. OTFs - pha");
	
		for (int comp=0; comp<3; comp++) {
		    for (int b=0;b<nrBands;b++) {
			Vec3d.Cplx tmp = bands[b].duplicate();
			
			if (comp==1) {
			    tmp.fourierShift( pos[0],pos[1],pos[2]);
			} 
			if (comp==2) {
			    tmp.fourierShift( posComp[0],posComp[1], pos[2]); 
			}
    

			Vec3d.Cplx tmp2 = Vec3d.createCplx(tmp);
			Transforms.swapQuadrant(tmp, tmp2);

			for (int z=0;z<depth;z++) {
			    Vec2d.Cplx res  = Vec2d.createCplx( width, height );
			    Vec2d.Real img1 = Vec2d.createReal( width, height );
			    Vec2d.Real img2 = Vec2d.createReal( width, height );
			    res.slice( tmp2, z);
			    img1.copyMagnitude( res );
			    img2.copyPhase( res );

			    isoMag.setImage( img1 , z, comp, b,"mag");
			    isoPha.setImage( img2 , z, comp, b,"pha");
			}
		    }
		}
		isoMag.update();
		isoPha.update();
	    }
		
			
	
	}

	iso1.update();

	return null;

    }


    // TODO: this should use a more robust center-fitting algorithm?!
    // get the bead position ( by simple center-of-mass )
    static double [] getBeadPos( Vec3d.Cplx img ) {
	
	int [] maxPos = new int[3];
	double max = 0;

	final int win1 = 2;
	final int win2 = 5;
	final int win21 = win1+win2;

	// locate peak intensity in win1^3 window
	for (int z=win21; z<img.vectorDepth()-win21; z++) 
	for (int y=win21; y<img.vectorHeight()-win21; y++) 
	for (int x=win21; x<img.vectorWidth()-win21; x++) {
	    
	    double sum=0;
	    for (int dz=-win1; dz<=win1; dz++) 
	    for (int dy=-win1; dy<=win1; dy++) 
	    for (int dx=-win1; dx<=win1; dx++) {
		sum += img.get(x+dx,y+dy,z+dz).abs();
	    }
	
	    if (sum>max) {
		max=sum;
		maxPos[0]=x; maxPos[1]=y; maxPos[2]=z;
	    }
	
	}

	Tool.trace("BEAD FIT: pxl prec. peak found at "+maxPos[0]+" "+maxPos[1]+" "+maxPos[2]);	

	// do a center-of-mass fit in a window +-4
	double [] pos = new double [4];
	
	for (int z=maxPos[2]-win2; z<=maxPos[2]+win2; z++) 
	for (int y=maxPos[1]-win2; y<=maxPos[1]+win2; y++) 
	for (int x=maxPos[0]-win2; x<=maxPos[0]+win2; x++) {
	    double val = img.get(x,y,z).abs();
	    pos[0] += val;
	    pos[1] += val*x;
	    pos[2] += val*y;
	    pos[3] += val*z;
	}

	pos[1]/=pos[0];
	pos[2]/=pos[0];
	pos[3]/=pos[0];

	Tool.trace(String.format(
	    "BEAD FIT: sub-pxl prec. peak found at %7.4f %7.4f %7.4f",pos[1],pos[2],pos[3]));	

	return new double[] { pos[1], pos[2], pos[3] };

    }




    /** This is to smooth in Fourier space */
    public static Vec3d.Cplx genSimpleApotize( int w, int h, int d ) {
	Vec3d.Cplx ret = Vec3d.createCplx( w,h,d);

	for (int z=0; z<d; z++) {
	    for (int y=0; y<h; y++) {
		for (int x=0; x<w; x++) {
		    double xh = (x>w/2)?(w-x):(x);
		    double yh = (y>h/2)?(h-y):(y);
		    double zh = (z>d/2)?(d-z):(z);
		    double pos = MTool.fhypot(xh/w, yh/h, zh/d );
		    ret.set(x,y,z, new Cplx.Double(Math.max(1-2*pos,0)) );	
		}
	    }
	}
	return ret;
    }


    public static double crossCorrelateX( Vec3d.Cplx data ) {
	final int w = data.vectorWidth();
	final int h = data.vectorHeight();
	final int d = data.vectorDepth();

	int z0=0;
	double xLeft=0, xRight=0; 

	for (int y0=2; y0<4; y0++) {
	    for (int x0=2;x0<30;x0++) {
		xLeft  += data.get(x0,y0,z0).phase();    
		xRight += data.get(x0,h-y0,z0).phase();    
	    
		data.set(x0,y0,z0,new Cplx.Float(-4));
		data.set(x0,h-y0,z0,new Cplx.Float(-4));
	    }
	}
	return Math.abs( xLeft-xRight);
    }

    public static double crossCorrelateY( Vec3d.Cplx data ) {
	final int w = data.vectorWidth();
	final int h = data.vectorHeight();
	final int d = data.vectorDepth();

	int z0=0;
	double xLeft=0, xRight=0; 

	for (int y0=2; y0<30; y0++) {
	    for (int x0=2;x0<4;x0++) {
		xLeft  += data.get(x0,y0,z0).phase();    
		xRight += data.get(w-x0,y0,z0).phase();    
	    
		data.set(x0,y0,z0,new Cplx.Float(-4));
		data.set(w-x0,y0,z0,new Cplx.Float(-4));
	    }
	}
	return Math.abs( xLeft-xRight);
    }
    
    public static double crossCorrelateZ( Vec3d.Cplx data ) {
	final int w = data.vectorWidth();
	final int h = data.vectorHeight();
	final int d = data.vectorDepth();

	double xLeft=0, xRight=0; 
	for (int z0=2; z0<4; z0++){
	    for (int y0=4; y0<8; y0++) {
		for (int x0=4;x0<20;x0++) {
		    xLeft  += data.get(x0,y0,z0).phase();    
		    xRight += data.get(x0,y0,d-z0).phase();    
	    
		    data.set(x0,y0,z0,new Cplx.Float(-4));
		    data.set(x0,y0,d-z0,new Cplx.Float(-4));
		}
	    }
	    for (int y0=8; y0<20; y0++) {
		for (int x0=4;x0<8;x0++) {
		    xLeft  += data.get(x0,y0,z0).phase();    
		    xRight += data.get(x0,y0,d-z0).phase();    
	    
		    data.set(x0,y0,z0,new Cplx.Float(-4));
		    data.set(x0,y0,d-z0,new Cplx.Float(-4));
		}
	    }
	}
	return Math.abs( xLeft-xRight);
    }





}
