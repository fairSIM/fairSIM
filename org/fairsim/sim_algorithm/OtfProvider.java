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

import org.fairsim.linalg.Vec2d;
import org.fairsim.linalg.Cplx;
import org.fairsim.linalg.MTool;

import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;
import org.fairsim.utils.SimpleMT;


/**
 * OTFs and associated functions (attenuation, apotization, ...).
 * Provides loading, saving, simple estimation, conversion
 * from 1D (radially symmetric, in phys. units) to 2D vectors.
 * */
public class OtfProvider {
    
    // --- internal parameters ----

    // vals[band][idx], where idx = cycles / cyclesPerMicron
    private Cplx.Float [][] vals	=  null; 
    private Cplx.Float [][] valsAtt	=  null; 
    private float      [][] valsOnlyAtt =  null; 
    
    // physical units
    private double cyclesPerMicron;
    private double na, lambda, cutOff;
    
    // was the OTF set from estimate? 
    // Are there different bands? If so, how many?
    private boolean isEstimate, isMultiBand; 
    int maxBand=1; 
    double estimateAValue=.3;
    
    // for estimate: sample with how many points?
    // for data: this is overridden by data
    private int samplesLateral = 512;

    // for vector output: physical pixel size
    private int vecSize=-1;
    private double vecCyclesPerMicron=-1;

    // attenuation strength (0..1) and fhwm (in cycles/micron)
    private double attStrength = .99, attFWHM = 1.2;
    private boolean useAttenuation;


    /** For [0..cutoff] normalized to [0..1], return the ideal OTF. OTF of an ideal, 
     *  i.e. abberation-free lens system, resolution-limited by a circular pupil in
     *  the Fourier place,
     *  "Joseph W. Goodman, Introduction to Fourier Optics, 3. edition, page 145".
     *  @param dist Distance to cutoff, in normalized range 0..1. Values outsize of 0..1 return 0.
     */
    public static double valIdealOTF(double dist) {
	if ((dist<0)||(dist>1))
	    return 0;
	return (2/Math.PI)*(Math.acos(dist) - dist*Math.sqrt(1-dist*dist)); 
    }


    // ------ setup / access methods -------

    /** use factory methods instead */
    private OtfProvider() {};


    /** Create a new OTF from a (very basic) estimate.
     *  The curvature factor account for deviation of
     *  real-world OTFs from the theoretical optimum.
     *  a=1 yields an ideal OTF, a=0.2 .. 0.4 are more realistic,
     *  a has no effect on cutoff.
     *  @param na Objectives NA
     *  @param lambda Emission wavelength (nm)
     *  @param a curvature factor, a = [0..1]
     * */
    public static OtfProvider fromEstimate(double na, double lambda, double a) {
	if ( (a<0)||(a>1) || (na<0.3) || (na>2.2) || (lambda<300) || (lambda>1500) )
	    throw new IllegalArgumentException("unphysical input parameters");
	
	OtfProvider ret = new OtfProvider();

	// compute / set parameters
	ret.na = na; ret.lambda = lambda;
	ret.cutOff = 1000 / ( lambda / na / 2 ); 
	ret.cyclesPerMicron = ret.cutOff / ret.samplesLateral ;
	ret.vals	= new Cplx.Float[1][ ret.samplesLateral ];
	ret.valsAtt	= new Cplx.Float[1][ ret.samplesLateral ];
	ret.valsOnlyAtt = new	   float[1][ ret.samplesLateral ];
	ret.isMultiBand = false;
	ret.isEstimate  = true;
	ret.estimateAValue = a;

	// sample some values up to cutoff
	for (int i=0; i< ret.samplesLateral ; i++) {
	    // v: normalize [0..cutoff] -> [0..1]
	    double v = i/(double)ret.samplesLateral ;
	    // get OTF at v, multiply empirical correction for curvature
	    float r =(float)( valIdealOTF(v) * ( Math.pow(a,v) ) );
	    ret.vals[0][i] = new Cplx.Float( r, 0 );
	}
	
	// initialize attenuation cache
	ret.setAttenuation( ret.attStrength, ret.attFWHM );
	ret.switchAttenuation( false );

	return ret;
    }

