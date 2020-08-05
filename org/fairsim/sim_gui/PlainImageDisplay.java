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

package org.fairsim.sim_gui;

import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import javax.swing.JSlider;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import java.awt.GridBagLayout;
import javax.swing.BoxLayout;
import java.awt.BorderLayout;
import javax.swing.Box;
import java.awt.GridBagConstraints;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.BorderFactory;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.Font;
import javax.swing.JCheckBox;
import javax.swing.SwingConstants;

import java.awt.geom.Line2D;
import java.awt.geom.Ellipse2D;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Color;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.Random;

import org.fairsim.linalg.Vec2d;
import org.fairsim.utils.Tool;


public class PlainImageDisplay {

    protected final JPanel mainPanel ;
    protected final ImageComponent ic ;
    protected JPanel channelsPanel;
	
    List<HistogramDisplay> histList = new ArrayList<HistogramDisplay>();
	    
    // absolute bit depth of channel
    final int [] bitDepth ;

    public PlainImageDisplay(int w, int h) {
	this(1,w,h);
    }

    public void refresh() {
	
	// TODO: add rate limit		
	
	// paint the new image
	ic.paintImage();
	// update histogram
	for ( int i=0; i<histList.size(); i++) {
	    histList.get(i).setData(
		ic.imgBufferLinearChannels[i], 0, (1<<bitDepth[i]));
	}
    }

    // store the sRGB gamma as precomputed array
    final static private byte [] gammaSRGB = new byte[ 2048 ];
    static {
	for (int i=0; i<gammaSRGB.length; i++) {
	    double pos = (float)i/gammaSRGB.length;

	    if (pos < 0.0031308 ) {
		gammaSRGB[i] = (byte)(256 * 12.92 * pos);
	    } else {
		double val = 1.055 * Math.pow( pos, 1./2.4 ) - 0.055;
		val *= 256;
		if (val>127) val-=256;
		gammaSRGB[i] = (byte)(val);
	    }
	    
	    // for deubg, output the table
	    //System.out.println(String.format("%d %7.5f %d #srgb-gamma", i, pos, gammaSRGB[i]));
	}
    }


    enum LUT {

	GREY(0), 
	CYAN(3),
	MAGENTA(5),
	RED(4),
	GREEN(2),
	BLUE(1),
	YELLOW(6);
	
	int color=0;
	public int getInt() { return color; }
	LUT(int i) { color=i; }
    
	public float [] getColorCoeff() {
	    
	    float [] values = new float[3];
	    // gray
	    if (color==0) {
                values[0] = .3f; // blue
                values[1] = .3f; // green
                values[2] = .3f; // red
             } else {
             // 1-6: blue, green, cyan, red, magenta, yellow
                 if ((color&4)!=0)
                      values[2] = .3f; // red
                 if ((color&2)!=0)
                      values[1] = .3f; // green
                 if ((color&1)!=0)
                      values[0] = .3f; // blue
             }
	     return values;
	}
    
    };


    enum CROSSHAIRS {
	NONE(0),
	XY(1),
	CROSS(2),
	XYCIRCLE(3),
	CROSSCIRCLE(4);

	int number =0;

	CROSSHAIRS(int i) {number = i;};

	public int getInt() { return number; }

	public String toString() {

	    switch (number) {
		case 0: return "off";
		case 1: return "XY";
		case 2: return "cross";
		case 3: return "XY + circle";
		case 4: return "cross + circle";

	    }

	    return null;
	}
	
    }


    public PlainImageDisplay(int nrChannels, int w, int h, String ... names) {
	this(nrChannels, w, h, true, names );
    }
    
   

