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

import org.fairsim.sim_algorithm.SimParam;
import org.fairsim.sim_algorithm.OtfProvider;
import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;

import java.util.List;
import java.util.ArrayList;

// we want to be able to serve as a table model,
// that is something that a JTable can display
// to do that, we implement the 'TableModel' interface
import javax.swing.table.TableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.event.TableModelListener;
import javax.swing.event.TableModelEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Dimension;



/**
 * Class to diplay the data in {@link SimParam}.
 * */
class SimParamGUI implements TableModel {

    private List<TableModelListener> tableListen = 
	new ArrayList<TableModelListener>();

    private boolean displayDeg=false;    // if true, use deg instead of rad
    private boolean displayLenAng=true;  // if true, use angle and length instead of px,py
    private int	    vectorLengthUnit=0;  // 0 - pxl, 1 - 1/micron, 2 - nanometer

    private final JFrame baseframe;

    final private SimParam par;
    final private JPanel ourContent = new JPanel();
    private JTable paramTable;



    SimParamGUI( JFrame baseframe, SimParam par ) {
	this.baseframe = baseframe;
	this.par = par;
	
	ourContent.setLayout(new BoxLayout(ourContent, 
	    BoxLayout.PAGE_AXIS));
	ourContent.setBorder(BorderFactory.createTitledBorder(
	    "SIM parameters") );

	ourContent.setLayout(new BoxLayout(ourContent, BoxLayout.PAGE_AXIS));

	// table
	JPanel tbPanel = new JPanel(); 
	{
	    tbPanel.setLayout(new BoxLayout(tbPanel, BoxLayout.PAGE_AXIS));
	    paramTable = new JTable( this );
	    tbPanel.add( paramTable.getTableHeader()) ;
	    tbPanel.add( paramTable );
	}

	JButton disp = new JButton("Parameters");
	disp.setAlignmentX(0);

	disp.addActionListener( new ActionListener () {
	    public void actionPerformed( ActionEvent e ) {
		displayDialog();
	    }
	});


	JPanel p1 = new JPanel();
	p1.add(disp);
	


	ourContent.add( tbPanel );
	ourContent.add( p1 );

    }


    void displayDialog() {

	final JDialog dialog = new JDialog(baseframe,"Parameter display",true);

	JPanel rbPanel = new JPanel(); 
	rbPanel.setLayout(new BoxLayout( rbPanel, BoxLayout.PAGE_AXIS));
	rbPanel.setBorder(BorderFactory.createTitledBorder(
	    "Parameter display") );
	
	Tiles.LComboBox<String> degOrRad 
	    = new Tiles.LComboBox<String>("Angle and phase in","rad","deg");
	degOrRad.addSelectListener( 
	    new Tiles.SelectListener<String>() {
	    public void selected(String e, int i) {
		displayDeg = (i==1);
		refreshTable();
	    }
	});


	Tiles.LComboBox<String> polOrCart 
	    = new Tiles.LComboBox<String>("Shift vector as","Angle/Length (pol.)","Shift px,py (cart.)");
	polOrCart.addSelectListener( 
	    new Tiles.SelectListener<String>() {
	    public void selected(String e, int i) {
		displayLenAng = (i==0);
		TableColumnModel col = paramTable.getColumnModel();
		col.getColumn(0).setHeaderValue( getColumnName(0) );
		col.getColumn(1).setHeaderValue( getColumnName(1) );
		paramTable.getTableHeader().repaint();
		refreshTable();
	    }
	});

	Tiles.LComboBox<String> pxlOrMicrons 
	    = new Tiles.LComboBox<String>("Shift vector length",
		"raw pixel","cycles/micron","wavelength nm");
	pxlOrMicrons.addSelectListener( 
	    new Tiles.SelectListener<String>() {
	    public void selected(String e, int i) {
		vectorLengthUnit = i;
		TableColumnModel col = paramTable.getColumnModel();
		col.getColumn(0).setHeaderValue( getColumnName(0) );
		col.getColumn(1).setHeaderValue( getColumnName(1) );
		paramTable.getTableHeader().repaint();
		refreshTable();
	    }
	});




	// save buttons
	JPanel p2 = new JPanel();
	p2.setLayout( new BoxLayout(p2, BoxLayout.LINE_AXIS));
	JButton save1 = new JButton("Save (w/o OTF)");
	JButton save2 = new JButton("Save (with OTF)");
	
	p2.add(save1);
	p2.add( Box.createRigidArea(new Dimension(5,5)));
	p2.add(save2);
	
	save1.addActionListener( new ActionListener () {
	    public void actionPerformed(ActionEvent e) {
		saveParam(false);
	    }
	});
	save2.addActionListener( new ActionListener () {
	    public void actionPerformed(ActionEvent e) {
		saveParam(true);
	    }
	});
	    
	save1.setToolTipText("Store SIM parameter set (without OTF)");
	save2.setToolTipText("Store SIM parameter set, include the OTF");

	// ok / close buttons
	JButton okcl = new JButton("ok/close");
	okcl.addActionListener( new ActionListener () {
	    public void actionPerformed(ActionEvent e) {
		dialog.dispose();
	    }
	});

	rbPanel.add( degOrRad );
	rbPanel.add( Box.createRigidArea(new Dimension(0,5)));
	rbPanel.add(polOrCart);
	rbPanel.add( Box.createRigidArea(new Dimension(0,5)));
	rbPanel.add(pxlOrMicrons);
	rbPanel.add( Box.createRigidArea(new Dimension(0,5)));
	rbPanel.add( p2 );
	rbPanel.add( Box.createRigidArea(new Dimension(0,5)));
	rbPanel.add(okcl);

	dialog.add( rbPanel);
	dialog.pack();
	dialog.setVisible(true);

    }
   
