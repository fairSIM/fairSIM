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
import java.awt.event.MouseMotionAdapter;
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
import java.nio.ByteBuffer;

import org.fairsim.linalg.Vec2d;
import org.fairsim.utils.Tool;
import org.fairsim.utils.SimpleMT;


public class PlainImageDisplay {

    protected final JPanel mainPanel ;
    protected final ImageComponent ic ;
    protected JPanel channelsPanel;
	protected JCheckBox displayInSRGB_checkbox = new JCheckBox("Display in sRGB", true);
	
    List<HistogramDisplay> histList = new ArrayList<HistogramDisplay>();
	
	protected final int viewportWidth, viewportHeight;
	protected int imageWidth=0, imageHeight=0, imageZoom=1;
	protected final MiniMap miniMap = new MiniMap(200, 200);

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
		// update the miniMap
		BufferedImage bfImg = miniMap.bufferedImage;
		byte[] miniMapData = ((DataBufferByte) bfImg.getRaster().getDataBuffer()).getData();
		for (int y=0; y<miniMap.our_height; y++) {
			for (int x=0; x<miniMap.our_width; x++) {
				int pos = (x + y * miniMap.our_width) * 3;
				int posImg = (x * imageWidth / miniMap.our_width + (y * imageHeight / miniMap.our_height) * imageWidth) * 3;
				// copy the RGB values from the main image
				miniMapData[pos] = ic.imgDataBufferSRGB[posImg];
				miniMapData[pos+1] = ic.imgDataBufferSRGB[posImg+1];
				miniMapData[pos+2] = ic.imgDataBufferSRGB[posImg+2];
			}
		}
		miniMap.repaint();

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

	public enum PXL_TYPE {
		UINT_8,
		UINT_16,
		FLOAT_32;
	
