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

import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;
import org.fairsim.linalg.Vec2d;

/** Storage / management of SIM reconstruction parameters. 
 *  Provides a good way to connect GUI and algorithm, as checks,
 *  conversions etc. can happen in this class instead of cluttering other
 *  code.
 * */
public class SimParam implements Vec2d.Size {

    public enum FilterStyle {
	Wiener("Wiener filter"),	
	RLin("RL on input"), 
	RLout("RL on output"),
	RLboth("RL on both");

	final String name;
	FilterStyle(String a) { name = a; };

	@Override public String toString() { return name; };
    }

    /** number of angles/pattern directions */
    final protected int nrDirs;	
    final protected int nrBands;	
    final protected int nrPhases;
    final protected Dir [] directions ;

    // Image parameters
    private int imgSize=-1;			    // image size in pxl
    private double micronsPerPixel=-1;		    // spatial extent of pixel
    private double cyclesPerMicron=-1;		    // freq extent of FFT pixel

    private int stackSize=-1;			    // z-stack length in #slices
    private double micronsPerSlice=-1;		    // spatial extent of the z-slice stage shift
    private double cyclesPerMicronInZ=-1;	    // freq extent of FFT in z-slices

    private IMGSEQ imgSeq = IMGSEQ.PAZ;		    // order of images in input
    private CLIPSCALE clipScaleMode 
	= CLIPSCALE.BOTH;			    // clip&scale of output


    private FilterStyle filterStyle = 
		FilterStyle.Wiener;		    // which filter to use

    private double wienerFilterParameter = 0.05;    // Wiener filter parameter
    private double apoCutOff = 2;		    // Apo cutoff parameter

    private int rlIterations = 5;		    // number of Richardson-Lucy iterations
    

    double modLowLimit = 0.3, modHighLimit = 1.1;

    // Transfer function, OTF attenuation, Apotization
    private OtfProvider   currentOtf2D=null;

    private long runtimeTimestamp = 0;


    /** Use factory method {@link #create} to obtain object */
    protected SimParam(int bands, int dirs, int phases, boolean threeD) { 
	if (dirs<1)
	    throw new RuntimeException("dirs < 1, not useful");
	
	nrDirs   = dirs;
	nrBands  = bands;
	nrPhases = phases;

	directions = new Dir[ nrDirs ];
	for (int i=0; i<nrDirs; i++)
	    directions[i] = new Dir( bands, phases, i );
	
    }
    
    /** New set of SIM reconstruction parameters for 2D reconstruction. 
     *	@param bands  Number of bands
     *	@param dirs   Number of pattern orientations
     *	@param phases Number of phases
     * */
    public static SimParam create(int bands, int dirs, int phases ) {
	return new SimParam( bands, dirs, phases, false);
    }
    
    /** New set of SIM reconstruction parameters for 2D reconstruction. 
     *	@param bands  Number of bands
     *	@param dirs   Number of pattern orientations
     *	@param phases Number of phases
     *	@param size Size in pixels (width or height) of raw images
     *	@param micronsPerPxl Size of a pixel in micrometers 
     * */
    public static SimParam create(int bands, int dirs, int phases, 
	int size, double micronsPerPxl, OtfProvider otf ) {
	SimParam sp = new SimParam( bands, dirs, phases, false);
	sp.setPxlSize( size, micronsPerPxl );
	sp.otf( otf );
	return sp;
    }
   
    
    /** Get parameter subset for pattern direction 'i' */
    public Dir dir(int i) {
	return directions[i];
    }

    /** Get the number of directions */
    public int nrDir() {
	return nrDirs;
    }
    
    /** Get the number of bands */
    public int nrBand() {
	return nrBands;
    }

    /** Get cycles / micron pxl size */
    public double pxlSizeCyclesMicron() {
	return cyclesPerMicron;
    }
    
   
    
    /** Get the image ordering */
    public IMGSEQ getImgSeq() {
	return imgSeq;
    }
    /** Set the image ordering */
    public SimParam setImgSeq( IMGSEQ i) {
	imgSeq = i;
	return this;
    }

    /** Get the image ordering */
    public CLIPSCALE getClipScale() {
	return clipScaleMode;
    }
    
    /** Set the image ordering */
    public SimParam setClipScale( CLIPSCALE i) {
	clipScaleMode = i;
	return this;
    }

