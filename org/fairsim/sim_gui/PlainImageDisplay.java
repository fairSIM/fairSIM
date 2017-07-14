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
import javax.swing.Box;
import java.awt.GridBagConstraints;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.BorderFactory;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JCheckBox;

import java.util.List;
import java.util.ArrayList;

import org.fairsim.linalg.Vec2d;
import org.fairsim.utils.Tool;

public class PlainImageDisplay {

    protected final JPanel mainPanel ;
    protected final ImageComponent ic ;
	
    List<HistogramDisplay> histList = new ArrayList<HistogramDisplay>();

    public PlainImageDisplay(int w, int h) {
	this(1,w,h);
    }

    public void refresh() {
	ic.paintImage();
    }

    enum LUT {

	GREY(0), 
	CYAN(3),
	MAGENTA(5),
	RED(4),
	GREEN(2),
	BLUE(1),
	YELLOW(6);
	
	int val=0;
	public int getInt() { return val; }
	LUT(int i) { val=i; }
    };

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
	JPanel channelsPanel = new JPanel();
	channelsPanel.setLayout( new BoxLayout( channelsPanel, BoxLayout.PAGE_AXIS ));


	for (int ch = 0; ch < nrChannels; ch++) {
	    
	    final int channel = ch;

	    // sliders and buttons
	    final JSlider sMin = new JSlider(JSlider.HORIZONTAL, 0, 16*100, 0);
	    final JSlider sMax = new JSlider(JSlider.HORIZONTAL, 200, 16*100, 12*100);
	    final JButton autoMin = new JButton("auto");
	    final JButton autoMax = new JButton("auto");
	    final JLabel  valMin = new JLabel( String.format("% 5d",sMin.getValue()));
	    final JLabel  valMax = new JLabel( String.format("% 5d",sMax.getValue()));
	    
	    final JSlider sGamma = new JSlider(JSlider.HORIZONTAL, 10, 300,100);
	    final JLabel  lGamma = new JLabel(String.format("g%4.2f", sGamma.getValue()/100.));
	    final JButton bGamma1 = new JButton("1.0");
	    final JButton bGamma2 = new JButton("2.2");

	    final HistogramDisplay hist = new HistogramDisplay(300,100);

	    histList.add( hist );

	    sMin.addChangeListener( new ChangeListener() {
		public void stateChanged(ChangeEvent e) {
		    double exponent = sMin.getValue()/100.;
		    int val = (int)Math.pow( 2, exponent );

		    if (sMax.getValue() -200 < exponent*100 )
			sMax.setValue( (int)(exponent*100)+200 );
		    
		    //valMin.setText(String.format("2^%4.2f -> % 5d",exponent, val));
		    valMin.setText(String.format("% 5d",val));

		    ic.scalMin[0] = val;
		    ic.paintImage();
		}
	    });

	    sMax.addChangeListener( new ChangeListener() {
		public void stateChanged(ChangeEvent e) {
		    
		    double exponent = sMax.getValue()/100.;
		    int val = (int)Math.pow( 2, exponent );

		    if (sMin.getValue() +200 > exponent*100 )
			sMin.setValue( (int)(exponent*100)-200 );
		    
		    //valMax.setText(String.format("2^%4.2f -> % 5d",exponent, val));
		    valMax.setText(String.format("% 5d",val));
		    
		    //int val = sMax.getValue();
		    //if (sMin.getValue()+9>val)
		    //    sMin.setValue(val-10);
		    //valMax.setText(String.format("% 5d",val));
		    
		    ic.scalMax[channel] = val;
		    ic.paintImage();
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
		    lGamma.setText(String.format("g%4.2f",gamma));
		    ic.recalcGammaTable( channel, gamma );
		    ic.paintImage();
		}
	    });
	    
	    bGamma1.addActionListener( new ActionListener() {
		public void actionPerformed( ActionEvent e ) {
		    sGamma.setValue( 100 );
		}
	    });
	    bGamma2.addActionListener( new ActionListener() {
		public void actionPerformed( ActionEvent e ) {
		    sGamma.setValue( 220 );
		}
	    });

	    // sliders setting min/max
	    JPanel sliders = new JPanel(new GridBagLayout());
	    GridBagConstraints c = new GridBagConstraints();	
	    
    
	    // min slider, histogram, max slider
	    c.gridx=1; c.gridy=1; c.gridwidth=10; c.gridheight=1;
	    sliders.add( sMin, c );
	    c.gridx=1; c.gridy=2; c.gridwidth=10; c.gridheight=5;
	    sliders.add( hist,c );
	    c.gridx=1; c.gridy=7; c.gridwidth=10; c.gridheight=1;
	    sliders.add( sMax, c );

	    
	    c.gridx=1; c.gridy=1; c.gridwidth=1; c.gridheight=1;
	    sliders.add( valMin,c );
	    c.gridx=10; c.gridy=7;
	    sliders.add( valMax,c);
	

	    c.gridx=1; c.gridy=8; c.gridwidth=2; c.gridheight=1;
	    sliders.add( lGamma , c);
	    c.gridx=4; c.gridy=8; c.gridwidth=8; c.gridheight=1;
	    sliders.add( sGamma , c);
	    
	    /* 
	    c.gridx=7; c.gridy=0; c.gridwidth=2;
	    sliders.add( autoMin, c );
	    c.gridy=1;
	    sliders.add( autoMax, c ); */
	
	    /*
	    c.gridx=7; c.gridwidth=1;
	    sliders.add( bGamma1 ,c );
	    c.gridx=8; 
	    sliders.add( bGamma2 ,c ); */
	    

	    // Lut selector
            
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
	    
	    c.gridx=1; c.gridy=11; c.gridwidth=4;
	    sliders.add(lutSelector,c);
            
            // show checkBox
            final JCheckBox showCheckBox = new JCheckBox("Show Channel", true);
            showCheckBox.addActionListener( new ActionListener() {
		public void actionPerformed( ActionEvent e ) {
		    ic.show[channel] = showCheckBox.isSelected();
		}
	    });
            c.gridx=6; c.gridy=11; c.gridwidth=4;
            sliders.add(showCheckBox,c);
	   
	    String chName="Ch "+channel;
	    if ( names.length > channel )
		chName = names[channel];

	    JPanel perChannelPanel = new JPanel();
	    perChannelPanel.setBorder( BorderFactory.createTitledBorder(chName));
	    perChannelPanel.add( sliders );

	    channelsPanel.add( perChannelPanel );
	}

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
	mainPanel.add(channelsPanel);

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