    /** Returns a short description (GUI display, etc) */
    public String printState(boolean html) {
	String ret ="";
	if (html)
	    ret = String.format("NA %4.2f, \u03bb<sub>em</sub> %4.0f, ", na, lambda);
	else 
	    ret = String.format("NA %4.2f, lambda %4.0f, ", na, lambda);
	if (isEstimate) 
	    ret+=String.format("(est., a=%4.2f)", estimateAValue);
	else
	    ret+=String.format("(from file)");
	return ret;
    }


    /** Return the OTF cutoff, unit is cycles/micron */
    public double getCutoff() {
	return cutOff;
    }

    /** Get the OTF value at 'cycl'.  
     *  @param band OTF band
     *  @param cycl Position in cycles/micron
     *  @param att  If true, return attenuated value (see {@link #setAttenuation})
     * */
    public Cplx.Float getOtfVal(int band, double cycl, boolean att) {
	// checks
	if ( !this.isMultiBand ) 
	    band=0;
	if ((band >= maxBand)||(band <0))
	    throw new IndexOutOfBoundsException("band idx too high or <0");
	if ( cycl < 0 )
	    throw new IndexOutOfBoundsException("cylc negative!");
	
	// out of support, return 0
	if ( cycl >= cutOff )
	    return Cplx.Float.zero();
	
	final double pos = cycl / cyclesPerMicron;
	if (Math.ceil(pos) >= samplesLateral)
	    return Cplx.Float.zero();

    
	// for now, linear interpolation, could be better with a nice cspline
	int lPos = (int)Math.floor( pos );	
	int hPos = (int)Math.ceil( pos );
	float f = (float)(pos - lPos);
    
	if (att) {
	    Cplx.Float retl = valsAtt[band][lPos].mult( 1-f );
	    Cplx.Float reth = valsAtt[band][lPos].mult( f );
	    return Cplx.add( retl, reth );
	} else {
	    Cplx.Float retl = vals[band][lPos].mult( 1-f );
	    Cplx.Float reth = vals[band][lPos].mult( f );
	    return Cplx.add( retl, reth );
	}

    }
   
    /** Sets pixel size, for output to vectors
     *	@param cyclesPerMicron Pixel size of output vector, in cycles/micron */
    public void setPixelSize( double cyclesPerMicron ) {
	if (cyclesPerMicron<=0)
	    throw new IllegalArgumentException("pxl size must be positive");
	vecCyclesPerMicron=cyclesPerMicron;
    }

    // ------ Attenuation -----

    /** Returns the attenuation value at dist.
     *  @param dist Distance to center in cycles/micron
     *  @param str  Strength of the attenuation
     *  @param fwhm FWHM of Attenuation, in cycles/micron  */
    public static float valAttenuation( final double dist, final double str, final double fwhm) {
	    return (float)(1 - str*Math.exp( -( Math.pow(dist,2) ) / (2*Math.pow( fwhm/2.355 ,2)) ));
    }

    /** Get the attenuation value at 'cycl'. 
     *  @param band OTF band
     *  @param cycl Position in cycles/micron
     * */
    public float getAttVal(int band, double cycl) {
	// checks (TODO: mult-band support for attenuation)
	if ( !this.isMultiBand ) 
	    band=0;
	if ((band >= maxBand)||(band <0))
	    throw new IndexOutOfBoundsException("band idx too high or <0");
	if ( cycl < 0 )
	    throw new IndexOutOfBoundsException("cylc negative!");
	
	// out of support, return 1
	if ( cycl >= cutOff )
	    return 1.f;
	
	double pos = cycl / cyclesPerMicron;
    
	// for now, linear interpolation, could be better with a nice cspline
	int lPos = (int)Math.floor( pos );	
	int hPos = (int)Math.floor( pos );
	float f = (float)(pos - lPos);
    
	float retl = valsOnlyAtt[band][lPos] * f ;
	float reth = valsOnlyAtt[band][lPos] * ( 1-f );
	return retl+reth;
    }
    