    /** Get the number of images in one z plane */
    public int getImgPerZ() {
	int ret=0;
	for ( Dir d : directions )
	    ret+= d.nrPha();
	return ret;
    }

    /** Set the image size
     *  @param pxl Number of pixels
     *  @param microns Number of microns per pxl */
    public SimParam setPxlSize( int pxl, double microns ) {
	imgSize  = pxl;
	micronsPerPixel = microns;
	cyclesPerMicron = 1/(pxl*microns);
	this.otf( currentOtf2D );	// propagate size to OTF
	return this;
    }



    /** Set the filter type to use */
    public void setFilterStyle( FilterStyle s ) {
	filterStyle = s;
    }

    /** Get the filter type to use */
    public FilterStyle getFilterStyle() {
	return filterStyle;
    }

    /** Determine if Wiener-filtering is used on output data*/
    public boolean useWienerFilter() {
	return ( filterStyle == FilterStyle.Wiener ||
		 filterStyle == FilterStyle.RLin );
    }



    /** Determine if RL-filtering is used on input data*/
    public boolean useRLonInput() {
	return ( filterStyle == FilterStyle.RLboth || 
		 filterStyle == FilterStyle.RLin   );
    }
    
    /** Determine if RL-filtering is used on output data*/
    public boolean useRLonOutput() {
	return ( filterStyle == FilterStyle.RLboth || 
		 filterStyle == FilterStyle.RLout   );
    }


    /** Set the number of RL iterations */
    public void setRLiterations( int n ) {
	rlIterations = n;
    }

    /** Get the number of RL iterations */
    public int getRLiterations() {
	return rlIterations;
    }


    /** Set the Wiener Filter parameter */
    public SimParam setWienerFilter( double wf ) {
	wienerFilterParameter = wf;
	return this;
    }
    
    /** Get the Wiener Filter parameter */
    public double getWienerFilter() {
	return wienerFilterParameter;
    }
    
    /** Set the APO cutoff factor (cutoff in relation to OTF cutoff) */
    public SimParam setApoCutoff( double af ) {
	apoCutOff = af;
	return this;
    }
    
    /** Get the APO cutoff factor */
    public double getApoCutoff() {
	return apoCutOff;
    }


    /** Get the current otf */
    public OtfProvider otf() {
	return currentOtf2D;
    }
    

    /** Set a new OTF */
    public void otf(OtfProvider otf) {

	if (otf!=null) { 
	    currentOtf2D=otf;
	    otf.setPixelSize( cyclesPerMicron );
	}
    }


    // ----------------------------------------------------------------------------------

    /** Parameters for each pattern orientation. */
    public class Dir {
	
	private final int nrBands;  // how many bands
	private final int nrPhases; // how many phases
	private double pX, pY;	    // shift vector (band1)
	private double phaOff;	    // global phase offsets
	private double [] phases;   // phases
	private double [] modul;    // modulation 
	private int thisBand;	    // which band is this
	private boolean hasIndividualPhases;	// if non-equidist. phases are set

	Dir(int nBands, int nPha, int thisBand) {
	    
	    if (nBands<2) 
		throw new RuntimeException("bands < 2, not useful");
	    if (nPha < (nBands*2) -1)
		throw new RuntimeException("not enough phases for bands:"+nBands+" "+nPha);
	    
	    nrBands = nBands;
	    nrPhases = nPha;
	    phases = new double[ nrPhases ];
	    resetPhases();  // inits the phases to equidistant
	    modul=new double[ nrBands ];
	    this.thisBand = thisBand;

	    for (int i=0;i<nrBands;i++)
		modul[i] = 1.0;
	
	}

	void failBand(int i) {
	    if ((i<0)||(i>=nrBands))
		throw new RuntimeException("Wrong band index");
	}

	/** Return number of bands */
	public int nrBand() { return nrBands; }
	
	/** Return number of components (2*bands-1) */
	public int nrComp() { return nrBands*2-1; }


	// --- Phases ---

	/** Set a global phase offset.  */
	public void setPhaOff( double pha ) {
	    phaOff = pha;
	}