	private int pointCounter =0;
    
	void setData( float [] dat, float min, float max) {


	    final int nrBins = width;
	    final float inc = (max-min)/(nrBins+1);

	    int [] count = new int[ nrBins ];

	    for (float v : dat ) {
		int pos = (int) ((v-min)/inc);
		if (pos>=0 && pos < nrBins )
		    count[pos]++;
	    }
	    
	    
	    int maxCount = 0;
	    for (int i : count ){
		if (maxCount < i) maxCount=i;
	    }


	    for (int y=0; y<height; y++) {
		for (int x=0; x<width; x++) {
		    
		    if (count[x]*height > (height-y-1)*maxCount) {
			imgData[(y*width+x)*3+0]=(byte)250;
			imgData[(y*width+x)*3+1]=(byte)250;
			imgData[(y*width+x)*3+2]=(byte)250;
		    } else {
			imgData[(y*width+x)*3+0]=0;
			imgData[(y*width+x)*3+1]=0;
			imgData[(y*width+x)*3+2]=0;
		    } 

		}
	    }


	    this.repaint();
	}



	final byte  [] imgData   ;

	HistogramDisplay(int w, int h) {
	    
	    setIgnoreRepaint(true);
	   
	    width = w; height= h;
	    
	    bufferedImage = new BufferedImage(width,height, BufferedImage.TYPE_3BYTE_BGR);
	    imgData = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();

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

	final int gammaLookupTableSize = 1024;

	int [] scalMax, scalMin;
	int [] currentImgMin, currentImgMax;
	double [] gamma;
        final boolean[] show;

	final float [][] imgBuffer ;
	final byte  [] imgData   ;
	final byte  [] imgDataBuffer ;
	final short  [][]   gammaLookupTable ;
	final short  [][][] colorLookupTable ;

	int zoomLevel=1, zoomX=0, zoomY=0;

        public ImageComponent(int ch, int w, int h) {
	    
	    width=w; height=h; nrChannels = ch;
	   
	    setIgnoreRepaint(true);
	    bufferedImage = new BufferedImage(width,height, BufferedImage.TYPE_3BYTE_BGR);
	    imgBuffer	  = new float[nrChannels][w*h];
	    
	    imgDataBuffer = new  byte[3*w*h];
	    
	    imgData = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();
	    
	    gammaLookupTable = new short[nrChannels][ gammaLookupTableSize ];
	    colorLookupTable = new short[nrChannels][256][3];

	    // init values
	    scalMax = new int[ch];
	    scalMin = new int[ch];
	    currentImgMin = new int[ch];
	    currentImgMax = new int[ch];
	    gamma = new double[ch];
            show = new boolean[ch];

	    for (int c=0; c<nrChannels; c++) {
		scalMin[c]=0; scalMax[c]=255;
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

	// adapted from imageJ
	void primaryColor(int color, short[][] values) {
	    // gray
	    if (color==0) {
		for (short i=0; i<256; i++) {
                    values[i][0] = i; // blue
                    values[i][1] = i; // green
                    values[i][2] = i; // red
		}
	    } else {
	    // 1-6: blue, green, cyan, red, magenta, yellow
            for (short i=0; i<256; i++) {
		values[i][0]=values[i][1]=values[i][2]=0;
		
		if ((color&4)!=0)
                    values[i][2] = i; // red
            	if ((color&2)!=0)
                    values[i][1] = i; // green
            	if ((color&1)!=0)
                    values[i][0] = i; // blue
        	}
	    }
	}


	public void setImage( int ch, float [] img ) {
	    if (img.length != width*height )
		throw new RuntimeException("Input array size does not match " + img.length + "/" + width + "/" + height);
	    System.arraycopy( img, 0, imgBuffer[ch], 0, width*height);
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
		imgBuffer[ch][i] = val;
	    }
	}


	public void recalcGammaTable( int ch, double gamma ) {
	    this.gamma[ch] = gamma;
	    for (int i=0; i<gammaLookupTableSize; i++) {
		gammaLookupTable[ch][i] = (short)(255*Math.pow(1.*i / gammaLookupTableSize, gamma));
	    }
	}

	public void setColorTable( int channel, LUT lut ) {
	    primaryColor( lut.getInt(), colorLookupTable[channel]);	   
	    //Tool.trace("set lut to: "+lut.toString()+" "+lut.getInt());
	}


	public void paintImage() {

	    for (int y=0; y<height; y++)
	    for (int x=0; x<width; x++) {
	
		short r=0,g=0,b=0;

		for (int ch=0; ch<nrChannels; ch++) {
                    if(show[ch]) {
                        // scale
                        float val = imgBuffer[ch][ x + y*width ];
                        if (val> currentImgMax[ch]) currentImgMax[ch] = (int)val;
                        if (val< currentImgMin[ch]) currentImgMin[ch] = (int)val;
                        float out=0;
                        if ( val >= scalMax[ch] ) out=1;
                        if ( val <  scalMin[ch] ) out=0;
                        if ( val >= scalMin[ch] && val < scalMax[ch] ) 
                            out = 1.f*(val - scalMin[ch]) / (scalMax[ch]-scalMin[ch]) ;

                        // correct for gamma
                        short pxl = gammaLookupTable[ch][ (int)(out*(gammaLookupTableSize-1)) ];

                        // apply lookup
                        b+= colorLookupTable[ch][pxl][0];
                        g+= colorLookupTable[ch][pxl][1];
                        r+= colorLookupTable[ch][pxl][2];
                    }
		}
		
		// clip to 0..255
		if (b>255) b=255;
		if (g>255) g=255;
		if (r>255) r=255;

		// set to output
		imgDataBuffer[3 * (y*width + x ) + 0 ] = (byte)b;
		imgDataBuffer[3 * (y*width + x ) + 1 ] = (byte)g;
		imgDataBuffer[3 * (y*width + x ) + 2 ] = (byte)r;
	    
	    }
	    
	    if (zoomLevel==1) {
		System.arraycopy( imgDataBuffer, 0 , imgData, 0, 3*width*height);
	    } else {
		for (int y=0; y<height; y++)	
		for (int x=0; x<width;  x++)	
		for (int i=0; i<3;  i++)	
		    imgData[3*(x + y*width)+i] = imgDataBuffer[ 
		     3*( (x/zoomLevel+zoomX) + width*(y/zoomLevel+zoomY))+i];
	    }
	    
	    this.repaint();
	}

	/** Set the zoom level and midpoint position */
	public void setZoom( int level, int xPos, int yPos ) {
	    if (level<1 || level>8 )
		return;
	    if (level==1) {
		zoomLevel=1;
		zoomX=0; zoomY=0;
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
    public static void main( String [] arg ) throws java.io.IOException {
	
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
	mainFrame.setVisible(true);

	float [][] pxl = new float[100][width*height];

	Tool.Timer t1 = Tool.getTimer();

	for (int ch = 0; ch < nrCh; ch++) 
	for (int i=0;i<100;i++) {
	    for (int y=0;y<height;y++)
	    for (int x=0;x<width;x++) {
		if ( (x<200 || x>220) && (y<150 || y>170) )
		    pxl[i][x+y*width]=(float)(1000+Math.random()*250);
		else
		    pxl[i][x+y*width]=(float)(100+Math.random()*50);
	    }
	}

	while (true) {
	    t1.start();
	    for (int i=0;i<100;i++) {
		for (int ch = 0; ch < nrCh; ch++) {
		    float [] pxls = pxl[(int)(Math.random()*99)]; 
		    pd.newImage(ch, pxls);
		    pd.histList.get(ch).setData(pxls, 0, 2000);
		}
		
		pd.refresh();
	    }
	    t1.stop();
	    System.out.println( "fps: "+((1000*100)/t1.msElapsed()) );
	}

	/*
	// create a network receiver
	ImageReceiver ir = new ImageReceiver(64,512,512);
	
	ir.addListener( new ImageReceiver.Notify() {
	    public void message(String m, boolean err, boolean fatal) {
		if (err || fatal) {
		    System.err.println((fatal)?("FATAL"):("Error")+" "+m);
		} else {
		    System.out.println("Recvr: "+m);
		}
	    }
	});
	
	// start receiving
	ir.startReceiving( null, null);
	int count=0; 
	double max=0;
	
	Vec2d.Real imgVec = Vec2d.createReal( 512, 512);
	
	while ( true ) {
	    ImageWrapper iw = ir.takeImage();
	   
	    iw.writeToVector( imgVec );

	    ir.recycleWrapper( iw );

	    double avr = imgVec.sumElements();
	    avr /= (iw.width() * iw.height());

	    max = Math.max(avr,max);

	    count++;
	    if (count%250==0) {
		Tool.trace("max avr pxl val: "+max);
           	max=0;
	    }
	
	    if (iw!=null)
		pd.newImage( imgVec );
	}   
	*/



    }


}