    /** Sets the OTF attenuation parameters.
     * Attenuation is also swichted on by this function.
     * @param strength Strength of attenuation, 0..1, usually 0.9 .. 0.99
     * @param fwhm     FWHM of the attenuation, in cycles / micron
     * */
    public void setAttenuation( double strength, double fwhm) {
	
	attStrength = strength; attFWHM = fwhm;
	
	// update cached attenuation values
	for ( int b = 0; b<vals.length; b++)
	for ( int v = 0; v<vals[b].length; v++) {
	    double dist = v * cyclesPerMicron;
	    valsOnlyAtt[b][v] = valAttenuation( dist, attStrength, attFWHM ) ;
	    valsAtt[b][v] = vals[b][v].mult( valsOnlyAtt[b][v] ); 
	}
    }

    /** Like {@link #setAttenuation} called with the current FWHM */
    public void setAttenuationStrength( double strength ) {
	setAttenuation( strength, attFWHM );
    }
    /** Like {@link #setAttenuation} called with the current strength */
    public void setAttenuationFWHM( double fwhm ) {
	setAttenuation( attStrength, fwhm );
    }
    


    /** Set if to apply attenuation to the OTF. */
    public void switchAttenuation( boolean on ) {
	useAttenuation = on;
    }
    
    /** Get if attenuation will be used. */
    public boolean isAttenuate() {
        return useAttenuation;
    }
    
    /** Get attenuation strength */
    public double getAttStr(int band) {
	return this.attStrength;
    }
    
    /** Get attenuation FWHM */
    public double getAttFWHM(int band) {
	return this.attFWHM;
    }

    // ------ applying OTF to vectors -------

    /** Multiplies / outputs OTF to a vector. Quite general function,
     *  some wrappers are provided for conveniece. 
     *  @param vec  Vector to write / multiply to
     *	@param band OTF band 
     *	@param kx OTF center position offset x
     *	@param ky OTF center position offset y
     *  @param useAtt if to use attenuation (independent of how {@link #switchAttenuation} is set)
     *  @param write  if set, vector is overridden instead of multiplied
     *  */
    public void otfToVector( final Vec2d.Cplx vec, final int band, 
	final double kx, final double ky,
	final boolean useAtt, final boolean write ) {
	
	// parameters
	if (vecCyclesPerMicron <=0)
	    throw new IllegalStateException("Vector pixel size not initialized");
	final int w = vec.vectorWidth(), h = vec.vectorHeight();

	// loop output vector
	new SimpleMT.StrPFor(0,h) {
	    public void at(int y) {
		for (int x=0; x<w; x++) {
		    // wrap to coordinates: x in [-w/2,w/2], y in [-h/2, h/2]
		    double xh = (x<w/2)?( x):(x-w);
		    double yh = (y<h/2)?(-y):(h-y);
		    // from these, calculate distance to kx,ky, convert to cycl/microns
		    double rad = MTool.fhypot( xh-kx, yh-ky );
		    double cycl = rad * vecCyclesPerMicron;
		    
		    // over cutoff? just set zero
		    if ( cycl > cutOff ) {
			vec.set(x,y, Cplx.Float.zero());
		    } 
		    
		    // within cutoff?
		    if ( cycl <= cutOff ) {
		    
			// get the OTF value
			Cplx.Float val = getOtfVal(band, cycl, useAtt);

			// multiply to vector or write to vector
			if (!write) {
			    vec.set(x, y, vec.get(x,y).mult( val.conj() ) );
			} else {
			    vec.set(x, y, val );
			}
		    }
		}
	    }
	}; 

    }

    // ------ Applying OTF to vectors ------
    
    /** Multiplied conjugated OTF to a vector.
     *  The desired vector pixel size has to be set (via {@link #setPixelSize}) first.
     *  Attenuation is used if set via {@link #switchAttenuation}.
     *  @param vec  Vector to write to
     *	@param band OTF band */
    public void applyOtf(Vec2d.Cplx vec, final int band) {
	otfToVector( vec, band, 0, 0, useAttenuation, false ) ; 
    }

    /** Multiplied conjugated OTF to a vector.
     *  The desired vector pixel size has to be set (via {@link #setPixelSize}) first.
     *  Attenuation is used if set via {@link #switchAttenuation}.
     *  @param vec  Vector to write to
     *	@param band OTF band 
     *	@param kx OTF center position offset x
     *	@param ky OTF center position offset y */
    public void applyOtf(Vec2d.Cplx vec, final int band, double kx, double ky ) {
	otfToVector( vec, band, kx, ky, useAttenuation, false ) ; 
    }