	/** Set individual phases.
	 *  @param pha Phases to set
	 *  @param reset If true, resets the phase offset to 0
	 * */
	public void setPhases( double [] pha, boolean reset ) {
	    if (pha.length != nrPhases )
		throw new RuntimeException("Length mismatch");
	    if (reset)
		phaOff = 0;
	    for (int i=0; i<nrPhases; i++)
		phases[i] = pha[i];
	    hasIndividualPhases = true;
	}

	/** Reset individual phases back to equidistant. */
	public void resetPhases() {
	    for (int i=0; i<nrPhases; i++) 
		phases[i] = ( 2*Math.PI / nrPhases ) * i;
	    hasIndividualPhases = false;
	}
	
	/** Get absolute phases. I.e. phases with the offset applied.
	 *  This can directly be passed as 
	 *  'phases' to {@link BandSeparation#separateBands}. */
	public double [] getPhases() {
	    double [] ret = new double[nrPhases];
	    for (int i=0; i<nrPhases; i++)
		ret[i] = phases[i] + phaOff;
	    return ret;
	}

	/** Get number of phases */
	public int nrPha() {
	    return nrPhases;
	}

	/** Get phase #i, without offset applied. */
	public double getPhase(int i) {
	    return phases[i];
	}
	/** Get the phase offset */
	public double getPhaOff(){
	    return phaOff;
	}


	// --- Shifts ---

	/** Set shift px, py (from estimate for highest band). 
	 *  Lower bands are set equidistant in between. 
	 *  @param cor Either an array (x[0],y[1]) or 2 numbers: x,y
	 *  */
	public void setPxPy(double ... cor) { 
	    if (cor.length<2)
		throw new RuntimeException("Array too short");
	    pX = cor[0]/(nrBands-1); 
	    pY = cor[1]/(nrBands-1); 
	}

	/** Return x for shift of band n. 
	 *  Bands count from 0.*/
	public double px(int band) {
	    failBand(band);
	    return pX*band;
	}
	
	/** Return y for shift of band n. 
	 *  Bands count from 0.*/
	public double py(int band) {
	    failBand(band);
	    return pY*band;
	}
    
	/** Return shift of band n. 
	 *  Bands count from 0.*/
	public double [] getPxPy(int band) {
	    failBand(band);
	    return new double [] { pX*band, pY*band };
	}

	/** Return angle of shift of band n (atan2). 
	 *  Bands count from 0.*/
	public double getPxPyAngle(int band) {
	    failBand(band);
	    return Math.atan2( pY*band, pX*band );
	}
	
	/** Return length of shift of band n (hypot). 
	 *  Bands count from 0.*/
	public double getPxPyLen(int band) {
	    failBand(band);
	    return Math.hypot( pY*band, pX*band );
	}

	// --- modulation ---
	/** Set the modulation of band n. */
	public boolean setModulation(int b, double m) {
	    failBand(b);
	    modul[b]=m;
	    return ((m>modLowLimit)&(m<modHighLimit));
	}

	/** Get modulations */
	public double [] getModulations() {
	    double [] ret = new double [nrBands];
	    System.arraycopy( modul, 0, ret, 0, nrBands );
	    // if any modulation is outside of limits, return limit
	    for (int b=0; b< nrBands; b++) {
		if (ret[b]<modLowLimit) ret[b]  = modLowLimit;
		if (ret[b]>modHighLimit) ret[b] = modHighLimit;
	    }
	    return ret;
	}
    
    }

    /** check if index belongs to a band */
    void failBand(int i) {
	if ((i<0)||(i>=nrBands))
	    throw new RuntimeException("Wrong band index");
    }

    // ----------------------------------------------------------------------------------
    
    /** Save the parameters to a configuration folder.
	Creates sub-folders 'sim-param'  */
    public void saveConfig( Conf.Folder cfg ) {
    
	Conf.Folder fd = cfg.mk("sim-param");

	fd.newInt("nr-angles").setVal(nrDirs);
	fd.newInt("nr-bands").setVal(nrBands);
	fd.newInt("nr-phases").setVal(nrPhases);
	fd.newStr("img-seq").val( imgSeq.name());
	fd.newInt("img-size-pxl").setVal(imgSize);
	fd.newDbl("microns-per-pxl").setVal(micronsPerPixel);
	fd.newDbl("wiener-parameter").setVal( wienerFilterParameter );
	fd.newDbl("apodization-cutoff").setVal( apoCutOff );
    
	for ( int d=0; d < nrDirs; d++ ) {
	    Conf.Folder df = fd.mk(String.format("dir-%d",d));
	    SimParam.Dir dir = this.dir(d);

	    //df.newInt("nr-bands").setVal(dir.nrBands);
	    //df.newInt("nr-phases").setVal(dir.nrPhases);
	    df.newDbl("shift").setVal( new double [] 
		{dir.pX *(dir.nrBands-1) , dir.pY * (dir.nrBands-1)} );
	    df.newDbl("phase-offset").setVal( dir.phaOff );
	    df.newDbl("modulations").setVal( dir.modul );
	    if ( dir.hasIndividualPhases )
		df.newDbl("phases").setVal( dir.phases );

	}
    }

