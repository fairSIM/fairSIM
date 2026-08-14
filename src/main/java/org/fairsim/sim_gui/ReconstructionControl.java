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
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.SwingWorker;

import java.awt.Dimension;
import java.awt.ComponentOrientation;
import java.awt.Component;
import java.awt.Color;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import org.fairsim.utils.Tool;
import org.fairsim.utils.ImageSelector;
import org.fairsim.utils.ImageDisplay;

import org.fairsim.sim_algorithm.OtfProvider;
import org.fairsim.sim_algorithm.SimParam;
import org.fairsim.sim_algorithm.SimAlgorithm;

import org.fairsim.linalg.Vec2d;

/**
 * GUI elements to control parameter fitting
 **/
public class ReconstructionControl {

	private final JPanel ourContent = new JPanel();
	final JLabel ourState = new JLabel("Parameters not known");
	boolean paramAvailable = false;
	boolean paramFitFailed = false;

	private final JFrame baseframe;
	private final SimParam simParam;
	private final SimParamGUI simp;
	private final ImageControl imgc;
	private ImageDisplay.Factory idpFactory;

	private String[] verbosityString = new String[] { "result only", "standard", "more", "most", "full" };

	private volatile boolean running = false;

	int verbosity = 0;

	public JPanel getPanel() {
		return ourContent;
	}

