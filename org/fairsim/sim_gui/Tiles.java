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

import java.util.List;
import java.util.ArrayList;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.JComponent;

import javax.swing.BoxLayout;
import javax.swing.Box;
import java.awt.Component;
import java.awt.Color;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.Dimension;


/** Various GUI components */
public class Tiles {


    /** Labeled JSpinner to select numbers */
    public static class LNSpinner extends JPanel {

	private List<NumberListener> listener = new ArrayList<NumberListener>();
    
	/** Access to the spinner */
	final public JSpinner spr; 
	
	/** Create a spinner
	 *  @param label Label text in front of spinner 
	 *  @param start Initial value
	 *  @param min	 Minmal value
	 *  @param max	 Maximal value
	 *  @param inc   Increment */
	public LNSpinner( String label, double start, double min, double max, double inc ) { 
	    super();
	    super.setLayout( new BoxLayout(this, BoxLayout.LINE_AXIS));
	    
	    final JLabel jl = new JLabel(label);
	    final LNSpinner ref = this;

	    spr = new JSpinner( new SpinnerNumberModel(start,min,max,inc));
	    spr.setMaximumSize( spr.getPreferredSize() );;
	    //spr.setEditor( new JSpinner.NumberEditor( spr, "##0.00"));

	    spr.addChangeListener( new ChangeListener() {
	        public void stateChanged( ChangeEvent e ) {
		   for ( NumberListener i : listener ) {
			i.number( ref.getVal(), ref );
		   }
		}
	    }); 
	    
	    super.add( Box.createRigidArea(new Dimension(5,0)));
	    super.add( Box.createHorizontalGlue());
	    super.add( jl );
	    super.add( Box.createRigidArea(new Dimension(5,0)));
	    super.add( spr );
	    super.add( Box.createHorizontalGlue());
	    super.add( Box.createRigidArea(new Dimension(5,0)));
	}

	@Override
	public void setEnabled(boolean onoff) {
	    spr.setEnabled(onoff);
	}


	/** Get the spinners current value */
	public double getVal() {
	    return ((Number)spr.getValue()).doubleValue();
	}
    
	/** Add a NumberListener */
	public void addNumberListener( NumberListener l ) {
	    listener.add( l );
	}
	
	/** Remove a NumberListener */
	public void removeNumberListener( NumberListener l ) {
	    listener.remove( l );
	}



    }

    /** Notification that an LNSpinner changed to a new number */
    public interface NumberListener {
	/** Gets called with the new number */
	public void number(double n, LNSpinner e);

    }


   

    /** Labeled drop-down selection box */
    public static class LComboBox<T> extends JPanel {
	
	boolean suppressEvents=false;

	final JLabel jl;

	List<SelectListener<T>> listener 
	    = new ArrayList<SelectListener<T>>();

	final Color defaultBackground = this.getBackground();

	/** Access to the ComboBox. */
	final public TComboBox<T> box;
	
	/** 
	 * @param label Label in front of box
	 * @param opts  Selectable elements */
	public LComboBox(String label, T ... opts ) {
	    this( label, (java.awt.Component)null, false, opts );
	}

	/** 
	 * @param label Label in front of box
	 * @param addComp Additional component, added directly after the box
	 * @param opts  Selectable elements */
	public LComboBox(String label, java.awt.Component addComp, T ... opts ) {
	    this( label, addComp, false, opts );
	}
	
