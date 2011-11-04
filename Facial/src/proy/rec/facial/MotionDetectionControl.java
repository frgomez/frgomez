package proy.rec.facial;
/**
 * Title:        Motion Detection Control toolkit
 * Copyright 2002 Konrad Rzeszutek <konrad AT darnok DOT org>
 * @version 1.0
 *
 *
 *    This file is part of Motion Detection toolkit.
 *
 *    Motion Detection toolkit is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    Motion Detection toolkit is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with Foobar; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.media.Control;

public class MotionDetectionControl implements Control, ActionListener, ChangeListener  {

  Component component;
  JButton button;
  JSlider threshold;
  MotionDetectionEffect motion;


  public MotionDetectionControl(MotionDetectionEffect motion) {

    this.motion = motion;

  }

  public Component getControlComponent () {

    if (component == null) {

      button = new JButton("Debug");
      button.addActionListener(this);

      button.setToolTipText("Click to turn debugging mode on/off");

      threshold = new JSlider(JSlider.HORIZONTAL,
                               0,
                               motion.THRESHOLD_MAX,
                               motion.THRESHOLD_INIT);

      threshold.setMajorTickSpacing(motion.THRESHOLD_INC);
      threshold.setPaintLabels(true);
      threshold.addChangeListener(this);

      Panel componentPanel = new Panel();
      componentPanel.setLayout(new BorderLayout());
      componentPanel.add("East", button);
      componentPanel.add("West", threshold);
      componentPanel.invalidate();
      component = componentPanel;
    }
    return component;
  }

  public void actionPerformed(ActionEvent e) {

    Object o = e.getSource();

    if (o == button) {
      if (motion.debug == false)
         motion.debug = true;
      else motion.debug = false;

    }

  }
  public void stateChanged(ChangeEvent e) {

    Object o = e.getSource();
    if (o == threshold) {
      motion.blob_threshold = threshold.getValue()*1000;
    }
  }
}