		public int getByteCount() {
			switch (this) {
				case UINT_8: return 1;
				case UINT_16: return 2;
				case FLOAT_32: return 4;
				default: throw new IllegalArgumentException("Unknown pixel type: " + this);
			}
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
                values[0] = 1.f; // blue
                values[1] = 1.f; // green
                values[2] = 1.f; // red
             } else {
             // 1-6: blue, green, cyan, red, magenta, yellow
                 if ((color&4)!=0)
                      values[2] = 1.f; // red
                 if ((color&2)!=0)
                      values[1] = 1.f; // green
                 if ((color&1)!=0)
                      values[0] = 1.f; // blue
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

	viewportWidth = w;
	viewportHeight = h;
	imageWidth = w;
	imageHeight = h;

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
		    //Tool.trace(l.toString());
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
	    JPanel perChannelPanel2 = new JPanel();
	    perChannelPanel2.setBorder( BorderFactory.createTitledBorder(chName));
	    perChannelPanel2.add( sliders );
		perChannelPanel.add( perChannelPanel2 );

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
	JPanel labelPanel2 = new JPanel();
	labelPanel2.setLayout( new BoxLayout(labelPanel2, BoxLayout.PAGE_AXIS));
	labelPanel2.add( imageInfoLabel );
	labelPanel.add( labelPanel2 );
	

	JPanel miniMapPanel = new JPanel();
	JPanel miniMapPanel2 = new JPanel();
	miniMap.setPreferredSize(new Dimension(200, 200));
	miniMap.setMinimumSize(new Dimension(200, 200));
	miniMap.setMaximumSize(new Dimension(200, 200));
	miniMapPanel2.setBorder(BorderFactory.createTitledBorder("Overview"));
	miniMapPanel2.add(miniMap);
	miniMapPanel.add(miniMapPanel2);

	ic.setUpdateListener( new IUpdate() {
	    @Override
	    public void newZoomOrPosition( int zoomLevel, int viewportX, int viewportY, 
		    int viewportWidth, int viewportHeight) {
		imageInfoLabel.setText(
		    String.format("Zoom %2dx, ROI: %4d, %4d", zoomLevel,
				viewportX+(viewportWidth/2),
				viewportY+(viewportHeight/2)));
		miniMap.updateImageSize(imageWidth, imageHeight);
		miniMap.updateViewport(viewportX, viewportY, viewportWidth, viewportHeight, zoomLevel);
			}
	});




	JPanel rgbPanel = new JPanel();
	rgbPanel.setLayout( new BoxLayout(rgbPanel, BoxLayout.LINE_AXIS));
	rgbPanel.add(displayInSRGB_checkbox);
	displayInSRGB_checkbox.addChangeListener(new ChangeListener() {
	    @Override
	    public void stateChanged(ChangeEvent e) {
		boolean isSelected = displayInSRGB_checkbox.isSelected();
		ic.displayInSRGB = isSelected;
		refresh();
	    }
	});

	// layout of the complete display
	mainPanel.setLayout( new BoxLayout( mainPanel, BoxLayout.LINE_AXIS));
	//mainPanel.add(labelPanel);
	mainPanel.add( p1 );
	JPanel tmpPanel = new JPanel();
	tmpPanel.setLayout( new BoxLayout( tmpPanel, BoxLayout.PAGE_AXIS));
	tmpPanel.add( labelPanel );
	tmpPanel.add( miniMapPanel );
	tmpPanel.add( rgbPanel );
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

	/** Set a new image */
	public void newImage( int ch, ByteBuffer img, PXL_TYPE bytePerPixel) {
		ic.setImage( ch, img, bytePerPixel);
	}


	public void resizeImageBuffer(int w, int h) {	
		imageWidth = w;
		imageHeight = h;
		ic.resizeImageBuffer(w,h);
	}

    /** Return the GUI panel for the component */
    public JPanel getPanel() {
	return mainPanel;
    }

	public boolean isDisplayInSRGB() {
		return ic.displayInSRGB;
	}
	public void setDisplayInSRGB(boolean displayInSRGB) {
		displayInSRGB_checkbox.setSelected(displayInSRGB);
		ic.displayInSRGB = displayInSRGB;
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
	    
	    //System.out.println("img len: "+imgData.length);
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
	protected int viewportX=0, viewportY=0;
	final int viewportWidth, viewportHeight;
	final int nrChannels;
	int imageWidth=0, imageHeight=0;
	private boolean displayInSRGB = true;
   
	IUpdate ourUpdateListener = null;
	    
	int crosshair = 0;
	Color crosshairColor = Color.GRAY;

	final int gammaLookupTableSize = 1024;

	int [] scalMax, scalMin;
	int [] currentImgMin, currentImgMax;
	double [] gamma;
        final boolean[] show;

	float [][] imgBufferLinearChannels ;
	byte  []   imgDataBufferSRGB ;
	final byte  [] imgDataOnScreen   ;

	float [][] colorCoeff;
	final float [][] gammaLookupTable ;

	int zoomLevel=1;

        public ImageComponent(int ch, int w, int h) {
	    
			imageWidth = w; imageHeight = h;
			viewportWidth=w; viewportHeight=h; 
			nrChannels = ch;
		
			setIgnoreRepaint(true);
			bufferedImage   = new BufferedImage(viewportWidth,viewportHeight, BufferedImage.TYPE_3BYTE_BGR);
			
			imgBufferLinearChannels = new float[nrChannels][imageWidth*imageHeight];
			imgDataBufferSRGB = new  byte[3*imageWidth*imageHeight];	    
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

			// to get all updates on the mouse
			MouseAdapter mouseAdapter = new MouseAdapter() {
				
				int dragStartX = 0, dragStartY = 0;
				int lastViewportX = 0, lastViewportY = 0;
				int lastZoomX = 0, lastZoomY = 0;
				
				@Override
				public void mouseWheelMoved( MouseWheelEvent e ) {
					int xPosMid = viewportWidth/2 - e.getX();
					int yPosMid = viewportHeight/2 - e.getY();
					//System.out.println("Scroller: "+e.getWheelRotation());
					//System.out.println("mouse clicked: "+x+" "+y);

					int viewportX_center = viewportX + (viewportWidth/zoomLevel)/2;
					int viewportY_center = viewportY + (viewportHeight/zoomLevel)/2;
					
					// zoom out 
					if (e.getWheelRotation()<0) {
						if (zoomLevel<8) {
							viewportX_center -= xPosMid/zoomLevel;
							viewportY_center -= yPosMid/zoomLevel;							
							zoomLevel++;
							viewportX_center += xPosMid/zoomLevel;
							viewportY_center += yPosMid/zoomLevel;							
							
							viewportX = (viewportX_center - (viewportWidth/zoomLevel)/2);
							viewportY = (viewportY_center - (viewportHeight/zoomLevel)/2);
							//System.out.println("new viewport: "+viewportX

							if (viewportX + (viewportWidth/zoomLevel) > imageWidth) viewportX = imageWidth - (viewportWidth/zoomLevel);
							if (viewportY + (viewportHeight/zoomLevel) > imageHeight) viewportY = imageHeight - (viewportHeight/zoomLevel);
							if (viewportX < 0) viewportX = 0;
							if (viewportY < 0) viewportY = 0;
						}
					} else {
						if (zoomLevel>1) {
							viewportX_center -= xPosMid/zoomLevel;
							viewportY_center -= yPosMid/zoomLevel;							
							zoomLevel--;
							viewportX_center += xPosMid/zoomLevel;
							viewportY_center += yPosMid/zoomLevel;							
							
							viewportX = (viewportX_center - (viewportWidth/zoomLevel)/2);
							viewportY = (viewportY_center - (viewportHeight/zoomLevel)/2);
							if (viewportX < 0) viewportX = 0;
							if (viewportY < 0) viewportY = 0;
							if (viewportX + (viewportWidth/zoomLevel) > imageWidth) viewportX = imageWidth - (viewportWidth/zoomLevel);
							if (viewportY + (viewportHeight/zoomLevel) > imageHeight) viewportY = imageHeight - (viewportHeight/zoomLevel);
						}
					}
					ourUpdateListener.newZoomOrPosition( zoomLevel, viewportX, viewportY, viewportWidth, viewportHeight);
					paintImage();
				}
				
				@Override
				public void mousePressed(MouseEvent e) {
					int x = e.getX();
					int y = e.getY();
					boolean leftButton = (e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0;

					// store the initial position for dragging
					if (leftButton) {
						dragStartX = x;
						dragStartY = y;
						lastViewportX = viewportX;
						lastViewportY = viewportY;
						//System.out.println("Mouse pressed: "+x+" "+y+" left button state "+leftButton);
					}
				}			
				
				@Override
				public void mouseDragged(MouseEvent e) {
					int x = e.getX();
					int y = e.getY();
				
					boolean leftButton = (e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0;
					boolean rightButton = (e.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) != 0;
					boolean middleButton = (e.getModifiersEx() & MouseEvent.BUTTON2_DOWN_MASK) != 0;
				
					if (leftButton ) {
						int moveX = (dragStartX - x)/zoomLevel;
						viewportX = moveX + lastViewportX;
						int moveY = (dragStartY - y)/zoomLevel;
						viewportY = moveY + lastViewportY;
						if (viewportX<0) {
							viewportX=0;
						}
						if (viewportY<0) { 
							viewportY=0;
						}
						if (viewportX+viewportWidth/zoomLevel > imageWidth) viewportX = imageWidth-viewportWidth/zoomLevel;
						if (viewportY+viewportHeight/zoomLevel > imageHeight) viewportY = imageHeight-viewportHeight/zoomLevel;
						//System.out.println("new viewport: "+viewportX+" "+viewportY);
					}
			
					if (ourUpdateListener != null) {
						// update the viewport
						ourUpdateListener.newZoomOrPosition(zoomLevel, viewportX, viewportY, viewportWidth, viewportHeight);
					}
					paintImage();
				}
			};

			this.addMouseListener(mouseAdapter);
			this.addMouseMotionListener(mouseAdapter);
			this.addMouseWheelListener(mouseAdapter);
		}

	public void resizeImageBuffer(int w, int h) {
	    if (w==imageWidth && h==imageHeight) return;
	    imageWidth = w; imageHeight = h;
	    imgBufferLinearChannels = new float[nrChannels][imageWidth*imageHeight];
	    imgDataBufferSRGB = new byte[3 * imageWidth*imageHeight];
	}

	public void setImage( int ch, float [] img ) {
	    if (img.length != imageWidth*imageHeight )
		throw new RuntimeException("Input array size does not match " + img.length + "/" 
			+ imageWidth + "/" + imageHeight);
	    System.arraycopy( img, 0, imgBufferLinearChannels[ch], 0, img.length);
	}
	
	public void setImage( int ch, Vec2d.Real img ) {
	    if (img.vectorWidth() != imageWidth || img.vectorHeight()!=imageHeight)
		throw new RuntimeException("Input vector size mismatch " + imageWidth + "/" + imageHeight);
	    setImage( ch, img.vectorData());
	}
		
	public void setImage( int ch, short [] pxl ) {
	    if (pxl.length != imageWidth*imageHeight ) 
		throw new RuntimeException("Input array size does not match: " + pxl.length + "/" 
			+ imageWidth + "/" + imageHeight);

	    for (int i=0; i<pxl.length; i++) {
			imgBufferLinearChannels[ch][i] = ((int)pxl[i]) & 0xFFFF; // make sure it is unsigned
	    }
	}

	/** Directly set the image from a ByteBuffer */
	public void setImage( int ch, ByteBuffer img,  PXL_TYPE bytePerPixel) {

		int imgSize = imageWidth * imageHeight * bytePerPixel.getByteCount();
		if (img.remaining() < imgSize)
			throw new IllegalArgumentException("Insufficient data in ByteBuffer. Expected: " + imgSize + ", Available: " + img.remaining());

		int numPixels = imageWidth * imageHeight;

		if (bytePerPixel == PXL_TYPE.UINT_8) {
			// Bulk copy for 8-bit data
			byte[] tempBuffer = new byte[numPixels];
			img.get(tempBuffer);
			for (int i = 0; i < numPixels; i++) {
				imgBufferLinearChannels[ch][i] = (float)(tempBuffer[i] & 0xFF);
			}
		} else if (bytePerPixel == PXL_TYPE.UINT_16) {
			// Bulk copy for 16-bit data
			short[] tempBuffer = new short[numPixels];
			img.asShortBuffer().get(tempBuffer);
			for (int i = 0; i < numPixels; i++) {
				imgBufferLinearChannels[ch][i] = (float)(tempBuffer[i] & 0xFFFF);
			}
		} else if (bytePerPixel == PXL_TYPE.FLOAT_32) {
			// Direct bulk copy for float data
			img.asFloatBuffer().get(imgBufferLinearChannels[ch]);
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

	public void setViewportPosition( int x, int y ) {
		viewportX = x-(viewportWidth/2/zoomLevel);
		viewportY = y-(viewportHeight/2/zoomLevel);
		
		if (viewportX<0) viewportX=0;
		if (viewportY<0) viewportY=0;
		if (viewportX+viewportWidth/zoomLevel >= imageWidth) {
			viewportX = imageWidth-viewportWidth/zoomLevel;
		};
		if (viewportY+viewportHeight/zoomLevel >= imageHeight) viewportY = imageHeight-viewportHeight/zoomLevel;
		if (ourUpdateListener != null) {
			// update the viewport
			ourUpdateListener.newZoomOrPosition(zoomLevel, viewportX, viewportY, viewportWidth, viewportHeight);
		}
		paintImage();
	}

	// TOOD: move this to an "image processing" class, I guess
	void paintImage() {

	    // for all pixels
		new SimpleMT.PFor(0,imageHeight, 1,4) {
		@Override 
		public void at(int y) {
	    
		float [] linRGB = new float[3];
	    float [] cieXYZ = new float[3];
		//for (int y=0; y<imageHeight; y++)
	    for (int x=0; x<imageWidth; x++) {
	
		// 0 - zero current pixel
		for (int col=0; col<3; col++) {
		    cieXYZ[ col ] =0;
		    linRGB[ col ] =0;
		}

		// 1 - convert all channels pixels to linear CIE XYZ
		for (int ch=0; ch<nrChannels; ch++) {
            if(show[ch]) {
				// find min / max (not needed here, but stored for histogram)
				float val = imgBufferLinearChannels[ch][ x + y*imageWidth ];
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
					// TODO: capping the color lookup table was a cheap trick to avoid color clipping in
					// sRGB conversion, but it is not really correct. We can fix that at some point :-)
					cieXYZ[ col ] += colorCoeff[ch][col] * out; // ;* (displayInSRGB ? .3f : 1.0f);
				}
			}
		}
	
		// 2 - clip the conversion input
		for (int col=0; col<3; col++) {
		    if ((cieXYZ[col]) > 1.0f) cieXYZ[col]=1.0f;
		    if ((cieXYZ[col]) < 0.0f) cieXYZ[col]=0.0f;
		}

		if (displayInSRGB) {
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
				imgDataBufferSRGB[3 * (y*imageWidth + x ) + i  ] = 
				gammaSRGB[ (int)(linRGB[i] * gammaSRGB.length) ];
			}
		} else {
			// use the input directly as sRGB pixels
			for (int i=0; i<3; i++) {
				imgDataBufferSRGB[3 * (y*imageWidth + x ) + i  ] = 
				(byte)(cieXYZ[i] * 255); // scale to 0-255
			}
		}
		};};};

	    // this handles 'zoom' and 'pan'
	    if (zoomLevel==1 && viewportWidth==imageWidth && viewportHeight==imageHeight) {
			System.arraycopy( imgDataBufferSRGB, 0 , imgDataOnScreen, 0, 3*viewportHeight*viewportWidth);
		} else {
			for (int y=0; y<viewportHeight; y++)	
			for (int x=0; x<viewportWidth;  x++) {
			
				int xPos = x/zoomLevel + viewportX;
				int yPos = y/zoomLevel + viewportY;
			
				for (int i=0; i<3;  i++) {
					if (xPos<0 || xPos>=imageWidth || yPos<0 || yPos>=imageHeight) {
						// out of bounds, set to black
						imgDataOnScreen[3*(x + y*viewportWidth)+i] = (byte)0;
					} else {
						// copy the pixel from the buffer
						imgDataOnScreen[3*(x + y*viewportWidth)+i] = imgDataBufferSRGB[3*(xPos + yPos*imageWidth)+i];
					}
				}
			}
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
		    Line2D line1 = new Line2D.Double(
				viewportWidth/2.,viewportHeight/10.,viewportWidth/2.,viewportHeight*9./10.);
		    Line2D line2 = new Line2D.Double(
				viewportWidth/10.,viewportHeight/2.,viewportWidth*9./10.,viewportHeight/2.);
		    ((Graphics2D)g).draw(line1);
		    ((Graphics2D)g).draw(line2);
		}
		if ( crosshair%2 == 0 ) {
		    Line2D line1 = new Line2D.Double(
				viewportWidth/10. , viewportHeight/10. , viewportWidth*9./10., viewportHeight*9./10.);
		    Line2D line2 = new Line2D.Double(
				viewportWidth*9/10.,viewportHeight/10., viewportWidth/10., viewportHeight*9./10.);
		    ((Graphics2D)g).draw(line1);
		    ((Graphics2D)g).draw(line2);
		}
	    }
	    if ( crosshair > 2 ) {
		double size = (viewportWidth+viewportHeight)/4.;
		Ellipse2D cir = new Ellipse2D.Double(
			viewportWidth/2.-size/2, viewportHeight/2.-size/2, size,size);
		((Graphics2D)g).draw(cir);
	    }

	}



	/** Set the zoom level and midpoint position */
	public void setZoom( int level, int xPos, int yPos ) {
	    if (level<1 || level>8 )
		return;
	    if (level==1) {
		zoomLevel=1;
		this.paintImage();	
		if (ourUpdateListener!=null)
		    ourUpdateListener.newZoomOrPosition( zoomLevel, 
			    viewportX, viewportY, viewportWidth, viewportHeight);
	    
		return;
	    }
	    
	    int wSize =  viewportWidth/level;
	    int hSize = viewportHeight/level;
	    xPos-=wSize/2;
	    yPos-=hSize/2;
	    xPos = Math.max(xPos,0);
	    yPos = Math.max(yPos,0);
	    xPos = Math.min(xPos, viewportWidth-wSize-1);
	    yPos = Math.min(yPos, viewportHeight-hSize-1);
	    zoomLevel=level;
		viewportX = xPos - (viewportWidth/2/zoomLevel);
		viewportY = yPos - (viewportHeight/2/zoomLevel);
		if (viewportX<0) viewportX=0;
		if (viewportY<0) viewportY=0;
		if (viewportX+viewportWidth/zoomLevel > imageWidth) viewportX = imageWidth - viewportWidth/zoomLevel;
		if (viewportY+viewportHeight/zoomLevel > imageHeight) viewportY = imageHeight - viewportHeight/zoomLevel;

	    this.paintImage();	

	    //System.out.println("Updated ROI: "+xPos+" "+yPos+"/"+zoomX+" "+zoomY+" l:"+zoomLevel);
	    if (ourUpdateListener!=null)
		ourUpdateListener.newZoomOrPosition( zoomLevel, viewportX, viewportY, viewportWidth, viewportHeight);
	}

	
    
	public void setUpdateListener( IUpdate l ) {
	    ourUpdateListener = l;
	}
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(viewportWidth, viewportHeight);
        }
 
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(viewportWidth, viewportHeight);
        }
 
        @Override
        public Dimension getMinimumSize() {
            return new Dimension(viewportWidth, viewportHeight);
        }
 
        @Override
        protected void paintComponent(Graphics g) {
            g.drawImage(bufferedImage, 0, 0, null);
        }
    }
    