    public PlainImageDisplay(int nrChannels, int w, int h, boolean slidersOnTop,
	String ... names) {

	ic = new ImageComponent(nrChannels, w,h);
	mainPanel = new JPanel();
	mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

	// panel containing the image
	JPanel p1 = new JPanel();
	p1.add(ic);

	// panel containing the channels
	channelsPanel = new JPanel();
	channelsPanel.setLayout( new BoxLayout( channelsPanel, BoxLayout.PAGE_AXIS ));

	bitDepth = new int[nrChannels];

	final String minMaxLabelFormat = " m% 6d M% 6d g %3.2f";

	for (int ch = 0; ch < nrChannels; ch++) {
	    
	    final int channel = ch;

	    final JLabel  lValues ;
	    final JSlider sMin = new JSlider(JSlider.HORIZONTAL, 0, 1<<16, 0);
	    final JSlider sMax = new JSlider(JSlider.HORIZONTAL, 0, 1<<16, 1<<16);
	    final JSlider sGamma = new JSlider(JSlider.HORIZONTAL, 10, 300,100);


	    bitDepth[ch] = 16;

	    final Tiles.LComboBox<Integer> bitDepthSelector = 
		new Tiles.LComboBox<Integer>( "bits", 8,10,12,14,16 );

	    bitDepthSelector.setSelectedIndex(4);

	    // sliders and buttons
	    final JButton autoMin = new JButton("auto");
	    final JButton autoMax = new JButton("auto");
	    
	    
	    lValues = new JLabel(String.format(minMaxLabelFormat,
		sMin.getValue(), sMax.getValue(), sGamma.getValue()/100.));

	    lValues.setFont( new Font( Font.MONOSPACED, Font.PLAIN, 10) );
	    final HistogramDisplay hist = new HistogramDisplay(100);
	    
	    sMin.setPreferredSize( new Dimension(265,20));
	    sMax.setPreferredSize( new Dimension(265,20));
	    sGamma.setPreferredSize( new Dimension(265,25));

	    histList.add( hist );

	    	sMin.addChangeListener( new ChangeListener() {
		public void stateChanged(ChangeEvent e) {
		    
		    int val = sMin.getValue();
		    if (sMax.getValue()-9<val)
		        sMax.setValue(val+9);

		    //updateMinMaxGamma();
		    lValues.setText( String.format(minMaxLabelFormat, 
			sMin.getValue(), sMax.getValue(), sGamma.getValue()/100.));
		    
		    hist.setMinMarker( val );
		    
		    ic.scalMin[channel] = val;
		    refresh();
		    //ic.paintImage();
		}
	    });

	    sMax.addChangeListener( new ChangeListener() {
		public void stateChanged(ChangeEvent e) {
		    
		    int val = sMax.getValue();
		    if (sMin.getValue()+9>val)
		        sMin.setValue(val-10);
		    
		    //updateMinMaxGamma();
		    lValues.setText( String.format(minMaxLabelFormat, 
			sMin.getValue(), sMax.getValue(), sGamma.getValue()/100.));
		    
		    hist.setMaxMarker( val );
		    
		    ic.scalMax[channel] = val;
		    //ic.paintImage();
		    refresh();
		}
	    });

	    autoMin.addActionListener( new ActionListener() {
		public void actionPerformed( ActionEvent e ) {
		    sMin.setValue( (int)(Math.log( ic.currentImgMin[channel] )*100/Math.log(2)) );
		}
	    });

	    autoMax.addActionListener( new ActionListener() {
		public void actionPerformed( ActionEvent e ) {
		    //sMax.setValue( ic.currentImgMax );
		    sMax.setValue( (int)(Math.log( ic.currentImgMax[channel] )*100/Math.log(2)) );
		}
	    });

	    sGamma.addChangeListener( new ChangeListener() {
		public void stateChanged(ChangeEvent e) {
		    double gamma = sGamma.getValue()/100.;
		    //updateMinMaxGamma();
		    lValues.setText( String.format(minMaxLabelFormat, 
			sMin.getValue(), sMax.getValue(), sGamma.getValue()/100.));
		    ic.recalcGammaTable( channel, gamma );
		    
		    hist.setGamma( gamma );
		    
		    //ic.paintImage();
		    refresh();
		}
	    });
	   

	    sGamma.addMouseListener(new MouseAdapter() {
		public void mouseClicked(MouseEvent evt) {
		    if (evt.getClickCount() == 2) {
			sGamma.setValue(100);
		    }
		}
	    });



	    bitDepthSelector.addSelectListener( new Tiles.SelectListener<Integer>() {
		@Override
		public void selected(Integer l, int i ) {
		    bitDepth[channel] = l;
		    sMin.setMaximum( 1<<l);
		    sMax.setMaximum( 1<<l);
		    sMin.setValue( 0 );
		    sMax.setValue( 1<<l );

		}
	    });


	    Tiles.LComboBox<LUT> lutSelector = 
		new Tiles.LComboBox<LUT>("LUT", LUT.values()); 
	    lutSelector.addSelectListener( new Tiles.SelectListener<LUT>() {
		@Override
		public void selected( LUT l, int i ) {
		    Tool.trace(l.toString());
		    ic.setColorTable(channel, l);
		}
	    });

	    lutSelector.box.setSelectedIndex( (channel+1)%7 );
	    
	  
	    
	    // show checkBox
            final JCheckBox showCheckBox = new JCheckBox("Show Channel", true);
            showCheckBox.addActionListener( new ActionListener() {
		public void actionPerformed( ActionEvent e ) {
		    ic.show[channel] = showCheckBox.isSelected();
		}
	    });


	    String chName="Ch "+channel;
	    if ( names.length > channel )
		chName = names[channel];
	    
	    // ==== LAYOUT ====
	    
	    // sliders setting min/max
	    JPanel sliders = new JPanel(new GridBagLayout());
	    GridBagConstraints c = new GridBagConstraints();	
	    

	    c.weightx=1;
    
	    // min slider, histogram, max slider
	    c.gridx=0; c.gridy=0; c.gridwidth=10; c.gridheight=1;
	    sliders.add( sMax, c );
	    c.gridx=0; c.gridy=1; c.gridwidth=10; c.gridheight=5;
	    sliders.add( hist,c );
	    c.gridx=0; c.gridy=6; c.gridwidth=10; c.gridheight=1;
	    sliders.add( sMin, c );

	    // Lut selector and "show image"
	    c.gridx=0; c.gridy=7; c.gridwidth=4;
	    sliders.add(lutSelector,c);
	    c.gridx=4; c.gridy=7; c.gridwidth=4;
            sliders.add(showCheckBox,c);
	    
	    
	    // gamma slider
	    c.gridx=0; c.gridy=8; c.gridwidth=10; c.gridheight=1;
	    sliders.add( sGamma , c);
	   
	    // label
	    c.gridx=0; c.gridy=9; c.gridwidth=7; c.gridheight=1;
	    c.fill = GridBagConstraints.BOTH;
	    c.anchor = GridBagConstraints.CENTER;
	    sliders.add( (new JPanel()).add(lValues),c);
	    c.gridx=7; c.gridy=9; c.gridwidth=3; c.gridheight=1;
	    sliders.add( bitDepthSelector,c  );
	    

	    /* 
	    c.gridx=7; c.gridy=0; c.gridwidth=2;
	    sliders.add( autoMin, c );
	    c.gridy=1;
	    sliders.add( autoMax, c ); */
	
	    JPanel perChannelPanel = new JPanel();
	    perChannelPanel.setBorder( BorderFactory.createTitledBorder(chName));
	    perChannelPanel.add( sliders );

	    channelsPanel.add( perChannelPanel );
	}


	Tiles.LComboBox<CROSSHAIRS> crosshairSelector = 
	    new Tiles.LComboBox<CROSSHAIRS>("Crosshair", CROSSHAIRS.values()); 
	crosshairSelector.addSelectListener( new Tiles.SelectListener<CROSSHAIRS>() {
	    @Override
	    public void selected( CROSSHAIRS c, int i ) {
		Tool.trace(c.toString());
		ic.crosshair = c.getInt();
	    }
	});


	Tiles.LComboBox<Tiles.NAMED_COLOR> crosshairColorSelector =
	    new Tiles.LComboBox<Tiles.NAMED_COLOR>("color", Tiles.NAMED_COLOR.values()); 
	crosshairColorSelector.addSelectListener( new Tiles.SelectListener<Tiles.NAMED_COLOR>() {
	    @Override
	    public void selected( Tiles.NAMED_COLOR c, int i) {
		ic.crosshairColor = c.getColor();
	    }
	});



	JPanel crosshairPanel = new JPanel();
	crosshairPanel.add( crosshairSelector  );
	crosshairPanel.add( crosshairColorSelector  );

	channelsPanel.add( crosshairPanel );
    


	// info
	final JLabel imageInfoLabel=new JLabel("use mousewheel to zoom");
	JPanel labelPanel = new JPanel();
	labelPanel.add( imageInfoLabel );
	
	ic.setUpdateListener( new IUpdate() {
	    @Override
	    public void newZoom( int zoomLevel, int zoomX, int zoomY){
		imageInfoLabel.setText(
		    String.format("Zoom %2dx, ROI: %4d, %4d", zoomLevel,zoomX,zoomY));
	    }
	});


	// layout of the complete display
	mainPanel.setLayout( new BoxLayout( mainPanel, BoxLayout.LINE_AXIS));
	//mainPanel.add(labelPanel);
	mainPanel.add( p1 );
	JPanel tmpPanel = new JPanel();
	tmpPanel.setLayout( new BoxLayout( tmpPanel, BoxLayout.PAGE_AXIS));
	tmpPanel.add( labelPanel );
	tmpPanel.add( channelsPanel );
	
	mainPanel.add(tmpPanel);
    
	refresh();
    }
  