    /** Initialize a parameter set from a configuraion folder */
    public static SimParam loadConfig( Conf.Folder cfg ) 
	throws Conf.EntryNotFoundException {
	
	Conf.Folder fd = cfg.cd("sim-param");
	
	// basics
	int nDirs = fd.getInt("nr-angles").val();
	int nBand = fd.getInt("nr-bands").val();
	int nPhas = fd.getInt("nr-phases").val();
	SimParam ret = new SimParam( nBand, nDirs, nPhas, false );
	
	// pixel size
	ret.setPxlSize( fd.getInt("img-size-pxl").val(),
			fd.getDbl("microns-per-pxl").val() );

	// image type
	IMGSEQ isq = IMGSEQ.fromName( fd.getStr("img-seq").val() );
	ret.setImgSeq(isq);
	
	// filter settings
	ret.setWienerFilter( fd.getDbl("wiener-parameter").val() );
	ret.setApoCutoff( fd.getDbl("apodization-cutoff").val() );
	
	// for each pattern direction ...
	for ( int d=0; d < ret.nrDirs; d++ ) {
	    Conf.Folder df = fd.cd(String.format("dir-%d",d));
	    SimParam.Dir dir = ret.dir(d);
	    
	    dir.setPxPy( df.getDbl("shift").vals() );
	    dir.setPhaOff( df.getDbl("phase-offset").val());
	    double [] mod = df.getDbl("modulations").vals();
	    for ( int i=0; i< mod.length ; i++)
		dir.setModulation( i, mod[i]);
	    if ( df.contains("phases"))
		dir.setPhases( 	df.getDbl("phases").vals(), false );

	}


	// return the result
	return ret;
    }





   
    // ----------------------------------------------------------------------------------

    public static enum CLIPSCALE {
	BOTH("clip&scale"),
	CLIP("clip zeros"),
	NONE("raw values");

	final String name;

	CLIPSCALE(String n) {
	    name=n;
	}

	@Override 
	public String toString() {
	    return name;
	}

    }

    public static enum IMGSEQ {
	PAZ("p,a,z (def)"), 
	PZA("p,z,a (OMX)"), 
	ZAP("z,a,p (Zeiss)");
	
	final String name;
    
	IMGSEQ(String n) {
	    name=n;
	}
	
	@Override 
	public String toString() {
	    return name;
	}

	/** Calculate position in (raw) z stack.
	 *  @param ang Index of angle
	 *  @param pha Index of phase
	 *  @param z   Index of z
	 *  @param angMax   Number of angles
	 *  @param phaMax   Number of phases
	 *  @param zMax	    Number of z slices */
	public int calcPos( int ang, int pha, int z, int angMax, int phaMax, int zMax ) {
	    if (( ang>=angMax ) || ( pha >= phaMax ) || ( z >= zMax ) || 
		( ang <0 ) || ( pha < 0) || ( z < 0 ))
		throw new RuntimeException("Parameter wrong!");
	    
	    switch(this) {
		case PZA: return ( pha +   z*phaMax  + ang*phaMax*zMax );
		case ZAP: return (  z  + ang*zMax    + pha*zMax*angMax );
		case PAZ: 
		default:
			  return ( pha + ang*phaMax  + z*phaMax*angMax );
	    }
	}
	