	/** 
	 * @param label Label in front of box
	 * @param addComp Additional component, added directly after the box
	 * @param showToolTip if true, display the full text for each entry as tooltip
	 * @param opts  Selectable elements */
	public LComboBox(String label, java.awt.Component addComp, 
	    boolean showToolTip, T ... opts ) {
	    
	    super();
	    super.setLayout( new BoxLayout(this, BoxLayout.LINE_AXIS));
	    jl = new JLabel(label);
	    
	    if ((opts!=null)&&(opts.length>0))
		box = new TComboBox<T>(opts);
	    else
		box = new TComboBox<T>();
	  
	    //box.setMaximumSize( box.getPreferredSize() );;

	    box.addActionListener( new ActionListener() {
	        public void actionPerformed( ActionEvent e ) {
		    if (!suppressEvents ) {
			for ( SelectListener<T> i : listener )
			    i.selected(  getSelectedItem(), box.getSelectedIndex() );
		    }
		}
	    });
	   

	    // display tooltip with full file name
	    if (showToolTip) {
		box.setRenderer( new DefaultListCellRenderer() {
		    @Override
		    public Component getListCellRendererComponent(JList list, Object value,
			int index, boolean isSelected, boolean cellHasFocus) {
			
			JComponent comp = (JComponent) super.getListCellRendererComponent(list,
			    value, index, isSelected, cellHasFocus);

			if (-1 < index && null != value ) {
			    list.setToolTipText( value.toString() );
			}
			return comp;
		    }
		});
	    }

	    super.add( Box.createHorizontalGlue());
	    super.add( jl );
	    super.add( Box.createRigidArea(new Dimension(5,0)));
	    super.add( box );
	    super.add( Box.createHorizontalGlue());
	    if (addComp!=null) {
		super.add( Box.createRigidArea(new Dimension(5,0)));
		super.add( addComp );
	    }
	}

	/** Returns the currently selected item. Might return
	 * 'null' if the list is empty. */
	@SuppressWarnings("unchecked")
	public T getSelectedItem() {
	    return (T)box.getSelectedItem();
	}

	public int getSelectedIndex() {
	    return box.getSelectedIndex();
	}

	public void setSelectedIndex(int i) {
	    box.setSelectedIndex(i);
	}

	@Override
	public void setEnabled(boolean onoff) {
	    box.setEnabled(onoff);
	}

	public void setLabelColor( Color c ) {
	    jl.setForeground(c);
	}
	
	public void setBackgroundColor( Color c ) {
	    if (c==null) c = defaultBackground;
	    super.setBackground(c);
	}


	/** Add a listener to be notified when the selection changes */
	public void addSelectListener( SelectListener<T> l ) {
	    listener.add(l);
	}
	/** Remove the listener */
	public void removeSelectListener( SelectListener<T> l ) {
	    listener.remove(l);
	}
    
	/** Fill the selector box with new elements.
	 *  If the currently selected element is contained in the new list,
	 *  it will be selected again. Otherwise, the first element is selected,
	 *  and an event is send.*/
	public void newElements( T ... opts ) {
	    newElements(-1, opts );
	}

	/** Fill the selector box with new elements, select the i'th element. */
	public void newElements( int idx, T ... opts ) {
	    
	    suppressEvents=true;

	    T curSel = getSelectedItem(); 
	    box.removeAllItems();
	    boolean newSelection=true;
	    
	    // only add new elements if opts is not empty
	    if (( opts != null ) && ( opts.length > 0)) {
		for ( T a : opts ) {
		    box.addItem( a );
		    if (( a.equals( curSel ) )&&(idx<0)) {
			box.setSelectedItem( a );
			newSelection=false;
		    }
		}
		if (idx>=0)
		    box.setSelectedIndex(idx);
	    } 
	    // set to empty 
	    else {
		    box.setSelectedIndex(-1);
	    }

	    if (newSelection)
		for ( SelectListener<T> i : listener )
		    i.selected(  getSelectedItem(), box.getSelectedIndex() );
	    
	    suppressEvents=false;
	}   

    }
   
   /** Listener to be called if things get selected */
    public interface SelectListener<T> {
	/* Selected element, its index, calling object. */
	public void selected(T e, int i); 
    } 
    
   
   /** Provides a type-save combo-box, like in java7.
     *  Wrapper around JComboBox, to fix java-1.6 to java-1.7 issue */
    public static class TComboBox<T> extends JComboBox {
	public TComboBox(T [] e) {
	    super(e);
	}
	public TComboBox() {
	    super();
	}
    };

    /** Container */
    public static class Container<T> {
	private T val;
	/** Construct a new container */
	public Container(T i) {
	    val=i;
	}
	/** set the container to a value */
	public void set(T i ) {
	    val = i;
	}
	/** get hte container value */
	public T get() {
	    return val;
	}
    }



}