    public interface IUpdate {
	    public void newZoomOrPosition( int level, int viewportX, int viewportY, 
		    int viewportWidth, int viewportHeight);
	}

	class MiniMap extends JComponent {

		final int our_width, our_height;
		final BufferedImage bufferedImage;
	 	int img_w, img_h, vp_x, vp_y, vp_w, vp_h, zoom_level;
		final int zoom_x, zoom_y;

		MiniMap(int w, int h) {
			our_width = w;
			our_height = h;
			bufferedImage = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
			img_w = 2048;
			img_h = 2048;
			vp_x = 0; vp_y = 0; vp_w = w; vp_h = h;
			zoom_x = 0; zoom_y = 0; zoom_level = 1;

			this.addMouseListener(new MouseAdapter() {
				// On double click, get coordinates and set the viewport
				@Override
				public void mouseClicked(MouseEvent e) {
					if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
						// Double click detected
					int x = e.getX();
					int y = e.getY();
					
					// Convert minimap coordinates to image coordinates
					int imageX = (x * img_w) / our_width;
					int imageY = (y * img_h) / our_height;
					
					// TODO: Set the main viewport to center on this position
					// You might want to call a method on the parent ImageComponent
					ic.setViewportPosition(imageX, imageY);
					}
					if (e.getButton() == MouseEvent.BUTTON3) {
						// Right click centers the viewport
						ic.setViewportPosition(img_w / 2, img_h / 2);
					}
				}
			});

		}

		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.drawImage(bufferedImage, 0, 0, null);
		}
		