    /** Create a 2-dimension, radial symmetric vector of the OTF, centered at kx,ky.
     *  The desired vector pixel size has to be set (via {@link #setPixelSize}) first.
     *  Attenuation is not applied. 
     *  @param vec  Vector to write to
     *	@param band OTF band 
     *	@param kx Position / offset kx
     *	@param ky Poistion / offset ky
     *	*/
    public void writeOtfVector(final Vec2d.Cplx vec, final int band, 
	final double kx, final double ky) {
	otfToVector( vec, band, kx, ky, false, true ) ; 
    }
    
    /** Create a 2-dimension, radial symmetric vector of the OTF, centered at kx,ky.
     *  The desired vector pixel size has to be set (via {@link #setPixelSize}) first.
     *  Attenuation is applied if set via {@link #switchAttenuation}. 
     *  @param vec  Vector to write to
     *	@param band OTF band 
     *	@param kx Position / offset kx
     *	@param ky Poistion / offset ky
     *	*/
    public void writeOtfWithAttVector(final Vec2d.Cplx vec, final int band, 
	final double kx, final double ky) {
	otfToVector( vec, band, kx, ky, useAttenuation, true ) ; 
    }




    // ------ Other vectors ------

    /** Creates an apotization vector.
     *  This yields an ideal OTF, with some 'bend' (augmenting medium frequencies),
     *  and a new cutoff value. Usually, cutoff is set to 2x original cutoff.
     *  Bend is used as "apo = idealOtf^bend"
     *  @param vec  Vector to write to
     *  @param bend Bend to augment medium frequencies, 0..1, 1 for ideal OTF
     *  @param cutOff Cutoff, as factor to the OTF cutoff (so e.g. 2 for 2x lateral improvement) */
    public void writeApoVector(final Vec2d.Cplx vec, final double bend, final double cutOff ) {
	if (vecCyclesPerMicron <=0)
	    throw new IllegalStateException("Vector pixel size not initialized");
	final int w = vec.vectorWidth(), h = vec.vectorHeight();

	//for (int y=0; y<h; y++)
	new SimpleMT.PFor(0,h) {
	    public void at(int y) {
		for (int x=0; x<w; x++) {
		    // wrap to coordinates: x in [-w/2,w/2], y in [-h/2, h/2]
		    double xh = (x<w/2)?( x):(x-w);
		    double yh = (y<h/2)?(-y):(h-y);
		    // from these, calculate distance to 0, convert to phys. units
		    double rad = MTool.fhypot( xh, yh );
		    double cycl = rad * vecCyclesPerMicron;
		    // calculate fraction of cutoff, get idealOTF, augment with 'bend'
		    double frac = cycl / (getCutoff()*cutOff);
		    double val  = Math.pow( valIdealOTF( frac ), bend );
		    // set output to that value	
		    vec.set(x,y, new Cplx.Float((float)val));
		}
	    }
	}; 
    }

    /** Creates an attenuation vector (for optical sectioning).
     *  Pixel size of output has to be set via {@link #setPixelSize}.
     *  @param out  The vector that will be multiplied
     *  @param str  Strength of the attenuation
     *  @param fwhm FWHM of Attenuation, in cycles/micron 
     *  @param kx   x-pos of the attenuation
     *  @param ky   y-pos of the attenuation
     *  */
    public void writeAttenuationVector(final Vec2d.Real out, final double str, final double fwhm, 
	final double kx, final double ky) {
    
	if (vecCyclesPerMicron <=0)
	    throw new IllegalStateException("Vector pixel size not initialized");
       
	final int w = out.vectorWidth();
	final int h = out.vectorHeight();

	//for (int y= 0;y<h ;y++)
	new SimpleMT.PFor(0,h) {
	    public void at(int y) {
		for (int x= 0;x<w ;x++) {
		
		    // wrap to coordinates: x in [-w/2,w/2], y in [-h/2, h/2]
		    double xh = (x<w/2)?( x):(x-w);
		    double yh = (y<h/2)?(-y):(h-y);

		    // from these, calculate distance to kx,ky, convert to cycl/microns
		    double rad = MTool.fhypot( xh-kx, yh-ky );
		    double cycl = rad * vecCyclesPerMicron;
		    
		    out.set(x,y, valAttenuation(cycl,str,fwhm) );
		}
	    }
	};
    }