    /** Set a new image */
    public void newImage( int ch, float [] data) {
	ic.setImage( ch, data);	
    }
    public void newImage( int ch, short [] data) {
	ic.setImage( ch, data);
    }
    
    /** Set a new image */
    public void newImage( int ch, Vec2d.Real img ) {
	ic.setImage( ch, img);
    }


    /** Return the GUI panel for the component */
    public JPanel getPanel() {
	return mainPanel;
    }




    static class HistogramDisplay extends JComponent {
         
        final BufferedImage bufferedImage ;
	final int width, height;

	final int [] gammaTable;


	private int pointCounter =0;
   
	float minData=0, maxData=1<<16;
	float minMarker=0, maxMarker=1<<16;
	double gamma=1;

	void setMinMarker( float val ) { 
	    minMarker = val; 
	    recalcGammaTable();
	}
	
	void setMaxMarker( float val ) { 
	    maxMarker = val; 
	    recalcGammaTable();
	}
	
	void setGamma( double val ) { 
	    gamma = val; 
	    recalcGammaTable();
	}

	void recalcGammaTable() {
	    
	    for (int x=0; x<width; x++) {
		
		double dataPos = minData + (1.*x/width)*(maxData-minData);
		
		if (dataPos<minMarker) {
		    gammaTable[x]=0;
		    continue;
		} 
		if (dataPos>maxMarker) {
		    gammaTable[x]=height-1;
		    continue;
		} 
		
		
		double gammaQuotient = (dataPos-minMarker)/(maxMarker-minMarker);
		gammaTable[x] = (int)(Math.pow( gammaQuotient, gamma ) * (height-1));
		if (gammaTable[x] <0) {
		    gammaTable[x] = 0;
		    Tool.trace("gamma too low");
		}
		if (gammaTable[x] >height-1) {
		    gammaTable[x] = height-1;
		    Tool.trace("gamma too high");
		}
		    //gammaTable[x]=height/2;

	    }
	}