	/** Calculate position in (raw) z stack.
	 *  @param ang Index of angle
	 *  @param pha Index of phase
	 *  @param z   Index of z
	 *  @param t   Index of time
	 *  @param angMax   Number of angles
	 *  @param phaMax   Number of phases
	 *  @param zMax	    Number of z slices */
	public int calcPosWithTime( int ang, int pha, int z, int t, int angMax, int phaMax, int zMax ) {
	    if (( ang>=angMax ) || ( pha >= phaMax ) || ( z >= zMax ) || 
		( ang <0 ) || ( pha < 0) || ( z < 0 ))
		throw new RuntimeException("Parameter wrong!");
	    
	    switch(this) {
		case PZA: return ( pha +   z*phaMax  + ang*phaMax*zMax + 
		    phaMax*zMax*angMax * t );
		case ZAP: return (  z  + ang*zMax    + pha*zMax*angMax +
		    phaMax*zMax*angMax * t );
		case PAZ: 
		default:
			  return ( pha + ang*phaMax  + z*phaMax*angMax +
		    phaMax*zMax*angMax * t );
	    }
	}

	/** Return a default SIM parameter set */
	public SimParam getParam() {
	    switch(this) {
		case PZA: return SimParam.create(3,3,5,512,0.082,null);
		case ZAP: return SimParam.create(3,5,5,1024,0.064,null);
		default:  return null;
	    }
	}
	
	/** Return the ENUM from name */
	public static IMGSEQ fromName( String n ){
	    for ( IMGSEQ i : IMGSEQ.values() )
		if ( n.trim().equals( i.name()) )
		    return i;
	    return null;
	}



    }

    // ----------------------------------------------------------------------------------

    /** update the internal timestamp to signal changes, return the timestamp */
    public long signalRuntimeChange() {
	runtimeTimestamp = System.currentTimeMillis();
	return runtimeTimestamp;
    }

    /** returns the last time 'signalRuntimeChange' was called, as
     * System.currentTimeMillis */
    public long getRuntimeTimestamp() {
	return runtimeTimestamp;
    }
    
    /** returns true if the timestamp provided is older than the stamp generated
     * by the last call to singalRuntimeChange. */ 
    public boolean compareRuntimeTimestamp( long timestamp ) {
	return (( timestamp - runtimeTimestamp ) < 0);
    }


    // ----------------------------------------------------------------------------------
    
    /** Return width */
    @Override public int vectorWidth() { return imgSize; }
    
    /** Return height */
    @Override public int vectorHeight() { return imgSize; }
    

    /** Returns a multi-line, human-readable output of parameters */
    public String prettyPrint(boolean phaInDeg) {
	final double pf = ((phaInDeg)?(180/Math.PI):(1));
	String ret = "#--- SIM parameter summary ---\n";
	ret += String.format("# pxl %7.5f microns (freq pxl %8.6f cycles/micron)\n ----\n",
	     micronsPerPixel, cyclesPerMicron);
	for ( Dir d : directions ) {
	    int b = d.nrBand()-1; 
	    ret += String.format("Nr bands: %d, shift of outer-most band:\n", b);
	    ret += String.format("px: %6.3f py: %6.3f (len %6.3f) \n",
		d.px(b), d.py(b), Math.sqrt(d.px(b)*d.px(b)+d.py(b)*d.py(b)));
	    ret += String.format("phases (%s) [ ",((phaInDeg)?("deg"):("rad")) );
	    for (double p : d.getPhases() )
		ret+=String.format("%6.3f ", p*pf);
	    ret += " ]\n Modulation: [ ";
	    for (double m: d.getModulations() ) {
		ret+=String.format("%6.3f ",m);
	    }
	    ret += " ]\n ----\n";
	}
	return ret;
    }
    
    // ----------------------------------------------------------------------------------

    /** For testing input / output to CONF */
    public static void main( String args [] ) throws Exception {

	if (args.length<2) {
	    System.out.println("Use: i - Input, o - Output [filename] ");
	    return;
	}

	SimParam foo = SimParam.create(3,4,5,512,0.082, null);

	// output
	if (args[0].equals("o")) {
	    Conf cfg = new Conf("fairsim");
	    foo.saveConfig( cfg.r() );
	    cfg.saveFile(args[1]);
	}
	
	// input
	if (args[0].equals("i")) {
	    Conf cfg = Conf.loadFile(args[1]);
	    SimParam bar = loadConfig(cfg.r());
	    System.out.println( bar.prettyPrint(true));
	}
	

	Tool.shutdown();
    }

}