	/** Contructor, initializes image list. */
	public ReconstructionControl(final JFrame baseframe,
			final ImageDisplay.Factory idpFactory,
			final ImageControl imgc,
			final SimParam simParam, final SimParamGUI simp) {

		// initialize variables
		this.baseframe = baseframe;
		this.simParam = simParam;
		this.idpFactory = idpFactory;
		this.imgc = imgc;
		this.simp = simp;

		// create our pnael
		ourContent.setLayout(new BoxLayout(ourContent, BoxLayout.PAGE_AXIS));
		ourContent.setBorder(BorderFactory.createTitledBorder("5 - Reconstruction"));

		// setup label
		ourState.setAlignmentX(Component.CENTER_ALIGNMENT);
		ourState.setForeground(Color.RED);

		JPanel p1 = new JPanel();
		p1.setLayout(new BoxLayout(p1, BoxLayout.LINE_AXIS));

		JButton setupRecon = new JButton("setup");
		p1.add(setupRecon);
		p1.add(Box.createRigidArea(new Dimension(5, 0)));

		JButton runRecon = new JButton("run");
		p1.add(runRecon);

		ourContent.add(Box.createRigidArea(new Dimension(0, 5)));
		ourContent.add(ourState);
		ourContent.add(Box.createRigidArea(new Dimension(0, 5)));
		ourContent.add(p1);

		// add listener
		runRecon.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				runRecon();
			}
		});
		// add listener
		setupRecon.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				displayDialog();
			}
		});

	}

	/** display the options dialog */
	void displayDialog() {

		final JDialog dialog = new JDialog(baseframe, "Setup reconstruction", true);

		JPanel p1 = new JPanel();
		p1.setLayout(new BoxLayout(p1, BoxLayout.PAGE_AXIS));

		// setup box for amount of feedback
		// used in dialog ...
		final Tiles.LComboBox<String> verbosityBox = new Tiles.LComboBox<String>("Intermediate results",
				verbosityString);
		verbosityBox.box.setToolTipText("<html>Amount of intermediate result to display</html>");
		verbosityBox.box.setSelectedIndex(verbosity + 1);
		p1.add(verbosityBox);
		p1.add(Box.createRigidArea(new Dimension(0, 5)));

		// image scaling box
		final Tiles.LComboBox<SimParam.CLIPSCALE> imgScaleBox =
				// new Tiles.LComboBox<String>("Output", "raw", "clip", "clip&scale" );
				new Tiles.LComboBox<SimParam.CLIPSCALE>("Output",
						SimParam.CLIPSCALE.values());
		imgScaleBox.box.setToolTipText("<html>raw: no scaling, keep negative values<br />" +
				"clip zeros: remove negative values<br />" +
				"clip&scale: scale output to 0..255<br />" +
				"(only effects 3 main results, not intermediate output)");
		imgScaleBox.box.setSelectedItem(simParam.getClipScale());

		p1.add(imgScaleBox);
		p1.add(Box.createRigidArea(new Dimension(0, 5)));

		// Filter selection
		final Tiles.LComboBox<SimParam.FilterStyle> filterTypeBox = new Tiles.LComboBox<SimParam.FilterStyle>(
				"Filter type",
				SimParam.FilterStyle.values());

		filterTypeBox.setSelectedItem(simParam.getFilterStyle());

		p1.add(filterTypeBox);
		p1.add(Box.createRigidArea(new Dimension(0, 5)));

		// Wiener parameter
		final Tiles.LNSpinner wienerParam = new Tiles.LNSpinner("Wiener parameter",
				simParam.getWienerFilter(),
				Math.min(0.005, simParam.getWienerFilter()),
				Math.max(0.5, simParam.getWienerFilter()), 0.0025);
		p1.add(wienerParam);
		p1.add(Box.createRigidArea(new Dimension(0, 5)));

		// APO shape
		final Tiles.LComboBox<OtfProvider.APO_SHAPE> apoShape = new Tiles.LComboBox<OtfProvider.APO_SHAPE>(
				"APO shape", OtfProvider.APO_SHAPE.values());
		apoShape.setSelectedItem(simParam.getApoShape());
		apoShape.box.setToolTipText("<html>Shape of the apodization<br />"
				+ "elliptical / stadium allow a different cutoff along vs. across<br />"
				+ "'APO angle', for data with resolution enhancement along one axis only");

		p1.add(apoShape);
		p1.add(Box.createRigidArea(new Dimension(0, 5)));

		// APO cutoff, along the main axis, with a button to set it from the
		// actual resolution enhancement measured for the current parameters
		final Tiles.LNSpinner apoCutOff = new Tiles.LNSpinner("APO cutoff",
				simParam.getApoCutoff(), 1.0, 2.5, 0.1);
		apoCutOff.spr.setToolTipText("<html>Cutoff freq. of the apodization<br />"
				+ "as factor of OTF cutoff.<br /> Set below 2 if the"
				+ "dataset does not reach full resolution enhancement");

		JButton apoCutOffAuto = new JButton("auto");
		apoCutOffAuto.setToolTipText("<html>Set to the resolution enhancement<br />"
				+ "of the direction with the highest measured shift");
		apoCutOffAuto.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (simParam.otf() == null)
					return;
				double best = 0;
				for (int d = 0; d < simParam.nrDir(); d++)
					best = Math.max(best, simParam.dir(d).getEstResImprovement());
				apoCutOff.setVal(best);
			}
		});

		JPanel apoCutOffRow = new JPanel();
		apoCutOffRow.setLayout(new BoxLayout(apoCutOffRow, BoxLayout.LINE_AXIS));
		apoCutOffRow.add(apoCutOff);
		apoCutOffRow.add(apoCutOffAuto);

		// APO cutoff, across the main axis (elliptical / stadium shapes only)
		final Tiles.LNSpinner apoCutOffMinor = new Tiles.LNSpinner("APO cutoff (minor)",
				simParam.getApoCutoffMinor(), 1.0, 2.5, 0.1);
		apoCutOffMinor.spr.setToolTipText("<html>Cutoff freq. of the apodization across 'APO angle'<br />"
				+ "as factor of OTF cutoff. Only used for elliptical / stadium shape.");

		p1.add(apoCutOffRow);
		p1.add(apoCutOffMinor);
		p1.add(Box.createRigidArea(new Dimension(0, 5)));

		// APO angle, pre-populated from the measured pattern angle (dir 0,
		// highest band) if available, in degrees
		double apoAngleDefault = Math.toDegrees(simParam.dir(0).getPxPyAngle(simParam.nrBand() - 1));
		final Tiles.LNSpinner apoAngle = new Tiles.LNSpinner("APO angle",
				apoAngleDefault, -180, 180, 1);
		apoAngle.spr.setToolTipText("<html>Rotation angle of the apodization's main axis, in degrees<br />"
				+ "Only used for elliptical / stadium shape. Pre-filled with the<br />"
				+ "pattern angle of direction 0, once parameter estimation has run.");

		p1.add(apoAngle);
		p1.add(Box.createRigidArea(new Dimension(0, 5)));

		// APO bend
		final Tiles.LNSpinner apoBend = new Tiles.LNSpinner("APO bend",
				simParam.getApoBend(), 0.1, 2.0, 0.1);
		apoBend.spr.setToolTipText("<html>Curvature of the apoditazion<br />"
				+ "Changes the medium frequency response of the reconstruction.");

		p1.add(apoBend);
		p1.add(Box.createRigidArea(new Dimension(0, 5)));

		// RL max iteration counter
		final Tiles.LNSpinner rlInterationCount = new Tiles.LNSpinner("RL iterations",
				simParam.getRLiterations(), 1, 200, 1);

		p1.add(rlInterationCount);
		p1.add(Box.createRigidArea(new Dimension(0, 5)));

		// ok and cancel buttons
		JButton ok = new JButton("Set");
		JButton cl = new JButton("Cancel");
		ok.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				verbosity = verbosityBox.getSelectedIndex() - 1;
				simParam.setFilterStyle(filterTypeBox.getSelectedItem());
				simParam.setWienerFilter(wienerParam.getVal());
				simParam.setApoCutoff(apoCutOff.getVal());
				simParam.setApoCutoffMinor(apoCutOffMinor.getVal());
				simParam.setApoBend(apoBend.getVal());
				simParam.setApoShape(apoShape.getSelectedItem());
				simParam.setApoAngle(Math.toRadians(apoAngle.getVal()));
				simParam.setRLiterations((int) rlInterationCount.getVal());
				simParam.setClipScale(imgScaleBox.getSelectedItem());
				dialog.dispose();
			}
		});

		cl.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dialog.dispose();
			}
		});

		JPanel p2 = new JPanel();
		p2.add(ok);
		p2.add(cl);

		// The dialog itself
		JPanel p3 = new JPanel();
		p3.setLayout(new BoxLayout(p3, BoxLayout.PAGE_AXIS));
		p3.setBorder(BorderFactory.createTitledBorder("Reconstruction"));
		p3.add(p1);
		p3.add(p2);

		dialog.add(p3);
		dialog.pack();
		dialog.setVisible(true);

	}

	/** just here to not clutter the ActionPerformed event with so much code */
	void runRecon() {

		// rudimentare checks
		if (simParam.otf() == null) {
			JOptionPane.showMessageDialog(baseframe,
					"No OTF available", "fairSIM error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (imgc.theFFTImages == null) {
			JOptionPane.showMessageDialog(baseframe,
					"No images available", "fairSIM error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (running) {
			JOptionPane.showMessageDialog(baseframe,
					"Reconstruction running, please wait", "fairSIM error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (!paramAvailable) {
			JOptionPane.showMessageDialog(baseframe,
					"<html>Reconstruction parameters not set<br>Run fit first</html>",
					"fairSIM error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (paramFitFailed) {
			int ret = JOptionPane.showConfirmDialog(baseframe,
					"<html>Reconstruction parameter fit likely failed<br />" +
							"Run reconstruction none the less?",
					"fairSIM warning",
					JOptionPane.YES_NO_OPTION);

			if (ret == JOptionPane.NO_OPTION) {
				return;
			}
		}

		// run
		ourState.setText("Running reconstruction");
		ourState.setToolTipText("Reconstruction performed in background");
		ourState.setForeground(Color.BLUE);

		/*
		 * Tool.Timer t1 = Tool.getTimer();
		 * SimAlgorithm.runReconstruction(
		 * simParam, imgc.theFFTImages, idpFactory, 2, false, t1);
		 * ourState.setText("Complete");
		 * ourState.setToolTipText("Reconstruction took "+t1);
		 * ourState.setForeground(Color.GREEN.darker());
		 * 
		 */
		(new SwingWorker<Object, Object>() {

			Tool.Timer t1 = Tool.getTimer();

			@Override
			public Object doInBackground() {
				running = true;
				// try {
				SimAlgorithm.runReconstruction(
						simParam, imgc.theFFTImages,
						idpFactory, verbosity, false,
						simParam.getClipScale(), t1);
				/*
				 * } catch (Exception e) {
				 * Tool.trace("Problem: "+e);
				 * e.printStackTrace();
				 * }
				 */
				return null;
			}

			@Override
			protected void done() {
				running = false;
				simp.refreshTable();
				ourState.setText("Complete");
				ourState.setToolTipText("Reconstruction took " + t1);
				ourState.setForeground(Color.GREEN.darker());
			}
		}).execute();
	}

}