    /** Save dialog, to save all parameters */
    void saveParam( boolean saveOtf ) {
	
	JFileChooser fc = new JFileChooser();
	int returnVal = fc.showSaveDialog(baseframe);
	
	if (returnVal == JFileChooser.APPROVE_OPTION) {
	    String fname = fc.getSelectedFile().getAbsolutePath();
	    if (!fname.endsWith(".xml"))
		fname = fname+".xml";
	   
	    Conf cfg = new Conf("fairsim");
	    par.saveConfig(cfg.r());
	    
	    if (saveOtf && par.otf() != null)
		par.otf().saveConfig( cfg );
	    try {
		cfg.saveFile( fname );    
	    } catch ( Conf.SomeIOException e ) {
		JOptionPane.showMessageDialog( baseframe,
		    "Error saving: "+e, "Error saving",
		    JOptionPane.ERROR_MESSAGE);
		return;
	    }
	}
    }


    /** Return the JPanel containing the table and control elements */
    JPanel getPanel() {
	return ourContent;
    }

    /** refresh the table */
    void refreshTable( ) {
	for ( TableModelListener i : tableListen  ) {
	    i.tableChanged( new TableModelEvent( this ));
	}
    }

    /** For testing */
    public static void main( String [] arg ) {
	OtfProvider otf = OtfProvider.fromEstimate(1.4, 515, 0.35);
	JFrame mainframe = new JFrame();
	SimParamGUI test = new SimParamGUI( mainframe, dummySP() ) ;
	mainframe.add( test.getPanel() );
	mainframe.pack();
	mainframe.setVisible(true);
	mainframe.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE);
    }

    /** returns a dummy SimParam instance for testing the GUI */
    static SimParam dummySP() {
	SimParam sp = SimParam.create( 3, 3, 5, 512, 0.086, null);
	sp.dir(0).setPxPy(23.24,184.31);
	sp.dir(1).setPxPy(89.34,123.45);
	sp.dir(2).setPxPy(135.32,77.54);
	sp.dir(0).setPhaOff( 0.34);
	sp.dir(1).setPhaOff( 0.54);
	sp.dir(2).setPhaOff( -.45);
	return sp;
    }




    /** Wraps phase to -pi, pi, converts to deg if set */
    Double parseAngle( Double a ) {
	if ( a>Math.PI )
	    return parseAngle(a-2*Math.PI);
	if ( a<-Math.PI )
	    return parseAngle(a+2*Math.PI);
	if (displayDeg)
	    return a*180/Math.PI;
	return a;
    }


    // ---- Implementation of the table interface ----

  
    public void addTableModelListener(TableModelListener l) {
	tableListen.add(l);
    }

    public void removeTableModelListener(TableModelListener l) {
	tableListen.remove(l);
    }

    public void setValueAt( Object aValue , int row, int col) {
	// col0 holds angles
	/*
	switch (col) {
	    case 0:
		dir(row).setPxPy( (Double)aValue;
		break;
		angles.get(row).k0    = (Double)aValue;
		break;
	    case 2:
		angles.get(row).startPhase = (Double)aValue;
		break;
	    default:
		angles.get(row).phases.set(col-3, (Double)aValue);
	} */
    }

    // all our entries are doubles
    public Class getColumnClass(int index) {
	return java.lang.Double.class;
    }

    public int getRowCount() {
	return  par.nrDir();
    }

    public String getColumnName(int index) {
	String unit [] = new String[] { "[pxl]", "[1/µm]", "[nm]" };
	switch (index) {
	    case 0:
		return ((displayLenAng)?("Angle"):("px "+unit[vectorLengthUnit]));
	    case 1:
		return ((displayLenAng)?("k0"+unit[vectorLengthUnit]):("py "+unit[vectorLengthUnit]));
	    case 2:
		return ("Phase offs.");
	    default:
		return ("Pha#"+(index-3));
	}
    }

    public int getColumnCount() {
	int max =0;
	for (int i=0; i<par.nrDir(); i++) {
	    SimParam.Dir d = par.dir(i);
	    max = (max<d.nrPha())?(d.nrPha()):(max);
	}
	return max+3; // two more for the 'angle', 'k0' and phase entry
    }

    public Double getValueAt(int row, int col) {
	int hBand = par.dir(row).nrBand()-1; // highest band


	switch (col) {
	    case 0: // either angle or px length
		if (displayLenAng)
		    return parseAngle(  par.dir(row).getPxPyAngle( hBand ));
		else
		    switch (vectorLengthUnit) {
			case 0:	// raw pxl
			    return par.dir(row).px( hBand );
			case 1: // cycles / microns
			    return par.dir(row).px( hBand ) * par.pxlSizeCyclesMicron();
			case 2:
			    return 1000/(par.dir(row).px( hBand ) * par.pxlSizeCyclesMicron());
		    }
	    case 1: // either full kx len or py
		double tmplen = (displayLenAng)?
		  (par.dir(row).getPxPyLen( hBand ) ):
		  (par.dir(row).py(	    hBand ) );
		switch (vectorLengthUnit) {
		    case 0:	// raw pxl
			return tmplen;
		    case 1: // cycles / microns
			return tmplen * par.pxlSizeCyclesMicron();
		    case 2:
			return 1000/(tmplen * par.pxlSizeCyclesMicron());
		}

	    case 2:
		return parseAngle( par.dir(row).getPhaOff() );

	    default:
		if ( (col-3)<par.dir(row).nrPha() )
		   return parseAngle( par.dir(row).getPhase(col-3) );
		else
		   return null;
	}
    }

    public boolean isCellEditable(int i, int j) {
	return false;
    }


}