		@Override
		public void paint(Graphics g) {
			super.paint(g);
			Graphics2D g2d = (Graphics2D) g;
			g2d.setColor(Color.RED);
	
			// calculate viewport size
			int vp_w_scaled = (vp_w * our_width) / (img_w * zoom_level);
			int vp_h_scaled = (vp_h * our_height) / (img_h * zoom_level);
			int vp_x_scaled = ((vp_x + zoom_x) * our_width) / img_w ;
			int vp_y_scaled = ((vp_y + zoom_y) * our_height) / img_h ;

			// draw the viewport rectangle
			g2d.drawRect(vp_x_scaled, vp_y_scaled, vp_w_scaled, vp_h_scaled);
		}
	
		public void updateViewport(int x, int y, int w, int h, int zoomLevel) {
			this.vp_x = x;
			this.vp_y = y;
			this.vp_w = w;
			this.vp_h = h;
			this.zoom_level = zoomLevel;

			// repaint the minimap
			repaint();
		}
		public void updateImageSize(int w, int h) {
			this.img_w = w;
			this.img_h = h;
		}
		
	}

    /** Main method for easy testing */
    public static void main( String [] arg ) throws java.io.IOException, InterruptedException {
	
	if (arg.length<2) {
	    System.out.println("Usage for test: image-size channels [viewport-size]");
	    return;
	}

	final int size = Integer.parseInt( arg[0]);
	final int nrCh = Integer.parseInt( arg[1]);

	int viewportSize = size;
	if (arg.length>2) {
	    viewportSize = Integer.parseInt( arg[2]);
	}
	final int width=size, height=size;

	System.out.println(String.format(" image size: %d, channels: %d, viewport size: %d", 
	    size, nrCh, viewportSize));

	// create an ImageDisplay sized 512x512
	PlainImageDisplay pd = new PlainImageDisplay(nrCh, viewportSize,viewportSize);
	if ( size != viewportSize ) {
	    pd.resizeImageBuffer(width,height);
	}
	
	// create a frame and add the display
	JFrame mainFrame = new JFrame("Plain Image Receiver");
	mainFrame.add( pd.getPanel() ); 
	
	mainFrame.pack();
	mainFrame.setLocation( 100, 100 );
	mainFrame.setVisible(true);

	float [][] pxl = new float[100][width*height];

	Tool.Timer t1 = Tool.getTimer();
	Tool.Timer t2 = Tool.getTimer();

	for (int ch = 0; ch < nrCh; ch++) 
	new SimpleMT.PFor(0,100) {
		public void at(int i) {
			Random rnd = new Random(42*i);
			for (int y=0;y<height;y++)
			for (int x=0;x<width;x++) {
				if ( (x>200 && x<250) || (y>150 && y<190) ) {
					pxl[i][x+y*width]=(float)(500 +rnd.nextGaussian()*Math.sqrt(500));
				} else 
				if ( (x>size-200 && x<size-150) || (y>size-300 && y<size-250) ) {
					pxl[i][x+y*width]=(float)(1400+rnd.nextGaussian()*Math.sqrt(1400));
				} else {
					pxl[i][x+y*width]=(float)(2400+rnd.nextGaussian()*Math.sqrt(2400));
				}
			}
		};
	};

	while (true) {
	    t1.start();
		long frametime=0;
		for (int i=0;i<100;i++) {
		t2.start();
		for (int ch = 0; ch < nrCh; ch++) {
		    float [] pxls = pxl[(int)(Math.random()*99)]; 
		    pd.newImage(ch, pxls);
		}
		
		pd.refresh();
		t2.stop();
		frametime += t2.msElapsed();
		Thread.sleep(25);
	    }
	    t1.stop();
	    System.out.println( "fps: "+((1000*100)/t1.msElapsed()+ " frametime "+frametime/100+" ms") );
	}



    }


}
