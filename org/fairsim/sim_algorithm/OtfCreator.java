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
	ImageStackOutput iso2 = new DisplayWrapper5D( width, height, depth, 2,nrBands*2,"full OTFs");

	// lets run everything with and w/o smoothing
	for (int smooth =0; smooth <2; smooth++) {

	    Tool.trace(" --- run "+((smooth>0)?("with"):("w/o"))+" smooth ---");

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
		bands[0] = separate[0];	bands[1] = separate[2];	bands[2] = separate[4];

		// if we're on a smooth loop run, smooth by fft'd
		if (smooth>0) {
		    Vec3d.Cplx apo = genSimpleApotize( width, height, depth );
		    for (int b=0; b<nrBands; b++) {
			bands[b].fft3d(false);
			bands[b].times( apo );
			bands[b].fft3d(true);
		    }
		}

		// output the band-separated input data
		for (int z=0;z<depth;z++) {
		    for (int b=0;b<nrBands;b++) {
			Vec2d.Real res = Vec2d.createReal( width, height );
			res.slice( separate[b*2] , z);
			iso1.setImage( res , z, smooth ,b, "");
		    }
		}

		// locate the bead in the pseudo-widefield 
		double [] pos = getBeadPos( bands[0] );
		Tool.trace(String.format(" bead found at: %7.3f %7.3f %7.3f ", pos[0],pos[1],pos[2]));
		
		// compensate bead position
		for (int b=0; b<nrBands; b++) {
		    //bands[b].fourierShift( -(width/2-pos[0]), -(height/2-pos[1]),-(depth/2-pos[2]));	    
		    bands[b].fourierShift( -pos[0], -pos[1],-pos[2]);	    
		}
		// generate the otf 
		for (int b=0; b<nrBands; b++) {
		    bands[b].fft3d(false);
		}


		// visualize result
		for (int b=0;b<nrBands;b++) {
		    Vec3d.Cplx tmp = bands[b].duplicate();
		    //Transforms.swapQuadrant(bands[b], tmp);

		    for (int z=0;z<depth;z++) {
			Vec2d.Cplx res  = Vec2d.createCplx( width, height );
			Vec2d.Real img1 = Vec2d.createReal( width, height );
			Vec2d.Real img2 = Vec2d.createReal( width, height );
			res.slice( tmp , z);
			img1.copyMagnitude( res );
			img2.copyPhase( res );

			iso2.setImage( img1 , z,smooth,b*2,"mag");
			iso2.setImage( img2 , z,smooth,b*2+1,"pha");
		    }
		}

		

	    }

	}



	iso1.update();
	iso2.update();

	return null;

    }


    // TODO: this should use a more robust center-fitting algorithm?!
    // get the bead position ( by simple center-of-mass )
    static double [] getBeadPos( Vec3d.Cplx img ) {
	
	double [] pos = new double [4];

	for (int z=0; z<img.vectorDepth(); z++) {
	    for (int y=0; y<img.vectorHeight(); y++) {
		for (int x=0; x<img.vectorWidth(); x++) {
		    pos[0] += img.get(x,y,z).abs() ;
		    pos[1] += img.get(x,y,z).abs() *x;
		    pos[2] += img.get(x,y,z).abs() *y;
		    pos[3] += img.get(x,y,z).abs() *z;
		}
	    }
	}

	return new double [] { pos[1]/pos[0], pos[2]/pos[0], pos[3]/pos[0] };

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




}