	void setData( float [] dat, float min, float max) {

	    minData=min;
	    maxData=max;

	    final int nrBins = 256;
	    final float inc = (max-min)/(nrBins+1);

	    int [] count = new int[ nrBins ];
	    double [] logCount = new double [ nrBins ];

	    int belowMinCount=0, aboveMaxCount=0;

	    Arrays.fill( imgData, (byte)0);

	    // compute the dataset
	    for (float v : dat ) {
		int pos = (int) ((v-min)/inc);
		if (pos>=0 && pos < nrBins )
		    count[pos]++;
		if (pos<0)
		    belowMinCount++;
		if (pos>nrBins)
		    aboveMaxCount++;
	    }

	    // compute its maximum
	    int maxCount = 0;
	    for (int i : count ){
		if (maxCount < i) maxCount=i;
	    }
	    
	    
	    // compute the log
	    double maxLogCount =0;
	    for (int i=0; i<nrBins; i++) {
		logCount[i] = Math.log(1+count[i]);			
		if (logCount[i]>maxLogCount) maxLogCount=logCount[i];
	    }


	    // draw the histogram
	    for (int y=0; y<height; y++) {
		for (int x=0; x<nrBins; x++) {
		    if (count[x]*height > (height-y-1)*maxCount) {
			imgData[(2+y*width+x)*3+0]=(byte)180;
			imgData[(2+y*width+x)*3+1]=(byte)180;
			imgData[(2+y*width+x)*3+2]=(byte)180;
		    } else  
		    if (logCount[x]*height > (height-y-1)*maxLogCount) {
			imgData[(2+y*width+x)*3+0]=(byte)60;
			imgData[(2+y*width+x)*3+1]=(byte)60;
			imgData[(2+y*width+x)*3+2]=(byte)60;
		    } 
		}
	    }
	    
	    
	    // draw the gamma table
	    for (int x=0; x<width; x++) {
		imgData[ (x+(width*(height-gammaTable[x]-1)))*3+2 ] =(byte)255;
	    }

	    // draw markers for above/ below values
	    for (int y=0; y<height; y++) {
		if (belowMinCount*height > (height-y-1)*maxCount) {
		    for (int x=0; x<2; x++) {
			imgData[(y*width+x)*3+0]=(byte)255;
			imgData[(y*width+x)*3+1]=(byte)150;
		    	imgData[(y*width+x)*3+2]=(byte)0;
		    }
		}
		if (aboveMaxCount*height > (height-y-1)*maxCount) {
		    for (int x=width-2; x<width; x++) {
			imgData[(y*width+x)*3+0]=(byte)0;
			imgData[(y*width+x)*3+1]=(byte)150;
			imgData[(y*width+x)*3+2]=(byte)255;
		    }
		}
	    }




	    this.repaint();
	}