    // ------ Mask OTF region ------
   
    /** Set components not covered by OTF support (see {@link #getCutoff}) to zero.
     *  @param vec The vector to multiply with the conj. OTF
     *  @param kx   x-pos of the attenuation
     *  @param ky   y-pos of the attenuation
     * */
    public void maskOtf(final Vec2d.Cplx vec, final double kx, final double ky) {
	
	final int w = vec.vectorWidth(), h = vec.vectorHeight();
	final double otfSupport = getCutoff();

	//for (int y=0; y<h; y++) {{
	new SimpleMT.PFor(0, h) {
	    public void at(int y) {
		for (int x=0; x<w; x++) {
		    // wrap to coordinates: x in [-w,w], y in [-h, h]
		    double xh = (x<w/2)?( x):(x-w);
		    double yh = (y<h/2)?(-y):(h-y);
		    // from these, calculate distance to (kx,ky), convert to cycl/microns
		    double rad = MTool.fhypot( xh-kx, yh-ky ) * vecCyclesPerMicron;
		    // if outside of support, set zero
		    if (rad>otfSupport)
			vec.set(x,y, Cplx.Float.zero());
		}
	    }
	}; 

    }

    // ------ Load / Save operations ------

    /** Create an OTF stored in a config.
     *	@param cfg The config to load from
     *  */
    public static OtfProvider loadFromConfig( Conf cfg ) 
	throws Conf.EntryNotFoundException {

	Conf.Folder fld = cfg.r().cd("otf2d");
	OtfProvider ret = null;
	boolean estimate = (!fld.contains("data"));
	
	// Initialize as estimate
	if (estimate) {
	    ret = fromEstimate(
		fld.getDbl("NA").val(),
		fld.getInt("emission").val(),
		fld.getDbl("a-estimate").val()
		);
	}
	
	// Initialize from data	
	if (!estimate) {
	
	    // main parameters
	    ret = new OtfProvider();
	    ret.na	    = fld.getDbl("NA").val();
	    ret.lambda  = fld.getInt("emission").val();
	    ret.cutOff  = 1000 / (ret.lambda / ret.na /2);
	    ret.isEstimate = false;
	    
	    // copy parameters
	    Conf.Folder data = fld.cd("data");
	    ret.maxBand = data.getInt("bands").val();
	    ret.isMultiBand = (ret.maxBand>1);
	    ret.isEstimate  = false;
	    ret.samplesLateral =data.getInt("samples").val(); 

	    ret.cyclesPerMicron = data.getDbl("cycles").val();
	    
	    // init bands
	    ret.vals	= new Cplx.Float[ret.maxBand][ ret.samplesLateral ];
	    ret.valsAtt	= new Cplx.Float[ret.maxBand][ ret.samplesLateral ];
	    ret.valsOnlyAtt = new  float[ret.maxBand][ ret.samplesLateral ];

	    // copy bands
	    for (int b=0; b<ret.maxBand; b++) {
		byte  [] bytes = data.getData(String.format("band-%d",b)).val();
		float [] val   = Conf.fromByte( bytes );
		
		for (int i=0; i< ret.samplesLateral ; i++) {
		    ret.vals[b][i] = new Cplx.Float( val[2*i], val[2*i+1]);	    
		    
		}
	    }
	
	    ret.setAttenuation( ret.attStrength, ret.attFWHM );
	    ret.switchAttenuation( false );
	}

	// attenuation, if set
	if ( fld.contains("attenuation") ) {
	    Conf.Folder att = fld.cd("attenuation");
	    ret.setAttenuation( att.getDbl("strength").val(), att.getDbl("FWHM").val());
	    ret.switchAttenuation( true );
	}
	
	return ret;
    }

    /** Write out an OTF to a config */
    public void saveConfig( Conf cfg ) {

	Conf.Folder fld  = cfg.r().mk("otf2d");
	fld.newDbl("NA").setVal( this.na );
	fld.newInt("emission").setVal( (int)this.lambda );
	
	// if this is an estimate
	if ( this.isEstimate ) {
	    fld.newDbl("a-estimate").setVal( this.estimateAValue );
	}

	// if this was read from file
	if ( !this.isEstimate ) {
	    Conf.Folder data = fld.mk("data");
	    data.newInt("bands").setVal( this.maxBand );
	    data.newInt("samples").setVal( this.samplesLateral );
	    data.newDbl("cycles").setVal( this.cyclesPerMicron );
	    
	    // store bands
	    for (int b=0; b< this.maxBand; b++) {
		float [] tmp = new float[ this.samplesLateral * 2 ];
		for ( int i=0; i<tmp.length/2; i++) {
		    tmp[2*i+0] = this.vals[b][i].re;
		    tmp[2*i+1] = this.vals[b][i].im;
		}
		byte [] outData = Conf.toByte( tmp );
		data.newData(String.format("band-%d",b)).setVal( outData );
	    }
	}

	// attenuation
	if ( this.useAttenuation ) {
	    Conf.Folder att = fld.mk("attenuation");
	    att.newDbl("strength").setVal( attStrength ); 
	    att.newDbl("FWHM").setVal( attFWHM ); 
	}




    }





    // ------ Testing ------

    /** For testing, outputs some OTF to stdout */
    public static void main( String args [] ) throws Exception {

	if (args.length==0) {
	    System.out.println("Use: t - Timing, o - Output, i - Input ");
	    return;
	}

	OtfProvider otf = OtfProvider.fromEstimate( 1.4, 515, .241);
	
	// output
	if (args[0].equals("o")) {

	    for (float cycl = 0; cycl < 6; cycl +=0.025 )
		System.out.println(String.format(" %5.3f %6.4f A", 
		    cycl, otf.getOtfVal(0, cycl, false).re ));
	    for (int i=0;i<512;i++)
		System.out.println(String.format(" %d %6.4f B",  
		    i, otf.getOtfVal(0, i/(512*0.082), false ).re )); 
	    
	    // save as test 
	    if ( args.length >= 2 ) {
		Conf cfgOut = new Conf("fairsim");
		otf.saveConfig(cfgOut);
		cfgOut.saveFile( args[1]);
	    }
	}
	
	// timing
	if (args[0].equals("t")) {

	    otf.setPixelSize(0.023);
	    //otf.switchAttenuation( true );
	    Vec2d.Cplx [] test = Vec2d.createArrayCplx(15,512,512);

	    for (int loop=0; loop<5; loop++) {

		// Test1: Create the OTF once, cache, apply from cache
		Tool.Timer t1 = Tool.getTimer();
		Vec2d.Cplx otfCache = Vec2d.createCplx( 512,512 );
		otf.writeOtfVector( otfCache, 0, 20, 50);
		t1.start();
		for (int i=0;i<15; i++) 
		    test[i].timesConj( otfCache );
		t1.stop();
		
		// Test1: Apply OTF on the fly
		Tool.Timer t2 = Tool.getTimer();
		t2.start();
		for (int i=0;i<15; i++) 
		    otf.applyOtf( test[i], 0 ) ;
		t2.stop();
		
		Tool.trace("OTF cached: "+t1);
		Tool.trace("OTF on-fly: "+t2);
	    }
	}
	
	// input
	if (args[0].equals("i")) {

	    Conf cfg = Conf.loadFile( args[1] );

	    OtfProvider otfl = OtfProvider.loadFromConfig( cfg ); 

	    for (float cycl = 0; cycl < 6; cycl +=0.025 )
		System.out.println(String.format(" %5.3f %6.4f A", 
		    cycl, otfl.getOtfVal(0, cycl, false).re ));
	    
	    // save as test 
	    if ( args.length >= 3 ) {
		Conf cfgOut = new Conf("fairsim");
		otfl.saveConfig(cfgOut);
		cfgOut.saveFile( args[2]);
	    }

	}



	Tool.shutdown();
    }

}