	final byte  [] imgData   ;

	HistogramDisplay(int h) {
	    
	    setIgnoreRepaint(true);
	   
	    width = 260; height= h;

	    bufferedImage = new BufferedImage(width,height, BufferedImage.TYPE_3BYTE_BGR);
	    imgData = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();

	    gammaTable = new int[width];
	    recalcGammaTable();
	    
	    System.out.println("img len: "+imgData.length);
	}

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width, height);
        }
 
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(width, height);
        }
 
        @Override
        public Dimension getMinimumSize() {
            return new Dimension(width, height);
        }
 
        @Override
        protected void paintComponent(Graphics g) {
            g.drawImage(bufferedImage, 0, 0, null);
        }

    }



    /** Internal class for the actual image display */
    static class ImageComponent extends JComponent{
         
        BufferedImage bufferedImage = null;
	final int width, height;
	final int nrChannels;
   
	IUpdate ourUpdateListener = null;
	    
	int crosshair = 0;
	Color crosshairColor = Color.GRAY;

	final int gammaLookupTableSize = 1024;

	int [] scalMax, scalMin;
	int [] currentImgMin, currentImgMax;
	double [] gamma;
        final boolean[] show;

	final float [][] imgBufferLinearChannels ;
	final byte  [] imgDataBufferSRGB ;
	final byte  [] imgDataOnScreen   ;

	float [][] colorCoeff;
	final float [][] gammaLookupTable ;

	int zoomLevel=1, zoomX=0, zoomY=0;

        public ImageComponent(int ch, int w, int h) {
	    
	    width=w; height=h; nrChannels = ch;
	   
	    setIgnoreRepaint(true);
	    bufferedImage   = new BufferedImage(width,height, BufferedImage.TYPE_3BYTE_BGR);
	    
	    imgBufferLinearChannels = new float[nrChannels][w*h];
	    
	    imgDataBufferSRGB = new  byte[3*w*h];
	    
	    imgDataOnScreen   = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();

	    colorCoeff = new float[nrChannels][3];
	    gammaLookupTable = new float[nrChannels][ gammaLookupTableSize ];

	    // init values
	    scalMax = new int[ch];
	    scalMin = new int[ch];
	    currentImgMin = new int[ch];
	    currentImgMax = new int[ch];
	    gamma = new double[ch];
            show = new boolean[ch];

	    for (int c=0; c<nrChannels; c++) {
		scalMin[c]=0; scalMax[c]=1<<16;
		currentImgMin[c]=0; currentImgMax[c]=1;
		recalcGammaTable(c,1);
                show[c] = true;
	    }

	    // init color lookup
	    for (int c=0; c<nrChannels; c++) {
		    setColorTable( c, LUT.values()[(c+1)%7] );
	    }
	
	    /*
	    this.addMouseListener( new MouseAdapter() {
		@Override
		public void mouseClicked(MouseEvent e) {
		    int x = e.getX();
		    int y = e.getY();
		    System.out.println("mouse clicked: "+x+" "+y);
		}
	    }); */
	    this.addMouseWheelListener( new MouseAdapter() {
		@Override
		public void mouseWheelMoved( MouseWheelEvent e ) {
		    int xPosMid = e.getX();
		    int yPosMid = e.getY();
		    //System.out.println("Scroller: "+e.getWheelRotation());
		    //System.out.println("mouse clicked: "+x+" "+y);
	    
		    int wSize =  width/zoomLevel;
		    int hSize = height/zoomLevel;
		   
		    // zoom out 
		    if (e.getWheelRotation()>0) {
			setZoom(zoomLevel-1, zoomX+wSize/2, zoomY+hSize/2);
		    } else {
		    // zoom in
			setZoom(zoomLevel+1, 
			    xPosMid/zoomLevel + zoomX, 
			    yPosMid/zoomLevel + zoomY);
		    }
		}
	    });
	}

	public void setImage( int ch, float [] img ) {
	    if (img.length != width*height )
		throw new RuntimeException("Input array size does not match " + img.length + "/" + width + "/" + height);
	    System.arraycopy( img, 0, imgBufferLinearChannels[ch], 0, width*height);
	}
	
	public void setImage( int ch, Vec2d.Real img ) {
	    if (img.vectorWidth() != width || img.vectorHeight()!=height)
		throw new RuntimeException("Input vector size mismatch " + width + "/" + height);
	    setImage( ch, img.vectorData());
	}
		
	public void setImage( int ch, short [] pxl ) {
	    if (pxl.length != width*height) 
		throw new RuntimeException("Input array size does not match: " + pxl.length + "/" + width + "/" + height);

	    for (int i=0; i<width*height; i++) {
		int val = (int)pxl[i];
		if (val<0) val+=65535;
		imgBufferLinearChannels[ch][i] = val;
	    }
	}


	public void recalcGammaTable( int ch, double gamma ) {
	    this.gamma[ch] = gamma;
	    for (int i=0; i<gammaLookupTableSize; i++) {
		gammaLookupTable[ch][i] = (float)(Math.pow(1.*i / gammaLookupTableSize, gamma));
	    }
	}

	public void setColorTable( int channel, LUT lut ) {
	    colorCoeff[channel] = lut.getColorCoeff();
	    /*
	    for (int c=0; c<3; c++) {
		Tool.trace(" color-coeff "+c+" "+colorCoeff[channel][c]);
	    } */
	    paintImage();
	}


	// TOOD: move this to an "image processing" class, I guess
	void paintImage() {


	    float [] linRGB = new float[3];
	    float [] cieXYZ = new float[3];

	    // for all pixels
	    for (int y=0; y<height; y++)
	    for (int x=0; x<width; x++) {
	
		// 0 - zero current pixel
		for (int col=0; col<3; col++) {
		    cieXYZ[ col ] =0;
		    linRGB[ col ] =0;
		}

		// 1 - convert all channels pixels to linear CIE XYZ
		for (int ch=0; ch<nrChannels; ch++) {
                    if(show[ch]) {
                        // find min / max (not needed here, but stored for histogram)
			float val = imgBufferLinearChannels[ch][ x + y*width ];
                        if (val> currentImgMax[ch]) currentImgMax[ch] = (int)val;
                        if (val< currentImgMin[ch]) currentImgMin[ch] = (int)val;
                        // scale to set min / max values
			float out = 1.f*(val - scalMin[ch]) / (scalMax[ch]-scalMin[ch]) ;
			if (out<0) out=0;
			if (out>=1) out=1-.5f/gammaLookupTableSize;
			// apply channel-specific gamma
			out = gammaLookupTable[ch][ (int)(out*gammaLookupTableSize) ];
			// add to the linear RGB buffer
			for (int col=0; col<3; col++) {
			    cieXYZ[ col ] += colorCoeff[ch][col] * out;
			}
                    }
		}
	
		// 2 - clip the conversion input
		for (int col=0; col<3; col++) {
		    if ((cieXYZ[col]) > 1.0f) cieXYZ[col]=1.0f;
		    if ((cieXYZ[col]) < 0.0f) cieXYZ[col]=0.0f;
		}

		// 3 - threat input as CIE XYZ
		linRGB[0] = (float)(cieXYZ[0] * 3.2406 + cieXYZ[1] * -1.5372 + cieXYZ[2] * -0.4986);
		linRGB[1] = (float)(cieXYZ[0] * -.9689 + cieXYZ[1] *  1.8758 + cieXYZ[2] *  0.0415);
		linRGB[2] = (float)(cieXYZ[0] * 0.0557 + cieXYZ[1] * -0.2040 + cieXYZ[2] *  1.0570);

		// 4 - clip the conversion output
		for (int col=0; col<3; col++) {
		    if ((linRGB[col]) >= 1.0f) linRGB[col]=1-0.5f/gammaSRGB.length;
		    if ((linRGB[col]) <  0.0f) linRGB[col]=0.0f;
		}
		
		// 5- set to output bytes (through sRGBs gamma table)
		for (int i=0; i<3; i++) {
		    imgDataBufferSRGB[3 * (y*width + x ) + i  ] = 
			gammaSRGB[ (int)(linRGB[i] * gammaSRGB.length) ];
		}
	    }
	   

	    // this handles 'zoom'
	    if (zoomLevel==1) {
		System.arraycopy( imgDataBufferSRGB, 0 , imgDataOnScreen, 0, 3*width*height);
	    } else {
		for (int y=0; y<height; y++)	
		for (int x=0; x<width;  x++)	
		for (int i=0; i<3;  i++)	
		    imgDataOnScreen[3*(x + y*width)+i] = imgDataBufferSRGB[ 
		     3*( (x/zoomLevel+zoomX) + width*(y/zoomLevel+zoomY))+i];
	    }
	    
	    this.repaint();
	}


	/** Add our own components after being drawn */
	@Override
	public void paint(Graphics g) {
	    super.paint(g);


	    if (crosshair>0) {
		g.setColor( crosshairColor );
		if ( crosshair%2 == 1 ) {
		    Line2D line1 = new Line2D.Double(width/2.,height/10.,width/2.,height*9./10.);
		    Line2D line2 = new Line2D.Double(width/10.,height/2.,width*9./10.,height/2.);
		    ((Graphics2D)g).draw(line1);
		    ((Graphics2D)g).draw(line2);
		}
		if ( crosshair%2 == 0 ) {
		    Line2D line1 = new Line2D.Double(width/10. ,height/10. , width*9./10., height*9./10.);
		    Line2D line2 = new Line2D.Double(width*9/10.,height/10.,width/10.,height*9./10.);
		    ((Graphics2D)g).draw(line1);
		    ((Graphics2D)g).draw(line2);
		}
	    }
	    if ( crosshair > 2 ) {
		double size = (width+height)/4.;
		Ellipse2D cir = new Ellipse2D.Double(width/2.-size/2, height/2.-size/2, size,size);
		((Graphics2D)g).draw(cir);
	    }

	}



	/** Set the zoom level and midpoint position */
	public void setZoom( int level, int xPos, int yPos ) {
	    if (level<1 || level>8 )
		return;
	    if (level==1) {
		zoomLevel=1;
		zoomX=0; zoomY=0;
		this.paintImage();	
		if (ourUpdateListener!=null)
		    ourUpdateListener.newZoom( zoomLevel, zoomX, zoomY);
	    
		return;
	    }
	    
	    int wSize =  width/level;
	    int hSize = height/level;
	    xPos-=wSize/2;
	    yPos-=hSize/2;
	    xPos = Math.max(xPos,0);
	    yPos = Math.max(yPos,0);
	    xPos = Math.min(xPos, width-wSize-1);
	    yPos = Math.min(yPos,height-hSize-1);
	    zoomLevel=level;
	    zoomX=xPos;
	    zoomY=yPos;
	
	    this.paintImage();	

	    //System.out.println("Updated ROI: "+xPos+" "+yPos+"/"+zoomX+" "+zoomY+" l:"+zoomLevel);
	    if (ourUpdateListener!=null)
		ourUpdateListener.newZoom( zoomLevel, zoomX, zoomY);
	}

	
    
	public void setUpdateListener( IUpdate l ) {
	    ourUpdateListener = l;
	}
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width, height);
        }
 
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(width, height);
        }
 
        @Override
        public Dimension getMinimumSize() {
            return new Dimension(width, height);
        }
 
        @Override
        protected void paintComponent(Graphics g) {
            g.drawImage(bufferedImage, 0, 0, null);
        }
    }
    
    public interface IUpdate {
	    public void newZoom( int level, int xPos, int yPos );
	}


    /** Main method for easy testing */
    public static void main( String [] arg ) throws java.io.IOException, InterruptedException {
	
	if (arg.length<2) {
	    System.out.println("Usage for test: image-size channels");
	    return;
	}

	final int size = Integer.parseInt( arg[0]);
	final int nrCh = Integer.parseInt( arg[1]);
	final int width=size, height=size;

	// create an ImageDisplay sized 512x512
	PlainImageDisplay pd = new PlainImageDisplay(nrCh, width,height);

	// create a frame and add the display
	JFrame mainFrame = new JFrame("Plain Image Receiver");
	mainFrame.add( pd.getPanel() ); 
	
	mainFrame.pack();
	mainFrame.setLocation( 100, 100 );
	mainFrame.setVisible(true);

	float [][] pxl = new float[100][width*height];

	Tool.Timer t1 = Tool.getTimer();
	Random rnd = new Random(42);

	for (int ch = 0; ch < nrCh; ch++) 
	for (int i=0;i<100;i++) {
	    for (int y=0;y<height;y++)
	    for (int x=0;x<width;x++) {
		if ( (x>200 && x<250) || (y>150 && y<190) ) {
		    pxl[i][x+y*width]=(float)(500 +rnd.nextGaussian()*Math.sqrt(500));
		} else 
		if ( (x>400 && x<450) || (y>250 && y<290) ) {
		    pxl[i][x+y*width]=(float)(1400+rnd.nextGaussian()*Math.sqrt(1400));
		} else {
		    pxl[i][x+y*width]=(float)(2400+rnd.nextGaussian()*Math.sqrt(2400));
		}
	    }
	}

	while (true) {
	    t1.start();
	    for (int i=0;i<100;i++) {
		for (int ch = 0; ch < nrCh; ch++) {
		    float [] pxls = pxl[(int)(Math.random()*99)]; 
		    pd.newImage(ch, pxls);
		}
		
		pd.refresh();
		Thread.sleep(25);
	    }
	    t1.stop();
	    System.out.println( "fps: "+((1000*100)/t1.msElapsed()) );
	}



    }


}
